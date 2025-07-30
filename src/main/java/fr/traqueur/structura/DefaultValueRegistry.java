package fr.traqueur.structura;

import fr.traqueur.structura.api.annotations.defaults.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class DefaultValueRegistry {

    private static DefaultValueRegistry INSTANCE;

    public static DefaultValueRegistry getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DefaultValueRegistry();
        }
        return INSTANCE;
    }

    private final Map<Class<?>, Map<Class<? extends Annotation>, Function<Annotation, Object>>> handlers = new ConcurrentHashMap<>();

    private DefaultValueRegistry() {
        registerDefaultHandlers();
    }

    public <T, A extends Annotation> void register(Class<T> targetType, Class<A> annotationType, Function<A, T> valueExtractor) {
        handlers.computeIfAbsent(targetType, k -> new ConcurrentHashMap<>())
                .put(annotationType, (Function<Annotation, Object>) valueExtractor);
    }

    public Object getDefaultValue(Parameter parameter) {
        Map<Class<? extends Annotation>, Function<Annotation, Object>> typeHandlers = handlers.get(parameter.getType());
        if (typeHandlers == null) {
            return null;
        }

        for (Annotation annotation : parameter.getAnnotations()) {
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
