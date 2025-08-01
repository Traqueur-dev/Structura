package fr.traqueur.structura;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.validation.Validator;
import org.yaml.snakeyaml.Yaml;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;


/**
 * StructuraProcessor is responsible for parsing YAML strings into Java record instances.
 * It supports validation, enum parsing, and handling of complex data structures.
 * The processor can be configured to validate settings on parse.
 */
public class StructuraProcessor {

    private static final String CAMEL_CASE_REGEX = "([a-z])([A-Z])";
    private static final String SNAKE_CASE_REGEX = "([A-Za-z0-9])_([A-Za-z0-9])";
    private static final String KEBAB_CASE_REPLACEMENT = "$1-$2";
    private static final String PATH_SEPARATOR = ".";
    private static final String PATH_SEPARATOR_REGEX = "\\.";

    private final boolean validateOnParse;
    private final ReentrantReadWriteLock enumLock;
    private final Yaml yaml;

    /**
     * Constructs a StructuraProcessor with the specified validation setting.
     * If validateOnParse is true, the processor will validate settings during parsing.
     *
     * @param validateOnParse Whether to validate settings on parse.
     */
    public StructuraProcessor(boolean validateOnParse) {
        this.yaml = new Yaml();
        this.validateOnParse = validateOnParse;
        this.enumLock = new ReentrantReadWriteLock();

    }

    /**
     * Parses a YAML string into an instance of the specified settings class.
     * The class must be a record type and implement the Loadable interface.
     *
     * @param yamlString The YAML string to parse.
     * @param settingsClass The class type to parse into.
     * @param <T> The type of the settings class.
     * @return An instance of the specified settings class populated with data from the YAML string.
     */
    public <T extends Loadable> T parse(String yamlString, Class<T> settingsClass) {
        if (yamlString == null || yamlString.isEmpty()) {
            throw new StructuraException("YAML string cannot be null or empty");
        }
        if (settingsClass == null) {
            throw new StructuraException("Settings class cannot be null");
        }

        Map<String, Object> settings = yaml.load(yamlString);
        T instance = settingsClass.cast(createInstance(settings, settingsClass, ""));
        if(validateOnParse) {
            Validator.INSTANCE.validate(instance, "");
        }
        return instance;
    }

    /**
     * Parses a YAML string into an enum type that implements Loadable.
     * The enum constants must be annotated with Options to specify their configuration.
     *
     * @param yamlString The YAML string to parse.
     * @param enumClass The enum class type to parse into.
     * @param <E> The type of the enum class.
     */
    public <E extends Enum<E> & Loadable> void parseEnum(String yamlString, Class<E> enumClass) {
        if (yamlString == null || yamlString.isEmpty()) {
            throw new StructuraException("YAML string cannot be null or empty");
        }
        enumLock.writeLock().lock();
        try {
            Map<String, Object> settings = yaml.load(yamlString);
            E[] enumConstants = enumClass.getEnumConstants();

            // Créer un mapping des noms kebab-case vers les constantes enum
            Map<String, E> enumByKebabCase = Arrays.stream(enumConstants)
                    .collect(Collectors.toMap(
                            e -> convertSnakeCaseToKebabCase(e.name()),
                            e -> e
                    ));

            for (Map.Entry<String, E> stringEEntry : enumByKebabCase.entrySet()) {
                String kebabCaseName = stringEEntry.getKey();
                E enumConstant = stringEEntry.getValue();

                if (!settings.containsKey(kebabCaseName)) {
                    throw new StructuraException("Missing data for enum constant: " + kebabCaseName);
                }
                Object data = settings.get(kebabCaseName);
                if (data == null) {
                    throw new StructuraException("Missing data for enum constant: " + kebabCaseName);
                }
                injectDataIntoEnum(enumConstant, data);
                if (validateOnParse) {
                    Validator.INSTANCE.validate(enumConstant, kebabCaseName);
                }
            }
        } finally {
            enumLock.writeLock().unlock();
        }
    }

