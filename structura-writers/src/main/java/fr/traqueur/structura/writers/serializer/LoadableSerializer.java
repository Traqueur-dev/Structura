package fr.traqueur.structura.writers.serializer;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.mapping.FieldMapper;
import fr.traqueur.structura.references.Reference;
import fr.traqueur.structura.registries.PolymorphicRegistry;
import fr.traqueur.structura.writers.exceptions.StructuraWriterException;
import fr.traqueur.structura.writers.registries.CustomWriterRegistry;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Serializes a {@link Loadable} record to a YAML string.
 *
 * <p>Supports the full symmetric set of features that the Structura reader understands:</p>
 * <ul>
 *   <li>camelCase → kebab-case key conversion</li>
 *   <li>{@code @Options(inline = true)} — flattens a sub-record's fields into the parent map</li>
 *   <li>{@code @Options(optional = true)} — null fields are silently omitted</li>
 *   <li>{@code @Options(name = "...")} — overrides the YAML key for a field</li>
 *   <li>{@code @Options(isKey = true)} simple — record serialized as {@code {keyValue: {otherFields}}}</li>
 *   <li>{@code @Options(isKey = true)} complex — key sub-record's fields flattened at same level</li>
 *   <li>{@code @Polymorphic} standard — discriminator written <em>inside</em> the nested map</li>
 *   <li>{@code @Polymorphic(inline = true)} — discriminator written at the <em>parent</em> level</li>
 *   <li>{@code @Polymorphic(inline = true)} + {@code @Options(inline = true)} — fully inline:
 *       discriminator and all concrete fields at parent level</li>
 *   <li>{@code @Polymorphic(useKey = true)} — discriminator value becomes the YAML key</li>
 *   <li>{@link java.time.LocalDate} / {@link java.time.LocalDateTime} — ISO-8601 strings</li>
 *   <li>{@link java.lang.Enum} — snake_case name converted to kebab-case</li>
 *   <li>{@link fr.traqueur.structura.references.Reference} — serialized as its key string</li>
 * </ul>
 *
 * <p>Custom types can be handled by registering a {@link fr.traqueur.structura.writers.writer.Writer}
 * in {@link CustomWriterRegistry}.</p>
 */
public class LoadableSerializer {

    private static final DateTimeFormatter DATE_FORMATTER      = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final FieldMapper fieldMapper;
    private final Yaml        yaml;

    public LoadableSerializer() {
        this.fieldMapper = new FieldMapper();
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setIndent(2);
        opts.setExplicitStart(false);
        this.yaml = new Yaml(opts);
    }

    /** Serializes {@code config} to a block-style YAML string. */
    public String toYaml(Loadable config) {
        Objects.requireNonNull(config, "config cannot be null");
        return yaml.dump(toMap(config));
    }

    // -------------------------------------------------------------------------
    // Core record → Map conversion
    // -------------------------------------------------------------------------

    /**
     * Converts any record to a {@code Map<String, Object>} that SnakeYAML can dump.
     *
     * <p>When the record has a component marked {@code @Options(isKey = true)}, delegates to
     * {@link #toMapWithKeyComponent} which handles both simple-key and complex-key variants.</p>
     */
    private Map<String, Object> toMap(Object obj) {
        Class<?> clazz = obj.getClass();
        if (!clazz.isRecord()) {
            throw new StructuraWriterException("Cannot serialize non-record type: " + clazz.getName());
        }

        RecordComponent[] components  = clazz.getRecordComponents();
        Constructor<?>    constructor = clazz.getDeclaredConstructors()[0];
        Parameter[]       parameters  = constructor.getParameters();

        RecordComponent keyComponent = fieldMapper.findKeyComponent(components);
        if (keyComponent != null) {
            return toMapWithKeyComponent(obj, components, parameters, keyComponent);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < components.length; i++) {
            contributeToMap(result, components[i], parameters[i], readField(components[i], obj));
        }
        return result;
    }

