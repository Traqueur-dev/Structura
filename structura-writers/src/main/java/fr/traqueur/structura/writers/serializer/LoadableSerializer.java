package fr.traqueur.structura.writers.serializer;

import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.mapping.FieldMapper;
import fr.traqueur.structura.references.Reference;
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
 * camelCase field names are converted to kebab-case automatically.
 * Custom types can be handled by registering a {@link fr.traqueur.structura.writers.writer.Writer}
 * in {@link CustomWriterRegistry}.
 */
public class LoadableSerializer {

    private static final DateTimeFormatter DATE_FORMATTER      = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final FieldMapper fieldMapper;
    private final Yaml yaml;

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

    private Map<String, Object> toMap(Object obj) {
        Class<?> clazz = obj.getClass();
        if (!clazz.isRecord()) {
            throw new StructuraWriterException("Cannot serialize non-record type: " + clazz.getName());
        }

        RecordComponent[] components = clazz.getRecordComponents();
        Constructor<?>    constructor = clazz.getDeclaredConstructors()[0];
        Parameter[]       parameters = constructor.getParameters();

        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            Parameter       parameter = parameters[i];
            String          key       = fieldMapper.getEffectiveFieldName(parameter, component.getName());
            Object          value     = read(component, obj);
            result.put(key, serializeValue(value, component.getGenericType()));
        }
        return result;
    }

    private Object serializeValue(Object value, Type genericType) {
        if (value == null) return null;

        if (value instanceof Reference<?> ref) return ref.key();

        Optional<Object> custom = CustomWriterRegistry.getInstance().write(value, value.getClass());
        if (custom.isPresent()) return custom.get();

        if (value.getClass().isRecord()) return toMap(value);

        if (genericType instanceof ParameterizedType paramType) {
            if (value instanceof List<?> list) {
                return list.stream().map(e -> serializeValue(e, Object.class)).collect(Collectors.toList());
            }
            if (value instanceof Set<?> set) {
                return set.stream().map(e -> serializeValue(e, Object.class)).collect(Collectors.toList());
            }
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> r = new LinkedHashMap<>();
                map.forEach((k, v) -> r.put(k.toString(), serializeValue(v, Object.class)));
                return r;
            }
        }

        if (value instanceof Enum<?> e)       return fieldMapper.convertSnakeCaseToKebabCase(e.name());
        if (value instanceof LocalDate d)      return d.format(DATE_FORMATTER);
        if (value instanceof LocalDateTime dt) return dt.format(DATE_TIME_FORMATTER);

        return value;
    }

    private Object read(RecordComponent component, Object obj) {
        try {
            return component.getAccessor().invoke(obj);
        } catch (ReflectiveOperationException e) {
            throw new StructuraWriterException("Cannot read field: " + component.getName(), e);
        }
    }
}
