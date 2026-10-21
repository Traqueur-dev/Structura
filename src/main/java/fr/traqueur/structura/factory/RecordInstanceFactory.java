package fr.traqueur.structura.factory;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.conversion.ReadSession;
import fr.traqueur.structura.conversion.ValueConverter;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.mapping.FieldMapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

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
        int errorsBefore = ReadSession.errorCount();

        Object[] args;
        if (keyComponent != null) {
            args = buildArgsWithKeyMapping(data, recordClass, keyComponent, prefix);
        } else {
            args = buildNormalArgs(components, data, recordClass, prefix);
        }

        if (ReadSession.errorCount() > errorsBefore) {
            // A component failed and its error was collected: the record cannot be built,
            // and building it from placeholders would only add misleading errors.
            return null;
        }

        return instantiateRecord(recordClass, args, prefix);
    }

    /**
     * Resolves one component, reporting its error instead of aborting the record when the read
     * collects errors, so that the following components are still read.
     *
     * @param resolver the resolution of the component
     * @return the resolved value, or null if it failed and its error was collected
     */
    private Object resolveOrReport(Supplier<Object> resolver) {
        try {
            return resolver.get();
        } catch (StructuraException e) {
            ReadSession.report(e);
            return null;
        }
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
        // The entry's key is the key component's value, its value holds the other components
        String entryPath = fieldMapper.buildPath(prefix, keyValue);

        Object[] args = new Object[components.length];

        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];

            if (component.getName().equals(keyComponent.getName())) {
                args[i] = resolveOrReport(() -> valueConverter.convert(keyValue, component.getType(), entryPath));
            } else {
                Parameter parameter = getConstructorParameter(recordClass, i);

                if (valueData instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> valueMap = (Map<String, Object>) valueData;
                    args[i] = resolveOrReport(() -> resolveComponentValue(component, parameter, valueMap, entryPath));
                } else {
                    String fieldName = fieldMapper.getEffectiveFieldName(parameter, component.getName());
                    String path = fieldMapper.buildPath(entryPath, fieldName);
                    args[i] = resolveOrReport(() -> fieldMapper.getDefaultValue(parameter, path, fieldName));
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
                args[i] = resolveOrReport(() -> createInstance(keyComponentData, component.getType(), prefix));
            } else {
                Parameter parameter = getConstructorParameter(recordClass, i);
                args[i] = resolveOrReport(() -> resolveComponentValue(component, parameter, otherFieldsData, prefix));
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
            args[i] = resolveOrReport(() -> resolveComponentValue(component, parameter, data, prefix));
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

            // For maps, absorb every key of the node that no sibling component claims
            if (Map.class.isAssignableFrom(component.getType())) {
                return resolveInlineMapValue(component, parameter, data, prefix);
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

        Object value = fieldMapper.getValueFromPath(data, fieldName);

        if (value == null) {
            value = fieldMapper.getDefaultValue(parameter, fullPath, fieldName);
        }

        // Handle inline polymorphic keys
        if (value != null && isPolymorphicWithInline(component.getType())) {
            value = enrichWithInlineKey(value, data, component.getType(), fullPath);
        }

        // Handle useKeyAsDiscriminator for simple polymorphic fields
        if (value != null && isPolymorphicWithKeyAsDiscriminator(component.getType())) {
            value = enrichWithFieldNameAsDiscriminator(value, fieldName, component.getType(), fullPath);
        }

        return value != null
                ? valueConverter.convert(value, component.getGenericType(), component.getType(), fullPath)
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

        // Inline also works for maps, which become the catch-all of their node.
        // Map is an interface, so this must be checked before the polymorphic
        // interface branch below, which would otherwise report false for it.
        if (Map.class.isAssignableFrom(type)) {
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
     * Resolves an inline catch-all map: every key of the node that no sibling
     * component claims is handed to the map, converted through its generic value type.
     *
     * <p>The map is never null — when nothing is left to absorb it is simply empty,
     * which is why this never falls back to {@code getDefaultValue}.</p>
     *
     * @param component the map component
     * @param parameter the corresponding constructor parameter
     * @param data the YAML data of the node
     * @param prefix the path prefix
     * @return the converted map, possibly empty
     */
    private Object resolveInlineMapValue(RecordComponent component, Parameter parameter,
                                         Map<String, Object> data, String prefix) {
        Class<?> recordClass = parameter.getDeclaringExecutable().getDeclaringClass();

        Set<String> claimedKeys = new HashSet<>();
        collectClaimedKeys(recordClass, component, component.getName(), claimedKeys);

        Map<String, Object> remaining = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!claimedKeys.contains(entry.getKey())) {
                remaining.put(entry.getKey(), entry.getValue());
            }
        }

        return valueConverter.convert(remaining, component.getGenericType(), component.getType(), prefix);
    }

    /**
     * Collects the YAML keys claimed by the siblings of an inline catch-all map.
     *
     * <p>A key is claimed by its component's effective name, so {@code @Options(name = "...")}
     * is honoured. An inline sibling record reads its own components at this very level,
     * so its keys are collected recursively.</p>
     *
     * @param recordClass the record whose components are inspected
     * @param inlineMapComponent the catch-all map, used for error messages
     * @param skippedComponentName the component to skip (the map itself), null when recursing
     * @param claimedKeys the set to populate
     * @throws StructuraException if a second inline map or a fully inline polymorphic
     *                            component competes for the same node
     */
    private void collectClaimedKeys(Class<?> recordClass, RecordComponent inlineMapComponent,
                                    String skippedComponentName, Set<String> claimedKeys) {
        RecordComponent[] components = recordClass.getRecordComponents();

        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            if (component.getName().equals(skippedComponentName)) {
                continue;
            }

            Parameter parameter = getConstructorParameter(recordClass, i);
            Class<?> type = component.getType();

            if (isInlineField(parameter, type)) {
                if (type.isRecord()) {
                    collectClaimedKeys(type, inlineMapComponent, null, claimedKeys);
                    continue;
                }

                if (Map.class.isAssignableFrom(type)) {
                    throw new StructuraException(
                            "Two inline maps compete for the same node: '" +
                                    describeComponent(inlineMapComponent) + "' and '" +
                                    recordClass.getSimpleName() + "." + component.getName() +
                                    "'. Nothing says how the remaining keys should be split between them: " +
                                    "keep a single @Options(inline = true) map and nest the other one under its own key."
                    );
                }

                throw new StructuraException(
                        "The inline map '" + describeComponent(inlineMapComponent) +
                                "' cannot live next to the fully inline polymorphic component '" +
                                recordClass.getSimpleName() + "." + component.getName() +
                                "': the keys of a @Options(inline = true) + @Polymorphic(inline = true) component " +
                                "are only known once its discriminator has been read, so the map would silently " +
                                "swallow them. Remove @Options(inline = true) from one of the two."
                );
            }

            claimedKeys.add(fieldMapper.getEffectiveFieldName(parameter, component.getName()));

            // A @Polymorphic(inline = true) sibling also reads its discriminator at this level
            if (isPolymorphicWithInline(type)) {
                claimedKeys.add(type.getAnnotation(Polymorphic.class).key());
            }
        }
    }

    /**
     * Renders a component as {@code Record.component} for error messages.
     */
    private String describeComponent(RecordComponent component) {
        return component.getDeclaringRecord().getSimpleName() + "." + component.getName();
    }

    /**
     * Checks if a type is a polymorphic interface with inline key.
     *
     * @param type the type to check
     * @return true if the type is polymorphic with inline=true
     * @throws StructuraException if both inline and useKey are true
     */
    private boolean isPolymorphicWithInline(Class<?> type) {
        if (!type.isInterface() || !Loadable.class.isAssignableFrom(type)) {
            return false;
        }
        Polymorphic polymorphic = type.getAnnotation(Polymorphic.class);
        if (polymorphic == null) {
            return false;
        }

        return polymorphic.inline();
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
            throw new StructuraException(StructuraException.Kind.WRONG_TYPE,
                    "Inline polymorphic field at " + fullPath + " must have a Map value, but got " + value.getClass().getName(),
                    fullPath
            );
        }

        Polymorphic polymorphic = type.getAnnotation(Polymorphic.class);
        String discriminatorKey = polymorphic.key();

        if (!parentData.containsKey(discriminatorKey)) {
            throw new StructuraException(StructuraException.Kind.MISSING,
                    "Inline polymorphic field at " + fullPath + " requires discriminator key '" +
                            discriminatorKey + "' at the same level as the field",
                    parentPath(fullPath)
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
            throw new StructuraException(StructuraException.Kind.MISSING,
                    "Fully inline polymorphic field at " + fullPath + " requires discriminator key '" +
                            discriminatorKey + "' at the parent level",
                    parentPath(fullPath)
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
     * The path of the section holding a key: {@code a.b} for {@code a.b.c}, empty at the root.
     */
    private static String parentPath(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(0, dot);
    }

    /**
     * Instantiates a record with the given arguments.
     *
     * <p>An exception thrown by the record itself (a check in its compact constructor) means the
     * values were read but refused: it is reported as {@link StructuraException.Kind#INVALID} at
     * the path of the record, with the record's own message.</p>
     */
    private Object instantiateRecord(Class<?> recordClass, Object[] args, String path) {
        try {
            Constructor<?> constructor = recordClass.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof StructuraException structuraException) {
                throw structuraException.atPath(path);
            }
            String reason = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
            throw new StructuraException(StructuraException.Kind.INVALID,
                    recordClass.getSimpleName() + " refused its values: " + reason, path, cause);
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

    /**
     * Checks if a type is polymorphic with useKeyAsDiscriminator enabled.
     *
     * @param type the type to check
     * @return true if the type has @Polymorphic(useKey = true)
     * @throws StructuraException if both inline and useKey are true
     */
    private boolean isPolymorphicWithKeyAsDiscriminator(Class<?> type) {
        if (!type.isInterface() || !Loadable.class.isAssignableFrom(type)) {
            return false;
        }
        Polymorphic polymorphic = type.getAnnotation(Polymorphic.class);
        if (polymorphic == null) {
            return false;
        }

        return polymorphic.useKey();
    }

    /**
     * Enriches a field value with the field name as discriminator for polymorphic resolution.
     * This is used when @Polymorphic(useKeyAsDiscriminator = true) is set on the field's type.
     *
     * @param value the field value (must be a Map)
     * @param fieldName the YAML field name to use as discriminator value
     * @param type the polymorphic type
     * @param fullPath the full path for error messages
     * @return a Map with the discriminator key added
     */
    private Map<String, Object> enrichWithFieldNameAsDiscriminator(Object value, String fieldName,
                                                                    Class<?> type, String fullPath) {
        if (!(value instanceof Map<?, ?>)) {
            throw new StructuraException(StructuraException.Kind.WRONG_TYPE,
                "Polymorphic field with useKeyAsDiscriminator at " + fullPath +
                " must have a Map value, but got " + value.getClass().getName(),
                fullPath
            );
        }

        Polymorphic polymorphic = type.getAnnotation(Polymorphic.class);
        String discriminatorKey = polymorphic.key();

        @SuppressWarnings("unchecked")
        Map<String, Object> valueMap = (Map<String, Object>) value;
        Map<String, Object> enrichedMap = new HashMap<>(valueMap);
        enrichedMap.put(discriminatorKey, fieldName);

        return enrichedMap;
    }

    /**
     * Gets the FieldMapper used by this factory.
     *
     * @return the FieldMapper
     */
    public FieldMapper getFieldMapper() {
        return fieldMapper;
    }
}