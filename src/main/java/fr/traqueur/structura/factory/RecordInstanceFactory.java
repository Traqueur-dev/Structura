package fr.traqueur.structura.factory;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.annotations.Strict;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.conversion.ReadSession;
import fr.traqueur.structura.conversion.ValueConverter;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.mapping.FieldMapper;
import fr.traqueur.structura.mapping.Polymorphism;
import fr.traqueur.structura.mapping.UnknownKeys;
import fr.traqueur.structura.registries.PolymorphicRegistry;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        return createInstance(data, recordClass, prefix, !ReadSession.isShared(data));
    }

    /**
     * Creates a record instance from YAML data.
     *
     * @param data the YAML data as Map
     * @param recordClass the record class to create
     * @param prefix the path of the node in the document
     * @param ownsNode whether the record owns its node: false for an inline record or a key record
     *                 reading their parent's node, whose keys the parent checks
     * @return the created and populated record instance, or null if a component failed and its
     *         error was collected
     */
    private Object createInstance(Map<String, Object> data, Class<?> recordClass, String prefix, boolean ownsNode) {
        validateInput(data, recordClass);

        if (recordClass.isAnnotationPresent(Strict.class)) {
            return ReadSession.strictScope(() -> buildInstance(data, recordClass, prefix, ownsNode, true));
        }
        return buildInstance(data, recordClass, prefix, ownsNode, ReadSession.strictKeys());
    }

    private Object buildInstance(Map<String, Object> data, Class<?> recordClass, String prefix,
                                 boolean ownsNode, boolean strict) {
        RecordComponent[] components = recordClass.getRecordComponents();
        RecordComponent keyComponent = fieldMapper.findKeyComponent(components);
        int errorsBefore = ReadSession.errorCount();

        if (strict && ownsNode) {
            // Before the components: a typo is the reason a key is missing, report it first
            checkUnknownKeys(data, recordClass, keyComponent, prefix);
        }

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
                args[i] = resolveOrReport(() -> createInstance(keyComponentData, component.getType(), prefix, false));
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
                return createInstance(data, component.getType(), prefix, false);
            }

            // For maps, absorb every key of the node that no sibling component claims
            if (Map.class.isAssignableFrom(component.getType())) {
                return resolveInlineMapValue(component, parameter, data, prefix);
            }

            // For @Polymorphic(useKey = true, inline = true), the registered key present in the node is the sub-type
            if (Polymorphism.isKeySelected(component.getType())) {
                return resolveKeySelectedComponent(component, parameter, data, prefix);
            }

            // For polymorphic interfaces, enrich parent data with discriminator and convert
            if (component.getType().isInterface() && isPolymorphicWithInline(component.getType())) {
                String fieldName = fieldMapper.getEffectiveFieldName(parameter, component.getName());
                String fullPath = fieldMapper.buildPath(prefix, fieldName);

                // Enrich parent data with discriminator key for polymorphic resolution
                // The sub-type reads its parent's node: its keys are checked here, by the parent
                Map<String, Object> enrichedData = ReadSession.share(
                        enrichParentDataWithDiscriminator(data, component.getType(), fullPath));

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
     * An inline {@code @Polymorphic(useKey = true, inline = true)} component: the registered key present in
     * the record's own node selects the sub-type. With no such key, an optional component is null and any
     * other is missing.
     */
    private Object resolveKeySelectedComponent(RecordComponent component, Parameter parameter,
                                               Map<String, Object> data, String prefix) {
        Class<?> type = component.getType();
        PolymorphicRegistry<?> registry = keySelectedRegistry(type);
        if (presentKeys(data, registry).isEmpty()) {
            if (fieldMapper.isOptional(parameter)) {
                return null;
            }
            String fieldName = fieldMapper.getEffectiveFieldName(parameter, component.getName());
            throw new StructuraException(StructuraException.Kind.MISSING,
                    "'" + fieldName + "' is required but not provided: set one of "
                            + String.join(", ", registry.availableNames()),
                    fieldMapper.buildPath(prefix, fieldName));
        }
        return readKeySelected(data, type, prefix, true);
    }

    /**
     * Reads a {@code @Polymorphic(useKey = true, inline = true)} value from a node: exactly one of the
     * registered keys must be present, and it names the sub-type.
     *
     * <ul>
     *   <li>When the sub-type has a component named like the key ({@code Choice(List<String> choice, ...)}
     *       selected by {@code choice}), it reads the whole node: {@code {choice: [a, b], initial: a}}.</li>
     *   <li>Otherwise it reads the value of the key: a section ({@code select: {tab: chat}}), or a single
     *       value read into its only component ({@code toggle: view.chat} for {@code Toggle(String target)}).</li>
     * </ul>
     *
     * @param value the node: the parent's node for an inline component, the value's own node otherwise
     * @param type the polymorphic interface
     * @param nodePath the path of the node in the document
     * @param shared whether the node belongs to a parent record, which checks its keys in strict mode
     * @return the sub-type instance, or null if it failed and its error was collected
     * @throws StructuraException if no registered key, or more than one, is present
     */
    public Object readKeySelected(Object value, Class<?> type, String nodePath, boolean shared) {
        PolymorphicRegistry<?> registry = keySelectedRegistry(type);
        String names = String.join(", ", registry.availableNames());
        if (!(value instanceof Map<?, ?> rawNode)) {
            throw new StructuraException(StructuraException.Kind.WRONG_TYPE,
                    "expected a section with one of " + names + " but got " + ValueConverter.describe(value), nodePath);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) rawNode;

        List<String> present = presentKeys(node, registry);
        if (present.isEmpty()) {
            throw new StructuraException(StructuraException.Kind.MISSING,
                    "a " + type.getSimpleName() + " needs one of " + names, nodePath);
        }
        if (present.size() > 1) {
            throw new StructuraException(StructuraException.Kind.INVALID,
                    "'" + present.get(0) + "' and '" + present.get(1) + "' cannot both be set"
                            + (nodePath.isEmpty() ? "" : " under '" + nodePath + "'")
                            + ": a " + type.getSimpleName() + " is one of " + names,
                    fieldMapper.buildPath(nodePath, present.get(1))).pointAtKey();
        }

        String key = present.getFirst();
        Class<?> subType = registry.get(key).orElseThrow();
        if (readsWholeNode(subType, key)) {
            return createInstance(node, subType, nodePath, !shared);
        }

        if (!shared && ReadSession.strictKeys()) {
            // The node holds the key alone: a neighbour is unknown, and suggested among the registered keys
            for (Object other : node.keySet()) {
                if (!key.equals(String.valueOf(other))) {
                    ReadSession.report(UnknownKeys.error(String.valueOf(other), nodePath, registry.availableNames()));
                }
            }
        }

        String valuePath = fieldMapper.buildPath(nodePath, key);
        Object subNode = node.get(key);
        if (subNode == null) {
            return createInstance(new LinkedHashMap<>(), subType, valuePath, true);
        }
        if (subNode instanceof Map<?, ?> section) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sectionData = (Map<String, Object>) section;
            return createInstance(sectionData, subType, valuePath, !ReadSession.isShared(sectionData));
        }
        return readSingleValue(subNode, subType, key, valuePath);
    }

    /**
     * A single value under the key of a sub-type ({@code toggle: view.chat}) is read into its only
     * component; a sub-type with several components, or an inline one, needs a section.
     */
    private Object readSingleValue(Object value, Class<?> subType, String key, String valuePath) {
        RecordComponent[] components = subType.getRecordComponents();
        Parameter parameter = components.length == 1 ? getConstructorParameter(subType, 0) : null;
        if (parameter == null || isInlineField(parameter, components[0].getType())) {
            String reads = String.join(", ", Arrays.stream(components).map(FieldMapper::keyOf).toList());
            throw new StructuraException(StructuraException.Kind.WRONG_TYPE,
                    ValueConverter.describe(value) + " cannot be read as a " + subType.getSimpleName()
                            + ", which reads " + (reads.isEmpty() ? "nothing" : reads)
                            + ": write a section under '" + key + "'", valuePath);
        }
        int errorsBefore = ReadSession.errorCount();
        Object argument = valueConverter.convert(value, components[0].getGenericType(), components[0].getType(), valuePath);
        if (ReadSession.errorCount() > errorsBefore) {
            return null;
        }
        return instantiateRecord(subType, new Object[]{argument}, valuePath);
    }

    /**
     * Whether a sub-type selected by a key reads the whole node: it has a component named like the key.
     */
    private static boolean readsWholeNode(Class<?> subType, String key) {
        return subType.isRecord() && Arrays.stream(subType.getRecordComponents())
                .anyMatch(component -> FieldMapper.keyOf(component).equals(key));
    }

    /**
     * The registered keys present in a node, in the order of the document.
     */
    private static List<String> presentKeys(Map<?, ?> node, PolymorphicRegistry<?> registry) {
        List<String> present = new ArrayList<>();
        for (Object key : node.keySet()) {
            String name = String.valueOf(key);
            if (registry.get(name).isPresent()) {
                present.add(name);
            }
        }
        return present;
    }

    @SuppressWarnings("unchecked")
    private static PolymorphicRegistry<?> keySelectedRegistry(Class<?> type) {
        return ReadSession.polymorphicRegistry((Class<? extends Loadable>) type);
    }

    /**
     * Strict mode: reports every key of the record's node that none of its components reads,
     * with the closest known key as a suggestion.
     *
     * @param data the node of the record
     * @param recordClass the record class
     * @param keyComponent the component marked {@code isKey}, or null
     * @param prefix the path of the node in the document
     */
    private void checkUnknownKeys(Map<String, Object> data, Class<?> recordClass,
                                  RecordComponent keyComponent, String prefix) {
        Map<?, ?> node = data;
        String nodePath = prefix;
        RecordComponent skipped = null;

        if (keyComponent != null && fieldMapper.isSimpleKeyMapping(data, keyComponent)) {
            // { id: { ...the other components... } }: the single key is a value, the inner node holds the keys
            Map.Entry<String, Object> entry = data.entrySet().iterator().next();
            if (!(entry.getValue() instanceof Map<?, ?> inner)) {
                return;
            }
            node = inner;
            nodePath = fieldMapper.buildPath(prefix, String.valueOf(entry.getKey()));
            skipped = keyComponent;
        }

        Set<String> known = new LinkedHashSet<>();
        if (!collectReadKeys(recordClass, node, skipped, known)) {
            return; // an inline map absorbs every remaining key
        }

        for (Object rawKey : node.keySet()) {
            String key = String.valueOf(rawKey);
            if (!known.contains(key)) {
                ReadSession.report(UnknownKeys.error(key, nodePath, known));
            }
        }
    }

    /**
     * Collects the keys a record reads at the level of a node: its components' keys, the keys of its
     * inline components (recursively), the discriminators of its polymorphic components and of the
     * polymorphic interfaces it implements, and for a fully inline polymorphic component, the keys of
     * the sub-type its discriminator selects.
     *
     * @param recordClass the record class
     * @param node the node being read
     * @param skipped a component that reads nothing from the node (the key of a simple key mapping), or null
     * @param known the set to populate
     * @return false if an inline map absorbs every remaining key, so that no key is unknown
     */
    private boolean collectReadKeys(Class<?> recordClass, Map<?, ?> node, RecordComponent skipped, Set<String> known) {
        RecordComponent[] components = recordClass.getRecordComponents();

        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            if (skipped != null && component.getName().equals(skipped.getName())) {
                continue;
            }
            Parameter parameter = getConstructorParameter(recordClass, i);
            Class<?> type = component.getType();

            if (isInlineField(parameter, type)) {
                if (type.isRecord()) {
                    if (!collectReadKeys(type, node, null, known)) {
                        return false;
                    }
                } else if (Map.class.isAssignableFrom(type)) {
                    return false;
                } else if (Polymorphism.isKeySelected(type)) {
                    if (!collectKeySelectedKeys(type, node, known)) {
                        return false;
                    }
                } else if (!collectPolymorphicKeys(type, node, known)) {
                    return false;
                }
                continue;
            }

            Options options = parameter.getAnnotation(Options.class);
            if (options != null && options.isKey() && type.isRecord()) {
                // Complex key mapping: the key record's components sit at this level
                if (!collectReadKeys(type, node, null, known)) {
                    return false;
                }
                continue;
            }

            String name = fieldMapper.getEffectiveFieldName(parameter, component.getName());
            // A dotted name reads a nested node: its first segment is the key at this level
            int dot = name.indexOf('.');
            known.add(dot < 0 ? name : name.substring(0, dot));

            if (isPolymorphicWithInline(type)) {
                known.add(type.getAnnotation(Polymorphic.class).key());
            }
        }

        collectDiscriminators(recordClass, known);
        return true;
    }

    /**
     * The keys of a fully inline polymorphic component: its discriminator, and the keys of the
     * sub-type the discriminator selects (an unknown type is reported by the conversion itself).
     */
    private boolean collectPolymorphicKeys(Class<?> type, Map<?, ?> node, Set<String> known) {
        String discriminator = type.getAnnotation(Polymorphic.class).key();
        known.add(discriminator);
        Object typeName = node.get(discriminator);
        if (typeName == null) {
            return true;
        }
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Loadable> polymorphicType = (Class<? extends Loadable>) type;
            Optional<? extends Class<?>> subType = ReadSession.polymorphicRegistry(polymorphicType).get(typeName.toString());
            return subType.isEmpty() || collectReadKeys(subType.get(), node, null, known);
        } catch (StructuraException e) {
            return true;
        }
    }

    /**
     * The keys of an inline {@code @Polymorphic(useKey = true, inline = true)} component: every registered
     * name (so that a typo is suggested one of them), and when the selected sub-type reads the whole node,
     * its keys too.
     */
    private boolean collectKeySelectedKeys(Class<?> type, Map<?, ?> node, Set<String> known) {
        PolymorphicRegistry<?> registry = keySelectedRegistry(type);
        known.addAll(registry.availableNames());
        List<String> present = presentKeys(node, registry);
        if (present.size() != 1) {
            return true;
        }
        Class<?> subType = registry.get(present.getFirst()).orElseThrow();
        return !readsWholeNode(subType, present.getFirst()) || collectReadKeys(subType, node, null, known);
    }

    /**
     * A sub-type of a polymorphic interface finds its discriminator in its own node.
     */
    private static void collectDiscriminators(Class<?> type, Set<String> known) {
        for (Class<?> implemented : type.getInterfaces()) {
            Polymorphic polymorphic = implemented.getAnnotation(Polymorphic.class);
            // A key-selected sub-type has no discriminator key: the key present names it
            if (polymorphic != null && !Polymorphism.isKeySelected(implemented)) {
                known.add(polymorphic.key());
            }
            collectDiscriminators(implemented, known);
        }
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

                if (Polymorphism.isKeySelected(type)) {
                    throw new StructuraException(
                            "The inline map '" + describeComponent(inlineMapComponent) +
                                    "' cannot live next to the inline polymorphic component '" +
                                    recordClass.getSimpleName() + "." + component.getName() +
                                    "': a @Options(inline = true) + @Polymorphic(useKey = true, inline = true) component " +
                                    "is selected by the registered key present in the node, so the map would silently " +
                                    "swallow it. Remove @Options(inline = true) from one of the two."
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
        // useKey + inline is another form: the sub-type is the key present, there is no discriminator key
        return type.isInterface() && Polymorphism.isInlineDiscriminated(type);
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
        // useKey + inline is another form: the sub-type is the key present in the value's own section
        return type.isInterface() && Polymorphism.isKeyDiscriminated(type);
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