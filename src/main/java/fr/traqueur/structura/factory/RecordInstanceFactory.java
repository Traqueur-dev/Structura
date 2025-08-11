package fr.traqueur.structura.factory;

import fr.traqueur.structura.conversion.ValueConverter;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.mapping.FieldMapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * RecordInstanceFactory is responsible for creating Java record instances
 * from YAML data. It handles key mapping and constructor argument building.
 */
public class RecordInstanceFactory {

    private final FieldMapper fieldMapper;
    private ValueConverter valueConverter;

    public RecordInstanceFactory(FieldMapper fieldMapper) {
        this.fieldMapper = fieldMapper;
    }

    /**
     * Injects the ValueConverter after construction to resolve circular dependency.
     *
     * @param valueConverter the value converter to inject
     */
    public void setValueConverter(ValueConverter valueConverter) {
        this.valueConverter = valueConverter;
    }

    /**
     * Creates a record instance from YAML data.
     *
     * @param data the YAML data as Map
     * @param recordClass the record class to create
     * @param prefix the prefix for error messages and path
     * @return the created and populated record instance
     * @throws StructuraException if creation fails
     */
    public Object createInstance(Map<String, Object> data, Class<?> recordClass, String prefix) {
        validateInput(data, recordClass);

        RecordComponent[] components = recordClass.getRecordComponents();
        RecordComponent keyComponent = fieldMapper.findKeyComponent(components);

        Object[] args;
        if (keyComponent != null) {
            args = buildArgsWithKeyMapping(data, recordClass, keyComponent, prefix);
        } else {
            args = buildNormalArgs(components, data, recordClass, prefix);
        }

        return instantiateRecord(recordClass, args);
    }

    /**
     * Builds arguments with key mapping (simple or complex).
     */
    private Object[] buildArgsWithKeyMapping(Map<String, Object> data, Class<?> recordClass,
                                             RecordComponent keyComponent, String prefix) {
        if (fieldMapper.isSimpleKeyMapping(data, keyComponent)) {
            return buildSimpleKeyArgs(data, recordClass, keyComponent, prefix);
        } else {
            return buildComplexKeyArgs(data, recordClass, keyComponent, prefix);
        }
    }

    /**
     * Simple key mapping: YAML key becomes the key field value.
     * Example: { "mykey": { "host": "localhost" } } -> id="mykey", host="localhost"
     */
    private Object[] buildSimpleKeyArgs(Map<String, Object> data, Class<?> recordClass,
                                        RecordComponent keyComponent, String prefix) {
        RecordComponent[] components = recordClass.getRecordComponents();
        Map.Entry<String, Object> entry = data.entrySet().iterator().next();
        String keyValue = entry.getKey();
        Object valueData = entry.getValue();

        Object[] args = new Object[components.length];

        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];

            if (component.getName().equals(keyComponent.getName())) {
                args[i] = valueConverter.convert(keyValue, component.getType(), prefix);
            } else {
                Parameter parameter = getConstructorParameter(recordClass, i);

                if (valueData instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> valueMap = (Map<String, Object>) valueData;
                    args[i] = resolveComponentValue(component, parameter, valueMap, prefix);
                } else {
                    String path = fieldMapper.buildPath(prefix, component.getName());
                    args[i] = fieldMapper.getDefaultValue(parameter, path);
                }
            }
        }

        return args;
    }

    /**
     * Complex key mapping: key record fields are flattened with other fields.
     * Example: { "host": "localhost", "port": 8080, "appName": "MyApp" }
     * where ServerInfo { host, port } is marked @Options(isKey = true)
     */
    private Object[] buildComplexKeyArgs(Map<String, Object> data, Class<?> recordClass,
                                         RecordComponent keyComponent, String prefix) {
        RecordComponent[] components = recordClass.getRecordComponents();
        Object[] args = new Object[components.length];

        Set<String> keyComponentFields = fieldMapper.getRecordFieldNames(keyComponent.getType());

        Map<String, Object> keyComponentData = new HashMap<>();
        Map<String, Object> otherFieldsData = new HashMap<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (keyComponentFields.contains(entry.getKey())) {
                keyComponentData.put(entry.getKey(), entry.getValue());
            } else {
                otherFieldsData.put(entry.getKey(), entry.getValue());
            }
        }

        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];

            if (component.getName().equals(keyComponent.getName())) {
                args[i] = createInstance(keyComponentData, component.getType(), prefix);
            } else {
                Parameter parameter = getConstructorParameter(recordClass, i);
                args[i] = resolveComponentValue(component, parameter, otherFieldsData, prefix);
            }
        }

        return args;
    }

    /**
     * Builds arguments for normal mapping (without key).
     */
    private Object[] buildNormalArgs(RecordComponent[] components, Map<String, Object> data,
                                     Class<?> recordClass, String prefix) {
        Object[] args = new Object[components.length];

        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            Parameter parameter = getConstructorParameter(recordClass, i);
            args[i] = resolveComponentValue(component, parameter, data, prefix);
        }

        return args;
    }

    /**
     * Resolves the value of a record component.
     *
     * @param component the record component
     * @param parameter the corresponding constructor parameter
     * @param data the YAML data
     * @param prefix the path prefix
     * @return the resolved and converted value
     */
    private Object resolveComponentValue(RecordComponent component, Parameter parameter,
                                         Map<String, Object> data, String prefix) {
        String fieldName = fieldMapper.getEffectiveFieldName(parameter, component.getName());
        String fullPath = fieldMapper.buildPath(prefix, fieldName);

        Object value = fieldMapper.getValueFromPath(data, fullPath);

        if (value == null) {
            value = fieldMapper.getDefaultValue(parameter, fullPath);
        }

        return value != null
                ? valueConverter.convert(value, component.getGenericType(), component.getType(), prefix)
                : null;
    }

    /**
     * Gets a constructor parameter by index.
     */
    private Parameter getConstructorParameter(Class<?> recordClass, int index) {
        try {
            Constructor<?> constructor = recordClass.getDeclaredConstructors()[0];
            return constructor.getParameters()[index];
        } catch (Exception e) {
            throw new StructuraException("Unable to find parameter at index: " + index + " for class: " + recordClass.getName(), e);
        }
    }

    /**
     * Instantiates a record with the given arguments.
     */
    private Object instantiateRecord(Class<?> recordClass, Object[] args) {
        try {
            Constructor<?> constructor = recordClass.getDeclaredConstructors()[0];
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new StructuraException("Failed to create instance of " + recordClass.getName() +
                    ". Arguments: " + Arrays.toString(args), e);
        }
    }

    /**
     * Validates the inputs of the createInstance method.
     */
    private void validateInput(Map<String, Object> data, Class<?> recordClass) {
        if (data == null) {
            throw new StructuraException("Data map cannot be null");
        }
        if (recordClass == null) {
            throw new StructuraException("Record class cannot be null");
        }
        if (!recordClass.isRecord()) {
            throw new StructuraException("Class " + recordClass.getName() + " is not a record type");
        }
        if (valueConverter == null) {
            throw new StructuraException("ValueConverter not injected - call setValueConverter() first");
        }
    }
}