package fr.traqueur.structura;

import fr.traqueur.structura.annotations.defaults.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Minimal tests for DefaultValueRegistry - Focus on registry behavior and custom handlers
 * Built-in type handling is covered by integration tests
 */
@DisplayName("DefaultValueRegistry - Registry Behavior Tests")
class DefaultValueRegistryTest {

    private final DefaultValueRegistry registry = DefaultValueRegistry.getInstance();

    @Test
    @DisplayName("Should maintain singleton pattern")
    void shouldMaintainSingletonPattern() {
        DefaultValueRegistry instance1 = DefaultValueRegistry.getInstance();
        DefaultValueRegistry instance2 = DefaultValueRegistry.getInstance();

        assertSame(instance1, instance2);
    }

    @interface CustomDefault {
        String value();
    }


    @Test
    @DisplayName("Should support custom handler registration and resolution")
    void shouldSupportCustomHandlerRegistration() {
        // Register custom handler
        registry.register(String.class, CustomDefault.class, CustomDefault::value);

        // Create mock annotation
        CustomDefault annotation = new CustomDefault() {
            @Override
            public String value() { return "custom_value"; }
            @Override
            public Class<? extends Annotation> annotationType() { return CustomDefault.class; }
        };

        // Test custom handler resolution
        Object result = registry.getDefaultValue(String.class, List.of(annotation));
        assertEquals("custom_value", result);
    }

    @interface CustomStringDefault {
        String value();
    }

    @Test
    @DisplayName("Should handle multiple handlers for same type with priority")
    void shouldHandleMultipleHandlersWithPriority() {
        // Built-in DefaultString should work
        DefaultString defaultStringAnnotation = new DefaultString() {
            @Override
            public String value() { return "default_string"; }
            @Override
            public Class<? extends Annotation> annotationType() { return DefaultString.class; }
        };

        Object result = registry.getDefaultValue(String.class, List.of(defaultStringAnnotation));
        assertEquals("default_string", result);

        registry.register(String.class, CustomStringDefault.class, CustomStringDefault::value);

        CustomStringDefault customAnnotation = new CustomStringDefault() {
            @Override
            public String value() { return "custom_string"; }
            @Override
            public Class<? extends Annotation> annotationType() { return CustomStringDefault.class; }
        };

        result = registry.getDefaultValue(String.class, List.of(customAnnotation));
        assertEquals("custom_string", result);
    }

    @interface SecondStringDefault {
        String value();
    }

    @Test
    @DisplayName("Should return first non-null value from multiple annotations")
    void shouldReturnFirstNonNullFromMultipleAnnotations() {
        DefaultString stringAnnotation = new DefaultString() {
            @Override
            public String value() { return "first_value"; }
            @Override
            public Class<? extends Annotation> annotationType() { return DefaultString.class; }
        };

        registry.register(String.class, SecondStringDefault.class, SecondStringDefault::value);

        SecondStringDefault secondAnnotation = new SecondStringDefault() {
            @Override
            public String value() { return "second_value"; }
            @Override
            public Class<? extends Annotation> annotationType() { return SecondStringDefault.class; }
        };

        // First matching handler should win
        Object result = registry.getDefaultValue(String.class, List.of(stringAnnotation, secondAnnotation));
        assertEquals("first_value", result);
    }

    @Test
    @DisplayName("Should return null for unhandled cases")
    void shouldReturnNullForUnhandledCases() {
        // Unknown type
        assertNull(registry.getDefaultValue(Thread.class, List.of()));

        // Empty annotation list
        assertNull(registry.getDefaultValue(String.class, List.of()));

        // Type with incompatible annotation
        DefaultInt intAnnotation = new DefaultInt() {
            @Override
            public int value() { return 42; }
            @Override
            public Class<? extends Annotation> annotationType() { return DefaultInt.class; }
        };

        assertNull(registry.getDefaultValue(String.class, List.of(intAnnotation)));
    }

    @interface NullReturningDefault {
        String value();
    }

    @Test
    @DisplayName("Should handle null handlers gracefully")
    void shouldHandleNullHandlersGracefully() {

        // Register handler that returns null
        registry.register(String.class, NullReturningDefault.class, annotation -> null);

        NullReturningDefault annotation = new NullReturningDefault() {
            @Override
            public String value() { return "ignored"; }
            @Override
            public Class<? extends Annotation> annotationType() { return NullReturningDefault.class; }
        };

        Object result = registry.getDefaultValue(String.class, List.of(annotation));
        assertNull(result);
    }
}