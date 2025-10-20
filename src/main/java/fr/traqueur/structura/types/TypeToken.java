package fr.traqueur.structura.types;

import fr.traqueur.structura.exceptions.StructuraException;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * A type token that captures generic type information at compile time.
 * This class is inspired by Gson's TypeToken and allows Structura to handle
 * custom readers for generic types like {@code List<Component>} or {@code Optional<String>}.
 *
 * <p>Example usage:</p>
 * <pre>
 * // Create a TypeToken for List&lt;Component&gt;
 * TypeToken&lt;List&lt;Component&gt;&gt; token = new TypeToken&lt;List&lt;Component&gt;&gt;() {};
 *
 * // Register a reader for this specific generic type
 * CustomReaderRegistry.getInstance().register(token, str -&gt; {
 *     // Custom parsing logic for List&lt;Component&gt;
 *     return parseComponentList(str);
 * });
 * </pre>
 *
 * <p><b>Important:</b> TypeToken must be instantiated as an anonymous class to capture
 * the generic type information. The following will NOT work:</p>
 * <pre>
 * // ❌ Wrong - type information is lost
 * TypeToken&lt;List&lt;Component&gt;&gt; token = new TypeToken&lt;List&lt;Component&gt;&gt;();
 *
 * // ✅ Correct - type information is captured
 * TypeToken&lt;List&lt;Component&gt;&gt; token = new TypeToken&lt;List&lt;Component&gt;&gt;() {};
 * </pre>
 *
 * @param <T> the type to capture
 */
public abstract class TypeToken<T> {

    private final Type type;
    private final Class<? super T> rawType;

    /**
     * Constructs a new TypeToken. This constructor must be called from an anonymous subclass
     * to capture the generic type information.
     *
     * @throws StructuraException if called without an anonymous subclass or if type information cannot be captured
     */
    @SuppressWarnings("unchecked")
    protected TypeToken() {
        Type superclass = getClass().getGenericSuperclass();

        if (!(superclass instanceof ParameterizedType parameterized)) {
            throw new StructuraException(
                    "TypeToken must be instantiated as an anonymous class with a type parameter. " +
                            "Example: new TypeToken<List<String>>() {}"
            );
        }

        this.type = parameterized.getActualTypeArguments()[0];
        this.rawType = (Class<? super T>) getRawType(this.type);
    }

    /**
     * Private constructor for creating TypeToken instances from a Type.
     * Used by the static factory method.
     */
    @SuppressWarnings("unchecked")
    private TypeToken(Type type) {
        this.type = type;
        this.rawType = (Class<? super T>) getRawType(type);
    }

    /**
     * Creates a TypeToken from a Type object.
     * This is useful when you already have a Type from reflection.
     *
     * @param type the type to wrap
     * @param <T> the generic type parameter
     * @return a TypeToken representing the given type
     */
    public static <T> TypeToken<T> of(Type type) {
        return new TypeToken<>(type) {};
    }

    /**
     * Creates a TypeToken from a Class object.
     * This is a convenience method for non-generic types.
     *
     * @param clazz the class to wrap
     * @param <T> the type parameter
     * @return a TypeToken representing the given class
     */
    public static <T> TypeToken<T> of(Class<T> clazz) {
        return new TypeToken<>(clazz) {};
    }

    /**
     * Gets the complete type information, including generic parameters.
     *
     * @return the Type object representing this TypeToken
     */
    public Type getType() {
        return type;
    }

    /**
     * Gets the raw (erased) class type.
     * For example, {@code List<String>} returns {@code List.class}.
     *
     * @return the raw class type
     */
    public Class<? super T> getRawType() {
        return rawType;
    }

    /**
     * Extracts the raw class from a Type.
     */
    private static Class<?> getRawType(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType parameterizedType) {
            Type rawType = parameterizedType.getRawType();
            if (!(rawType instanceof Class<?>)) {
                throw new StructuraException("Expected a Class, but got: " + rawType);
            }
            return (Class<?>) rawType;
        } else {
            throw new StructuraException("Unsupported type: " + type);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TypeToken<?> typeToken)) return false;
        return Objects.equals(type, typeToken.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }

    @Override
    public String toString() {
        return "TypeToken{" + type + "}";
    }
}
