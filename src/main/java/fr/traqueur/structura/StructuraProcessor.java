package fr.traqueur.structura;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.conversion.ReadSession;
import fr.traqueur.structura.conversion.ValueConverter;
import fr.traqueur.structura.document.YamlDocument;
import fr.traqueur.structura.exceptions.StructuraErrors;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.factory.RecordInstanceFactory;
import fr.traqueur.structura.mapping.FieldMapper;
import fr.traqueur.structura.registries.DefaultValueRegistry;
import fr.traqueur.structura.validation.Validator;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * StructuraProcessor orchestrates YAML parsing and coordinates other components.
 * It manages record instance creation and specialized enum processing.
 */
public class StructuraProcessor {

    private final boolean validateOnParse;
    private final boolean collectErrors;

    private final RecordInstanceFactory recordFactory;
    private final FieldMapper fieldMapper;
    private final ValueConverter valueConverter;

    /**
     * Constructs a StructuraProcessor.
     *
     * @param validateOnParse if true, validates instances after parsing
     */
    public StructuraProcessor(boolean validateOnParse) {
        this(validateOnParse, false);
    }

    /**
     * Constructs a StructuraProcessor.
     *
     * @param validateOnParse if true, validates instances after parsing
     * @param collectErrors if true, a read carries on past an error and reports every error of the
     *                      document together in a {@link StructuraErrors}
     */
    public StructuraProcessor(boolean validateOnParse, boolean collectErrors) {
        this.validateOnParse = validateOnParse;
        this.collectErrors = collectErrors;

        this.fieldMapper = new FieldMapper();
        this.recordFactory = new RecordInstanceFactory(fieldMapper);

        this.valueConverter = new ValueConverter(recordFactory);
        recordFactory.setValueConverter(valueConverter);
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
        return parse(yamlString, settingsClass, null);
    }

    /**
     * Parses a YAML string to a record instance, naming its source in error messages.
     *
     * @param yamlString the YAML content to parse
     * @param settingsClass the target class (must be a record implementing Loadable)
     * @param sourceName the name of the source (a file name) shown in error messages, or null
     * @param <T> the type of the target class
     * @return the created instance populated with YAML data
     * @throws StructuraException if parsing or validation fails, located in the text when possible
     */
    public <T extends Loadable> T parse(String yamlString, Class<T> settingsClass, String sourceName) {
        validateInput(yamlString, settingsClass);

        YamlDocument document = document(yamlString, sourceName);
        return read(document, () -> {
            Map<String, Object> settings = rootSection(document.data());

            T instance = settingsClass.cast(recordFactory.createInstance(settings, settingsClass, ""));

            if (validateOnParse) {
                Validator.INSTANCE.validate(instance, "", "");
            }

            return instance;
        }, "Failed to parse YAML for class " + settingsClass.getName());
    }

    /**
     * Reads the text of a document; when errors are collected, a syntax error is reported the same
     * way as the others, in a {@link StructuraErrors}.
     */
    private YamlDocument document(String yamlString, String sourceName) {
        try {
            return YamlDocument.parse(yamlString, sourceName);
        } catch (StructuraException e) {
            throw collectErrors ? new StructuraErrors(List.of(e)) : e;
        }
    }

    /**
     * Runs the read of a document: binds a {@link ReadSession}, locates every error in the text and,
     * when errors are collected, throws them all together at the end.
     *
     * @param document the document being read
     * @param body the read
     * @param failure the message of an unexpected failure
     * @return the result of the read
     */
    private <T> T read(YamlDocument document, Supplier<T> body, String failure) {
        ReadSession session = new ReadSession(collectErrors);
        try {
            T result = session.run(body);
            if (!session.errors().isEmpty()) {
                throw collected(document, session, null);
            }
            return result;
        } catch (StructuraErrors e) {
            throw e;
        } catch (StructuraException e) {
            if (session.errors().isEmpty()) {
                throw document.locate(e);
            }
            throw collected(document, session, e);
        } catch (Exception e) {
            throw new StructuraException(failure + ": " + e, e);
        }
    }

    private static StructuraErrors collected(YamlDocument document, ReadSession session, StructuraException last) {
        List<StructuraException> errors = new ArrayList<>(session.errors());
        if (last != null) {
            errors.add(last);
        }
        errors.forEach(document::locate);
        // Fixed top to bottom: report them in the order of the text, not of the record declarations
        errors.sort(Comparator.comparingInt((StructuraException error) -> error.line().orElse(Integer.MAX_VALUE))
                .thenComparingInt(error -> error.column().orElse(Integer.MAX_VALUE)));
        return new StructuraErrors(errors);
    }

