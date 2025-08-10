package fr.traqueur.structura;

import fr.traqueur.structura.annotations.defaults.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultValueRegistry Tests")
class DefaultValueRegistryTest {

    private DefaultValueRegistry registry;

    @BeforeEach
    void setUp() {
        registry = DefaultValueRegistry.getInstance();
    }

    @Nested
    @DisplayName("Built-in Default Value Tests")
    class BuiltinDefaultValueTest {

        @Test
        @DisplayName("Should provide default string values")
        void shouldProvideDefaultStringValues() {
            DefaultString annotation = new DefaultString() {
                @Override
                public String value() { return "test"; }
                @Override
                public Class<? extends Annotation> annotationType() { return DefaultString.class; }
            };

            Object result = registry.getDefaultValue(String.class, List.of(annotation));
            assertEquals("test", result);
        }

        @Test
        @DisplayName("Should provide default int values")
        void shouldProvideDefaultIntValues() {
            DefaultInt annotation = new DefaultInt() {
                @Override
                public int value() { return 42; }
                @Override
                public Class<? extends Annotation> annotationType() { return DefaultInt.class; }
            };

            Object result = registry.getDefaultValue(int.class, List.of(annotation));
            assertEquals(42, result);
            
            result = registry.getDefaultValue(Integer.class, List.of(annotation));
            assertEquals(42, result);
        }

        @Test
        @DisplayName("Should provide default long values")
        void shouldProvideDefaultLongValues() {
            DefaultLong annotation = new DefaultLong() {
                @Override
                public long value() { return 123456L; }
                @Override
                public Class<? extends Annotation> annotationType() { return DefaultLong.class; }
            };

            Object result = registry.getDefaultValue(long.class, List.of(annotation));
            assertEquals(123456L, result);
            
            result = registry.getDefaultValue(Long.class, List.of(annotation));
            assertEquals(123456L, result);
        }

        @Test
        @DisplayName("Should provide default double values")
        void shouldProvideDefaultDoubleValues() {
            DefaultDouble annotation = new DefaultDouble() {
                @Override
                public double value() { return 3.14; }
                @Override
                public Class<? extends Annotation> annotationType() { return DefaultDouble.class; }
            };

            Object result = registry.getDefaultValue(double.class, List.of(annotation));
            assertEquals(3.14, result);
            
            result = registry.getDefaultValue(Double.class, List.of(annotation));
            assertEquals(3.14, result);
        }

        @Test
        @DisplayName("Should provide default boolean values")
        void shouldProvideDefaultBooleanValues() {
            DefaultBool annotation = new DefaultBool() {
                @Override
                public boolean value() { return true; }
                @Override
                public Class<? extends Annotation> annotationType() { return DefaultBool.class; }
            };

            Object result = registry.getDefaultValue(boolean.class, List.of(annotation));
            assertEquals(true, result);
            
            result = registry.getDefaultValue(Boolean.class, List.of(annotation));
            assertEquals(true, result);
        }
    }

    @Nested
    @DisplayName("Custom Handler Tests")
    class CustomHandlerTest {

        public @interface CustomDefault {
            String value();
        }

        @Test
        @DisplayName("Should allow registration of custom handlers")
        void shouldAllowRegistrationOfCustomHandlers() {
            registry.register(String.class, CustomDefault.class, CustomDefault::value);

            CustomDefault annotation = new CustomDefault() {
                @Override
                public String value() { return "custom"; }
                @Override
                public Class<? extends Annotation> annotationType() { return CustomDefault.class; }
            };

            Object result = registry.getDefaultValue(String.class, List.of(annotation));
            assertEquals("custom", result);
        }

        @Test
        @DisplayName("Should handle multiple handlers for same type")
        void shouldHandleMultipleHandlersForSameType() {
            // First, test with DefaultString
            DefaultString defaultStringAnnotation = new DefaultString() {
                @Override
                public String value() { return "default"; }
                @Override
                public Class<? extends Annotation> annotationType() { return DefaultString.class; }
            };

            Object result = registry.getDefaultValue(String.class, List.of(defaultStringAnnotation));
            assertEquals("default", result);

            // Register custom handler
            registry.register(String.class, CustomDefault.class, CustomDefault::value);

            CustomDefault customAnnotation = new CustomDefault() {
                @Override
                public String value() { return "custom"; }
                @Override
                public Class<? extends Annotation> annotationType() { return CustomDefault.class; }
            };

            result = registry.getDefaultValue(String.class, List.of(customAnnotation));
            assertEquals("custom", result);
        }

        @Test
        @DisplayName("Should return first non-null value from multiple annotations")
        void shouldReturnFirstNonNullValueFromMultipleAnnotations() {
            DefaultString stringAnnotation = new DefaultString() {
                @Override
                public String value() { return "string_default"; }
                @Override
                public Class<? extends Annotation> annotationType() { return DefaultString.class; }
            };

            registry.register(String.class, CustomDefault.class, CustomDefault::value);

            CustomDefault customAnnotation = new CustomDefault() {
                @Override
                public String value() { return "custom_default"; }
                @Override
                public Class<? extends Annotation> annotationType() { return CustomDefault.class; }
            };

            Object result = registry.getDefaultValue(String.class, List.of(stringAnnotation, customAnnotation));
            assertEquals("string_default", result); // First matching handler wins
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTest {

        @Test
        @DisplayName("Should return null for unknown types")
        void shouldReturnNullForUnknownTypes() {
            Object result = registry.getDefaultValue(Object.class, List.of());
            assertNull(result);
        }

        @Test
        @DisplayName("Should return null for empty annotation list")
        void shouldReturnNullForEmptyAnnotationList() {
            Object result = registry.getDefaultValue(String.class, List.of());
            assertNull(result);
        }

        @Test
        @DisplayName("Should return null when no matching handlers exist")
        void shouldReturnNullWhenNoMatchingHandlersExist() {
            DefaultInt intAnnotation = new DefaultInt() {
                @Override
                public int value() { return 42; }
                @Override
                public Class<? extends Annotation> annotationType() { return DefaultInt.class; }
            };

            // Try to get default for String with int annotation
            Object result = registry.getDefaultValue(String.class, List.of(intAnnotation));
            assertNull(result);
        }

        @Test
        @DisplayName("Should be singleton")
        void shouldBeSingleton() {
            DefaultValueRegistry instance1 = DefaultValueRegistry.getInstance();
            DefaultValueRegistry instance2 = DefaultValueRegistry.getInstance();
            
            assertSame(instance1, instance2);
        }
    }
}