package fr.traqueur.structura.validation;

import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.annotations.validation.*;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.exceptions.ValidationException;

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
        if (instance == null) return;

        Class<?> clazz = instance.getClass();

        if (clazz.isRecord()) {
            validateRecord(instance, path);
        } else if (clazz.isEnum()) {
            validateEnum(instance, path);
        }
    }

    /**
     * Validates a record instance by checking each of its components.
     *
     * @param record The record instance to validate.
     * @param path The path for error messages.
     * @throws ValidationException if validation fails.
     */
    private void validateRecord(Object record, String path) {
        Class<?> recordClass = record.getClass();
        RecordComponent[] components = recordClass.getRecordComponents();

        for (RecordComponent component : components) {
            try {
                Method accessor = component.getAccessor();
                accessor.setAccessible(true);
                Object value = accessor.invoke(record);
                String fieldPath = path.isEmpty() ? component.getName() : path + "." + component.getName();

                validateField(component.getType(), value, component, fieldPath);

                if (value != null && isSettingsType(component.getType())) {
                    validate(value, fieldPath);
                }
            } catch (ReflectiveOperationException e) {
                throw new ValidationException("Failed to validate field: " + component.getName(), e);
            }
        }
    }

    /**
     * Validates the fields of an enum instance.
     * It checks for various constraints such as min, max, pattern, not empty, and size.
     *
     * @param enumInstance The enum instance to validate.
     * @param path The path for error messages.
     */
    private void validateEnum(Object enumInstance, String path) {
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
            } catch (IllegalAccessException e) {
                throw new ValidationException("Failed to validate enum field: " + field.getName(), e);
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