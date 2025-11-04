package fr.traqueur.structura;

import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.conversion.ValueConverter;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.factory.RecordInstanceFactory;
import fr.traqueur.structura.mapping.FieldMapper;
import fr.traqueur.structura.registries.DefaultValueRegistry;
import fr.traqueur.structura.validation.Validator;
import org.yaml.snakeyaml.Yaml;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * StructuraProcessor orchestrates YAML parsing and coordinates other components.
 * It manages record instance creation and specialized enum processing.
 */
public class StructuraProcessor {

    private final boolean validateOnParse;
    private Yaml yaml; // Not final for lazy initialization

    private final RecordInstanceFactory recordFactory;
    private final FieldMapper fieldMapper;
    private final ValueConverter valueConverter;

    /**
     * Constructs a StructuraProcessor.
     *
     * @param validateOnParse if true, validates instances after parsing
     */
    public StructuraProcessor(boolean validateOnParse) {
        this.validateOnParse = validateOnParse;
        // yaml will be initialized on first use (lazy initialization)

        this.fieldMapper = new FieldMapper();
        this.recordFactory = new RecordInstanceFactory(fieldMapper);

        this.valueConverter = new ValueConverter(recordFactory);
        recordFactory.setValueConverter(valueConverter);
    }

    /**
     * Gets the Yaml instance, initializing it lazily on first access.
     * This improves performance by only creating the Yaml parser when needed.
     *
     * @return the Yaml instance
     */
    private Yaml getYaml() {
        if (yaml == null) {
            yaml = new Yaml();
        }
        return yaml;
    }

    /**
     * Parses a YAML string to a record instance.
     *
     * @param yamlString the YAML content to parse
     * @param settingsClass the target class (must be a record implementing Loadable)
     * @param <T> the type of the target class
     * @return the created instance populated with YAML data
     * @throws StructuraException if parsing or validation fails
     */
    public <T extends Loadable> T parse(String yamlString, Class<T> settingsClass) {
        validateInput(yamlString, settingsClass);

        try {
            Map<String, Object> settings = getYaml().load(yamlString);
            if (settings == null) {
                settings = Map.of();
            }

            T instance = settingsClass.cast(recordFactory.createInstance(settings, settingsClass, ""));

            if (validateOnParse) {
                Validator.INSTANCE.validate(instance, "");
            }

            return instance;
        } catch (Exception e) {
            if (e instanceof StructuraException) {
                throw e;
            }
            throw new StructuraException("Failed to parse YAML for class " + settingsClass.getName(), e);
        }
    }

    /**
     * Parses a YAML string to populate enum constant fields.
     *
     * @param yamlString the YAML content to parse
     * @param enumClass the enum class (must implement Loadable)
     * @param <E> the type of the enum
     * @throws StructuraException if parsing or validation fails
     */
    public <E extends Enum<E> & Loadable> void parseEnum(String yamlString, Class<E> enumClass) {
        validateInput(yamlString, enumClass);

        try {
            Map<String, Object> settings = getYaml().load(yamlString);
            if (settings == null) {
                throw new StructuraException("YAML content is empty or null for enum " + enumClass.getName());
            }

            processEnum(settings, enumClass);
        } catch (Exception e) {
            if (e instanceof StructuraException) {
                throw e;
            }
            throw new StructuraException("Failed to parse enum YAML for class " + enumClass.getName(), e);
        }
    }

    /**
     * Processes YAML data to populate enum constants.
     */
    private <E extends Enum<E> & Loadable> void processEnum(Map<String, Object> settings, Class<E> enumClass) {
        E[] enumConstants = enumClass.getEnumConstants();

        Map<String, E> enumByKebabCase = Arrays.stream(enumConstants)
                .collect(Collectors.toMap(
                        e -> fieldMapper.convertSnakeCaseToKebabCase(e.name()),
                        e -> e
                ));

        for (Map.Entry<String, E> entry : enumByKebabCase.entrySet()) {
            String kebabCaseName = entry.getKey();
            E enumConstant = entry.getValue();

            if (!settings.containsKey(kebabCaseName)) {
                throw new StructuraException("Missing data for enum constant: " + kebabCaseName);
            }

            Object data = settings.get(kebabCaseName);
            if (data == null) {
                throw new StructuraException("Null data for enum constant: " + kebabCaseName);
            }

            injectDataIntoEnum(enumConstant, data);

            if (validateOnParse) {
                Validator.INSTANCE.validate(enumConstant, kebabCaseName);
            }
        }
    }

    /**
     * Injects YAML data into enum constant fields.
     *
     * @param enumConstant the enum constant to populate
     * @param data the corresponding YAML data
     */
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

                if (fieldValue == null) {
                    fieldValue = DefaultValueRegistry.getInstance()
                            .getDefaultValue(field.getType(), List.of(field.getAnnotations()));
                }

                if (fieldValue != null) {
                    field.set(enumConstant, fieldValue);
                }
            } catch (IllegalAccessException e) {
                throw new StructuraException("Cannot inject into enum field: " + field.getName()
                        + " of constant: " + enumConstant.name(), e);
            }
        }
    }

    /**
     * Extracts a field value from YAML data.
     *
     * @param field the enum field to populate
     * @param data the YAML data
     * @return the converted value or null if not found
     */
    private Object getFieldValueFromData(Field field, Object data) {
        String fieldName = fieldMapper.getFieldNameFromField(field);

        if (data instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) map;
            Object value = dataMap.get(fieldName);

            if (value != null) {
                return valueConverter.convert(value, field.getType(), "");
            }
        } else {
            if (field.getType().isAssignableFrom(data.getClass())) {
                return data;
            }
            return valueConverter.convert(data, field.getType(), "");
        }

        return null;
    }

    /**
     * Validates common inputs to parse and parseEnum methods.
     *
     * @param yamlString the YAML string
     * @param targetClass the target class
     * @throws StructuraException if inputs are invalid
     */
    private void validateInput(String yamlString, Class<?> targetClass) {
        if (yamlString == null || yamlString.trim().isEmpty()) {
            throw new StructuraException("YAML string cannot be null or empty");
        }
        if (targetClass == null) {
            throw new StructuraException("Target class cannot be null");
        }
        if (!Loadable.class.isAssignableFrom(targetClass)) {
            throw new StructuraException("Class " + targetClass.getName() + " must implement Loadable interface");
        }
    }

}