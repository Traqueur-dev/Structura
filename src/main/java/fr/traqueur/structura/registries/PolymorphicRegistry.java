package fr.traqueur.structura.registries;

import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.exceptions.StructuraException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * PolymorphicRegistry is a registry for polymorphic types that extends Loadable.
 * It allows for the registration and retrieval of classes based on their names.
 * @param <T> the type of Loadable that this registry manages
 */
public class PolymorphicRegistry<T extends Loadable> {

    /**
     * A static map that holds registries for different Loadable classes.
     * This allows for the retrieval of registries based on the class type.
     */
    private static final Map<Class<? extends Loadable>, PolymorphicRegistry<?>> REGISTRIES = new HashMap<>();

    /**
     * Retrieves the PolymorphicRegistry for a given Loadable class.
     * If no registry is found for the specified class, an exception is thrown.
     *
     * @param clazz the class of Loadable for which to retrieve the registry
     * @param <T>   the type of Loadable that this registry manages
     * @return the PolymorphicRegistry for the specified class
     * @throws StructuraException if no registry is found for the specified class
     */
    public static <T extends Loadable> PolymorphicRegistry<T> get(Class<T> clazz) {
        if(clazz == null) {
            throw new StructuraException("Cannot retrieve a registry for a null class.");
        }

        PolymorphicRegistry<?> registry = REGISTRIES.get(clazz);
        if (registry == null) {
            throw new StructuraException("No polymorphic registry registered for " + clazz.getSimpleName());
        }
        //noinspection unchecked
        return (PolymorphicRegistry<T>) registry;
    }

    /**
     * Creates a PolymorphicRegistry for a given Loadable class and allows for configuration via a Consumer.
     * This method is useful for setting up the registry with initial values or behaviors.
     *
     * @param clazz       the class of Loadable for which to create the registry
     * @param configurator a Consumer that takes the created PolymorphicRegistry and allows for configuration
     * @param <T>         the type of Loadable that this registry manages
     */
    public static <T extends Loadable> void create(
            Class<T> clazz,
            Consumer<PolymorphicRegistry<T>> configurator) {

        if (clazz == null) {
            throw new StructuraException("Cannot create registry for null class.");
        }

        if (configurator == null) {
            throw new StructuraException("Cannot create registry for null configurator.");
        }

        if (REGISTRIES.containsKey(clazz)) {
            throw new StructuraException("Registry already exists for " + clazz.getSimpleName() +
                    ". Use get() to retrieve it or unregister() first.");
        }

        PolymorphicRegistry<T> registry = new PolymorphicRegistry<>();
        configurator.accept(registry);
        REGISTRIES.put(clazz, registry);
    }

    /**
     * Private constructor to prevent direct instantiation.
     * This class is intended to be used as a singleton for each Loadable type.
     * Instead, use the static methods to create or retrieve a registry.
     */
    private PolymorphicRegistry() {
        // Private constructor to prevent direct instantiation
    }

    /**
     * A map that holds the registered classes for this registry.
     * The key is the name of the class (in lowercase), and the value is the class itself.
     */
    private final Map<String, Class<? extends T>> loaders = new HashMap<>();

    /**
     * Registers a class in the registry using its simple name in lowercase.
     * This allows for easy retrieval of the class by its name.
     * @param clazz the class to register
     */
    public void register(Class<? extends T> clazz) {
        this.register(clazz.getSimpleName().toLowerCase(), clazz);
    }

    /**
     * Registers a class in the registry with a specific name.
     * This allows for retrieval of the class by a custom
     * name, which can be useful for polymorphic behavior.
     * @param name the name to register the class under
     * @param clazz the class to register
     * throws StructuraException if the name is already registered
     */
    public void register(String name, Class<? extends T> clazz) {
        if(loaders.containsKey(name)) {
            throw new StructuraException("A loader with the name '" + name + "' is already registered.");
        }
        if(name == null || name.trim().isEmpty()) {
            throw  new StructuraException("Cannot register a class with a null or empty name.");
        }
        if(clazz == null) {
            throw new StructuraException("Cannot register a null class for name '" + name + "'.");
        }

        loaders.put(name.toLowerCase(), clazz);
    }

    /**
     * Retrieves a class from the registry by its name.
     * The name is converted to lowercase to ensure case-insensitive retrieval.
     *
     * @param name the name of the class to retrieve
     * @return an Optional containing the class if found, or an empty Optional if not found
     */
    public Optional<Class<? extends T>> get(String name) {
        if(name == null || name.trim().isEmpty()) {
            return Optional.empty();
        }
        name = name.toLowerCase();
        return Optional.ofNullable(loaders.get(name));
    }

    /**
     * Retrieves all available names in the registry.
     * This method returns a set of all names under which classes are registered.
     * @return a Set of names of the registered classes
     */
    public Set<String> availableNames() {
        return loaders.keySet();
    }

}
