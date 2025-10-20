package fr.traqueur.structura.registries;

import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.readers.Reader;
import fr.traqueur.structura.types.TypeToken;

import java.lang.reflect.Type;
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
 * <p>Example usage with non-generic types (Adventure API):</p>
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
 * <p>Example usage with generic types:</p>
 * <pre>
 * // Register a reader for a specific generic type
 * CustomReaderRegistry.getInstance().register(
 *     new TypeToken&lt;List&lt;Component&gt;&gt;() {},
 *     str -&gt; parseComponentList(str)
 * );
 *
 * // This reader only applies to List&lt;Component&gt;, not List&lt;String&gt;
 * </pre>
 *
 * @see Reader
 * @see TypeToken
 */
public class CustomReaderRegistry {

    private static final CustomReaderRegistry INSTANCE = new CustomReaderRegistry();

    // Single map using TypeToken for unified type handling
    private final ConcurrentMap<TypeToken<?>, Reader<?>> readers;

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
     * <p>This method internally converts the Class to a TypeToken for unified type handling.</p>
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
        // Convert Class to TypeToken for unified internal handling
        TypeToken<T> token = TypeToken.of(targetClass);
        if (readers.containsKey(token)) {
            throw new StructuraException("A reader is already registered for class " + targetClass.getName());
        }
        readers.put(token, reader);
    }

    /**
     * Registers a custom reader for a specific generic type using a TypeToken.
     * This allows registering readers for generic types like {@code List<Component>} or {@code Optional<String>}.
     *
     * <p>The reader will only be used for the exact generic type specified in the TypeToken.
     * For example, a reader registered for {@code List<Component>} will not be used for {@code List<String>}.</p>
     *
     * <p>Example:</p>
     * <pre>
     * CustomReaderRegistry.getInstance().register(
     *     new TypeToken&lt;List&lt;Component&gt;&gt;() {},
     *     str -&gt; parseComponentList(str)
     * );
     * </pre>
     *
     * @param typeToken the TypeToken representing the generic type
     * @param reader the reader to convert strings to the target type
     * @param <T> the type to convert to
     * @throws StructuraException if typeToken or reader is null, or if a reader is already registered
     */
    public <T> void register(TypeToken<T> typeToken, Reader<T> reader) {
        if (typeToken == null) {
            throw new StructuraException("Cannot register reader for null TypeToken");
        }
        if (reader == null) {
            throw new StructuraException("Cannot register null reader for type " + typeToken.getType());
        }
        if (readers.containsKey(typeToken)) {
            throw new StructuraException("A reader is already registered for type " + typeToken.getType());
        }
        readers.put(typeToken, reader);
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
        return readers.remove(TypeToken.of(targetClass)) != null;
    }

    /**
     * Unregisters the custom reader for a specific generic type.
     *
     * @param typeToken the TypeToken to unregister
     * @return true if a reader was removed, false if no reader was registered
     * @throws StructuraException if typeToken is null
     */
    public boolean unregister(TypeToken<?> typeToken) {
        if (typeToken == null) {
            throw new StructuraException("Cannot unregister reader for null TypeToken");
        }
        return readers.remove(typeToken) != null;
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
        return readers.containsKey(TypeToken.of(targetClass));
    }

    /**
     * Checks if a custom reader is registered for the given generic type.
     *
     * @param typeToken the TypeToken to check
     * @return true if a reader is registered, false otherwise
     */
    public boolean hasReader(TypeToken<?> typeToken) {
        if (typeToken == null) {
            return false;
        }
        return readers.containsKey(typeToken);
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
        return convert(value, targetClass, targetClass);
    }

    /**
     * Attempts to convert a value to the target type using a registered reader.
     * This method supports both generic and non-generic types through TypeToken-based lookup.
     *
     * <p>Lookup strategy:</p>
     * <ol>
     *   <li>Try to find a reader for the exact generic type (e.g., {@code List<Component>})</li>
     *   <li>If not found, fallback to raw class type (e.g., {@code List.class})</li>
     * </ol>
     *
     * <p>This method only works if:</p>
     * <ul>
     *   <li>The value is a String</li>
     *   <li>A reader is registered for the target type or its raw class</li>
     * </ul>
     *
     * @param value the value to convert (must be a String)
     * @param genericType the generic type (may include type parameters like {@code List<Component>})
     * @param targetClass the raw target class
     * @param <T> the type to convert to
     * @return an Optional containing the converted value, or empty if no conversion is possible
     * @throws StructuraException if the reader throws an exception during conversion
     */
    public <T> Optional<T> convert(Object value, Type genericType, Class<T> targetClass) {
        if (!(value instanceof String stringValue)) {
            return Optional.empty();
        }
        if (targetClass == null) {
            return Optional.empty();
        }

        // Create TypeToken from the generic type for unified lookup
        TypeToken<?> token = TypeToken.of(genericType != null ? genericType : targetClass);
        Reader<?> reader = readers.get(token);

        // Fallback to raw class if no reader found for the generic type
        if (reader == null && !token.getType().equals(targetClass)) {
            token = TypeToken.of(targetClass);
            reader = readers.get(token);
        }

        if (reader == null) {
            return Optional.empty();
        }

        try {
            Object result = reader.read(stringValue);
            return Optional.of(targetClass.cast(result));
        } catch (Exception e) {
            throw new StructuraException(
                    "Failed to convert value '" + value + "' to type " + genericType +
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
     * Returns the total number of registered readers.
     *
     * @return the count of registered readers
     */
    public int size() {
        return readers.size();
    }
}