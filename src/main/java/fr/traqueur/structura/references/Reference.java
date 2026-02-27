package fr.traqueur.structura.references;

import java.util.function.Supplier;

/**
 * A lazy reference to an externally managed object, resolved by string key.
 *
 * <p>Use {@link ReferenceRegistry} to register providers for each type,
 * then declare {@code Reference<T>} fields in your {@code Loadable} records.
 * Structura will parse the YAML string value and wrap it in a Reference automatically.</p>
 *
 * <p>Resolution is lazy: {@link #element()} resolves the object at call time
 * from the live provider collection.</p>
 *
 * @param <T> the type of the referenced object
 */
public final class Reference<T> {

    private final String key;
    private final Supplier<T> resolver;

    private Reference(String key, Supplier<T> resolver) {
        this.key = key;
        this.resolver = resolver;
    }

    /**
     * Resolves and returns the referenced object.
     *
     * @return the referenced object
     * @throws fr.traqueur.structura.exceptions.StructuraException if no element matches the key
     */
    public T element() {
        return resolver.get();
    }

    /**
     * Returns the string key used to resolve this reference.
     *
     * @return the key
     */
    public String key() {
        return key;
    }

    static <T> Reference<T> of(String key, Supplier<T> resolver) {
        return new Reference<>(key, resolver);
    }
}