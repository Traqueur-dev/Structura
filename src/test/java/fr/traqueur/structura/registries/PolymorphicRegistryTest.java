package fr.traqueur.structura.registries;

import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.annotations.defaults.DefaultInt;
import fr.traqueur.structura.annotations.defaults.DefaultString;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.exceptions.StructuraException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PolymorphicRegistry - Essential Tests")
class PolymorphicRegistryTest {

    // Test interfaces and implementations
    @Polymorphic(key = "type")
    public interface TestDatabaseConfig extends Loadable {
        String getHost();
        int getPort();
    }

    @Polymorphic(key = "provider")
    public interface TestPaymentProvider extends Loadable {
        String getName();
    }

    public record TestMySQLConfig(
            @DefaultString("localhost") String host,
            @DefaultInt(3306) int port
    ) implements TestDatabaseConfig {
        @Override public String getHost() { return host; }
        @Override public int getPort() { return port; }
    }

    public record TestPostgreSQLConfig(
            @DefaultString("localhost") String host,
            @DefaultInt(5432) int port
    ) implements TestDatabaseConfig {
        @Override public String getHost() { return host; }
        @Override public int getPort() { return port; }
    }

    public record TestStripeProvider(
            @DefaultString("Stripe") String name
    ) implements TestPaymentProvider {
        @Override public String getName() { return name; }
    }

    @BeforeEach
    void setUp() {
        clearAllRegistries();
    }

    private void clearAllRegistries() {
        try {
            var field = PolymorphicRegistry.class.getDeclaredField("REGISTRIES");
            field.setAccessible(true);
            ((java.util.Map<?, ?>) field.get(null)).clear();
        } catch (Exception e) {
            fail("Failed to clear registries: " + e.getMessage());
        }
    }

    @Nested
    @DisplayName("Core Functionality")
    class CoreFunctionalityTest {

        @Test
        @DisplayName("Should create and retrieve registry with implementations")
        void shouldCreateAndRetrieveRegistryWithImplementations() {
            // Create registry
            PolymorphicRegistry.create(TestDatabaseConfig.class, registry -> {
                registry.register("mysql", TestMySQLConfig.class);
                registry.register("postgres", TestPostgreSQLConfig.class);
                registry.register(TestMySQLConfig.class); // Auto-naming test
            });

            // Retrieve and verify
            PolymorphicRegistry<TestDatabaseConfig> registry = PolymorphicRegistry.get(TestDatabaseConfig.class);

            assertTrue(registry.get("mysql").isPresent());
            assertTrue(registry.get("postgres").isPresent());
            assertTrue(registry.get("testmysqlconfig").isPresent()); // Auto-generated name
            assertEquals(TestMySQLConfig.class, registry.get("mysql").get());
            assertEquals(3, registry.availableNames().size());
        }

        @Test
        @DisplayName("Should handle case-insensitive retrieval")
        void shouldHandleCaseInsensitiveRetrieval() {
            PolymorphicRegistry.create(TestDatabaseConfig.class, registry -> {
                registry.register("MySQL", TestMySQLConfig.class);
            });

            PolymorphicRegistry<TestDatabaseConfig> registry = PolymorphicRegistry.get(TestDatabaseConfig.class);

            assertTrue(registry.get("mysql").isPresent());
            assertTrue(registry.get("MYSQL").isPresent());
            assertTrue(registry.get("MySql").isPresent());
        }

