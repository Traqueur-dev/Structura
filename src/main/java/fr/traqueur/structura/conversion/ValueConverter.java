package fr.traqueur.structura.conversion;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.factory.RecordInstanceFactory;
import fr.traqueur.structura.references.Reference;
import fr.traqueur.structura.references.ReferenceRegistry;
import fr.traqueur.structura.registries.CustomReaderRegistry;
import fr.traqueur.structura.registries.PolymorphicRegistry;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ValueConverter handles conversion of YAML values to appropriate Java types.
 * It manages conversions for primitives, collections, maps, and records.
 */
public class ValueConverter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final RecordInstanceFactory recordFactory;

    /**
     * Constructor for ValueConverter.
     *
     * @param recordFactory the factory to create record instances
     */
    public ValueConverter(RecordInstanceFactory recordFactory) {
        this.recordFactory = recordFactory;
    }

    /**
     * Converts a value to the target type with generic type handling.
     *
     * @param value the value to convert
     * @param genericType the generic target type (for collections/maps)
     * @param rawType the raw target type
     * @param prefix the prefix for error messages
     * @return the converted value
     */
    public Object convert(Object value, Type genericType, Class<?> rawType, String prefix) {
        if (value == null) return null;

        try {
            return convertValue(value, genericType, rawType, prefix);
        } catch (StructuraException e) {
            // The innermost conversion knows the most precise path: only fill it when absent
            throw e.atPath(prefix);
        } catch (RuntimeException e) {
            throw new StructuraException(StructuraException.Kind.WRONG_TYPE,
                    "cannot read " + describe(value) + " as " + describe(genericType) + ": " + e.getMessage(),
                    prefix, e);
        }
    }

    /**
     * Describes a YAML value for an error message: a string is quoted, a section or a list is named.
     *
     * @param value the value as read from the document
     * @return its description, such as {@code 'many'}, {@code 42}, {@code a section} or {@code a list}
     */
    public static String describe(Object value) {
        return switch (value) {
            case null -> "nothing";
            case String string -> "'" + string + "'";
            case Map<?, ?> ignored -> "a section";
            case Collection<?> ignored -> "a list";
            default -> String.valueOf(value);
        };
    }

    private static String describe(Type type) {
        return type instanceof Class<?> clazz ? clazz.getSimpleName() : type.getTypeName();
    }

    private Object convertValue(Object value, Type genericType, Class<?> rawType, String prefix) {
        // Try custom reader first (with generic type information)
        Optional<?> customResult = CustomReaderRegistry.getInstance().convert(value, genericType, rawType);
        if (customResult.isPresent()) {
            return customResult.get();
        }

        if (rawType.isAssignableFrom(value.getClass()) &&
                !needsSpecialConversion(rawType, genericType)) {
            return value;
        }

        rawType = this.getClassFromRegistry(rawType, value, prefix);

        if (rawType.isEnum()) {
            return convertToEnum(value, rawType);
        }

        if (Collection.class.isAssignableFrom(rawType) && genericType instanceof ParameterizedType paramType) {
            return convertToCollection(value, rawType, paramType, prefix);
        }

        if (Map.class.isAssignableFrom(rawType) && genericType instanceof ParameterizedType paramType) {
            return convertToMap(value, paramType, prefix);
        }

        if (Map.class.isAssignableFrom(rawType) && value instanceof Map<?, ?>) {
            return value;
        }

        if (Reference.class.isAssignableFrom(rawType) && genericType instanceof ParameterizedType refParamType) {
            return convertToReference(value, refParamType, prefix);
        }

        if (isSettingsRecord(rawType) && value instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> valueMap = (Map<String, Object>) value;
            return recordFactory.createInstance(valueMap, rawType, prefix);
        }

        return convertPrimitive(value, rawType, prefix);
    }

    /**
     * Détermine si un type a besoin d'une conversion spéciale même s'il est assignable.
     */
    private boolean needsSpecialConversion(Class<?> rawType, Type genericType) {
        // Les collections et maps avec génériques ont besoin d'une conversion spéciale
        return ((Collection.class.isAssignableFrom(rawType) || Map.class.isAssignableFrom(rawType))
                && genericType instanceof ParameterizedType)
                || (Reference.class.isAssignableFrom(rawType) && genericType instanceof ParameterizedType);
    }

    /**
     * Converts a value to a simple type (without generics).
     *
     * @param value the value to convert
     * @param targetType the target type
     * @param prefix the prefix for error messages
     * @return the converted value
     */
    public Object convert(Object value, Class<?> targetType, String prefix) {
        return convert(value, targetType, targetType, prefix);
    }

    /**
     * Converts a value to a typed collection.
     */
    private Collection<?> convertToCollection(Object value, Class<?> collectionType,
                                              ParameterizedType paramType, String prefix) {
        Type[] typeArgs = paramType.getActualTypeArguments();
        if (typeArgs.length == 0) {
            throw new StructuraException("Collection type missing generic parameter at " + prefix);
        }

        Type elementGenericType = typeArgs[0];
        Class<?> elementType = getClassFromType(elementGenericType);
        Collection<Object> result = createCollection(collectionType);

        // Check if element type uses key-as-discriminator mode
        boolean useKeyAsDiscriminator = isPolymorphicWithKeyAsDiscriminator(elementType);

        if (value instanceof List<?> list) {
            convertListElements(list, elementGenericType, elementType, result, prefix);
        } else if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> valueMap = (Map<String, Object>) value;
            convertMapToCollection(valueMap, elementGenericType, elementType, result, useKeyAsDiscriminator, prefix);
        } else {
            result.add(convert(value, elementGenericType, elementType, prefix));
        }

        return result;
    }

    /**
     * Converts list elements to the target collection type.
     *
     * @param list the source list
     * @param elementGenericType the generic type of elements (may include type parameters)
     * @param elementRawType the raw type of elements
     * @param result the target collection to populate
     * @param prefix the path prefix for error messages
     */
    private void convertListElements(List<?> list, Type elementGenericType, Class<?> elementRawType,
                                     Collection<Object> result, String prefix) {
        for (int i = 0; i < list.size(); i++) {
            result.add(convertOrReport(list.get(i), elementGenericType, elementRawType, prefix + "[" + i + "]"));
        }
    }

    /**
     * Converts one element of a list or one value of a map, reporting its error instead of aborting
     * the whole collection when the read collects errors, so that the following elements are still read.
     *
     * @return the converted value, or null if it failed and its error was collected
     */
    private Object convertOrReport(Object value, Type genericType, Class<?> rawType, String path) {
        try {
            return convert(value, genericType, rawType, path);
        } catch (StructuraException e) {
            ReadSession.report(e);
            return null;
        }
    }

    /**
     * The path of the value under a key of a section.
     */
    private static String childPath(String prefix, Object key) {
        return prefix.isEmpty() ? String.valueOf(key) : prefix + "." + key;
    }

    /**
     * Converts a map to a collection, handling polymorphic and record-based mappings.
     *
     * @param valueMap the source map
     * @param elementGenericType the generic type of elements (may include type parameters)
     * @param elementRawType the raw type of elements
     * @param result the target collection to populate
     * @param useKeyAsDiscriminator whether to use keys as discriminators
     * @param prefix the path prefix for error messages
     */
    private void convertMapToCollection(Map<String, Object> valueMap, Type elementGenericType, Class<?> elementRawType,
                                       Collection<Object> result,
                                       boolean useKeyAsDiscriminator, String prefix) {
        if (useKeyAsDiscriminator && Loadable.class.isAssignableFrom(elementRawType)) {
            convertPolymorphicMapEntries(valueMap, elementGenericType, elementRawType, result, prefix);
        } else if (shouldTreatMapAsMultipleRecords(valueMap, elementRawType)) {
            convertMapEntriesToRecords(valueMap, elementGenericType, elementRawType, result, prefix);
        } else {
            result.add(convert(valueMap, elementGenericType, elementRawType, prefix));
        }
    }

    /**
     * Converts map entries as polymorphic elements using keys as discriminators.
     *
     * @param valueMap the source map
     * @param elementGenericType the generic type of elements (may include type parameters)
     * @param elementRawType the raw type of elements
     * @param result the target collection to populate
     * @param prefix the path prefix for error messages
     */
    private void convertPolymorphicMapEntries(Map<String, Object> valueMap, Type elementGenericType, Class<?> elementRawType,
                                             Collection<Object> result, String prefix) {
        for (Map.Entry<String, Object> entry : valueMap.entrySet()) {
            String discriminatorValue = entry.getKey();
            Object itemValue = entry.getValue();

            String itemPath = childPath(prefix, discriminatorValue);
            Map<String, Object> enrichedValue = enrichWithDiscriminator(
                itemValue, discriminatorValue, elementRawType, itemPath
            );

            result.add(convertOrReport(enrichedValue, elementGenericType, elementRawType, itemPath));
        }
    }

    /**
     * Converts map entries to separate records.
     *
     * @param valueMap the source map
     * @param elementGenericType the generic type of elements (may include type parameters)
     * @param elementRawType the raw type of elements
     * @param result the target collection to populate
     * @param prefix the path prefix for error messages
     */
    private void convertMapEntriesToRecords(Map<String, Object> valueMap, Type elementGenericType, Class<?> elementRawType,
                                           Collection<Object> result, String prefix) {
        boolean hasKey = hasKeyComponent(elementRawType);
        for (Map.Entry<String, Object> entry : valueMap.entrySet()) {
            Object itemValue = entry.getValue();
            if (hasKey) {
                // Wrap as single-entry map so isSimpleKeyMapping works correctly
                Map<String, Object> wrappedEntry = Map.of(entry.getKey(), itemValue);
                result.add(convertOrReport(wrappedEntry, elementGenericType, elementRawType, prefix));
            } else {
                result.add(convertOrReport(itemValue, elementGenericType, elementRawType, childPath(prefix, entry.getKey())));
            }
        }
    }

    /**
     * Converts a value to a typed map.
     */
    private Map<?, ?> convertToMap(Object value, ParameterizedType paramType, String prefix) {
        Type[] typeArgs = paramType.getActualTypeArguments();
        if (typeArgs.length != 2) {
            throw new StructuraException("Map type must have exactly 2 generic parameters at " + prefix);
        }

        if (!(value instanceof Map<?, ?>)) {
            throw new StructuraException(StructuraException.Kind.WRONG_TYPE,
                    "Cannot convert " + describe(value) + " to a section (key: value)", prefix);
        }

        Class<?> keyType = getClassFromType(typeArgs[0]);
        Class<?> valueType = getClassFromType(typeArgs[1]);

        @SuppressWarnings("unchecked")
        Map<String, Object> sourceMap = (Map<String, Object>) value;

        // Check if value type uses key-as-discriminator mode
        boolean useKeyAsDiscriminator = isPolymorphicWithKeyAsDiscriminator(valueType);

        // Values are converted one by one (rather than through Collectors.toMap, which refuses a null
        // value) so that a failing value is reported and the following ones are still read.
        Map<Object, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : sourceMap.entrySet()) {
            String itemPath = childPath(prefix, entry.getKey());
            Object key = convert(entry.getKey(), typeArgs[0], keyType, itemPath);

            if (useKeyAsDiscriminator && Loadable.class.isAssignableFrom(valueType)) {
                // Use the map key as discriminator for the value
                try {
                    Map<String, Object> enrichedValue = enrichWithDiscriminator(
                        entry.getValue(), entry.getKey(), valueType, itemPath
                    );
                    result.put(key, convert(enrichedValue, typeArgs[1], valueType, itemPath));
                } catch (StructuraException e) {
                    ReadSession.report(e.atPath(itemPath));
                    result.put(key, null);
                }
            } else {
                // Thread the full generic Type so nested generics
                // (e.g. Map<String, Map<String, Loadable>>) resolve their innermost value type.
                result.put(key, convertOrReport(entry.getValue(), typeArgs[1], valueType, itemPath));
            }
        }
        return result;
    }

    /**
     * Converts a string value to a {@code Reference<T>} using the ReferenceRegistry.
     * If the value is already a {@code Reference<?>} (e.g., from {@code @DefaultReference}),
     * it is returned as-is.
     *
     * @param value     the YAML value (must be a String key or an existing Reference)
     * @param paramType the parameterized type of the Reference field
     * @param prefix    the path prefix for error messages
     * @return a lazy {@link Reference} resolved via {@link ReferenceRegistry}
     */
    private Reference<?> convertToReference(Object value, ParameterizedType paramType, String prefix) {
        if (value instanceof Reference<?> alreadyResolved) {
            return alreadyResolved;
        }
        if (!(value instanceof String stringValue)) {
            throw new StructuraException(StructuraException.Kind.WRONG_TYPE,
                "Cannot convert " + describe(value) + " to Reference (expected a String key)", prefix
            );
        }
        Type[] typeArgs = paramType.getActualTypeArguments();
        if (typeArgs.length != 1) {
            throw new StructuraException("Reference type must have exactly 1 generic parameter at " + prefix);
        }
        Class<?> referencedType = getClassFromType(typeArgs[0]);
        return ReferenceRegistry.getInstance().resolve(stringValue, referencedType);
    }

    /**
     * Converts primitive types and enums.
     */
    private Object convertPrimitive(Object value, Class<?> targetType, String prefix) {
        if (targetType == String.class) {
            return value.toString();
        }

        if (targetType.isEnum()) {
            return convertToEnum(value, targetType);
        }

        if(targetType == LocalDate.class) {
            try {
                return LocalDate.parse(value.toString(), DATE_FORMATTER);
            } catch (DateTimeParseException e) {
                throw wrongType(describe(value) + " is not a date (expected yyyy-MM-dd)", prefix);
            }
        }

        if(targetType == LocalDateTime.class) {
            try {
                return LocalDateTime.parse(value.toString(), DATE_TIME_FORMATTER);
            } catch (DateTimeParseException e) {
                throw wrongType(describe(value) + " is not a date and time (expected yyyy-MM-ddTHH:mm:ss)", prefix);
            }
        }

        try {
            if (targetType == int.class || targetType == Integer.class) {
                return value instanceof Number n ? n.intValue() : Integer.parseInt(scalarText(value, prefix));
            }

            if (targetType == boolean.class || targetType == Boolean.class) {
                return value instanceof Boolean b ? b : Boolean.parseBoolean(scalarText(value, prefix));
            }

            if (targetType == long.class || targetType == Long.class) {
                return value instanceof Number n ? n.longValue() : Long.parseLong(scalarText(value, prefix));
            }

            if (targetType == double.class || targetType == Double.class) {
                return value instanceof Number n ? n.doubleValue() : Double.parseDouble(scalarText(value, prefix));
            }

            if (targetType == float.class || targetType == Float.class) {
                return value instanceof Number n ? n.floatValue() : Float.parseFloat(scalarText(value, prefix));
            }

            if (targetType == byte.class || targetType == Byte.class) {
                return value instanceof Number n ? n.byteValue() : Byte.parseByte(scalarText(value, prefix));
            }

            if (targetType == short.class || targetType == Short.class) {
                return value instanceof Number n ? n.shortValue() : Short.parseShort(scalarText(value, prefix));
            }
        } catch (NumberFormatException e) {
            throw wrongType(describe(value) + " is not a number", prefix);
        }

        if (targetType == char.class || targetType == Character.class) {
            String str = scalarText(value, prefix);
            if (str.length() != 1) {
                throw wrongType("Cannot convert string of length " + str.length() + " to char: "
                        + describe(value) + " is not a single character", prefix);
            }
            return str.charAt(0);
        }

        if (isSettingsRecord(targetType) || (targetType.isInterface() && Loadable.class.isAssignableFrom(targetType))) {
            throw wrongType("expected a section (key: value) but got " + describe(value), prefix);
        }

        throw wrongType("Unsupported conversion from " + describe(value) +
                " to " + targetType.getName(), prefix);
    }

    /**
     * The text of a scalar, refusing a section or a list where a single value is expected.
     */
    private static String scalarText(Object value, String prefix) {
        if (value instanceof Map<?, ?> || value instanceof Collection<?>) {
            throw wrongType("expected a single value but got " + describe(value), prefix);
        }
        return value.toString();
    }

    private static StructuraException wrongType(String detail, String prefix) {
        return new StructuraException(StructuraException.Kind.WRONG_TYPE, detail, prefix);
    }

    /**
     * Converts a value to an enum.
     */
    private Object convertToEnum(Object value, Class<?> targetType) {
        if (value instanceof String strValue) {
            try {
                @SuppressWarnings("unchecked")
                Class<Enum> enumClass = (Class<Enum>) targetType;

                try {
                    return Enum.valueOf(enumClass, strValue);
                } catch (IllegalArgumentException e) {
                    return Enum.valueOf(enumClass, strValue.toUpperCase());
                }
            } catch (IllegalArgumentException e) {
                throw new StructuraException(StructuraException.Kind.WRONG_TYPE,
                        "Invalid enum value: " + strValue + " for type: " + targetType.getName() +
                        ". Available values: " + Arrays.toString(targetType.getEnumConstants()), null, e);
            }
        }
        throw new StructuraException(StructuraException.Kind.WRONG_TYPE,
                "Cannot convert " + describe(value) + " to enum type: " + targetType.getName(), null);
    }

    /**
     * Creates a collection instance according to the requested type.
     */
    private Collection<Object> createCollection(Class<?> collectionType) {
        if (List.class.isAssignableFrom(collectionType)) {
            return new ArrayList<>();
        } else if (Set.class.isAssignableFrom(collectionType)) {
            return new HashSet<>();
        } else {
            throw new StructuraException("Unsupported collection type: " + collectionType.getName());
        }
    }

    /**
     * Extracts the class from a Type (handles Class and ParameterizedType).
     */
    private Class<?> getClassFromType(Type type) {
        return switch (type) {
            case Class<?> clazz -> clazz;
            case ParameterizedType paramType -> (Class<?>) paramType.getRawType();
            default -> throw new StructuraException("Unsupported type: " + type);
        };
    }

    /**
     * Retrieves the class from the polymorphic registry if applicable.
     * If the class is not polymorphic, it returns the class itself.
     * If the value is null, it throws an exception.
     *
     * @param clazz the class to check
     * @param value the value to convert
     * @param prefix the path of the value in the document
     * @return the class from the registry or the original class
     */
    private Class<?> getClassFromRegistry(Class<?> clazz, Object value, String prefix) {
        if(clazz == null) {
            throw new StructuraException("Cannot convert null class type.");
        }
        if (value == null) {
            throw new StructuraException("Cannot convert null value to polymorphic type " + clazz.getName() + ".");
        }
        if(!Loadable.class.isAssignableFrom(clazz)) {
            return clazz;
        }
        //noinspection unchecked
        Class<? extends Loadable> loadableClass = (Class<? extends Loadable>) clazz;

        if(!clazz.isAnnotationPresent(Polymorphic.class)) {
            return loadableClass;
        }

        if(!(value instanceof Map<?, ?>)) {
            throw new StructuraException(StructuraException.Kind.WRONG_TYPE,
                    "Polymorphic type " + clazz.getName() + " requires a Map value for conversion, but got "
                            + describe(value), null);
        }
        //noinspection unchecked
        Map<String, Object> valueMap = (Map<String, Object>) value;

        Polymorphic polymorphic = loadableClass.getAnnotation(Polymorphic.class);
        String key = polymorphic.key();
        PolymorphicRegistry<?> registry = PolymorphicRegistry.get(loadableClass);

        if (!valueMap.containsKey(key)) {
            throw new StructuraException(StructuraException.Kind.MISSING,
                    "Polymorphic type " + clazz.getName() + " requires key '" + key + "' in value map.", null);
        }

        String typeName = valueMap.get(key).toString();
        return registry.get(typeName).orElseThrow(() ->
                new StructuraException(StructuraException.Kind.WRONG_TYPE,
                        "No registered type found for " + typeName + " in polymorphic type " + loadableClass.getName() +
                                ". Available types: " + String.join(", ", registry.availableNames()),
                        childPath(prefix, key)
                )
        );
    }

    /**
     * Checks if a type is a record that implements Loadable.
     */
    private boolean isSettingsRecord(Class<?> type) {
        return type.isRecord() && Loadable.class.isAssignableFrom(type);
    }

    /**
     * Checks if a type is polymorphic with useKeyAsDiscriminator enabled.
     *
     * @param type the type to check
     * @return true if the type has @Polymorphic(useKey = true)
     * @throws StructuraException if both inline and useKey are true
     */
    private boolean isPolymorphicWithKeyAsDiscriminator(Class<?> type) {
        if (!Loadable.class.isAssignableFrom(type)) {
            return false;
        }
        Polymorphic polymorphic = type.getAnnotation(Polymorphic.class);
        if (polymorphic == null) {
            return false;
        }

        return polymorphic.useKey();
    }

    /**
     * Gets all field names of a record type converted to kebab-case.
     * This is a utility method to avoid code duplication.
     *
     * @param recordType the record type
     * @return a set of field names in kebab-case
     */
    private Set<String> getRecordFieldNamesAsKebabCase(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
            .map(component -> recordFactory.getFieldMapper()
                .convertCamelCaseToKebabCase(component.getName()))
            .collect(Collectors.toSet());
    }

    /**
     * Determines if a Map should be treated as multiple records for a collection.
     * This is used for concrete (non-polymorphic) types where a YAML map structure
     * represents multiple record instances.
     *
     * @param valueMap the Map value from YAML
     * @param elementType the target element type
     * @return true if each map entry should become a separate record
     */
    private boolean shouldTreatMapAsMultipleRecords(Map<String, Object> valueMap, Class<?> elementType) {
        // Only applies to record types that implement Loadable
        if (!elementType.isRecord() || !Loadable.class.isAssignableFrom(elementType)) {
            return false;
        }

        // Empty map should result in empty list
        if (valueMap.isEmpty()) {
            return true;
        }

        // Get record field names (converted to kebab-case to match YAML)
        Set<String> recordFieldNames = getRecordFieldNamesAsKebabCase(elementType);

        // Check if all values in the map are Maps (indicating nested records)
        boolean allValuesAreMaps = valueMap.values().stream()
                .allMatch(v -> v instanceof Map);

        if (!allValuesAreMaps) {
            return false;
        }

        // Check if the keys of the map match the record field names
        // If they do, it's a single record. If they don't, it's multiple records.
        boolean keysMatchFields = recordFieldNames.containsAll(valueMap.keySet());

        // If keys don't match record fields AND all values are Maps,
        // treat as multiple records (each entry is a separate record)
        return !keysMatchFields;
    }

    /**
     * Enriches a value with a discriminator key for polymorphic resolution.
     * The discriminator key is added to the value map using the key from @Polymorphic annotation.
     *
     * @param value the value to enrich (will be converted to a Map if it isn't one)
     * @param discriminatorValue the discriminator value (typically the YAML key)
     * @param type the polymorphic type
     * @param prefix the path prefix for error messages
     * @return a Map with the discriminator key added
     */
    private Map<String, Object> enrichWithDiscriminator(Object value, String discriminatorValue,
                                                        Class<?> type, String prefix) {
        Polymorphic polymorphic = type.getAnnotation(Polymorphic.class);
        if (polymorphic == null) {
            throw new StructuraException("Type " + type.getName() + " is not annotated with @Polymorphic at " + prefix);
        }

        String discriminatorKey = polymorphic.key();

        // Convert value to Map if it isn't already
        Map<String, Object> valueMap;
        if (value instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> temp = (Map<String, Object>) value;
            valueMap = new HashMap<>(temp);
        } else if (value == null) {
            valueMap = new HashMap<>();
        } else {
            throw new StructuraException(
                "Expected Map for polymorphic type " + type.getName() + " at " + prefix +
                " but got " + value.getClass().getName()
            );
        }

        // Add discriminator key
        valueMap.put(discriminatorKey, discriminatorValue);

        return valueMap;
    }

    /**
     * Checks if a record type has a component marked with @Options(isKey = true).
     *
     * @param recordType the record type to check
     * @return true if the record has a key component
     */
    private boolean hasKeyComponent(Class<?> recordType) {
        if (!recordType.isRecord()) {
            return false;
        }
        return Arrays.stream(recordType.getRecordComponents())
            .anyMatch(component -> {
                Options options = component.getAnnotation(Options.class);
                return options != null && options.isKey();
            });
    }
}