package fr.traqueur.structura.api;

import fr.traqueur.structura.DefaultValueRegistry;
import fr.traqueur.structura.api.annotations.Options;
import fr.traqueur.structura.api.exceptions.StructuraException;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

public class StructuraProcessor {

    private static final String CAMEL_CASE_REGEX = "([a-z])([A-Z])";
    private static final String KEBAB_CASE_REPLACEMENT = "$1-$2";
    private static final String NUMERIC_KEY_PATTERN = "\\d+";
    private static final String PATH_SEPARATOR = ".";
    private static final String PATH_SEPARATOR_REGEX = "\\.";

    private final Yaml yaml;

    public StructuraProcessor() {
        this.yaml = new Yaml();
    }

    /**
     * Parse une chaîne YAML et la convertit en instance de Settings.
     *
     * @param yamlString    La chaîne YAML à parser
     * @param settingsClass La classe Settings cible
     * @param <T>           Le type de Settings
     * @return L'instance Settings créée
     * @throws StructuraException Si la conversion échoue
     */
    public <T extends Settings> T parse(String yamlString, Class<T> settingsClass) {
        validateInput(yamlString, settingsClass);

        Map<String, Object> settings = yaml.load(yamlString);
        return settingsClass.cast(createInstance(settings, settingsClass, ""));
    }

    /**
     * Crée une instance de record à partir des données YAML.
     *
     * @param data        Les données YAML sous forme de Map
     * @param recordClass La classe record à instancier
     * @param prefix      Le préfixe de chemin pour les messages d'erreur
     * @return L'instance créée
     * @throws StructuraException Si la création échoue
     */
    public Object createInstance(Map<String, Object> data, Class<?> recordClass, String prefix) {
        validateRecordClass(recordClass);

        RecordComponent[] components = recordClass.getRecordComponents();
        Object[] constructorArgs = buildConstructorArguments(components, data, recordClass, prefix);

        return instantiateRecord(recordClass, constructorArgs);
    }

    private void validateInput(String yamlString, Class<?> settingsClass) {
        if (yamlString == null || yamlString.trim().isEmpty()) {
            throw new StructuraException("YAML string cannot be null or empty");
        }
        if (settingsClass == null) {
            throw new StructuraException("Settings class cannot be null");
        }
    }

    private void validateRecordClass(Class<?> recordClass) {
        if (!recordClass.isRecord()) {
            throw new StructuraException("Class must be a record type: " + recordClass.getName());
        }
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
        Object defaultValue = DefaultValueRegistry.getInstance().getDefaultValue(parameter);

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
        @SuppressWarnings("unchecked")
        Map<String, Object> stringMap = (Map<String, Object>) map;

        if (isNumericKeyMap(stringMap)) {
            convertNumericMapToCollection(stringMap, elementType, result, prefix);
        } else {
            result.add(convertCollectionElement(map, elementType, prefix));
        }
    }

    private boolean isNumericKeyMap(Map<String, Object> map) {
        return map.keySet().stream().allMatch(key -> key.matches(NUMERIC_KEY_PATTERN));
    }

    private void convertNumericMapToCollection(Map<String, Object> map, Class<?> elementType,
                                               Collection<Object> result, String prefix) {
        map.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> Integer.parseInt(entry.getKey())))
                .map(Map.Entry::getValue)
                .map(value -> convertCollectionElement(value, elementType, prefix))
                .forEach(result::add);
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
        return type.isRecord() && Settings.class.isAssignableFrom(type);
    }

    private Object convertPrimitiveValue(Object value, Class<?> targetType) {
        if (targetType == String.class) {
            return value.toString();
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
        return parameter.getAnnotation(Nullable.class) != null;
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
}