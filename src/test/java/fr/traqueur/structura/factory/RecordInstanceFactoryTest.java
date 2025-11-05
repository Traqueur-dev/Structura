package fr.traqueur.structura.factory;

import fr.traqueur.structura.conversion.ValueConverter;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.mapping.FieldMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static fr.traqueur.structura.fixtures.TestModels.*;
import static fr.traqueur.structura.helpers.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Refactored RecordInstanceFactory tests using common test models.
 * Tests record instance creation with various scenarios.
 */
@DisplayName("RecordInstanceFactory - Refactored Tests")
class RecordInstanceFactoryTest {

    private RecordInstanceFactory factory;
    private FieldMapper fieldMapper;

    @BeforeEach
    void setUp() {
        fieldMapper = new FieldMapper();
        factory = new RecordInstanceFactory(fieldMapper);
        ValueConverter valueConverter = new ValueConverter(factory);
        factory.setValueConverter(valueConverter);
    }

    @Nested
    @DisplayName("Basic Instance Creation Tests")
    class BasicInstanceCreationTest {

        @Test
        @DisplayName("Should create simple record instances")
        void shouldCreateSimpleRecordInstances() {
            Map<String, Object> data = Map.of(
                    "name", "test",
                    "value", 123
            );

            SimpleRecord result = (SimpleRecord) factory.createInstance(data, SimpleRecord.class, "");

            assertEquals("test", result.name());
            assertEquals(123, result.value());
        }

        @Test
        @DisplayName("Should handle default values")
        void shouldHandleDefaultValues() {
            Map<String, Object> data = Map.of();

            ConfigWithDefaults result = (ConfigWithDefaults) factory.createInstance(data, ConfigWithDefaults.class, "");

            assertEquals("default-app", result.appName());
            assertEquals(8080, result.serverPort());
            assertTrue(result.debugMode());
            assertEquals(30000L, result.timeout());
            assertEquals(1.5, result.multiplier());
        }

        @Test
        @DisplayName("Should handle custom field names")
        void shouldHandleCustomFieldNames() {
            Map<String, Object> data = Map.of(
                    "app-name", "test",
                    "server-config", Map.of("host", "localhost", "port", 8080)
            );

            ConfigWithCustomNames result = (ConfigWithCustomNames) factory.createInstance(data, ConfigWithCustomNames.class, "");

            assertEquals("test", result.applicationName());
            assertNotNull(result.serverConfig());
            assertEquals("localhost", result.serverConfig().host());
        }

        @Test
        @DisplayName("Should handle optional fields")
        void shouldHandleOptionalFields() {
            Map<String, Object> data = Map.of("required", "test");

            ConfigWithOptionalFields result = (ConfigWithOptionalFields) factory.createInstance(data, ConfigWithOptionalFields.class, "");

            assertEquals("test", result.required());
            assertNull(result.optional());
            assertEquals("fallback", result.optionalWithDefault());
        }
    }

    @Nested
    @DisplayName("Key Mapping Tests")
    class KeyMappingTest {

        @Test
        @DisplayName("Should handle simple key mapping")
        void shouldHandleSimpleKeyMapping() {
            Map<String, Object> data = Map.of(
                    "mykey", Map.of("value-int", 42, "value-double", 3.14)
            );

            SimpleKeyRecord result = (SimpleKeyRecord) factory.createInstance(data, SimpleKeyRecord.class, "");

            assertEquals("mykey", result.id());
            assertEquals(42, result.valueInt());
            assertEquals(3.14, result.valueDouble());
        }

        @Test
        @DisplayName("Should handle simple key mapping with empty data")
        void shouldHandleSimpleKeyMappingWithEmptyData() {
            Map<String, Object> data = Map.of("mykey", Map.of());

            SimpleKeyRecord result = (SimpleKeyRecord) factory.createInstance(data, SimpleKeyRecord.class, "");

            assertEquals("mykey", result.id());
            assertEquals(0, result.valueInt()); // Default value
            assertEquals(0.0, result.valueDouble()); // Default value
        }

        @Test
        @DisplayName("Should throw exception for simple key mapping with missing required data")
        void shouldThrowExceptionForSimpleKeyMappingWithMissingRequiredData() {
            // Using DatabaseConfig which has required fields without defaults
            Map<String, Object> data = Map.of("mykey", Map.of());

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    factory.createInstance(data, DatabaseConfig.class, "")
            );

            assertContainsAll(exception.getMessage(), "is required but not provided");
        }

        @Test
        @DisplayName("Should handle complex key mapping")
        void shouldHandleComplexKeyMapping() {
            Map<String, Object> data = Map.of(
                    "host", "db.example.com",
                    "port", 5432,
                    "protocol", "https",
                    "app-name", "TestApp",
                    "debug-mode", true
            );

            ComplexKeyConfig result = (ComplexKeyConfig) factory.createInstance(data, ComplexKeyConfig.class, "");

            assertNotNull(result.server());
            assertEquals("db.example.com", result.server().host());
            assertEquals(5432, result.server().port());
            assertEquals("https", result.server().protocol());
            assertEquals("TestApp", result.appName());
            assertTrue(result.debugMode());
        }

        @Test
        @DisplayName("Should handle complex key mapping with defaults")
        void shouldHandleComplexKeyMappingWithDefaults() {
            Map<String, Object> data = Map.of(
                    "host", "localhost",
                    "app-name", "DefaultApp"
            );

            ComplexKeyConfig result = (ComplexKeyConfig) factory.createInstance(data, ComplexKeyConfig.class, "");

            assertNotNull(result.server());
            assertEquals("localhost", result.server().host());
            assertEquals(8080, result.server().port()); // Default value
            assertEquals("http", result.server().protocol()); // Default value
            assertEquals("DefaultApp", result.appName());
            assertFalse(result.debugMode()); // Default value
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTest {

        @Test
        @DisplayName("Should throw exception for null data")
        void shouldThrowExceptionForNullData() {
            StructuraException exception = assertThrows(StructuraException.class, () ->
                    factory.createInstance(null, SimpleRecord.class, "")
            );

            assertEquals("Data map cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception for null class")
        void shouldThrowExceptionForNullClass() {
            Map<String, Object> data = Map.of("name", "test");

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    factory.createInstance(data, null, "")
            );

            assertEquals("Record class cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception for non-record class")
        void shouldThrowExceptionForNonRecordClass() {
            Map<String, Object> data = Map.of("value", "test");

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    factory.createInstance(data, String.class, "")
            );

            assertContainsAll(exception.getMessage(), "is not a record type");
        }

        @Test
        @DisplayName("Should throw exception for missing required fields")
        void shouldThrowExceptionForMissingRequiredFields() {
            Map<String, Object> data = Map.of("name", "test");

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    factory.createInstance(data, SimpleRecord.class, "")
            );

            assertContainsAll(exception.getMessage(), "is required but not provided");
        }

        @Test
        @DisplayName("Should throw exception if ValueConverter not injected")
        void shouldThrowExceptionIfValueConverterNotInjected() {
            RecordInstanceFactory factoryWithoutConverter = new RecordInstanceFactory(fieldMapper);

            Map<String, Object> data = Map.of(
                    "app-name", "MyApp",
                    "database", Map.of("host", "localhost", "port", 5432, "database", "testdb"),
                    "server", Map.of("host", "localhost", "port", 8080)
            );

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    factoryWithoutConverter.createInstance(data, NestedConfig.class, "")
            );

            assertContainsAll(exception.getMessage(), "ValueConverter not injected");
        }
    }
}