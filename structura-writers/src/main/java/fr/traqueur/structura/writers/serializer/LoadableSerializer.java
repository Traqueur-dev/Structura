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
 * <p>Handles:</p>
 * <ul>
 *   <li>camelCase → kebab-case key conversion</li>
 *   <li>{@code @Options(inline = true)} — flattens a sub-record's fields into the parent map</li>
 *   <li>{@code @Polymorphic} standard — discriminator written <em>inside</em> the nested map</li>
 *   <li>{@code @Polymorphic(inline = true)} — discriminator written at the <em>parent</em> level</li>
 *   <li>{@code @Polymorphic(inline = true)} + {@code @Options(inline = true)} — fully inline:
 *       discriminator and all concrete fields at parent level</li>
 * </ul>
 *
 * <p>Not yet handled (TODO):</p>
 * <ul>
 *   <li>{@code @Polymorphic(useKey = true)}</li>
 *   <li>{@code @Options(isKey = true)}</li>
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

    private Map<String, Object> toMap(Object obj) {
        Class<?> clazz = obj.getClass();
        if (!clazz.isRecord()) {
            throw new StructuraWriterException("Cannot serialize non-record type: " + clazz.getName());
        }

        RecordComponent[] components  = clazz.getRecordComponents();
        Constructor<?>    constructor = clazz.getDeclaredConstructors()[0];
        Parameter[]       parameters  = constructor.getParameters();

        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < components.length; i++) {
            contributeToMap(result, components[i], parameters[i], readField(components[i], obj));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Per-field contribution
    // -------------------------------------------------------------------------

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

        if (isInline) {
            if (type.isRecord() && Loadable.class.isAssignableFrom(type)) {
                // Flatten sub-record fields at parent level
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

        if (isPolymorphicInterface(type)) {
            Polymorphic poly      = type.getAnnotation(Polymorphic.class);
            String      discValue = lookupRegisteredName(value.getClass(), type);

            if (poly.useKey()) {
                // useKey: discriminator value becomes the YAML key; field name is dropped
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

    private Object serializeCollection(List<?> elements, Type elementGenericType) {
        if (elements.isEmpty()) return new ArrayList<>();

        Class<?> elementRawType = getRawClass(elementGenericType);

        // isKey records: list becomes a YAML map keyed by the @Options(isKey=true) field value
        if (elementRawType.isRecord()) {
            RecordComponent keyComp = fieldMapper.findKeyComponent(elementRawType.getRecordComponents());
            if (keyComp != null) {
                Map<String, Object> resultMap = new LinkedHashMap<>();
                for (Object elem : elements) {
                    Object   keyValue   = readField(keyComp, elem);
                    String   mapKey     = keyValue == null ? "null" : keyValue.toString();
                    Map<String, Object> fields = toMap(elem);
                    fields.remove(fieldMapper.convertCamelCaseToKebabCase(keyComp.getName()));
                    resultMap.put(mapKey, fields);
                }
                return resultMap;
            }
        }

        // Polymorphic list: discriminator inside each element map
        if (isPolymorphicInterface(elementRawType)) {
            Polymorphic poly = elementRawType.getAnnotation(Polymorphic.class);

            if (poly.useKey()) {
                // useKey list: becomes a YAML map — discriminator value is the key
                Map<String, Object> resultMap = new LinkedHashMap<>();
                for (Object elem : elements) {
                    resultMap.put(lookupRegisteredName(elem.getClass(), elementRawType), toMap(elem));
                }
                return resultMap;
            }

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

    private Object serializeMap(Map<?, ?> map, Type[] typeArgs) {
        Type     valueGenericType = typeArgs.length > 1 ? typeArgs[1] : Object.class;
        Class<?> valueRawType     = getRawClass(valueGenericType);

        Map<String, Object> result = new LinkedHashMap<>();

        // Polymorphic map values: discriminator inside each value
        if (isPolymorphicInterface(valueRawType)) {
            Polymorphic poly = valueRawType.getAnnotation(Polymorphic.class);
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getValue() == null) { result.put(e.getKey().toString(), null); continue; }
                if (poly.useKey()) {
                    // useKey map: the map key IS the discriminator — just write concrete fields
                    result.put(e.getKey().toString(), toMap(e.getValue()));
                } else {
                    Map<String, Object> valueMap = new LinkedHashMap<>();
                    valueMap.put(poly.key(), lookupRegisteredName(e.getValue().getClass(), valueRawType));
                    valueMap.putAll(toMap(e.getValue()));
                    result.put(e.getKey().toString(), valueMap);
                }
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
                "' in polymorphic registry of '" + polymorphicInterface.getName() + "'"
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
}

