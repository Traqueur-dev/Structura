package fr.traqueur.structura.writers.factory;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.registries.DefaultValueRegistry;
import fr.traqueur.structura.writers.exceptions.StructuraWriterException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.*;

/**
 * Creates a default instance of a {@link Loadable} record by resolving
 * values from {@code @Default*} annotations and Java zero-values.
 */
public class DefaultInstanceFactory {

    /**
     * Instantiates {@code configClass} with default values.
     * Resolution order: {@code @Default*} annotations → nested records → empty collections → zero-values.
     *
     * @throws StructuraWriterException if the class is not a record or instantiation fails
     */
    public <T extends Loadable> T createDefault(Class<T> configClass) {
        if (configClass == null || !configClass.isRecord()) {
            throw new StructuraWriterException(
                    (configClass == null ? "null" : configClass.getName()) + " is not a record type.");
        }

        RecordComponent[] components = configClass.getRecordComponents();
        Constructor<?> constructor = configClass.getDeclaredConstructors()[0];
        Parameter[] parameters = constructor.getParameters();
        Object[] args = new Object[components.length];

        for (int i = 0; i < components.length; i++) {
            args[i] = resolveDefault(components[i], parameters[i]);
        }

        try {
            constructor.setAccessible(true);
            return configClass.cast(constructor.newInstance(args));
        } catch (ReflectiveOperationException e) {
            throw new StructuraWriterException("Failed to create default instance of " + configClass.getName(), e);
        }
    }

    private Object resolveDefault(RecordComponent component, Parameter parameter) {
        Class<?> type = component.getType();
        List<Annotation> annotations = Arrays.asList(parameter.getAnnotations());

        Object registryDefault = DefaultValueRegistry.getInstance().getDefaultValue(type, annotations);
        if (registryDefault != null) return registryDefault;

        if (type.isRecord() && Loadable.class.isAssignableFrom(type)) {
            @SuppressWarnings("unchecked")
            Class<? extends Loadable> nested = (Class<? extends Loadable>) type;
            return createDefault(nested);
        }

        if (List.class.isAssignableFrom(type)) return new ArrayList<>();
        if (Set.class.isAssignableFrom(type))  return new HashSet<>();
        if (Map.class.isAssignableFrom(type))  return new LinkedHashMap<>();

        if (type == int.class     || type == Integer.class)   return 0;
        if (type == long.class    || type == Long.class)      return 0L;
        if (type == double.class  || type == Double.class)    return 0.0;
        if (type == float.class   || type == Float.class)     return 0.0f;
        if (type == boolean.class || type == Boolean.class)   return false;
        if (type == byte.class    || type == Byte.class)      return (byte) 0;
        if (type == short.class   || type == Short.class)     return (short) 0;
        if (type == char.class    || type == Character.class) return '\0';

        Options options = parameter.getAnnotation(Options.class);
        if (options != null && options.optional()) return null;

        if (type == String.class) return "";

        return null;
    }
}
