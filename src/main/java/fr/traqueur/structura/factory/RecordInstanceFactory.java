package fr.traqueur.structura.factory;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.api.Loadable;
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

    /**
     * Constructor for RecordInstanceFactory.
     *
     * @param fieldMapper the field mapper to use
     */
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
        // Check if this field should be inlined (flattened) at parent level
        if (isInlineField(parameter, component.getType())) {
            // For concrete records, create instance directly with parent data
            if (component.getType().isRecord()) {
                return createInstance(data, component.getType(), prefix);
            }

            // For polymorphic interfaces, enrich parent data with discriminator and convert
            if (component.getType().isInterface() && isPolymorphicWithInline(component.getType())) {
                String fieldName = fieldMapper.getEffectiveFieldName(parameter, component.getName());
                String fullPath = fieldMapper.buildPath(prefix, fieldName);

                // Enrich parent data with discriminator key for polymorphic resolution
                Map<String, Object> enrichedData = enrichParentDataWithDiscriminator(data, component.getType(), fullPath);

                return valueConverter.convert(enrichedData, component.getGenericType(), component.getType(), prefix);
            }
        }

        String fieldName = fieldMapper.getEffectiveFieldName(parameter, component.getName());
        String fullPath = fieldMapper.buildPath(prefix, fieldName);

        Object value = fieldMapper.getValueFromPath(data, fullPath);

        if (value == null) {
            value = fieldMapper.getDefaultValue(parameter, fullPath);
        }

        // Handle inline polymorphic keys
        if (value != null && isPolymorphicWithInline(component.getType())) {
            value = enrichWithInlineKey(value, data, component.getType(), fullPath);
        }

        return value != null
                ? valueConverter.convert(value, component.getGenericType(), component.getType(), prefix)
                : null;
    }

    /**
     * Checks if a field should be inlined (flattened) at the parent level.
     *
     * @param parameter the parameter to check
     * @param type the type of the field
     * @return true if the field should be inlined
     */
    private boolean isInlineField(Parameter parameter, Class<?> type) {
        Options options = parameter.getAnnotation(Options.class);
        if (options == null || !options.inline()) {
            return false;
        }

        // Inline works for records implementing Loadable
        if (type.isRecord() && Loadable.class.isAssignableFrom(type)) {
            return true;
        }

        // Inline also works for polymorphic interfaces with @Polymorphic(inline = true)
        if (type.isInterface() && Loadable.class.isAssignableFrom(type)) {
            Polymorphic polymorphic = type.getAnnotation(Polymorphic.class);
            return polymorphic != null && polymorphic.inline();
        }

        return false;
    }

    /**
     * Checks if a type is a polymorphic interface with inline key.
     *
     * @param type the type to check
     * @return true if the type is polymorphic with inline=true
     */
    private boolean isPolymorphicWithInline(Class<?> type) {
        if (!type.isInterface() || !Loadable.class.isAssignableFrom(type)) {
            return false;
        }
        Polymorphic polymorphic = type.getAnnotation(Polymorphic.class);
        return polymorphic != null && polymorphic.inline();
    }

    /**
     * Enriches a field value with the inline discriminator key from the parent data.
     *
     * @param value the field value (must be a Map)
     * @param parentData the parent data containing the discriminator key
     * @param type the polymorphic type
     * @param fullPath the full path for error messages
     * @return the enriched value with the discriminator key added
     */
    private Object enrichWithInlineKey(Object value, Map<String, Object> parentData, Class<?> type, String fullPath) {
        if (!(value instanceof Map)) {
            throw new StructuraException(
                    "Inline polymorphic field at " + fullPath + " must have a Map value, but got " + value.getClass().getName()
            );
        }

        Polymorphic polymorphic = type.getAnnotation(Polymorphic.class);
        String discriminatorKey = polymorphic.key();

        if (!parentData.containsKey(discriminatorKey)) {
            throw new StructuraException(
                    "Inline polymorphic field at " + fullPath + " requires discriminator key '" +
                            discriminatorKey + "' at the same level as the field"
            );
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> valueMap = (Map<String, Object>) value;
        Map<String, Object> enrichedMap = new HashMap<>(valueMap);
        enrichedMap.put(discriminatorKey, parentData.get(discriminatorKey));

        return enrichedMap;
    }

    /**
     * Enriches parent data with discriminator key for fully inline polymorphic fields.
     * When both @Options(inline = true) and @Polymorphic(inline = true) are used,
     * all fields including the discriminator are at the parent level.
     *
     * @param parentData the parent data containing all fields
     * @param type the polymorphic interface type
     * @param fullPath the full path for error messages
     * @return a copy of parent data (already contains discriminator)
     */
    private Map<String, Object> enrichParentDataWithDiscriminator(Map<String, Object> parentData,
                                                                   Class<?> type, String fullPath) {
        Polymorphic polymorphic = type.getAnnotation(Polymorphic.class);
        String discriminatorKey = polymorphic.key();

        if (!parentData.containsKey(discriminatorKey)) {
            throw new StructuraException(
                    "Fully inline polymorphic field at " + fullPath + " requires discriminator key '" +
                            discriminatorKey + "' at the parent level"
            );
        }

        // Parent data already contains all fields including the discriminator
        // Just return a copy to avoid mutations
        return new HashMap<>(parentData);
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