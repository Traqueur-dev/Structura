package fr.traqueur.structura;

import fr.traqueur.structura.api.annotations.defaults.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class DefaultValueRegistry {

    private static final DefaultValueRegistry INSTANCE = new DefaultValueRegistry();

    public static DefaultValueRegistry getInstance() {
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

    public <T, A extends Annotation> void register(Class<T> clazz, Function<A, T> valueExtractor) {
        Type[] genericInterfaces = valueExtractor.getClass().getGenericInterfaces();
        for (Type genericInterface : genericInterfaces) {
            if (genericInterface instanceof ParameterizedType paramType) {
                if (paramType.getRawType().equals(Function.class)) {
                    Type[] typeArgs = paramType.getActualTypeArguments();
                    if (typeArgs.length == 2 && typeArgs[0] instanceof Class) {
                        Class<A> annotationType = (Class<A>) typeArgs[0];
                        register(clazz, annotationType, valueExtractor);
                        return;
                    }
                }
            }
        }
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
        register(boolean.class, DefaultBool::value);
        register(Boolean.class, DefaultBool::value);
        register(String.class, DefaultString::value);
        register(long.class, DefaultLong::value);
        register(Long.class, DefaultLong::value);
        register(int.class, DefaultInt::value);
        register(Integer.class, DefaultInt::value);
        register(double.class, DefaultDouble::value);
        register(Double.class, DefaultDouble::value);
    }


}
