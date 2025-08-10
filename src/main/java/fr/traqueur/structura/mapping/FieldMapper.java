package fr.traqueur.structura.mapping;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.registries.DefaultValueRegistry;
import fr.traqueur.structura.exceptions.StructuraException;

import java.lang.reflect.Parameter;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.*;

/**
 * FieldMapper handles field name mapping and key logic.
 * It manages camelCase ↔ kebab-case conversion and @Options annotation processing.
 */
public class FieldMapper {

    private static final String CAMEL_CASE_REGEX = "([a-z])([A-Z])";
    private static final String SNAKE_CASE_REGEX = "([A-Za-z0-9])_([A-Za-z0-9])";
    private static final String KEBAB_CASE_REPLACEMENT = "$1-$2";

    /**
     * Gets the effective field name based on parameter and annotations.
     *
     * @param parameter the constructor parameter
     * @param defaultName the default name (parameter name)
     * @return the effective name to use for YAML mapping
     */
    public String getEffectiveFieldName(Parameter parameter, String defaultName) {
        if (parameter.isAnnotationPresent(Options.class)) {
            Options options = parameter.getAnnotation(Options.class);
            if (!options.name().trim().isEmpty()) {
                return options.name();
            }
        }
        return convertCamelCaseToKebabCase(defaultName);
    }

    /**
     * Gets the effective field name based on Field and annotations.
     * Used primarily for enums.
     *
     * @param field the field
     * @return the effective name for YAML mapping
     */
    public String getFieldNameFromField(Field field) {
        if (field.isAnnotationPresent(Options.class)) {
            Options options = field.getAnnotation(Options.class);
            if (!options.name().trim().isEmpty()) {
                return options.name();
            }
        }
        return convertCamelCaseToKebabCase(field.getName());
    }

    /**
     * Converts a snake_case name to kebab-case.
     * Example: MYSQL_DATABASE -> mysql-database
     *
     * @param snakeCase the snake_case string
     * @return the kebab-case string
     */
    public String convertSnakeCaseToKebabCase(String snakeCase) {
        return snakeCase.replaceAll(SNAKE_CASE_REGEX, KEBAB_CASE_REPLACEMENT).toLowerCase();
    }

    /**
     * Converts a camelCase name to kebab-case.
     * Example: databaseUrl -> database-url
     *
     * @param camelCase the camelCase string
     * @return the kebab-case string
     */
    public String convertCamelCaseToKebabCase(String camelCase) {
        return camelCase.replaceAll(CAMEL_CASE_REGEX, KEBAB_CASE_REPLACEMENT).toLowerCase();
    }

    /**
     * Finds the component marked as key (@Options(isKey = true)) in a record.
     *
     * @param components the record components
     * @return the key component or null if none
     */
    public RecordComponent findKeyComponent(RecordComponent[] components) {
        return Arrays.stream(components)
                .filter(this::isKeyComponent)
                .findFirst()
                .orElse(null);
    }

    /**
     * Determines if key mapping is simple (single key with primitive type).
     *
     * @param data the YAML data
     * @param keyComponent the key component
     * @return true if it's simple mapping
     */
    public boolean isSimpleKeyMapping(Map<String, Object> data, RecordComponent keyComponent) {
        return data.size() == 1 && !isComplexType(keyComponent.getType());
    }

    /**
     * Gets all field names of a record for complex key mapping.
     *
     * @param recordClass the record class
     * @return the set of field names in kebab-case
     */
    public Set<String> getRecordFieldNames(Class<?> recordClass) {
        if (!recordClass.isRecord()) {
            return Collections.emptySet();
        }

        RecordComponent[] components = recordClass.getRecordComponents();
        Set<String> fieldNames = new HashSet<>();

        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            try {
                Parameter parameter = getConstructorParameter(recordClass, component.getName(), i);
                String fieldName = getEffectiveFieldName(parameter, component.getName());
                fieldNames.add(fieldName);
            } catch (Exception e) {
                fieldNames.add(convertCamelCaseToKebabCase(component.getName()));
            }
        }

        return fieldNames;
    }

    /**
     * Navigates through YAML data structure to get a value by its path.
     *
     * @param data the YAML data map
     * @param path the path to the value (e.g., "database.connection.host")
     * @return the found value or null
     */
    public Object getValueFromPath(Map<String, Object> data, String path) {
        String[] pathParts = path.split("\\.");
        Object current = data;

        for (String part : pathParts) {
            if (current instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stringMap = (Map<String, Object>) map;
                current = stringMap.get(part);
                if (current == null) {
                    return null;
                }
            } else {
                return null;
            }
        }

        return current;
    }

    /**
     * Gets the default value for a given parameter.
     *
     * @param parameter the constructor parameter
     * @param path the complete field path (for error messages)
     * @return the default value or null
     * @throws StructuraException if the field is required but missing
     */
    public Object getDefaultValue(Parameter parameter, String path) {
        Object defaultValue = DefaultValueRegistry.getInstance()
                .getDefaultValue(parameter.getType(), List.of(parameter.getAnnotations()));

        if (defaultValue == null && !isOptional(parameter)) {
            throw new StructuraException(path + " is required but not provided");
        }

        return defaultValue;
    }

    /**
     * Checks if a parameter is optional (@Options(optional = true)).
     *
     * @param parameter the parameter to check
     * @return true if the parameter is optional
     */
    public boolean isOptional(Parameter parameter) {
        return parameter.isAnnotationPresent(Options.class) &&
                parameter.getAnnotation(Options.class).optional();
    }

    /**
     * Builds a complete path from a prefix and field name.
     *
     * @param prefix the prefix (can be empty)
     * @param fieldName the field name
     * @return the complete path
     */
    public String buildPath(String prefix, String fieldName) {
        return prefix.isEmpty() ? fieldName : prefix + "." + fieldName;
    }

    /**
     * Checks if a component is marked as key.
     */
    private boolean isKeyComponent(RecordComponent component) {
        return component.isAnnotationPresent(Options.class) &&
                component.getAnnotation(Options.class).isKey();
    }

    /**
     * Checks if a type is complex (record).
     */
    private boolean isComplexType(Class<?> type) {
        return type.isRecord();
    }

    /**
     * Gets a constructor parameter by index.
     */
    private Parameter getConstructorParameter(Class<?> recordClass, String componentName, int index) {
        try {
            return recordClass.getDeclaredConstructors()[0].getParameters()[index];
        } catch (Exception e) {
            throw new StructuraException("Unable to find parameter for component: " + componentName, e);
        }
    }
}