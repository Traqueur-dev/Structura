package fr.traqueur.structura.integration;

import fr.traqueur.structura.registries.CustomReaderRegistry;
import fr.traqueur.structura.types.TypeToken;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static fr.traqueur.structura.fixtures.TestModels.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Generic Type Reader Integration Tests")
class GenericTypeReaderIntegrationTest {

    @BeforeEach
    void setUp() {
        CustomReaderRegistry.getInstance().clear();
    }

    @AfterEach
    void tearDown() {
        CustomReaderRegistry.getInstance().clear();
    }

    @Nested
    @DisplayName("Generic Type Reader Registration")
    class GenericTypeReaderRegistrationTest {

        @Test
        @DisplayName("Should register reader for Box<String> using TypeToken")
        void shouldRegisterReaderForBoxString() {
            CustomReaderRegistry registry = CustomReaderRegistry.getInstance();

            TypeToken<Box<String>> token = new TypeToken<Box<String>>() {};
            registry.register(token, str -> new Box<>(str.toUpperCase()));

            assertTrue(registry.hasReader(token));
        }

        @Test
        @DisplayName("Should allow different readers for Box<String> and Box<Integer>")
        void shouldAllowDifferentReadersForDifferentBoxTypes() {
            CustomReaderRegistry registry = CustomReaderRegistry.getInstance();

            TypeToken<Box<String>> stringToken = new TypeToken<Box<String>>() {};
            TypeToken<Box<Integer>> intToken = new TypeToken<Box<Integer>>() {};

            registry.register(stringToken, str -> new Box<>(str.toUpperCase()));
            registry.register(intToken, str -> new Box<>(Integer.parseInt(str)));

            assertTrue(registry.hasReader(stringToken));
            assertTrue(registry.hasReader(intToken));
            assertEquals(2, registry.size());
        }

        @Test
        @DisplayName("Should prevent duplicate registration for same generic type")
        void shouldPreventDuplicateRegistration() {
            CustomReaderRegistry registry = CustomReaderRegistry.getInstance();

            TypeToken<Box<String>> token = new TypeToken<Box<String>>() {};
            registry.register(token, str -> new Box<>(str));

            assertThrows(Exception.class, () -> {
                registry.register(token, str -> new Box<>(str.toLowerCase()));
            });
        }

        @Test
        @DisplayName("Should allow unregistering generic type readers")
        void shouldAllowUnregisteringGenericTypeReaders() {
            CustomReaderRegistry registry = CustomReaderRegistry.getInstance();

            TypeToken<Box<String>> token = new TypeToken<Box<String>>() {};
            registry.register(token, str -> new Box<>(str));

            assertTrue(registry.hasReader(token));
            assertTrue(registry.unregister(token));
            assertFalse(registry.hasReader(token));
        }
    }

    @Nested
    @DisplayName("Generic Type Conversion")
    class GenericTypeConversionTest {

        @Test
        @DisplayName("Should convert using Box<String> reader")
        void shouldConvertUsingBoxStringReader() {
            CustomReaderRegistry registry = CustomReaderRegistry.getInstance();

            TypeToken<Box<String>> token = new TypeToken<Box<String>>() {};
            registry.register(token, str -> new Box<>(str.toUpperCase()));

            @SuppressWarnings("unchecked")
            Optional<Box<String>> result = (Optional<Box<String>>) (Optional<?>) registry.convert("hello", token.getType(), Box.class);

            assertTrue(result.isPresent());
            assertEquals(new Box<>("HELLO"), result.get());
        }

