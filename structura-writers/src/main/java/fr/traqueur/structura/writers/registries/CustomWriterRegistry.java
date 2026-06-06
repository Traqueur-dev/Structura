package fr.traqueur.structura.writers.registries;

import fr.traqueur.structura.writers.exceptions.StructuraWriterException;
import fr.traqueur.structura.writers.writer.Writer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Singleton registry for custom {@link Writer} instances.
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
        return writers.containsKey(type);
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
        Writer<?> writer = writers.get(type);
        if (writer == null) return Optional.empty();
        try {
            return Optional.of(((Writer<Object>) writer).write(value));
        } catch (Exception e) {
            throw new StructuraWriterException("Writer failed for " + type.getName() + ": " + e.getMessage(), e);
        }
    }
}