    /**
     * Serializes a record that has a component marked {@code @Options(isKey = true)}.
     *
     * <ul>
     *   <li><b>Simple key</b> (String / primitive): returns {@code {keyValue: {otherFields}}}</li>
     *   <li><b>Complex key</b> (record type): flattens the key sub-record's fields alongside the
     *       other fields at the same level</li>
     * </ul>
     */
    private Map<String, Object> toMapWithKeyComponent(Object obj, RecordComponent[] components,
                                                       Parameter[] parameters,
                                                       RecordComponent keyComponent) {
        Class<?> keyType = keyComponent.getType();

        if (keyType.isRecord()) {
            // Complex key: flatten key-record fields alongside other fields
            Map<String, Object> result = new LinkedHashMap<>();
            for (int i = 0; i < components.length; i++) {
                RecordComponent comp  = components[i];
                Object          value = readField(comp, obj);
                if (comp.getName().equals(keyComponent.getName())) {
                    if (value != null) result.putAll(toMap(value));
                } else {
                    contributeToMap(result, comp, parameters[i], value);
                }
            }
            return result;
        }

        // Simple key: { keyValue → { otherFields } }
        Object              keyValue    = readField(keyComponent, obj);
        Map<String, Object> otherFields = new LinkedHashMap<>();
        int                 keyIdx      = findComponentIndex(components, keyComponent);
        for (int i = 0; i < components.length; i++) {
            if (i != keyIdx) {
                contributeToMap(otherFields, components[i], parameters[i], readField(components[i], obj));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(keyValue != null ? keyValue.toString() : "", otherFields.isEmpty() ? null : otherFields);
        return result;
    }

    // -------------------------------------------------------------------------
    // Per-field contribution
    // -------------------------------------------------------------------------

    /**
     * Adds the serialized representation of a single record component to {@code result}.
     *
     * <p>Dispatch (in priority order):</p>
     * <ol>
     *   <li>{@code @Options(inline=true)} + concrete Loadable record → flatten sub-fields</li>
     *   <li>{@code @Options(inline=true)} + {@code @Polymorphic(inline=true)} → fully inline</li>
     *   <li>{@code @Polymorphic(useKey=true)} → discriminator value becomes the YAML key</li>
     *   <li>{@code @Polymorphic(inline=true)} → discriminator at parent, fields under key</li>
     *   <li>{@code @Polymorphic} standard → discriminator inside nested map</li>
     *   <li>Everything else → normal key/value</li>
     * </ol>
     */
    private void contributeToMap(Map<String, Object> result, RecordComponent component,
                                  Parameter parameter, Object value) {
        Options  opts     = parameter.getAnnotation(Options.class);
        boolean  isInline = opts != null && opts.inline();
        Class<?> type     = component.getType();
        String   key      = fieldMapper.getEffectiveFieldName(parameter, component.getName());

        if (value == null) {
            // Optional null fields are silently omitted; non-optional nulls are written explicitly
            if (opts == null || !opts.optional()) {
                result.put(key, null);
            }
            return;
        }

        // ── @Options(inline = true) ──────────────────────────────────────────
        if (isInline) {
            if (type.isRecord() && Loadable.class.isAssignableFrom(type)) {
                result.putAll(toMap(value));
            } else if (isPolymorphicInterface(type)) {
                Polymorphic poly = type.getAnnotation(Polymorphic.class);
                if (poly.inline()) {
                    // Fully inline: discriminator + all concrete fields at parent level
                    result.put(poly.key(), lookupRegisteredName(value.getClass(), type));
                    result.putAll(toMap(value));
                } else {
                    result.put(key, serializeValue(value, component.getGenericType()));
                }
            } else {
                result.put(key, serializeValue(value, component.getGenericType()));
            }
            return;
        }

        // ── Polymorphic interface ────────────────────────────────────────────
        if (isPolymorphicInterface(type)) {
            Polymorphic poly      = type.getAnnotation(Polymorphic.class);
            String      discValue = lookupRegisteredName(value.getClass(), type);

            if (poly.useKey()) {
                // Discriminator value becomes the YAML key; field name is dropped
                result.put(discValue, toMap(value));
            } else if (poly.inline()) {
                // Discriminator at parent level, concrete fields nested under key
                result.put(poly.key(), discValue);
                result.put(key, toMap(value));
            } else {
                // Standard: discriminator first, inside the nested map
                Map<String, Object> nested = new LinkedHashMap<>();
                nested.put(poly.key(), discValue);
                nested.putAll(toMap(value));
                result.put(key, nested);
            }
            return;
        }

        result.put(key, serializeValue(value, component.getGenericType()));
    }

    // -------------------------------------------------------------------------
    // Value serialization
    // -------------------------------------------------------------------------

    private Object serializeValue(Object value, Type genericType) {
        if (value == null) return null;

        if (value instanceof Reference<?> ref) return ref.key();

        Optional<Object> custom = CustomWriterRegistry.getInstance().write(value, value.getClass());
        if (custom.isPresent()) return custom.get();

        if (genericType instanceof ParameterizedType paramType) {
            Type[] typeArgs = paramType.getActualTypeArguments();
            Type   elemType = typeArgs.length > 0 ? typeArgs[0] : Object.class;
            if (value instanceof List<?> list) return serializeCollection(list, elemType);
            if (value instanceof Set<?> set)  return serializeCollection(new ArrayList<>(set), elemType);
            if (value instanceof Map<?, ?> map) return serializeMap(map, typeArgs);
        }

        if (value.getClass().isRecord())       return toMap(value);
        if (value instanceof Enum<?> e)        return fieldMapper.convertSnakeCaseToKebabCase(e.name());
        if (value instanceof LocalDate d)      return d.format(DATE_FORMATTER);
        if (value instanceof LocalDateTime dt) return dt.format(DATE_TIME_FORMATTER);

        return value;
    }

    /**
     * Serializes a {@code List} or (flattened) {@code Set}.
     *
     * <ul>
     *   <li>{@code @Polymorphic(useKey=true)} elements → map keyed by registered name</li>
     *   <li>{@code @Options(isKey=true)} record elements → {@code toMap()} already wraps each
     *       element as {@code {keyValue: {otherFields}}}; results are merged into one map</li>
     *   <li>Standard polymorphic elements → list with discriminator inside each entry</li>
     *   <li>Otherwise → plain list</li>
     * </ul>
     */
    private Object serializeCollection(List<?> elements, Type elementGenericType) {
        if (elements.isEmpty()) return new ArrayList<>();

        Class<?> elementRawType = getRawClass(elementGenericType);

        // useKey polymorphic list → map { registeredName: { fields } }
        if (isPolymorphicWithUseKey(elementRawType)) {
            Map<String, Object> resultMap = new LinkedHashMap<>();
            for (Object elem : elements) {
                resultMap.put(lookupRegisteredName(elem.getClass(), elementRawType), toMap(elem));
            }
            return resultMap;
        }

        // isKey record list
        if (elementRawType.isRecord()) {
            RecordComponent keyComp = fieldMapper.findKeyComponent(elementRawType.getRecordComponents());
            if (keyComp != null) {
                if (keyComp.getType().isRecord()) {
                    // Complex key: toMap() flattens the key sub-record; serialize as a YAML list
                    return elements.stream()
                            .map(e -> toMap(e))
                            .collect(Collectors.toList());
                }
                // Simple key: toMap() returns { keyValue: { otherFields } }; merge into one map
                Map<String, Object> resultMap = new LinkedHashMap<>();
                for (Object elem : elements) {
                    resultMap.putAll(toMap(elem));
                }
                return resultMap;
            }
        }

        // Standard polymorphic list → list with discriminator inside each entry
        if (isPolymorphicInterface(elementRawType)) {
            Polymorphic  poly       = elementRawType.getAnnotation(Polymorphic.class);
            List<Object> resultList = new ArrayList<>();
            for (Object elem : elements) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put(poly.key(), lookupRegisteredName(elem.getClass(), elementRawType));
                entry.putAll(toMap(elem));
                resultList.add(entry);
            }
            return resultList;
        }

        return elements.stream()
                .map(e -> serializeValue(e, elementGenericType))
                .collect(Collectors.toList());
    }

    /**
     * Serializes a {@code Map}.
     *
     * <ul>
     *   <li>{@code @Polymorphic(useKey=true)} values → outer key is the discriminator,
     *       just write concrete fields (no extra discriminator key)</li>
     *   <li>{@code @Options(isKey=true)} record values → {@code toMap()} wraps as
     *       {@code {keyValue: {otherFields}}}; extract the value so the outer map key is kept</li>
     *   <li>Standard polymorphic values → discriminator inside each value map</li>
     *   <li>Otherwise → normal map</li>
     * </ul>
     */
    private Object serializeMap(Map<?, ?> map, Type[] typeArgs) {
        Type     valueGenericType = typeArgs.length > 1 ? typeArgs[1] : Object.class;
        Class<?> valueRawType     = getRawClass(valueGenericType);

        Map<String, Object> result = new LinkedHashMap<>();

        // useKey map: outer key IS the discriminator — write concrete fields only
        if (isPolymorphicWithUseKey(valueRawType)) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                result.put(e.getKey().toString(), e.getValue() != null ? toMap(e.getValue()) : null);
            }
            return result;
        }

