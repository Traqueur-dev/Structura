package fr.traqueur.structura.factory;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.defaults.DefaultInt;
import fr.traqueur.structura.annotations.defaults.DefaultString;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.conversion.ValueConverter;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.mapping.FieldMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RecordInstanceFactory Tests")
class RecordInstanceFactoryTest {

    private RecordInstanceFactory factory;
    private FieldMapper fieldMapper;
    private ValueConverter valueConverter;

    @BeforeEach
    void setUp() {
        fieldMapper = new FieldMapper();
        factory = new RecordInstanceFactory(fieldMapper);
        valueConverter = new ValueConverter(factory);
        factory.setValueConverter(valueConverter);
    }

    // Test records
    public record SimpleRecord(String name, int value) implements Loadable {}

    public record RecordWithDefaults(
            @DefaultString("default") String name,
            @DefaultInt(42) int value
    ) implements Loadable {}

    public record RecordWithOptions(
            @Options(name = "custom-name") String customName,
            @Options(optional = true) String optional
    ) implements Loadable {}

    public record SimpleKeyRecord(
            @Options(isKey = true) String id,
            String data
    ) implements Loadable {}

    public record SimpleKeyRecordWithOptionalData(
            @Options(isKey = true) String id,
            @Options(optional = true) String data
    ) implements Loadable {}

    public record NestedRecord(
            String host,
            @DefaultInt(8080) int port
    ) implements Loadable {}

    public record ComplexKeyRecord(
            @Options(isKey = true) NestedRecord server,
            String appName
    ) implements Loadable {}

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

            RecordWithDefaults result = (RecordWithDefaults) factory.createInstance(data, RecordWithDefaults.class, "");

            assertEquals("default", result.name());
            assertEquals(42, result.value());
        }

        @Test
        @DisplayName("Should handle custom field names")
        void shouldHandleCustomFieldNames() {
            Map<String, Object> data = Map.of("custom-name", "test");

            RecordWithOptions result = (RecordWithOptions) factory.createInstance(data, RecordWithOptions.class, "");

            assertEquals("test", result.customName());
            assertNull(result.optional());
        }

        @Test
        @DisplayName("Should handle optional fields")
        void shouldHandleOptionalFields() {
            Map<String, Object> data = Map.of(
                    "custom-name", "test",
                    "optional", "present"
            );

            RecordWithOptions result = (RecordWithOptions) factory.createInstance(data, RecordWithOptions.class, "");

            assertEquals("test", result.customName());
            assertEquals("present", result.optional());
        }
    }

    @Nested
    @DisplayName("Key Mapping Tests")
    class KeyMappingTest {

        @Test
        @DisplayName("Should handle simple key mapping")
        void shouldHandleSimpleKeyMapping() {
            Map<String, Object> data = Map.of(
                    "mykey", Map.of("data", "value")
            );

            SimpleKeyRecord result = (SimpleKeyRecord) factory.createInstance(data, SimpleKeyRecord.class, "");

            assertEquals("mykey", result.id());
            assertEquals("value", result.data());
        }

        @Test
        @DisplayName("Should handle simple key mapping with empty data")
        void shouldHandleSimpleKeyMappingWithEmptyData() {
            Map<String, Object> data = Map.of("testkey", Map.of());

            SimpleKeyRecordWithOptionalData result = (SimpleKeyRecordWithOptionalData)
                    factory.createInstance(data, SimpleKeyRecordWithOptionalData.class, "");

            assertEquals("testkey", result.id());
            assertNull(result.data()); // Optional field, so null when missing
        }

        @Test
        @DisplayName("Should throw exception for simple key mapping with missing required data")
        void shouldThrowExceptionForSimpleKeyMappingWithMissingRequiredData() {
            Map<String, Object> data = Map.of("testkey", Map.of());

            StructuraException exception = assertThrows(StructuraException.class, () -> {
                factory.createInstance(data, SimpleKeyRecord.class, "");
            });

            assertTrue(exception.getMessage().contains("data is required"));
        }

        @Test
        @DisplayName("Should handle complex key mapping")
        void shouldHandleComplexKeyMapping() {
            Map<String, Object> data = Map.of(
                    "host", "localhost",
                    "port", 9000,
                    "app-name", "TestApp"
            );

            ComplexKeyRecord result = (ComplexKeyRecord) factory.createInstance(data, ComplexKeyRecord.class, "");

            assertNotNull(result.server());
            assertEquals("localhost", result.server().host());
            assertEquals(9000, result.server().port());
            assertEquals("TestApp", result.appName());
        }

        @Test
        @DisplayName("Should handle complex key mapping with defaults")
        void shouldHandleComplexKeyMappingWithDefaults() {
            Map<String, Object> data = Map.of(
                    "host", "localhost",
                    "app-name", "TestApp"
            );

            ComplexKeyRecord result = (ComplexKeyRecord) factory.createInstance(data, ComplexKeyRecord.class, "");

            assertNotNull(result.server());
            assertEquals("localhost", result.server().host());
            assertEquals(8080, result.server().port()); // Default value
            assertEquals("TestApp", result.appName());
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTest {

        @Test
        @DisplayName("Should throw exception for null data")
        void shouldThrowForNullData() {
            assertThrows(StructuraException.class, () -> {
                factory.createInstance(null, SimpleRecord.class, "");
            });
        }

        @Test
        @DisplayName("Should throw exception for null class")
        void shouldThrowForNullClass() {
            assertThrows(StructuraException.class, () -> {
                factory.createInstance(Map.of(), null, "");
            });
        }

        @Test
        @DisplayName("Should throw exception for non-record class")
        void shouldThrowForNonRecordClass() {
            assertThrows(StructuraException.class, () -> {
                factory.createInstance(Map.of(), String.class, "");
            });
        }

        @Test
        @DisplayName("Should throw exception if ValueConverter not injected")
        void shouldThrowIfValueConverterNotInjected() {
            RecordInstanceFactory factoryWithoutConverter = new RecordInstanceFactory(fieldMapper);

            assertThrows(StructuraException.class, () -> {
                factoryWithoutConverter.createInstance(Map.of(), SimpleRecord.class, "");
            });
        }

        @Test
        @DisplayName("Should throw exception for missing required fields")
        void shouldThrowExceptionForMissingRequiredFields() {
            Map<String, Object> data = Map.of("name", "test"); // missing 'value' field

            StructuraException exception = assertThrows(StructuraException.class, () -> {
                factory.createInstance(data, SimpleRecord.class, "");
            });

            assertTrue(exception.getMessage().contains("value is required"));
        }
    }
}