        @Test
        @DisplayName("Should maintain type safety across different registries")
        void shouldMaintainTypeSafetyAcrossDifferentRegistries() {
            // Create separate registries
            PolymorphicRegistry.create(TestDatabaseConfig.class, registry -> {
                registry.register("mysql", TestMySQLConfig.class);
            });

            PolymorphicRegistry.create(TestPaymentProvider.class, registry -> {
                registry.register("stripe", TestStripeProvider.class);
            });

            // Verify isolation
            PolymorphicRegistry<TestDatabaseConfig> dbRegistry = PolymorphicRegistry.get(TestDatabaseConfig.class);
            PolymorphicRegistry<TestPaymentProvider> paymentRegistry = PolymorphicRegistry.get(TestPaymentProvider.class);

            assertTrue(dbRegistry.get("mysql").isPresent());
            assertFalse(dbRegistry.get("stripe").isPresent());
            assertTrue(paymentRegistry.get("stripe").isPresent());
            assertFalse(paymentRegistry.get("mysql").isPresent());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTest {

        @Test
        @DisplayName("Should throw exception for null class in create")
        void shouldThrowExceptionForNullClassInCreate() {
            StructuraException exception = assertThrows(StructuraException.class, () -> {
                PolymorphicRegistry.create(null, registry -> {});
            });
            assertEquals("Cannot create registry for null class.", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception for null configurator")
        void shouldThrowExceptionForNullConfigurator() {
            StructuraException exception = assertThrows(StructuraException.class, () -> {
                PolymorphicRegistry.create(TestDatabaseConfig.class, null);
            });
            assertEquals("Cannot create registry for null configurator.", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception when registry already exists")
        void shouldThrowExceptionWhenRegistryAlreadyExists() {
            PolymorphicRegistry.create(TestDatabaseConfig.class, registry -> {});

            StructuraException exception = assertThrows(StructuraException.class, () -> {
                PolymorphicRegistry.create(TestDatabaseConfig.class, registry -> {});
            });
            assertTrue(exception.getMessage().contains("Registry already exists for TestDatabaseConfig"));
        }

        @Test
        @DisplayName("Should throw exception for non-existent registry")
        void shouldThrowExceptionForNonExistentRegistry() {
            StructuraException exception = assertThrows(StructuraException.class, () -> {
                PolymorphicRegistry.get(TestDatabaseConfig.class);
            });
            assertEquals("No polymorphic registry registered for TestDatabaseConfig", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception for duplicate implementation name")
        void shouldThrowExceptionForDuplicateImplementationName() {
            PolymorphicRegistry.create(TestDatabaseConfig.class, registry -> {
                registry.register("mysql", TestMySQLConfig.class);

                StructuraException exception = assertThrows(StructuraException.class, () -> {
                    registry.register("mysql", TestPostgreSQLConfig.class);
                });
                assertEquals("A loader with the name 'mysql' is already registered.", exception.getMessage());
            });
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should throw exception for invalid implementation names")
        void shouldThrowExceptionForInvalidImplementationNames(String invalidName) {
            PolymorphicRegistry.create(TestDatabaseConfig.class, registry -> {
                StructuraException exception = assertThrows(StructuraException.class, () -> {
                    registry.register(invalidName, TestMySQLConfig.class);
                });
                assertEquals("Cannot register a class with a null or empty name.", exception.getMessage());
            });
        }

        @Test
        @DisplayName("Should throw exception for null implementation")
        void shouldThrowExceptionForNullImplementation() {
            PolymorphicRegistry.create(TestDatabaseConfig.class, registry -> {
                StructuraException exception = assertThrows(StructuraException.class, () -> {
                    registry.register("mysql", null);
                });
                assertEquals("Cannot register a null class for name 'mysql'.", exception.getMessage());
            });
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTest {

        @Test
        @DisplayName("Should return empty optional for non-existent implementation")
        void shouldReturnEmptyOptionalForNonExistentImplementation() {
            PolymorphicRegistry.create(TestDatabaseConfig.class, registry -> {
                registry.register("mysql", TestMySQLConfig.class);
            });

            PolymorphicRegistry<TestDatabaseConfig> registry = PolymorphicRegistry.get(TestDatabaseConfig.class);

            assertTrue(registry.get("mysql").isPresent());
            assertFalse(registry.get("nonexistent").isPresent());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should return empty optional for invalid retrieval names")
        void shouldReturnEmptyOptionalForInvalidRetrievalNames(String invalidName) {
            PolymorphicRegistry.create(TestDatabaseConfig.class, registry -> {
                registry.register("mysql", TestMySQLConfig.class);
            });

            PolymorphicRegistry<TestDatabaseConfig> registry = PolymorphicRegistry.get(TestDatabaseConfig.class);
            Optional<Class<? extends TestDatabaseConfig>> result = registry.get(invalidName);

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Should handle empty registry")
        void shouldHandleEmptyRegistry() {
            PolymorphicRegistry.create(TestDatabaseConfig.class, registry -> {
                // No registrations
            });

            PolymorphicRegistry<TestDatabaseConfig> registry = PolymorphicRegistry.get(TestDatabaseConfig.class);

            assertTrue(registry.availableNames().isEmpty());
            assertFalse(registry.get("anything").isPresent());
        }
    }
}