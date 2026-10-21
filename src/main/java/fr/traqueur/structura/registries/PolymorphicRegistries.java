package fr.traqueur.structura.registries;

import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.exceptions.StructuraException;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * A set of {@link PolymorphicRegistry polymorphic registries}, one per polymorphic interface.
 *
 * <p>The static {@link PolymorphicRegistry#create} and {@link PolymorphicRegistry#get} work on the
 * {@link #global() global} set. A processor can be given its own set, so that two plugins of the same
 * server, or two tests running in parallel, do not share their sub-types:</p>
 * <pre>
 * PolymorphicRegistries types = PolymorphicRegistries.create();
 * types.register(Reward.class, registry -&gt; registry.register("money", MoneyReward.class));
 *
 * StructuraProcessor processor = Structura.builder().polymorphics(types).build();
 * </pre>
 * <p>A processor looks an interface up in its own set first, then in the global one.</p>
 */
public final class PolymorphicRegistries {

    private static final PolymorphicRegistries GLOBAL = new PolymorphicRegistries(PolymorphicRegistry.globalRegistries());

    private final Map<Class<? extends Loadable>, PolymorphicRegistry<?>> registries;

    private PolymorphicRegistries(Map<Class<? extends Loadable>, PolymorphicRegistry<?>> registries) {
        this.registries = registries;
    }

    /**
     * @return a new, empty set of registries
     */
    public static PolymorphicRegistries create() {
        return new PolymorphicRegistries(new ConcurrentHashMap<>());
    }

    /**
     * @return the global set, used by {@link PolymorphicRegistry#create} and {@link PolymorphicRegistry#get}
     */
    public static PolymorphicRegistries global() {
        return GLOBAL;
    }

    /**
     * Creates the registry of a polymorphic interface in this set.
     *
     * @param clazz        the polymorphic interface
     * @param configurator registers the sub-types
     * @param <T>          the polymorphic interface type
     * @throws StructuraException if a registry already exists for the interface in this set
     */
    public <T extends Loadable> void register(Class<T> clazz, Consumer<PolymorphicRegistry<T>> configurator) {
        if (clazz == null) {
            throw new StructuraException("Cannot create registry for null class.");
        }
        if (configurator == null) {
            throw new StructuraException("Cannot create registry for null configurator.");
        }
        if (registries.containsKey(clazz)) {
            throw alreadyExists(clazz);
        }
        PolymorphicRegistry<T> registry = new PolymorphicRegistry<>();
        configurator.accept(registry);
        if (registries.putIfAbsent(clazz, registry) != null) {
            throw alreadyExists(clazz);
        }
    }

    private static StructuraException alreadyExists(Class<?> clazz) {
        return new StructuraException("Registry already exists for " + clazz.getSimpleName() +
                ". Use get() to retrieve it or unregister() first.");
    }

    /**
     * @param clazz the polymorphic interface
     * @param <T>   the polymorphic interface type
     * @return its registry in this set, if any
     */
    public <T extends Loadable> Optional<PolymorphicRegistry<T>> find(Class<T> clazz) {
        if (clazz == null) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        PolymorphicRegistry<T> registry = (PolymorphicRegistry<T>) registries.get(clazz);
        return Optional.ofNullable(registry);
    }

    /**
     * @param clazz the polymorphic interface
     * @param <T>   the polymorphic interface type
     * @return its registry in this set
     * @throws StructuraException if there is none
     */
    public <T extends Loadable> PolymorphicRegistry<T> get(Class<T> clazz) {
        if (clazz == null) {
            throw new StructuraException("Cannot retrieve a registry for a null class.");
        }
        return find(clazz).orElseThrow(() ->
                new StructuraException("No polymorphic registry registered for " + clazz.getSimpleName()));
    }

    /**
     * Removes the registry of a polymorphic interface from this set.
     *
     * @param clazz the polymorphic interface
     * @return true if a registry was removed
     */
    public boolean unregister(Class<? extends Loadable> clazz) {
        return clazz != null && registries.remove(clazz) != null;
    }
}
