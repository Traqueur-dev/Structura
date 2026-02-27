package fr.traqueur.structura.references;

import fr.traqueur.structura.exceptions.StructuraException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Registry for {@link Reference} providers.
 *
 * <p>Register a provider once at application startup for each referenceable type.
 * Structura will automatically resolve {@code Reference<T>} fields in config records.</p>
 *
 * <p>Example:</p>
 * <pre>
 * ReferenceRegistry.getInstance().install(
 *     Arena.class,
 *     Arena::id,
 *     () -&gt; plugin.getArenaManager().getArenas()
 * );
 * </pre>
 */
public class ReferenceRegistry {

    private static final ReferenceRegistry INSTANCE = new ReferenceRegistry();

    private final ConcurrentMap<Class<?>, RegisteredProvider<?>> providers;

    private ReferenceRegistry() {
        this.providers = new ConcurrentHashMap<>();
    }

    /**
     * Returns the singleton instance of ReferenceRegistry.
     *
     * @return the singleton instance
     */
    public static ReferenceRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Validates that an object is not null and throws a descriptive exception if it is.
     *
     * @param obj       the object to validate
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
     * Registers a provider for the given type.
     *
     * @param type         the referenceable type
     * @param keyExtractor function to extract the string key from an instance
     * @param provider     supplies the collection of available instances
     * @param <T>          the type
     * @throws StructuraException if any parameter is null, or if a provider is already registered for this type
     */
    public <T> void install(Class<T> type, Function<T, String> keyExtractor, ReferenceProvider<T> provider) {
        validateNonNull(type, "type", "install reference provider");
        validateNonNull(keyExtractor, "keyExtractor", "install reference provider");
        validateNonNull(provider, "provider", "install reference provider");

        if (providers.containsKey(type)) {
            throw new StructuraException(
                "A ReferenceProvider is already registered for type " + type.getName() +
                ". Call uninstall(" + type.getSimpleName() + ".class) first."
            );
        }
        providers.put(type, new RegisteredProvider<>(provider, keyExtractor));
    }

    /**
     * Unregisters the provider for the given type.
     *
     * @param type the type to unregister
     * @return true if a provider was removed, false if none was registered
     */
    public boolean uninstall(Class<?> type) {
        return providers.remove(type) != null;
    }

    /**
     * Checks whether a provider is registered for the given type.
     *
     * @param type the type to check
     * @return true if a provider is registered, false otherwise
     */
    public boolean hasProvider(Class<?> type) {
        return providers.containsKey(type);
    }

    /**
     * Returns the total number of registered providers.
     *
     * @return the count of registered providers
     */
    public int size() {
        return providers.size();
    }

    /**
     * Removes all registered providers.
     * This is primarily useful for testing.
     */
    public void clear() {
        providers.clear();
    }

    /**
     * Resolves a {@code Reference<T>} for the given key and type.
     * Called internally by ValueConverter.
     *
     * @param key  the string key used to look up the object
     * @param type the type of the referenced object
     * @param <T>  the type
     * @return a lazy {@link Reference} that resolves the object when {@link Reference#element()} is called
     * @throws StructuraException if no provider is registered for the type
     */
    @SuppressWarnings("unchecked")
    public <T> Reference<T> resolve(String key, Class<T> type) {
        RegisteredProvider<T> entry = (RegisteredProvider<T>) providers.get(type);
        if (entry == null) {
            throw new StructuraException(
                "No ReferenceProvider registered for type: " + type.getName() +
                ". Call ReferenceRegistry.getInstance().install(" + type.getSimpleName() + ".class, ...) first."
            );
        }
        return Reference.of(key, () ->
            entry.provider().provide().stream()
                .filter(t -> entry.keyExtractor().apply(t).equals(key))
                .findFirst()
                .orElseThrow(() -> new StructuraException(
                    "No " + type.getSimpleName() + " found for key '" + key + "'"
                ))
        );
    }

    private record RegisteredProvider<T>(ReferenceProvider<T> provider, Function<T, String> keyExtractor) {}
}