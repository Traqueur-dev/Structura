package fr.traqueur.structura.validation;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.annotations.validation.*;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.conversion.ReadSession;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.exceptions.ValidationException;
import fr.traqueur.structura.mapping.FieldMapper;

import java.lang.reflect.*;
import java.util.Collection;
import java.util.Map;

/**
 * Validator class for validating instances of records and enums.
 * It checks for various constraints such as min, max, pattern, not empty, and size.
 * The validation is performed recursively for nested settings types.
 */
public class Validator {

    /**
     * Singleton instance of the Validator.
     * This instance can be used to validate records and enums.
     */
    public static final Validator INSTANCE = new Validator();

    /**
     * Private constructor to enforce singleton pattern.
     */
    private Validator() {}

    /**
     * Validates the given instance based on its type.
     * If the instance is a record, it validates each field of the record.
     * If the instance is an enum, it validates each field of the enum.
     *
     * @param instance The instance to validate.
     * @param path The path for error messages.
     */
    public void validate(Object instance, String path) {
        validate(instance, path, null);
    }

    /**
     * Validates the given instance, attaching to each error the path of the refused key in the
     * document it was read from, so that the error can be located in the text.
     *
     * <p>{@code path} keeps naming components the Java way, as the {@code {path}} placeholder of a
     * validation message always did; {@code documentPath} follows the keys of the document
     * ({@code max-items}, inline components flattened).</p>
     *
     * @param instance The instance to validate.
     * @param path The path for error messages.
     * @param documentPath The path of the instance in the document, or null when it was not read from one.
     */
    public void validate(Object instance, String path, String documentPath) {
        validate(instance, path, documentPath, false);
    }

    /**
     * Validates the given instance, optionally descending into the records held by its lists, sets
     * and maps, and into polymorphic components.
     *
     * <p>Without {@code nested}, only the components and the nested records declared as such are
     * validated, as before 2.7.0: a {@code @Min} on the price of each offer of a {@code List<Offer>}
     * is not checked. With it, every record reached through a list ({@code offers[2].price}), a map
     * value ({@code currencies.gems.symbol}) or a polymorphic component is validated too.</p>
     *
     * @param instance The instance to validate.
     * @param path The path for error messages.
     * @param documentPath The path of the instance in the document, or null when it was not read from one.
     * @param nested whether to validate the records held by lists, maps and polymorphic components
     */
    public void validate(Object instance, String path, String documentPath, boolean nested) {
        if (instance == null) return;

        Class<?> clazz = instance.getClass();

        if (clazz.isRecord()) {
            validateRecord(instance, path, documentPath, nested);
        } else if (clazz.isEnum() || (clazz.getSuperclass() != null && clazz.getSuperclass().isEnum())) {
            validateEnum(instance, path, documentPath, nested);
        }
    }

