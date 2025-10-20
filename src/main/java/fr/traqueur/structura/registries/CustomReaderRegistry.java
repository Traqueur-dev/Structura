package fr.traqueur.structura.registries;

import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.readers.Reader;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registry for custom type readers that convert String values to custom types.
 * This registry enables Structura to support external libraries and custom types
 * by providing user-defined conversion logic.
 *
 * <p>This is a thread-safe singleton that manages custom readers for types
 * that require special conversion from YAML string values.</p>
 *
 * <p>Example usage with Adventure API:</p>
 * <pre>
 * // Registration (once, typically at application startup)
 * CustomReaderRegistry.getInstance().register(
 *     Component.class,
 *     str -&gt; MiniMessage.miniMessage().deserialize(str)
 * );
 *
 * // Configuration record
 * public record MessageConfig(
 *     Component welcomeMessage,
 *     Component errorMessage
 * ) implements Loadable {}
 *
 * // YAML content
 * String yaml = """
 *     welcome-message: "&lt;green&gt;Welcome!&lt;/green&gt;"
 *     error-message: "&lt;red&gt;Error!&lt;/red&gt;"
 *     """;
 *
 * // Parsing - automatic conversion via registered reader
 * MessageConfig config = Structura.parse(yaml, MessageConfig.class);
 * </pre>
 *
 * @see Reader
 */
public class CustomReaderRegistry {

    private static final CustomReaderRegistry INSTANCE = new CustomReaderRegistry();

    private final ConcurrentMap<Class<?>, Reader<?>> readers;

    /**
     * Private constructor to enforce singleton pattern.
     */
    private CustomReaderRegistry() {
        this.readers = new ConcurrentHashMap<>();
    }

    /**
     * Gets the singleton instance of CustomReaderRegistry.
     *
     * @return the singleton instance
     */
    public static CustomReaderRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a custom reader for a specific target type.
     * The reader will be used to convert YAML string values to the target type.
     *
     * @param targetClass the class of the target type
     * @param reader the reader to convert strings to the target type
     * @param <T> the type to convert to
     * @throws StructuraException if targetClass or reader is null, or if a reader is already registered
     */
    public <T> void register(Class<T> targetClass, Reader<T> reader) {
        if (targetClass == null) {
            throw new StructuraException("Cannot register reader for null class");
        }
        if (reader == null) {
            throw new StructuraException("Cannot register null reader for class " + targetClass.getName());
        }
        if (readers.containsKey(targetClass)) {
            throw new StructuraException("A reader is already registered for class " + targetClass.getName());
        }
        readers.put(targetClass, reader);
    }

    /**
     * Unregisters the custom reader for a specific target type.
     *
     * @param targetClass the class to unregister
     * @return true if a reader was removed, false if no reader was registered
     * @throws StructuraException if targetClass is null
     */
    public boolean unregister(Class<?> targetClass) {
        if (targetClass == null) {
            throw new StructuraException("Cannot unregister reader for null class");
        }
        return readers.remove(targetClass) != null;
    }

    /**
     * Checks if a custom reader is registered for the given class.
     *
     * @param targetClass the class to check
     * @return true if a reader is registered, false otherwise
     */
    public boolean hasReader(Class<?> targetClass) {
        if (targetClass == null) {
            return false;
        }
        return readers.containsKey(targetClass);
    }

    /**
     * Attempts to convert a value to the target type using a registered reader.
     * This method only works if:
     * <ul>
     *   <li>The value is a String</li>
     *   <li>A reader is registered for the target class</li>
     * </ul>
     *
     * @param value the value to convert (must be a String)
     * @param targetClass the target class
     * @param <T> the type to convert to
     * @return an Optional containing the converted value, or empty if no conversion is possible
     * @throws StructuraException if the reader throws an exception during conversion
     */
    public <T> Optional<T> convert(Object value, Class<T> targetClass) {
        if (!(value instanceof String)) {
            return Optional.empty();
        }
        if (targetClass == null) {
            return Optional.empty();
        }

        Reader<?> reader = readers.get(targetClass);
        if (reader == null) {
            return Optional.empty();
        }

        try {
            String stringValue = (String) value;
            Object result = reader.read(stringValue);
            return Optional.of(targetClass.cast(result));
        } catch (Exception e) {
            throw new StructuraException(
                    "Failed to convert value '" + value + "' to type " + targetClass.getName() +
                            " using custom reader", e
            );
        }
    }

    /**
     * Removes all registered readers.
     * This is primarily useful for testing.
     */
    public void clear() {
        readers.clear();
    }

    /**
     * Returns the number of registered readers.
     *
     * @return the count of registered readers
     */
    public int size() {
        return readers.size();
    }
}