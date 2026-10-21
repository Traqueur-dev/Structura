package fr.traqueur.structura.writers.registries;

import fr.traqueur.structura.types.TypeToken;
import fr.traqueur.structura.writers.exceptions.StructuraWriterException;
import fr.traqueur.structura.writers.writer.ContextualWriter;
import fr.traqueur.structura.writers.writer.WriteContext;
import fr.traqueur.structura.writers.writer.Writer;

import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Singleton registry for custom {@link Writer} instances. A writer registered for a class or an
 * interface also serves its subclasses and implementations.
 * Allows serializing types not natively supported by {@link fr.traqueur.structura.writers.serializer.LoadableSerializer}.
 *
 * <pre>{@code
 * CustomWriterRegistry.getInstance().register(
 *     Component.class,
 *     c -> MiniMessage.miniMessage().serialize(c)
 * );
 * }</pre>
 */
public class CustomWriterRegistry {

    private static final CustomWriterRegistry INSTANCE = new CustomWriterRegistry();

    private final Map<Class<?>, ContextualWriter<?>> writers = new HashMap<>();

    private CustomWriterRegistry() {}

    public static CustomWriterRegistry getInstance() {
        return INSTANCE;
    }

    public <T> void register(Class<T> type, Writer<T> writer) {
        writers.put(type, (ContextualWriter<T>) (value, context) -> writer.write(value));
    }

    /**
     * Registers a writer that knows the type declared for the value and its path, and can hand the
     * values it holds back to Structura ({@link WriteContext#write}). Like a plain writer, it also
     * serves the subclasses and implementations of {@code type}; registering a raw generic type such
     * as {@code Resolvable.class} serves every {@code Resolvable<T>}.
     *
     * @param type the type to serialize
     * @param writer the writer
     * @param <T> the type to serialize
     */
    public <T> void registerContextual(Class<T> type, ContextualWriter<T> writer) {
        writers.put(type, writer);
    }

    public boolean unregister(Class<?> type) {
        return writers.remove(type) != null;
    }

    public boolean hasWriter(Class<?> type) {
        return find(type) != null;
    }

    /**
     * The writer of a type: the one registered for the type itself, else for its closest superclass,
     * else for the closest interface it implements. A writer registered for an interface such as
     * Adventure's {@code Component} thus serves its implementations, whose runtime class is internal.
     *
     * @param type the runtime class of the value
     * @return the writer, or null if none applies
     */
    private ContextualWriter<?> find(Class<?> type) {
        if (type == null) return null;
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            ContextualWriter<?> writer = writers.get(current);
            if (writer != null) return writer;
        }
        // Interfaces, breadth-first from the closest ones
        Deque<Class<?>> queue = new ArrayDeque<>();
        Set<Class<?>> seen = new HashSet<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            queue.addAll(List.of(current.getInterfaces()));
        }
        while (!queue.isEmpty()) {
            Class<?> candidate = queue.poll();
            if (!seen.add(candidate)) continue;
            ContextualWriter<?> writer = writers.get(candidate);
            if (writer != null) return writer;
            queue.addAll(List.of(candidate.getInterfaces()));
        }
        return null;
    }

    public void clear() {
        writers.clear();
    }

    @SuppressWarnings("unchecked")
    /**
     * Attempts to serialize {@code value} using the registered {@link Writer} for {@code type}.
     * Returns {@link Optional#empty()} if no writer is registered for that type.
     */
    public Optional<Object> write(Object value, Class<?> type) {
        return write(value, type, "", (inner, innerType) -> inner);
    }

    /**
     * Serializes {@code value} with the writer of its runtime class, as the serializer does.
     *
     * @param value the value
     * @param declaredType the type declared for it, or null
     * @param path the path of the value in the document
     * @param serializer Structura's serialization, for {@link WriteContext#write}
     * @return the YAML form, or empty if no writer applies
     * @throws StructuraWriterException if the writer fails, naming the path
     */
    @SuppressWarnings("unchecked")
    public Optional<Object> write(Object value, Type declaredType, String path,
                                  BiFunction<Object, Type, Object> serializer) {
        if (value == null) return Optional.empty();
        ContextualWriter<?> writer = find(value.getClass());
        if (writer == null) return Optional.empty();
        Type target = declaredType == null || declaredType == Object.class ? value.getClass() : declaredType;
        WriteContext context = new Context(target, path, serializer);
        try {
            return Optional.of(((ContextualWriter<Object>) writer).write(value, context));
        } catch (Exception e) {
            String at = path == null || path.isEmpty() ? "" : " at " + path;
            throw new StructuraWriterException("Writer failed for " + value.getClass().getName() + at + ": "
                    + e.getMessage(), e);
        }
    }

    private record Context(Type type, String path, BiFunction<Object, Type, Object> serializer)
            implements WriteContext {

        @Override
        public TypeToken<?> target() {
            return TypeToken.of(type);
        }

        @Override
        public Object write(Object value) {
            return serializer.apply(value, value == null ? Object.class : value.getClass());
        }

        @Override
        public Object write(Object value, Type type) {
            return serializer.apply(value, type);
        }
    }
}