    /**
     * Validates what a value holds: a record, or the records of a list, a set or a map, at any depth.
     */
    private void validateContent(Object value, String path, String documentPath) {
        if (value instanceof Record && value instanceof Loadable) {
            validate(value, path, documentPath, true);
        } else if (value instanceof Collection<?> collection) {
            int index = 0;
            for (Object element : collection) {
                String suffix = "[" + index++ + "]";
                validateContent(element, path + suffix, documentPath == null ? null : documentPath + suffix);
            }
        } else if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                validateContent(entry.getValue(), join(path, key), documentPath == null ? null : join(documentPath, key));
            }
        }
    }

    private static String join(String path, String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    /**
     * Validates a record instance by checking each of its components.
     *
     * @param record The record instance to validate.
     * @param path The path for error messages.
     * @throws ValidationException if validation fails.
     */
    private void validateRecord(Object record, String path, String documentPath, boolean nested) {
        Class<?> recordClass = record.getClass();
        RecordComponent[] components = recordClass.getRecordComponents();

        for (RecordComponent component : components) {
            String fieldDocumentPath = documentPath == null ? null : componentDocumentPath(component, documentPath);
            try {
                Method accessor = component.getAccessor();
                accessor.setAccessible(true);
                Object value = accessor.invoke(record);
                String fieldPath = path.isEmpty() ? component.getName() : path + "." + component.getName();

                validateField(component.getType(), value, component, fieldPath);

                if (value != null && nested) {
                    validateContent(value, fieldPath, fieldDocumentPath);
                } else if (value != null && isSettingsType(component.getType())) {
                    validate(value, fieldPath, fieldDocumentPath, false);
                }
            } catch (ReflectiveOperationException e) {
                throw new ValidationException("Failed to validate field: " + component.getName(), e);
            } catch (ValidationException e) {
                // Collected when the read collects errors, so that the next components are validated too
                ReadSession.report(e.atPath(fieldDocumentPath));
            }
        }
    }

    /**
     * The path of a component in the document: an inline component reads its parent's node,
     * any other component sits under its own key.
     */
    private static String componentDocumentPath(RecordComponent component, String documentPath) {
        Options options = component.getAnnotation(Options.class);
        if (options != null && options.inline()) {
            return documentPath;
        }
        String key = FieldMapper.keyOf(component);
        return documentPath.isEmpty() ? key : documentPath + "." + key;
    }

    /**
     * Validates the fields of an enum instance.
     * It checks for various constraints such as min, max, pattern, not empty, and size.
     *
     * @param enumInstance The enum instance to validate.
     * @param path The path for error messages.
     */
    private void validateEnum(Object enumInstance, String path, String documentPath, boolean nested) {
        Class<?> enumClass = enumInstance.getClass();
        Field[] fields = enumClass.getDeclaredFields();

        for (Field field : fields) {
            if (field.isSynthetic() || field.isEnumConstant() || Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object value = field.get(enumInstance);
                String fieldPath = path.isEmpty() ? field.getName() : path + "." + field.getName();

                validateField(enumClass, value, field, fieldPath);

                if (value != null && nested) {
                    Options options = field.getAnnotation(Options.class);
                    String fieldDocumentPath = documentPath == null ? null
                            : options != null && options.inline() ? documentPath
                            : join(documentPath, new FieldMapper().getFieldNameFromField(field));
                    validateContent(value, fieldPath, fieldDocumentPath);
                }
            } catch (IllegalAccessException e) {
                throw new ValidationException("Failed to validate enum field: " + field.getName(), e);
            } catch (ValidationException e) {
                if (documentPath != null) {
                    e.atPath(documentPath);
                }
                ReadSession.report(e);
            }
        }
    }

    /**
     * Validates a field with its validation annotations.
     *
     * @param value The field value
     * @param element The annotated element (RecordComponent or Field)
     * @param path The path for error messages
     */
    private void validateField(Class<?> clazz, Object value, AnnotatedElement element, String path) {
        if (value == null) {
            return;
        }

        validateMin(value, element, path);
        validateMax(value, element, path);
        validatePattern(value, element, path);
        validateNotEmpty(value, element, path);
        validateSize(value, element, path);
        validatePolymorphic(clazz, path);
    }

    private void validatePolymorphic(Class<?> value, String path) {
        if (!value.isAnnotationPresent(Polymorphic.class)) return;

        Polymorphic polymorphic = value.getAnnotation(Polymorphic.class);
        if (polymorphic.inline() && polymorphic.useKey()) {
            throw new ValidationException(formatMessage("Invalid @Polymorphic configuration: 'inline' and 'useKey' cannot both be true in {path}",
                    Map.of("path", path)));
        }
    }

    /**
     * Validates minimum value constraint.
     */
    private void validateMin(Object value, AnnotatedElement element, String path) {
        if (!element.isAnnotationPresent(Min.class)) return;

        Min min = element.getAnnotation(Min.class);
        long numericValue = extractNumericValue(value, path);

        if (numericValue < min.value()) {
            throw new ValidationException(formatMessage(min.message(),
                    Map.of("value", String.valueOf(min.value()), "path", path)));
        }
    }

    /**
     * Validates maximum value constraint.
     */
    private void validateMax(Object value, AnnotatedElement element, String path) {
        if (!element.isAnnotationPresent(Max.class)) return;

        Max max = element.getAnnotation(Max.class);
        long numericValue = extractNumericValue(value, path);

        if (numericValue > max.value()) {
            throw new ValidationException(formatMessage(max.message(),
                    Map.of("value", String.valueOf(max.value()), "path", path)));
        }
    }

    /**
     * Validates pattern constraint.
     */
    private void validatePattern(Object value, AnnotatedElement element, String path) {
        if (!element.isAnnotationPresent(Pattern.class)) return;

        Pattern annotation = element.getAnnotation(Pattern.class);
        String stringValue = value.toString();

        if (!java.util.regex.Pattern.matches(annotation.value(), stringValue)) {
            throw new ValidationException(formatMessage(annotation.message(),
                    Map.of("value", annotation.value(), "path", path)));
        }
    }

    /**
     * Validates not empty constraint.
     */
    private void validateNotEmpty(Object value, AnnotatedElement element, String path) {
        if (!element.isAnnotationPresent(NotEmpty.class)) return;

        NotEmpty notEmpty = element.getAnnotation(NotEmpty.class);

        if (value instanceof String str && str.isEmpty()) {
            throw new ValidationException(formatMessage(notEmpty.message(), Map.of("path", path)));
        } else if (value instanceof Collection<?> coll && coll.isEmpty()) {
            throw new ValidationException(formatMessage(notEmpty.message(), Map.of("path", path)));
        } else if (value instanceof Map<?, ?> map && map.isEmpty()) {
            throw new ValidationException(formatMessage(notEmpty.message(), Map.of("path", path)));
        }
    }

    /**
     * Validates size constraint.
     */
    private void validateSize(Object value, AnnotatedElement element, String path) {
        if (!element.isAnnotationPresent(Size.class)) return;

        Size size = element.getAnnotation(Size.class);
        int actualSize = extractSize(value, path);

        if (actualSize < size.min() || actualSize > size.max()) {
            throw new ValidationException(formatMessage(size.message(),
                    Map.of("min", String.valueOf(size.min()),
                            "max", String.valueOf(size.max()),
                            "path", path)));
        }
    }

    /**
     * Extracts numeric value from an object for min/max validation.
     */
    private long extractNumericValue(Object value, String path) {
        if (value instanceof Number num) {
            return num.longValue();
        }
        throw new ValidationException("Cannot validate numeric constraint on non-numeric field: " + path);
    }

    /**
     * Extracts size from an object for size validation.
     */
    private int extractSize(Object value, String path) {
        if (value instanceof String str) return str.length();
        if (value instanceof Collection<?> coll) return coll.size();
        if (value instanceof Map<?, ?> map) return map.size();
        if (value.getClass().isArray()) return Array.getLength(value);

        throw new ValidationException("Cannot validate size constraint on field: " + path);
    }

    /**
     * Checks if a type is a settings record that implements Loadable.
     */
    private boolean isSettingsType(Class<?> type) {
        return type.isRecord() && Loadable.class.isAssignableFrom(type);
    }

    /**
     * Formats a message template by replacing placeholders with actual values.
     */
    private String formatMessage(String template, Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}