    private Object createInstance(Map<String, Object> data, Class<?> recordClass, String prefix) {
        if (recordClass == null) {
            throw new StructuraException("Record class cannot be null");
        }
        if (!recordClass.isRecord()) {
            throw new StructuraException("Class " + recordClass.getName() + " is not a record type");
        }

        RecordComponent[] components = recordClass.getRecordComponents();

        // Check if any component is marked as key
        Optional<RecordComponent> keyComponent = Arrays.stream(components)
                .filter(this::isKeyComponent)
                .findFirst();

        if (keyComponent.isPresent()) {
            return createInstanceWithKeyMapping(data, recordClass, keyComponent.get(), prefix);
        }

        Object[] constructorArgs = buildConstructorArguments(components, data, recordClass, prefix);

        return instantiateRecord(recordClass, constructorArgs);
    }

    private boolean isKeyComponent(RecordComponent component) {
        return component.isAnnotationPresent(Options.class) &&
                component.getAnnotation(Options.class).isKey();
    }

    private Object createInstanceWithKeyMapping(Map<String, Object> data, Class<?> recordClass,
                                                RecordComponent keyComponent, String prefix) {
        RecordComponent[] components = recordClass.getRecordComponents();

        if (data.size() == 1) {
            Map.Entry<String, Object> entry = data.entrySet().iterator().next();
            String keyValue = entry.getKey();
            Object valueData = entry.getValue();

            Object[] args = new Object[components.length];

            for (int i = 0; i < components.length; i++) {
                RecordComponent component = components[i];
                if (component.equals(keyComponent)) {
                    args[i] = convertValue(keyValue, component.getType(), prefix);
                } else {
                    Parameter parameter = getConstructorParameter(recordClass, component.getName(), i);
                    args[i] = resolveComponentValue(component, parameter,
                            valueData instanceof Map ? (Map<String, Object>) valueData : Collections.emptyMap(),
                            prefix);
                }
            }

            return instantiateRecord(recordClass, args);
        }

        throw new StructuraException("Key-based mapping requires exactly one key-value pair");
    }

    private void injectDataIntoEnum(Enum<?> enumConstant, Object data) {
        Class<?> enumClass = enumConstant.getClass();

        Field[] fields = enumClass.getDeclaredFields();

        for (Field field : fields) {
            if (field.isSynthetic() || field.isEnumConstant() || Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object fieldValue = getFieldValueFromData(field, data);

                if(fieldValue == null) {
                    fieldValue = DefaultValueRegistry.getInstance().getDefaultValue(field.getType(),List.of(field.getAnnotations()));
                }

                if (fieldValue != null) {
                    field.set(enumConstant, fieldValue);
                }
            } catch (IllegalAccessException e) {
                throw new StructuraException("Cannot inject into enum field: " + field.getName(), e);
            }
        }
    }
    private Object getFieldValueFromData(Field field, Object data) {
        String fieldName = getFieldNameFromField(field);

        if (data instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) map;
            Object value = dataMap.get(fieldName);

            if (value != null) {
                return convertValue(value, field.getType(), "");
            }
        } else {
            // Si les données ne sont pas une map, essayer de les convertir directement
            if (field.getType().isAssignableFrom(data.getClass())) {
                return data;
            }
            return convertValue(data, field.getType(), "");
        }

