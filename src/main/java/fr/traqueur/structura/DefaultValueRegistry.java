package fr.traqueur.structura;

import fr.traqueur.structura.annotations.defaults.*;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * DefaultValueRegistry is a singleton class that manages default value handlers for various parameter types.
 * It allows the registration of handlers that can extract default values from annotations applied to parameters.
 * This is particularly useful for frameworks that need to provide default values based on annotations.
 */
public class DefaultValueRegistry {

    /**
     * Singleton instance of DefaultValueRegistry.
     * This is used to ensure that there is only one instance of the registry throughout the application.
     */
    private static DefaultValueRegistry INSTANCE;

    /**
     * Singleton instance of DefaultValueRegistry.
     * This ensures that there is only one instance of the registry throughout the application.
     *
     * @return The singleton instance of DefaultValueRegistry.
     */
    public static DefaultValueRegistry getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DefaultValueRegistry();
        }
        return INSTANCE;
    }

    /**
     * A registry that maps parameter types to their default value handlers.
     * Each handler is a function that takes an annotation and returns the default value for that type.
     */
    private final Map<Class<?>, Map<Class<? extends Annotation>, Function<Annotation, Object>>> handlers = new ConcurrentHashMap<>();

    /**
     * Private constructor to enforce singleton pattern.
     * Initializes the registry with default handlers for common types.
     */
    private DefaultValueRegistry() {
        registerDefaultHandlers();
    }

    /**
     * Registers a handler for a specific type and annotation.
     * This allows the registry to extract default values from annotations
     * for parameters of the specified type.
     *
     * @param targetType The type of the parameter for which to register the handler.
     * @param annotationType The annotation type that will be used to extract the default value.
     * @param valueExtractor A function that extracts the default value from the annotation.
     *                       It should take an instance of the annotation and return the default value.
     */
    public <T, A extends Annotation> void register(Class<T> targetType, Class<A> annotationType, Function<A, T> valueExtractor) {
        handlers.computeIfAbsent(targetType, k -> new ConcurrentHashMap<>())
                .put(annotationType, (Function<Annotation, Object>) valueExtractor);
    }

    /**
     * Retrieves the default value for a given parameter based on its type and annotations.
     * It checks the registered handlers for the parameter's type and applies the appropriate handler
     * for each annotation present on the parameter.
     *
     * @param type The type of the parameter for which to retrieve the default value.
     * @param annotations The list of annotations presents on the parameter.
     * @return The default value if found, otherwise null.
     */
    public Object getDefaultValue(Class<?> type, List<Annotation> annotations) {
        Map<Class<? extends Annotation>, Function<Annotation, Object>> typeHandlers = handlers.get(type);
        if (typeHandlers == null) {
            return null;
        }

        for (Annotation annotation : annotations) {
            Function<Annotation, Object> handler = typeHandlers.get(annotation.annotationType());
            if (handler != null) {
                Object value = handler.apply(annotation);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * Registers default handlers for common types and their corresponding default value annotations.
     * This method is called during the initialization of the registry.
     */
    private void registerDefaultHandlers() {
        register(boolean.class, DefaultBool.class, DefaultBool::value);
        register(Boolean.class, DefaultBool.class, DefaultBool::value);
        register(String.class, DefaultString.class, DefaultString::value);
        register(long.class, DefaultLong.class, DefaultLong::value);
        register(Long.class, DefaultLong.class, DefaultLong::value);
        register(int.class, DefaultInt.class, DefaultInt::value);
        register(Integer.class, DefaultInt.class, DefaultInt::value);
        register(double.class, DefaultDouble.class, DefaultDouble::value);
        register(Double.class, DefaultDouble.class, DefaultDouble::value);
    }


}
