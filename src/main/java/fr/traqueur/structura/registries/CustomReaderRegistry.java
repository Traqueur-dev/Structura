package fr.traqueur.structura.registries;

import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.readers.ContextualReader;
import fr.traqueur.structura.readers.ReadContext;
import fr.traqueur.structura.readers.Reader;
import fr.traqueur.structura.types.TypeToken;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

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
    private final ConcurrentMap<TypeToken<?>, ContextualReader<?>> contextualReaders;
    private final ConcurrentMap<Class<?>, ContextualReader<?>> genericReaders;

    /**
     * Private constructor to enforce singleton pattern.
     */
    private CustomReaderRegistry() {
        this.readers = new ConcurrentHashMap<>();
        this.contextualReaders = new ConcurrentHashMap<>();
        this.genericReaders = new ConcurrentHashMap<>();
    }

    /**
     * Validates that an object is not null and throws a descriptive exception if it is.
     *
     * @param obj the object to validate
     * @param paramName the name of the parameter (for error message)
     * @param operation the operation being performed (for error message)
     * @throws StructuraException if the object is null
     */
    private void validateNonNull(Object obj, String paramName, String operation) {
        if (obj == null) {
            throw new StructuraException(
                String.format("Cannot %s for null %s", operation, paramName)
            );
        }
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
        validateNonNull(targetClass, "class", "register reader");
        if (reader == null) {
            throw new StructuraException("Cannot register null reader for class " + targetClass.getName());
        }

        // Convert Class to TypeToken for unified internal handling
        TypeToken<T> token = TypeToken.of(targetClass);
        if (readers.containsKey(token) || contextualReaders.containsKey(token)) {
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
        validateNonNull(typeToken, "TypeToken", "register reader");
        if (reader == null) {
            throw new StructuraException("Cannot register null reader for type " + typeToken.getType());
        }

        if (readers.containsKey(typeToken) || contextualReaders.containsKey(typeToken)) {
            throw new StructuraException("A reader is already registered for type " + typeToken.getType());
        }
        readers.put(typeToken, reader);
    }

    /**
     * Registers a reader that sees the value as YAML typed it ({@code amount: 100} as well as
     * {@code amount: "100"}), the full type requested and the path of the value.
     *
     * @param targetClass the class of the target type
     * @param reader the reader
     * @param <T> the type to convert to
     * @throws StructuraException if targetClass or reader is null, or if a reader is already registered
     * @see ContextualReader
     */
    public <T> void registerContextual(Class<T> targetClass, ContextualReader<T> reader) {
        validateNonNull(targetClass, "class", "register reader");
        registerContextual(TypeToken.of(targetClass), reader);
    }

    /**
     * Registers a reader that sees the value as YAML typed it, for an exact generic type.
     *
     * @param typeToken the TypeToken representing the type, such as {@code new TypeToken<List<Team>>() {}}
     * @param reader the reader
     * @param <T> the type to convert to
     * @throws StructuraException if typeToken or reader is null, or if a reader is already registered
     * @see ContextualReader
     */
    public <T> void registerContextual(TypeToken<T> typeToken, ContextualReader<T> reader) {
        validateNonNull(typeToken, "TypeToken", "register reader");
        if (reader == null) {
            throw new StructuraException("Cannot register null reader for type " + typeToken.getType());
        }
        if (readers.containsKey(typeToken) || contextualReaders.containsKey(typeToken)) {
            throw new StructuraException("A reader is already registered for type " + typeToken.getType());
        }
        contextualReaders.put(typeToken, reader);
    }

    /**
     * Registers one reader for every parameterization of a generic type: a reader registered for
     * {@code Resolvable.class} reads {@code Resolvable<Integer>}, {@code Resolvable<Team>}… and finds
     * the type requested in {@link ReadContext#target()}. A reader registered for an exact type
     * ({@link #register(TypeToken, Reader)}) wins over the generic one.
     *
     * <pre>
     * registry.registerGeneric(Resolvable.class, ctx -&gt; Resolvable.of(ctx.convert(ctx.typeArgument(0))));
     * </pre>
     *
     * @param rawType the generic type, such as {@code Resolvable.class}
     * @param reader the reader, given the full requested type
     * @throws StructuraException if rawType or reader is null, or if a generic reader is already registered
     */
    public void registerGeneric(Class<?> rawType, ContextualReader<?> reader) {
        validateNonNull(rawType, "class", "register generic reader");
        if (reader == null) {
            throw new StructuraException("Cannot register null reader for class " + rawType.getName());
        }
        if (genericReaders.putIfAbsent(rawType, reader) != null) {
            throw new StructuraException("A generic reader is already registered for class " + rawType.getName());
        }
    }

    /**
     * Unregisters the generic reader of a type.
     *
     * @param rawType the generic type
     * @return true if a reader was removed
     */
    public boolean unregisterGeneric(Class<?> rawType) {
        validateNonNull(rawType, "class", "unregister generic reader");
        return genericReaders.remove(rawType) != null;
    }

    /**
     * Unregisters the custom reader for a specific target type.
     *
     * @param targetClass the class to unregister
     * @return true if a reader was removed, false if no reader was registered
     * @throws StructuraException if targetClass is null
     */
    public boolean unregister(Class<?> targetClass) {
        validateNonNull(targetClass, "class", "unregister reader");
        TypeToken<?> token = TypeToken.of(targetClass);
        boolean removed = readers.remove(token) != null;
        return contextualReaders.remove(token) != null || removed;
    }

    /**
     * Unregisters the custom reader for a specific generic type.
     *
     * @param typeToken the TypeToken to unregister
     * @return true if a reader was removed, false if no reader was registered
     * @throws StructuraException if typeToken is null
     */
    public boolean unregister(TypeToken<?> typeToken) {
        validateNonNull(typeToken, "TypeToken", "unregister reader");
        boolean removed = readers.remove(typeToken) != null;
        return contextualReaders.remove(typeToken) != null || removed;
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
        TypeToken<?> token = TypeToken.of(targetClass);
        return readers.containsKey(token) || contextualReaders.containsKey(token) || genericReaders.containsKey(targetClass);
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
        return readers.containsKey(typeToken) || contextualReaders.containsKey(typeToken)
                || genericReaders.containsKey(typeToken.getRawType());
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
            String reason = e.getMessage() != null ? ": " + e.getMessage() : "";
            throw new StructuraException(StructuraException.Kind.READER,
                    "Failed to convert value '" + value + "' to type " + genericType +
                            " using custom reader" + reason, null, e
            );
        }
    }

    /**
     * Reads a value of the document with the registered readers, as the conversion does.
     *
     * <p>Unlike {@link #convert(Object, Type, Class)}, which only hands strings to a reader, a value
     * reaches a reader as YAML typed it: a {@link ContextualReader} receives any value, a {@link Reader}
     * receives the text of a scalar ({@code amount: 100} as {@code "100"}) unless the value already has
     * the requested type. Lookup order: a reader for the exact type, then for its raw class, then the
     * generic reader of its raw class.</p>
     *
     * @param value the value as read from the document
     * @param genericType the full type requested
     * @param targetClass the raw type requested
     * @param path the path of the value in the document
     * @param converter converts the value to another type with Structura's rules, for {@link ReadContext#convert}
     * @return the value read, or empty if no reader applies
     * @throws StructuraException of kind READER if the reader fails
     */
    public Optional<Object> read(Object value, Type genericType, Class<?> targetClass, String path,
                                 Function<Type, Object> converter) {
        if (value == null || targetClass == null) {
            return Optional.empty();
        }
        Type requested = genericType != null ? genericType : targetClass;
        TypeToken<?> token = TypeToken.of(requested);

        Optional<Object> result = readWith(token, value, requested, targetClass, path, converter);
        if (result.isEmpty() && !requested.equals(targetClass)) {
            result = readWith(TypeToken.of(targetClass), value, requested, targetClass, path, converter);
        }
        if (result.isEmpty()) {
            ContextualReader<?> generic = genericReaders.get(targetClass);
            if (generic != null) {
                result = Optional.of(invoke(generic, value, requested, path, converter));
            }
        }
        return result;
    }

    private Optional<Object> readWith(TypeToken<?> token, Object value, Type requested, Class<?> targetClass,
                                      String path, Function<Type, Object> converter) {
        ContextualReader<?> contextual = contextualReaders.get(token);
        if (contextual != null) {
            return Optional.of(invoke(contextual, value, requested, path, converter));
        }
        Reader<?> reader = readers.get(token);
        if (reader == null || !isScalar(value) || (!(value instanceof String) && wrap(targetClass).isInstance(value))) {
            return Optional.empty();
        }
        String text = String.valueOf(value);
        return Optional.of(invoke(context -> reader.read(text), value, requested, path, converter));
    }

    private Object invoke(ContextualReader<?> reader, Object value, Type requested, String path,
                          Function<Type, Object> converter) {
        try {
            return reader.read(new Context(value, requested, path, converter));
        } catch (Exception e) {
            String reason = e instanceof StructuraException structuraException
                    ? structuraException.detail()
                    : e.getMessage();
            throw new StructuraException(StructuraException.Kind.READER,
                    "Failed to convert value '" + value + "' to type " + requested.getTypeName() +
                            " using custom reader" + (reason != null ? ": " + reason : ""), path, e);
        }
    }

    private static boolean isScalar(Object value) {
        return !(value instanceof Map<?, ?>) && !(value instanceof Collection<?>);
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private record Context(Object raw, Type type, String path, Function<Type, Object> converter)
            implements ReadContext {

        @Override
        public String text() {
            return String.valueOf(raw);
        }

        @Override
        public TypeToken<?> target() {
            return TypeToken.of(type);
        }

        @Override
        public Object convert(Type type) {
            return converter.apply(type);
        }
    }

    /**
     * Removes all registered readers.
     * This is primarily useful for testing.
     */
    public void clear() {
        readers.clear();
        contextualReaders.clear();
        genericReaders.clear();
    }

    /**
     * Returns the total number of registered readers.
     *
     * @return the count of registered readers
     */
    public int size() {
        return readers.size() + contextualReaders.size() + genericReaders.size();
    }
}