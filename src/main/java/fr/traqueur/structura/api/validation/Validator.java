package fr.traqueur.structura.api.validation;

import fr.traqueur.structura.api.annotations.validation.*;
import fr.traqueur.structura.api.exceptions.ValidationException;

import java.lang.reflect.*;
import java.util.*;

public class Validator {

    public void validate(Object instance, String path) {
        if (instance == null) return;

        Class<?> clazz = instance.getClass();
        
        if (clazz.isRecord()) {
            validateRecord(instance, path);
        } else if (clazz.isEnum()) {
            validateEnum(instance, path);
        }
    }

    private void validateRecord(Object record, String path) {
        Class<?> recordClass = record.getClass();
        RecordComponent[] components = recordClass.getRecordComponents();

        for (RecordComponent component : components) {
            try {
                Method accessor = component.getAccessor();
                Object value = accessor.invoke(record);
                String fieldPath = path.isEmpty() ? component.getName() : path + "." + component.getName();
                
                validateField(value, component.getType(), fieldPath);

                if (value != null && isSettingsType(component.getType())) {
                    validate(value, fieldPath);
                }
            } catch (ReflectiveOperationException e) {
                throw new ValidationException("Failed to validate field: " + component.getName(), e);
            }
        }
    }

    private void validateEnum(Object enumInstance, String path) {
        Class<?> enumClass = enumInstance.getClass();
        Field[] fields = enumClass.getDeclaredFields();

        for (Field field : fields) {
            if (field.isSynthetic()) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object value = field.get(enumInstance);
                String fieldPath = path.isEmpty() ? field.getName() : path + "." + field.getName();
                
                validateField(value, field.getType(), fieldPath);
            } catch (IllegalAccessException e) {
                throw new ValidationException("Failed to validate enum field: " + field.getName(), e);
            }
        }
    }

    private void validateField(Object value, AnnotatedElement element, String path) {
        if (value == null) {
            return;
        }

        validateMin(value, element, path);
        validateMax(value, element, path);
        validatePattern(value, element, path);
        validateNotEmpty(value, element, path);
        validateSize(value, element, path);
    }

    private void validateMin(Object value, AnnotatedElement element, String path) {
        if (!element.isAnnotationPresent(Min.class)) return;
        
        Min min = element.getAnnotation(Min.class);
        long numericValue = extractNumericValue(value, path);
        
        if (numericValue < min.value()) {
            throw new ValidationException(formatMessage(min.message(), 
                Map.of("value", String.valueOf(min.value()), "path", path)));
        }
    }

    private void validateMax(Object value, AnnotatedElement element, String path) {
        if (!element.isAnnotationPresent(Max.class)) return;
        
        Max max = element.getAnnotation(Max.class);
        long numericValue = extractNumericValue(value, path);
        
        if (numericValue > max.value()) {
            throw new ValidationException(formatMessage(max.message(), 
                Map.of("value", String.valueOf(max.value()), "path", path)));
        }
    }

    private void validatePattern(Object value, AnnotatedElement element, String path) {
        if (!element.isAnnotationPresent(Pattern.class)) return;
        
        Pattern annotation = element.getAnnotation(Pattern.class);
        String stringValue = value.toString();
        
        if (!java.util.regex.Pattern.matches(annotation.value(), stringValue)) {
            throw new ValidationException(formatMessage(annotation.message(), 
                Map.of("value", annotation.value(), "path", path)));
        }
    }

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

    private long extractNumericValue(Object value, String path) {
        if (value instanceof Number num) {
            return num.longValue();
        }
        throw new ValidationException("Cannot validate numeric constraint on non-numeric field: " + path);
    }

    private int extractSize(Object value, String path) {
        if (value instanceof String str) return str.length();
        if (value instanceof Collection<?> coll) return coll.size();
        if (value instanceof Map<?, ?> map) return map.size();
        if (value.getClass().isArray()) return Array.getLength(value);
        
        throw new ValidationException("Cannot validate size constraint on field: " + path);
    }

    private boolean isSettingsType(Class<?> type) {
        return type.isRecord() && fr.traqueur.structura.api.Settings.class.isAssignableFrom(type);
    }

    private String formatMessage(String template, Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}