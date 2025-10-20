package fr.traqueur.structura.conversion;

import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.factory.RecordInstanceFactory;
import fr.traqueur.structura.registries.CustomReaderRegistry;
import fr.traqueur.structura.registries.PolymorphicRegistry;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ValueConverter handles conversion of YAML values to appropriate Java types.
 * It manages conversions for primitives, collections, maps, and records.
 */
public class ValueConverter {

    private final RecordInstanceFactory recordFactory;

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

        // Try custom reader first
        Optional<?> customResult = CustomReaderRegistry.getInstance().convert(value, rawType);
        if (customResult.isPresent()) {
            return customResult.get();
        }

        if (rawType.isAssignableFrom(value.getClass()) &&
                !needsSpecialConversion(rawType, genericType)) {
            return value;
        }

        rawType = this.getClassFromRegistry(rawType, value);

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
        return (Collection.class.isAssignableFrom(rawType) || Map.class.isAssignableFrom(rawType))
                && genericType instanceof ParameterizedType;
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

        Class<?> elementType = getClassFromType(typeArgs[0]);
        Collection<Object> result = createCollection(collectionType);

        if (value instanceof List<?> list) {
            for (Object item : list) {
                result.add(convert(item, elementType, prefix));
            }
        } else if (value instanceof Map<?, ?> map) {
            result.add(convert(map, elementType, prefix));
        } else {
            result.add(convert(value, elementType, prefix));
        }

        return result;
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
            throw new StructuraException("Cannot convert " + value.getClass().getName() + " to Map at " + prefix);
        }

        Class<?> keyType = getClassFromType(typeArgs[0]);
        Class<?> valueType = getClassFromType(typeArgs[1]);

        @SuppressWarnings("unchecked")
        Map<String, Object> sourceMap = (Map<String, Object>) value;

        return sourceMap.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> convert(entry.getKey(), keyType, prefix),
                        entry -> convert(entry.getValue(), valueType, prefix)
                ));
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

        if (targetType == int.class || targetType == Integer.class) {
            return value instanceof Number n ? n.intValue() : Integer.parseInt(value.toString());
        }

        if (targetType == boolean.class || targetType == Boolean.class) {
            return value instanceof Boolean b ? b : Boolean.parseBoolean(value.toString());
        }

        if (targetType == long.class || targetType == Long.class) {
            return value instanceof Number n ? n.longValue() : Long.parseLong(value.toString());
        }

        if (targetType == double.class || targetType == Double.class) {
            return value instanceof Number n ? n.doubleValue() : Double.parseDouble(value.toString());
        }

        if (targetType == float.class || targetType == Float.class) {
            return value instanceof Number n ? n.floatValue() : Float.parseFloat(value.toString());
        }

        if (targetType == byte.class || targetType == Byte.class) {
            return value instanceof Number n ? n.byteValue() : Byte.parseByte(value.toString());
        }

        if (targetType == short.class || targetType == Short.class) {
            return value instanceof Number n ? n.shortValue() : Short.parseShort(value.toString());
        }

        if (targetType == char.class || targetType == Character.class) {
            String str = value.toString();
            if (str.length() != 1) {
                throw new StructuraException("Cannot convert string of length " + str.length() + " to char");
            }
            return str.charAt(0);
        }

        throw new StructuraException("Unsupported conversion from " + value.getClass().getName() +
                " to " + targetType.getName() + " at " + prefix);
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
                throw new StructuraException("Invalid enum value: " + strValue + " for type: " + targetType.getName() +
                        ". Available values: " + Arrays.toString(targetType.getEnumConstants()), e);
            }
        }
        throw new StructuraException("Cannot convert " + value.getClass().getName() + " to enum type: " + targetType.getName());
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
     * @return the class from the registry or the original class
     */
    private Class<?> getClassFromRegistry(Class<?> clazz, Object value) {
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
            throw new StructuraException("Polymorphic type " + clazz.getName() + " requires a Map value for conversion.");
        }
        //noinspection unchecked
        Map<String, Object> valueMap = (Map<String, Object>) value;

        Polymorphic polymorphic = loadableClass.getAnnotation(Polymorphic.class);
        String key = polymorphic.key();
        PolymorphicRegistry<?> registry = PolymorphicRegistry.get(loadableClass);

        if (!valueMap.containsKey(key)) {
            throw new StructuraException("Polymorphic type " + clazz.getName() + " requires key '" + key + "' in value map.");
        }

        String typeName = valueMap.get(key).toString();
        return registry.get(typeName).orElseThrow(() ->
                new StructuraException(
                        "No registered type found for " + typeName + " in polymorphic type " + loadableClass.getName() +
                                ". Available types: " + String.join(", ", registry.availableNames())
                )
        );
    }

    /**
     * Checks if a type is a record that implements Loadable.
     */
    private boolean isSettingsRecord(Class<?> type) {
        return type.isRecord() && Loadable.class.isAssignableFrom(type);
    }
}