package fr.traqueur.structura;

import fr.traqueur.structura.annotations.Options;
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
import java.util.LinkedHashMap;
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
        boolean hasInlineField = rejectSeveralInlineFields(enumClass, fields);

        for (Field field : fields) {
            if (isIgnoredEnumField(field)) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object fieldValue = getFieldValueFromData(field, data, hasInlineField);

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
     * Enum fields that carry no data: constants, synthetic members and statics.
     */
    private static boolean isIgnoredEnumField(Field field) {
        return field.isSynthetic() || field.isEnumConstant() || Modifier.isStatic(field.getModifiers());
    }

    private static boolean isInlineField(Field field) {
        Options options = field.getAnnotation(Options.class);
        return options != null && options.inline();
    }

    /**
     * Two inline fields in one enum would both claim the whole node, with nothing saying how
     * to split it — rejected up front rather than resolved by declaration order.
     *
     * @return whether the enum declares an inline field
     */
    private boolean rejectSeveralInlineFields(Class<?> enumClass, Field[] fields) {
        List<String> inlineFields = Arrays.stream(fields)
                .filter(field -> !isIgnoredEnumField(field))
                .filter(StructuraProcessor::isInlineField)
                .map(Field::getName)
                .toList();
        if (inlineFields.size() > 1) {
            throw new StructuraException("Enum " + enumClass.getSimpleName()
                    + " declares several inline fields " + inlineFields
                    + ": only one field can absorb the node, remove @Options(inline = true) from the others");
        }
        return !inlineFields.isEmpty();
    }

    /**
     * The node handed to an inline enum field: the node itself for a scalar, and for a map the
     * node minus the keys the sibling fields claim — the same rule as an inline component of a
     * record, so that a data key and a sibling field never compete for the same name.
     *
     * @param field the inline field
     * @param data the YAML node of the enum constant
     * @return the value to convert into the field's type
     */
    private Object inlineNode(Field field, Object data) {
        if (!(data instanceof Map<?, ?> map)) {
            return data;
        }
        Map<String, Object> remaining = new LinkedHashMap<>();
        map.forEach((key, value) -> remaining.put(String.valueOf(key), value));
        for (Field sibling : field.getDeclaringClass().getDeclaredFields()) {
            if (sibling.equals(field) || isIgnoredEnumField(sibling)) {
                continue;
            }
            remaining.remove(fieldMapper.getFieldNameFromField(sibling));
        }
        return remaining;
    }

    /**
     * Extracts a field value from YAML data.
     *
     * @param field the enum field to populate
     * @param data the YAML data
     * @param hasInlineField whether a sibling field absorbs the whole node
     * @return the converted value or null if not found
     */
    private Object getFieldValueFromData(Field field, Object data, boolean hasInlineField) {
        if (isInlineField(field)) {
            // The whole node is this field's value: a scalar goes through the readers as usual,
            // a map is handed to the converter minus the keys the sibling fields claim, exactly
            // like an inline component of a record.
            return valueConverter.convert(inlineNode(field, data), field.getGenericType(), field.getType(), "");
        }

        if (hasInlineField && !(data instanceof Map<?, ?>)) {
            // A scalar node belongs to the inline field alone: without this, the scalar branch
            // below would hand "Au revoir" to every sibling whose type happens to be String.
            return null;
        }

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