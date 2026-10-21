package fr.traqueur.structura.writers.registries;

import fr.traqueur.structura.writers.exceptions.StructuraWriterException;
import fr.traqueur.structura.writers.writer.Writer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;

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

    private final Map<Class<?>, Writer<?>> writers = new HashMap<>();

    private CustomWriterRegistry() {}

    public static CustomWriterRegistry getInstance() {
        return INSTANCE;
    }

    public <T> void register(Class<T> type, Writer<T> writer) {
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
    private Writer<?> find(Class<?> type) {
        if (type == null) return null;
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            Writer<?> writer = writers.get(current);
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
            Writer<?> writer = writers.get(candidate);
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
        Writer<?> writer = find(type);
        if (writer == null) return Optional.empty();
        try {
            return Optional.of(((Writer<Object>) writer).write(value));
        } catch (Exception e) {
            throw new StructuraWriterException("Writer failed for " + type.getName() + ": " + e.getMessage(), e);
        }
    }
}