        // isKey record values: toMap() wraps as { keyValue: { otherFields } };
        // the outer map key is already provided, so we extract the inner value
        if (valueRawType.isRecord()
                && fieldMapper.findKeyComponent(valueRawType.getRecordComponents()) != null) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getValue() == null) { result.put(e.getKey().toString(), null); continue; }
                Map<String, Object> wrapped = toMap(e.getValue());
                result.put(e.getKey().toString(), wrapped.values().iterator().next());
            }
            return result;
        }

        // Standard polymorphic map values: discriminator inside each value
        if (isPolymorphicInterface(valueRawType)) {
            Polymorphic poly = valueRawType.getAnnotation(Polymorphic.class);
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getValue() == null) { result.put(e.getKey().toString(), null); continue; }
                Map<String, Object> valueMap = new LinkedHashMap<>();
                valueMap.put(poly.key(), lookupRegisteredName(e.getValue().getClass(), valueRawType));
                valueMap.putAll(toMap(e.getValue()));
                result.put(e.getKey().toString(), valueMap);
            }
            return result;
        }

        for (Map.Entry<?, ?> e : map.entrySet()) {
            result.put(e.getKey().toString(), serializeValue(e.getValue(), valueGenericType));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Polymorphic registry reverse-lookup
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private String lookupRegisteredName(Class<?> concreteClass, Class<?> polymorphicInterface) {
        PolymorphicRegistry<Loadable> registry =
                (PolymorphicRegistry<Loadable>) PolymorphicRegistry.get(
                        (Class<? extends Loadable>) polymorphicInterface);

        for (String name : registry.availableNames()) {
            if (registry.get(name).filter(c -> c == concreteClass).isPresent()) {
                return name;
            }
        }
        throw new StructuraWriterException(
                "No registered name for '" + concreteClass.getName() +
                "' in polymorphic registry of '" + polymorphicInterface.getName() + "'. " +
                "Register it via PolymorphicRegistry.get(" + polymorphicInterface.getSimpleName() +
                ".class).register(\"name\", " + concreteClass.getSimpleName() + ".class)"
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isPolymorphicInterface(Class<?> type) {
        return type.isInterface()
                && Loadable.class.isAssignableFrom(type)
                && type.isAnnotationPresent(Polymorphic.class);
    }

    private boolean isPolymorphicWithUseKey(Class<?> type) {
        return isPolymorphicInterface(type) && type.getAnnotation(Polymorphic.class).useKey();
    }

    private Class<?> getRawClass(Type type) {
        return switch (type) {
            case Class<?> c           -> c;
            case ParameterizedType pt -> (Class<?>) pt.getRawType();
            default                   -> Object.class;
        };
    }

    private Object readField(RecordComponent component, Object obj) {
        try {
            return component.getAccessor().invoke(obj);
        } catch (ReflectiveOperationException e) {
            throw new StructuraWriterException("Cannot read field: " + component.getName(), e);
        }
    }

    private int findComponentIndex(RecordComponent[] components, RecordComponent target) {
        for (int i = 0; i < components.length; i++) {
            if (components[i].getName().equals(target.getName())) return i;
        }
        return -1;
    }
}