        return null;
    }


    private Object[] buildConstructorArguments(RecordComponent[] components, Map<String, Object> data,
                                               Class<?> recordClass, String prefix) {
        Object[] args = new Object[components.length];

        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            Parameter parameter = getConstructorParameter(recordClass, component.getName(), i);
            args[i] = resolveComponentValue(component, parameter, data, prefix);
        }

        return args;
    }

    private Object instantiateRecord(Class<?> recordClass, Object[] args) {
        try {
            Constructor<?> constructor = recordClass.getDeclaredConstructors()[0];
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new StructuraException("Failed to create instance of " + recordClass.getName(), e);
        }
    }

    private Object resolveComponentValue(RecordComponent component, Parameter parameter,
                                         Map<String, Object> data, String prefix) {
        String fieldName = getEffectiveFieldName(parameter, component.getName());
        String fullPath = buildPath(prefix, fieldName);

        Object value = getValueFromPath(data, fullPath);

        if (value == null) {
            value = getDefaultValue(parameter, fullPath);
        }

        return value != null
                ? convertValueWithGenerics(value, component.getGenericType(), component.getType(), prefix)
                : null;
    }

    private Object getDefaultValue(Parameter parameter, String fullPath) {
        Object defaultValue = DefaultValueRegistry.getInstance().getDefaultValue(parameter.getType(), List.of(parameter.getAnnotations()));

        if (defaultValue == null && !isNullable(parameter)) {
            throw new StructuraException(fullPath + " is required but not provided");
        }

        return defaultValue;
    }

    private Object convertValueWithGenerics(Object value, Type genericType, Class<?> rawType, String prefix) {

        if (Collection.class.isAssignableFrom(rawType) && genericType instanceof ParameterizedType paramType) {
            return convertToTypedCollection(value, rawType, paramType, prefix);
        }

        if (Map.class.isAssignableFrom(rawType) && genericType instanceof ParameterizedType paramType) {
            return convertToTypedMap(value, paramType, prefix);
        }

        return convertValue(value, rawType, prefix);
    }

    private Collection<?> convertToTypedCollection(Object value, Class<?> collectionType,
                                                   ParameterizedType paramType, String prefix) {
        Type[] typeArgs = paramType.getActualTypeArguments();

        if (typeArgs.length == 0) {
            throw new StructuraException("Collection type missing generic parameter");
        }

        Class<?> elementType = getClassFromType(typeArgs[0]);
        Collection<Object> result = createCollectionInstance(collectionType);

        if (value instanceof List<?> list) {
            convertListToCollection(list, elementType, result, prefix);
        } else if (value instanceof Map<?, ?> map) {
            convertMapToCollection(map, elementType, result, prefix);
        } else {
            result.add(convertCollectionElement(value, elementType, prefix));
        }

        return result;
    }

    private Collection<Object> createCollectionInstance(Class<?> collectionType) {
        if (List.class.isAssignableFrom(collectionType)) {
            return new ArrayList<>();
        } else if (Set.class.isAssignableFrom(collectionType)) {
            return new HashSet<>();
        } else {
            throw new StructuraException("Unsupported collection type: " + collectionType.getName());
        }
    }

    private void convertListToCollection(List<?> list, Class<?> elementType,
                                         Collection<Object> result, String prefix) {
        for (Object item : list) {
            result.add(convertCollectionElement(item, elementType, prefix));
        }
    }

    private void convertMapToCollection(Map<?, ?> map, Class<?> elementType,
                                        Collection<Object> result, String prefix) {
        result.add(convertCollectionElement(map, elementType, prefix));
    }

    private Map<?, ?> convertToTypedMap(Object value, ParameterizedType paramType, String prefix) {
        Type[] typeArgs = paramType.getActualTypeArguments();

        if (typeArgs.length != 2) {
            throw new StructuraException("Map type must have exactly 2 generic parameters");
        }

        if (!(value instanceof Map<?, ?>)) {
            throw new StructuraException("Cannot convert " + value.getClass().getName() + " to Map");
        }

        Class<?> keyType = getClassFromType(typeArgs[0]);
        Class<?> valueType = getClassFromType(typeArgs[1]);

        @SuppressWarnings("unchecked")
        Map<String, Object> sourceMap = (Map<String, Object>) value;

        return sourceMap.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> convertValue(entry.getKey(), keyType, prefix),
                        entry -> convertCollectionElement(entry.getValue(), valueType, prefix)
                ));
    }

    private Object convertCollectionElement(Object item, Class<?> elementType, String prefix) {
        if (elementType.isAssignableFrom(item.getClass())) {
            return item;
        }

        if (isSettingsRecord(elementType) && item instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> itemMap = (Map<String, Object>) item;
            return createInstance(itemMap, elementType, prefix);
        }

        return convertValue(item, elementType, prefix);
    }

    private Object convertValue(Object value, Class<?> targetType, String prefix) {
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        if (isSettingsRecord(targetType) && value instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> valueMap = (Map<String, Object>) value;
            return createInstance(valueMap, targetType, prefix);
        }

        return convertPrimitiveValue(value, targetType);
    }

    private boolean isSettingsRecord(Class<?> type) {
        return type.isRecord() && Loadable.class.isAssignableFrom(type);
    }

    private Object convertPrimitiveValue(Object value, Class<?> targetType) {
        if (targetType == String.class) {
            return value.toString();
        }

        if (targetType.isEnum()) {
            if (value instanceof String strValue) {
                try {
                    @SuppressWarnings("unchecked")
                    Class<Enum> enumClass = (Class<Enum>) targetType;
                    return Enum.valueOf(enumClass, strValue.toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new StructuraException("Invalid enum value: " + strValue + " for type: " + targetType.getName(), e);
                }
            }
            throw new StructuraException("Cannot convert " + value.getClass().getName() + " to enum type: " + targetType.getName());
        }

        if (targetType == int.class || targetType == Integer.class) {
            return value instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(value.toString());
        }

        if (targetType == boolean.class || targetType == Boolean.class) {
            return value instanceof Boolean bool
                    ? bool
                    : Boolean.parseBoolean(value.toString());
        }

        if (targetType == long.class || targetType == Long.class) {
            return value instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(value.toString());
        }

        if (targetType == double.class || targetType == Double.class) {
            return value instanceof Number number
                    ? number.doubleValue()
                    : Double.parseDouble(value.toString());
        }

        return value;
    }

    private boolean isNullable(Parameter parameter) {
        if(!parameter.isAnnotationPresent(Options.class)) {
            return false;
        }
        Options options = parameter.getAnnotation(Options.class);
        return options.optional();
    }

    private Class<?> getClassFromType(Type type) {
        return switch (type) {
            case Class<?> clazz -> clazz;
            case ParameterizedType paramType -> (Class<?>) paramType.getRawType();
            default -> throw new StructuraException("Unsupported type: " + type);
        };
    }

    private Parameter getConstructorParameter(Class<?> recordClass, String componentName, int index) {
        try {
            Constructor<?> constructor = recordClass.getDeclaredConstructors()[0];
            Parameter[] parameters = constructor.getParameters();
            return parameters[index];
        } catch (Exception e) {
            throw new StructuraException("Unable to find parameter for component: " + componentName, e);
        }
    }

    private String getEffectiveFieldName(Parameter parameter, String defaultName) {
        if (parameter.isAnnotationPresent(Options.class)) {
            Options options = parameter.getAnnotation(Options.class);
            if (!options.name().trim().isEmpty()) {
                return options.name();
            }
        }
        return convertCamelCaseToKebabCase(defaultName);
    }

    private String convertSnakeCaseToKebabCase(String snakeCase) {
        return snakeCase.replaceAll(SNAKE_CASE_REGEX, KEBAB_CASE_REPLACEMENT).toLowerCase();
    }

    private String convertCamelCaseToKebabCase(String camelCase) {
        return camelCase.replaceAll(CAMEL_CASE_REGEX, KEBAB_CASE_REPLACEMENT).toLowerCase();
    }

    private String buildPath(String prefix, String fieldName) {
        return prefix.isEmpty() ? fieldName : prefix + PATH_SEPARATOR + fieldName;
    }

    private Object getValueFromPath(Map<String, Object> data, String path) {
        String[] pathParts = path.split(PATH_SEPARATOR_REGEX);
        Object current = data;

        for (String part : pathParts) {
            if (current instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stringMap = (Map<String, Object>) map;
                current = stringMap.get(part);
            } else {
                return null;
            }
        }

        return current;
    }

    private String getFieldNameFromField(Field field) {
        // Vérifier s'il y a une annotation @Options
        if (field.isAnnotationPresent(Options.class)) {
            Options options = field.getAnnotation(Options.class);
            if (!options.name().trim().isEmpty()) {
                return options.name();
            }
        }

        return convertCamelCaseToKebabCase(field.getName());
    }
}