        @Test
        @DisplayName("Should NOT apply Box<String> reader to raw Box class")
        void shouldNotApplyGenericReaderToRawClass() {
            CustomReaderRegistry registry = CustomReaderRegistry.getInstance();

            // Register only for Box<String>
            TypeToken<Box<String>> token = new TypeToken<Box<String>>() {};
            registry.register(token, str -> new Box<>(str.toUpperCase()));

            // Try to convert using raw Box class (no generic info)
            Optional<?> result = registry.convert("hello", Box.class);

            // Should NOT find a reader because we only registered for Box<String>, not raw Box
            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("Backward Compatibility")
    class BackwardCompatibilityTest {

        @Test
        @DisplayName("Should still support Class-based registration")
        void shouldStillSupportClassBasedRegistration() {
            CustomReaderRegistry registry = CustomReaderRegistry.getInstance();

            // Old API still works
            registry.register(StringBox.class, str -> new StringBox(str.toUpperCase()));

            assertTrue(registry.hasReader(StringBox.class));
        }

        @Test
        @DisplayName("Should allow both Class and TypeToken registrations simultaneously")
        void shouldAllowBothRegistrationTypes() {
            CustomReaderRegistry registry = CustomReaderRegistry.getInstance();

            // Class-based
            registry.register(StringBox.class, str -> new StringBox(str.toUpperCase()));

            // TypeToken-based
            TypeToken<Box<String>> token = new TypeToken<Box<String>>() {};
            registry.register(token, str -> new Box<>(str.toLowerCase()));

            assertTrue(registry.hasReader(StringBox.class));
            assertTrue(registry.hasReader(token));
            assertEquals(2, registry.size());
        }
    }

    @Nested
    @DisplayName("Complex Generic Types")
    class ComplexGenericTypesTest {

        @Test
        @DisplayName("Should handle Optional<String>")
        void shouldHandleOptionalString() {
            CustomReaderRegistry registry = CustomReaderRegistry.getInstance();

            TypeToken<Optional<String>> token = new TypeToken<Optional<String>>() {};
            registry.register(token, str -> Optional.of(str.toUpperCase()));

            @SuppressWarnings("unchecked")
            Optional<Optional<String>> result = (Optional<Optional<String>>) (Optional<?>) registry.convert("hello", token.getType(), Optional.class);

            assertTrue(result.isPresent());
            assertTrue(result.get().isPresent());
            assertEquals("HELLO", result.get().get());
        }

        @Test
        @DisplayName("Should handle List<String> with custom parsing")
        void shouldHandleListString() {
            CustomReaderRegistry registry = CustomReaderRegistry.getInstance();

            TypeToken<List<String>> token = new TypeToken<List<String>>() {};
            registry.register(token, str -> Arrays.asList(str.split(",")));

            @SuppressWarnings("unchecked")
            Optional<List<String>> result = (Optional<List<String>>) (Optional<?>) registry.convert("a,b,c", token.getType(), List.class);

            assertTrue(result.isPresent());
            assertEquals(List.of("a", "b", "c"), result.get());
        }
    }

    @Nested
    @DisplayName("Registry Management")
    class RegistryManagementTest {

        @Test
        @DisplayName("Should clear both Class and TypeToken registrations")
        void shouldClearBothRegistrations() {
            CustomReaderRegistry registry = CustomReaderRegistry.getInstance();

            registry.register(StringBox.class, str -> new StringBox(str));

            TypeToken<Box<String>> token = new TypeToken<Box<String>>() {};
            registry.register(token, str -> new Box<>(str));

            assertEquals(2, registry.size());

            registry.clear();

            assertEquals(0, registry.size());
            assertFalse(registry.hasReader(StringBox.class));
            assertFalse(registry.hasReader(token));
        }

        @Test
        @DisplayName("Should count both types of readers in size()")
        void shouldCountBothTypesInSize() {
            CustomReaderRegistry registry = CustomReaderRegistry.getInstance();

            registry.register(StringBox.class, str -> new StringBox(str));
            registry.register(IntBox.class, str -> new IntBox(Integer.parseInt(str)));

            TypeToken<Box<String>> token1 = new TypeToken<Box<String>>() {};
            TypeToken<Box<Integer>> token2 = new TypeToken<Box<Integer>>() {};

            registry.register(token1, str -> new Box<>(str));
            registry.register(token2, str -> new Box<>(Integer.parseInt(str)));

            assertEquals(4, registry.size());
        }
    }

    @Nested
    @DisplayName("Priority and Fallback")
    class PriorityAndFallbackTest {

        @Test
        @DisplayName("Should prioritize TypeToken reader over Class reader")
        void shouldPrioritizeTypeTokenReaderOverClassReader() {
            CustomReaderRegistry registry = CustomReaderRegistry.getInstance();

            // Register both
            registry.register(Box.class, str -> new Box<>("CLASS:" + str));

            TypeToken<Box<String>> token = new TypeToken<Box<String>>() {};
            registry.register(token, str -> new Box<>("TYPETOKEN:" + str));

            // When converting with generic type info, TypeToken reader should be used
            @SuppressWarnings("unchecked")
            Optional<Box<String>> result = (Optional<Box<String>>) (Optional<?>) registry.convert("test", token.getType(), Box.class);

            assertTrue(result.isPresent());
            assertEquals(new Box<>("TYPETOKEN:test"), result.get());
        }

        @Test
        @DisplayName("Should fallback to Class reader when no TypeToken reader found")
        void shouldFallbackToClassReader() {
            CustomReaderRegistry registry = CustomReaderRegistry.getInstance();

            // Register only Class reader
            registry.register(Box.class, str -> new Box<>("CLASS:" + str));

            TypeToken<Box<String>> token = new TypeToken<Box<String>>() {};

            // When converting with generic type info but no TypeToken reader, should fallback to Class reader
            @SuppressWarnings("unchecked")
            Optional<Box<String>> result = (Optional<Box<String>>) (Optional<?>) registry.convert("test", token.getType(), Box.class);

            assertTrue(result.isPresent());
            assertEquals(new Box<>("CLASS:test"), result.get());
        }
    }
}