    /**
     * The root of a record document must be a section; an empty document is an empty section.
     */
    private static Map<String, Object> rootSection(Object data) {
        if (data == null) {
            return Map.of();
        }
        if (!(data instanceof Map<?, ?> map)) {
            throw new StructuraException(StructuraException.Kind.WRONG_TYPE,
                    "expected a section (key: value) at the root of the document, but got "
                            + ValueConverter.describe(data), null);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> settings = (Map<String, Object>) map;
        return settings;
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
        parseEnum(yamlString, enumClass, null);
    }

    /**
     * Parses a YAML string to populate enum constant fields, naming its source in error messages.
     *
     * @param yamlString the YAML content to parse
     * @param enumClass the enum class (must implement Loadable)
     * @param sourceName the name of the source (a file name) shown in error messages, or null
     * @param <E> the type of the enum
     * @throws StructuraException if parsing or validation fails, located in the text when possible
     */
    public <E extends Enum<E> & Loadable> void parseEnum(String yamlString, Class<E> enumClass, String sourceName) {
        validateInput(yamlString, enumClass);

        YamlDocument document = document(yamlString, sourceName);
        read(document, () -> {
            if (document.data() == null) {
                throw new StructuraException("YAML content is empty or null for enum " + enumClass.getName());
            }

            processEnum(rootSection(document.data()), enumClass);
            return null;
        }, "Failed to parse enum YAML for class " + enumClass.getName());
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
            try {
                processEnumConstant(settings, entry.getKey(), entry.getValue());
            } catch (StructuraException e) {
                // Collected when the read collects errors, so that the next constants are read too
                ReadSession.report(e);
            }
        }
    }

    /**
     * Reads the node of one enum constant into its fields.
     */
    private <E extends Enum<E> & Loadable> void processEnumConstant(Map<String, Object> settings,
                                                                    String kebabCaseName, E enumConstant) {
        if (!settings.containsKey(kebabCaseName)) {
            throw new StructuraException(StructuraException.Kind.MISSING,
                    "Missing data for enum constant: " + kebabCaseName, kebabCaseName);
        }

        Object data = settings.get(kebabCaseName);
        if (data == null) {
            throw new StructuraException(StructuraException.Kind.MISSING,
                    "Null data for enum constant: " + kebabCaseName, kebabCaseName);
        }

        injectDataIntoEnum(enumConstant, data, kebabCaseName);

        if (validateOnParse) {
            Validator.INSTANCE.validate(enumConstant, kebabCaseName, kebabCaseName);
        }
    }

    /**
     * Injects YAML data into enum constant fields.
     *
     * @param enumConstant the enum constant to populate
     * @param data the corresponding YAML data
     * @param path the path of the constant in the document
     */
    private void injectDataIntoEnum(Enum<?> enumConstant, Object data, String path) {
        Class<?> enumClass = enumConstant.getClass();
        Field[] fields = enumClass.getDeclaredFields();
        boolean hasInlineField = rejectSeveralInlineFields(enumClass, fields);

        for (Field field : fields) {
            if (isIgnoredEnumField(field)) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object fieldValue;
                try {
                    fieldValue = getFieldValueFromData(field, data, hasInlineField, path);
                } catch (StructuraException e) {
                    // Collected when the read collects errors: the field keeps its value, the next one is read
                    ReadSession.report(e);
                    continue;
                }

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
     * @param path the path of the constant in the document
     * @return the converted value or null if not found
     */
    private Object getFieldValueFromData(Field field, Object data, boolean hasInlineField, String path) {
        if (isInlineField(field)) {
            // The whole node is this field's value: a scalar goes through the readers as usual,
            // a map is handed to the converter minus the keys the sibling fields claim, exactly
            // like an inline component of a record.
            return valueConverter.convert(inlineNode(field, data), field.getGenericType(), field.getType(), path);
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
                return valueConverter.convert(value, field.getType(), fieldMapper.buildPath(path, fieldName));
            }
        } else {
            if (field.getType().isAssignableFrom(data.getClass())) {
                return data;
            }
            return valueConverter.convert(data, field.getType(), path);
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