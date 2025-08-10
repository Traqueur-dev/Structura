package fr.traqueur.structura.conversion;

import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.annotations.defaults.DefaultBool;
import fr.traqueur.structura.annotations.defaults.DefaultInt;
import fr.traqueur.structura.annotations.defaults.DefaultString;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.factory.RecordInstanceFactory;
import fr.traqueur.structura.mapping.FieldMapper;
import fr.traqueur.structura.registries.PolymorphicRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ValueConverter - Complete Type Conversion Tests")
class ValueConverterTest {

    private ValueConverter valueConverter;

    @BeforeEach
    void setUp() {
        clearAllRegistries();
        FieldMapper fieldMapper = new FieldMapper();
        RecordInstanceFactory recordFactory = new RecordInstanceFactory(fieldMapper);
        valueConverter = new ValueConverter(recordFactory);
        recordFactory.setValueConverter(valueConverter);
        setupPolymorphicRegistries();
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

    private void setupPolymorphicRegistries() {
        // Setup database registry
        PolymorphicRegistry.create(TestDatabaseConfig.class, registry -> {
            registry.register("mysql", TestMySQLConfig.class);
            registry.register("postgres", TestPostgreSQLConfig.class);
        });

        // Setup payment registry
        PolymorphicRegistry.create(TestPaymentProvider.class, registry -> {
            registry.register("stripe", TestStripeProvider.class);
            registry.register("paypal", TestPayPalProvider.class);
        });
    }

    // Basic test records
    public record SimpleRecord(String name, int value) implements Loadable {}
    public record RecordWithDefaults(
            @DefaultString("defaultName") String name,
            @DefaultInt(0) int value
    ) implements Loadable {}
    public enum TestEnum { VALUE1, VALUE2, VALUE3 }

    // Polymorphic interfaces and implementations
    @Polymorphic(key = "type")
    public interface TestDatabaseConfig extends Loadable {
        String getHost();
        int getPort();
    }

    @Polymorphic(key = "provider")
    public interface TestPaymentProvider extends Loadable {
        String getName();
        boolean isEnabled();
    }

    public record TestMySQLConfig(
            @DefaultString("localhost") String host,
            @DefaultInt(3306) int port,
            @DefaultString("mysql") String driver
    ) implements TestDatabaseConfig {
        @Override public String getHost() { return host; }
        @Override public int getPort() { return port; }
    }

    public record TestPostgreSQLConfig(
            @DefaultString("localhost") String host,
            @DefaultInt(5432) int port,
            @DefaultString("postgresql") String driver
    ) implements TestDatabaseConfig {
        @Override public String getHost() { return host; }
        @Override public int getPort() { return port; }
    }

    public record TestStripeProvider(
            @DefaultString("Stripe") String name,
            @DefaultBool(true) boolean enabled,
            @DefaultString("sk_test_") String apiKey
    ) implements TestPaymentProvider {
        @Override public String getName() { return name; }
        @Override public boolean isEnabled() { return enabled; }
    }

    public record TestPayPalProvider(
            @DefaultString("PayPal") String name,
            @DefaultBool(false) boolean enabled,
            @DefaultString("client_id_") String clientId
    ) implements TestPaymentProvider {
        @Override public String getName() { return name; }
        @Override public boolean isEnabled() { return enabled; }
    }

    // Configuration using polymorphic interfaces
    public record TestConfigWithPolymorphic(
            String appName,
            TestDatabaseConfig database,
            List<TestDatabaseConfig> backupDatabases,
            TestPaymentProvider paymentProvider
    ) implements Loadable {}

    @Nested
    @DisplayName("Complex Type Conversions")
    class ComplexTypeConversionsTest {

        @Test
        @DisplayName("Should handle numeric type conversions with precision")
        void shouldHandleNumericConversions() {
            // Integer conversions from various sources
            assertEquals(42, valueConverter.convert("42", int.class, "test"));
            assertEquals(42, valueConverter.convert(42.0, int.class, "test"));
            assertEquals(42, valueConverter.convert(42L, int.class, "test"));

            // Long conversions
            assertEquals(123456789L, valueConverter.convert("123456789", long.class, "test"));
            assertEquals(123456789L, valueConverter.convert(123456789, long.class, "test"));

            // Double conversions
            assertEquals(3.14159, valueConverter.convert("3.14159", double.class, "test"));
            assertEquals(3.14159, valueConverter.convert(3.14159, double.class, "test"));

            // Boundary values
            assertEquals(Integer.MAX_VALUE, valueConverter.convert(String.valueOf(Integer.MAX_VALUE), int.class, "test"));
            assertEquals(Long.MIN_VALUE, valueConverter.convert(String.valueOf(Long.MIN_VALUE), long.class, "test"));
        }

        @Test
        @DisplayName("Should handle character conversions and edge cases")
        void shouldHandleCharacterConversions() {
            assertEquals('a', valueConverter.convert("a", char.class, "test"));
            assertEquals('Z', valueConverter.convert("Z", Character.class, "test"));

            // Should throw for multi-character strings
            StructuraException exception = assertThrows(StructuraException.class, () ->
                    valueConverter.convert("abc", char.class, "test")
            );
            assertTrue(exception.getMessage().contains("Cannot convert string of length"));
        }

        @Test
        @DisplayName("Should handle boolean conversion variations")
        void shouldHandleBooleanConversions() {
            // Various true representations
            assertTrue((Boolean) valueConverter.convert("true", boolean.class, "test"));
            assertTrue((Boolean) valueConverter.convert("TRUE", Boolean.class, "test"));
            assertTrue((Boolean) valueConverter.convert("True", boolean.class, "test"));
            assertTrue((Boolean) valueConverter.convert(true, boolean.class, "test"));

            // Various false representations
            assertFalse((Boolean) valueConverter.convert("false", boolean.class, "test"));
            assertFalse((Boolean) valueConverter.convert("FALSE", Boolean.class, "test"));
            assertFalse((Boolean) valueConverter.convert("anything_else", boolean.class, "test"));
        }
    }

    @Nested
    @DisplayName("Enum Conversion Logic")
    class EnumConversionTest {

        @Test
        @DisplayName("Should convert strings to enums with case handling")
        void shouldConvertStringsToEnums() {
            assertEquals(TestEnum.VALUE1, valueConverter.convert("VALUE1", TestEnum.class, "test"));
            assertEquals(TestEnum.VALUE2, valueConverter.convert("value2", TestEnum.class, "test"));
            assertEquals(TestEnum.VALUE3, valueConverter.convert("Value3", TestEnum.class, "test"));
        }

        @Test
        @DisplayName("Should provide detailed error messages for invalid enum values")
        void shouldProvideDetailedEnumErrorMessages() {
            StructuraException exception = assertThrows(StructuraException.class, () ->
                    valueConverter.convert("INVALID_VALUE", TestEnum.class, "test")
            );

            assertTrue(exception.getMessage().contains("Invalid enum value: INVALID_VALUE"));
            assertTrue(exception.getMessage().contains("Available values:"));
            assertTrue(exception.getMessage().contains("VALUE1, VALUE2, VALUE3"));
        }

        @Test
        @DisplayName("Should reject non-string enum conversions")
        void shouldRejectNonStringEnumConversions() {
            StructuraException exception = assertThrows(StructuraException.class, () ->
                    valueConverter.convert(123, TestEnum.class, "test")
            );
            assertTrue(exception.getMessage().contains("Cannot convert"));
        }
    }

    @Nested
    @DisplayName("Collection Type Conversions")
    class CollectionConversionsTest {

        @Test
        @DisplayName("Should convert to typed collections correctly")
        void shouldConvertToTypedCollections() throws Exception {
            // List<String> conversion
            Type listType = getClass().getMethod("getStringList").getGenericReturnType();
            List<String> inputList = List.of("a", "b", "c");

            @SuppressWarnings("unchecked")
            List<String> result = (List<String>) valueConverter.convert(inputList, listType, List.class, "test");
            assertEquals(List.of("a", "b", "c"), result);

            // Set<Integer> conversion with type conversion
            Type setType = getClass().getMethod("getIntegerSet").getGenericReturnType();
            List<String> stringNumbers = List.of("1", "2", "3", "1"); // Note duplicate

            @SuppressWarnings("unchecked")
            Set<Integer> integerSet = (Set<Integer>) valueConverter.convert(stringNumbers, setType, Set.class, "test");
            assertEquals(Set.of(1, 2, 3), integerSet); // Duplicates removed
        }

        @Test
        @DisplayName("Should handle single item to collection conversion")
        void shouldHandleSingleItemToCollectionConversion() throws Exception {
            Type listType = getClass().getMethod("getStringList").getGenericReturnType();

            // Single map converted to list containing that map
            @SuppressWarnings("unchecked")
            List<String> result = (List<String>) valueConverter.convert(
                    Map.of("key", "value"), listType, List.class, "test"
            );
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("Should throw for unsupported collection types")
        void shouldThrowForUnsupportedCollectionTypes() throws Exception {
            Type listType = getClass().getMethod("getStringList").getGenericReturnType();

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    valueConverter.convert(List.of("a"), listType, Collection.class, "test")
            );
            assertTrue(exception.getMessage().contains("Unsupported collection type"));
        }

        // Helper methods for reflection
        public List<String> getStringList() { return null; }
        public Set<Integer> getIntegerSet() { return null; }
    }

    @Nested
    @DisplayName("Map Type Conversions")
    class MapConversionsTest {

        @Test
        @DisplayName("Should convert maps with type conversion of keys and values")
        void shouldConvertMapsWithTypeConversion() throws Exception {
            Type mapType = getClass().getMethod("getStringIntegerMap").getGenericReturnType();
            Map<String, Object> input = Map.of(
                    "count", "42",
                    "total", "100",
                    "average", "50"
            );

            @SuppressWarnings("unchecked")
            Map<String, Integer> result = (Map<String, Integer>) valueConverter.convert(
                    input, mapType, Map.class, "test"
            );

            assertEquals(Map.of("count", 42, "total", 100, "average", 50), result);
        }

        @Test
        @DisplayName("Should validate map type parameters")
        void shouldValidateMapTypeParameters() {
            ParameterizedType invalidType = new ParameterizedType() {
                @Override
                public Type[] getActualTypeArguments() { return new Type[]{String.class}; } // Only 1 param
                @Override
                public Type getRawType() { return Map.class; }
                @Override
                public Type getOwnerType() { return null; }
            };

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    valueConverter.convert(Map.of(), invalidType, Map.class, "test")
            );
            assertTrue(exception.getMessage().contains("must have exactly 2 generic parameters"));
        }

        @Test
        @DisplayName("Should reject non-map input for map conversion")
        void shouldRejectNonMapInputForMapConversion() throws Exception {
            Type mapType = getClass().getMethod("getStringStringMap").getGenericReturnType();

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    valueConverter.convert("not a map", mapType, Map.class, "test")
            );
            assertTrue(exception.getMessage().contains("Cannot convert"));
        }

        // Helper methods
        public Map<String, String> getStringStringMap() { return null; }
        public Map<String, Integer> getStringIntegerMap() { return null; }
    }

    @Nested
    @DisplayName("Polymorphic Type Conversions")
    class PolymorphicTypeConversionsTest {

        @Test
        @DisplayName("Should convert polymorphic interface to concrete implementation")
        void shouldConvertPolymorphicInterfaceToConcrete() {
            Map<String, Object> mysqlData = Map.of(
                    "type", "mysql",
                    "host", "prod.mysql.com",
                    "port", 3306,
                    "driver", "com.mysql.cj.jdbc.Driver"
            );

            TestDatabaseConfig result = (TestDatabaseConfig) valueConverter.convert(
                    mysqlData, TestDatabaseConfig.class, ""
            );

            assertInstanceOf(TestMySQLConfig.class, result);
            TestMySQLConfig mysql = (TestMySQLConfig) result;
            assertEquals("prod.mysql.com", mysql.host());
            assertEquals(3306, mysql.port());
            assertEquals("com.mysql.cj.jdbc.Driver", mysql.driver());
        }

        @Test
        @DisplayName("Should handle different polymorphic key names")
        void shouldHandleDifferentPolymorphicKeyNames() {
            Map<String, Object> stripeData = Map.of(
                    "provider", "stripe", // Different key name
                    "name", "Production Stripe",
                    "enabled", true,
                    "api-key", "sk_live_123"
            );

            TestPaymentProvider result = (TestPaymentProvider) valueConverter.convert(
                    stripeData, TestPaymentProvider.class, ""
            );

            assertInstanceOf(TestStripeProvider.class, result);
            TestStripeProvider stripe = (TestStripeProvider) result;
            assertEquals("Production Stripe", stripe.name());
            assertTrue(stripe.enabled());
            assertEquals("sk_live_123", stripe.apiKey());
        }

        @Test
        @DisplayName("Should use default values in polymorphic implementations")
        void shouldUseDefaultValuesInPolymorphicImplementations() {
            Map<String, Object> minimalData = Map.of(
                    "type", "postgres",
                    "host", "custom.postgres.com"
            );

            TestDatabaseConfig result = (TestDatabaseConfig) valueConverter.convert(
                    minimalData, TestDatabaseConfig.class, ""
            );

            assertInstanceOf(TestPostgreSQLConfig.class, result);
            TestPostgreSQLConfig postgres = (TestPostgreSQLConfig) result;
            assertEquals("custom.postgres.com", postgres.host());
            assertEquals(5432, postgres.port()); // Default value
            assertEquals("postgresql", postgres.driver()); // Default value
        }

        @Test
        @DisplayName("Should convert collections of polymorphic interfaces")
        void shouldConvertCollectionsOfPolymorphicInterfaces() throws Exception {
            List<Map<String, Object>> databaseList = List.of(
                    Map.of("type", "mysql", "host", "mysql1.com", "port", 3306),
                    Map.of("type", "postgres", "host", "postgres1.com", "port", 5432)
            );

            Type listType = getClass().getMethod("getDatabaseConfigList").getGenericReturnType();

            @SuppressWarnings("unchecked")
            List<TestDatabaseConfig> result = (List<TestDatabaseConfig>) valueConverter.convert(
                    databaseList, listType, List.class, ""
            );

            assertEquals(2, result.size());
            assertInstanceOf(TestMySQLConfig.class, result.get(0));
            assertInstanceOf(TestPostgreSQLConfig.class, result.get(1));

            TestMySQLConfig mysql = (TestMySQLConfig) result.get(0);
            assertEquals("mysql1.com", mysql.host());
            assertEquals(3306, mysql.port());

            TestPostgreSQLConfig postgres = (TestPostgreSQLConfig) result.get(1);
            assertEquals("postgres1.com", postgres.host());
            assertEquals(5432, postgres.port());
        }

        @Test
        @DisplayName("Should throw exception for missing polymorphic key")
        void shouldThrowExceptionForMissingPolymorphicKey() {
            Map<String, Object> dataWithoutType = Map.of(
                    "host", "some.host.com",
                    "port", 3306
            );

            StructuraException exception = assertThrows(StructuraException.class, () -> {
                valueConverter.convert(dataWithoutType, TestDatabaseConfig.class, "test");
            });

            assertTrue(exception.getMessage().contains("requires key 'type'"));
        }

        @Test
        @DisplayName("Should throw exception for unknown polymorphic type")
        void shouldThrowExceptionForUnknownPolymorphicType() {
            Map<String, Object> dataWithUnknownType = Map.of(
                    "type", "oracle",
                    "host", "oracle.host.com"
            );

            StructuraException exception = assertThrows(StructuraException.class, () -> {
                valueConverter.convert(dataWithUnknownType, TestDatabaseConfig.class, "test");
            });

            assertTrue(exception.getMessage().contains("No registered type found for oracle"));
            assertTrue(exception.getMessage().contains("Available types: mysql, postgres"));
        }

        @Test
        @DisplayName("Should throw exception for non-Map polymorphic data")
        void shouldThrowExceptionForNonMapPolymorphicData() {
            StructuraException exception = assertThrows(StructuraException.class, () -> {
                valueConverter.convert("invalid-string", TestDatabaseConfig.class, "test");
            });

            assertTrue(exception.getMessage().contains("requires a Map value for conversion"));
        }

        // Helper method for reflection
        public List<TestDatabaseConfig> getDatabaseConfigList() { return null; }
    }

    @Nested
    @DisplayName("Record and Edge Cases")
    class RecordAndEdgeCasesTest {

        @Test
        @DisplayName("Should convert maps to records")
        void shouldConvertMapsToRecords() {
            Map<String, Object> input = Map.of("name", "test", "value", 42);

            SimpleRecord result = (SimpleRecord) valueConverter.convert(input, SimpleRecord.class, "");

            assertEquals("test", result.name());
            assertEquals(42, result.value());
        }

        @Test
        @DisplayName("Should convert maps to records with defaults for missing fields")
        void shouldConvertMapsToRecordsWithDefaults() {
            Map<String, Object> inputPartial = Map.of("name", "partialTest");

            RecordWithDefaults result = (RecordWithDefaults) valueConverter.convert(inputPartial, RecordWithDefaults.class, "");

            assertEquals("partialTest", result.name());
            assertEquals(0, result.value()); // Default value
        }

        @Test
        @DisplayName("Should handle null values gracefully")
        void shouldHandleNullValues() {
            assertNull(valueConverter.convert(null, String.class, "test"));
            assertNull(valueConverter.convert(null, int.class, "test"));
            assertNull(valueConverter.convert(null, List.class, "test"));
        }

        @Test
        @DisplayName("Should return same object for compatible types")
        void shouldReturnSameObjectForCompatibleTypes() {
            String input = "test";
            String result = (String) valueConverter.convert(input, String.class, "test");
            assertSame(input, result);

            Integer intInput = 42;
            Integer intResult = (Integer) valueConverter.convert(intInput, Integer.class, "test");
            assertSame(intInput, intResult);
        }

        @Test
        @DisplayName("Should handle unsupported type conversions gracefully")
        void shouldHandleUnsupportedTypeConversions() {
            // This should throw a clear exception for unsupported types
            StructuraException exception = assertThrows(StructuraException.class, () ->
                    valueConverter.convert("invalid", Thread.class, "test")
            );

            assertTrue(exception.getMessage().contains("Unsupported conversion"));
            assertTrue(exception.getMessage().contains("Thread"));
        }

        @Test
        @DisplayName("Should throw exception for invalid numeric conversions")
        void shouldThrowExceptionForInvalidNumericConversions() {
            // This should definitely throw an exception
            assertThrows(Exception.class, () ->
                    valueConverter.convert("not_a_number", int.class, "test")
            );

            assertThrows(Exception.class, () ->
                    valueConverter.convert("not_a_double", double.class, "test")
            );
        }

        @Test
        @DisplayName("Should handle complex nested polymorphic structures")
        void shouldHandleComplexNestedPolymorphicStructures() {
            Map<String, Object> complexConfig = Map.of(
                    "app-name", "ComplexApp",
                    "database", Map.of(
                            "type", "mysql",
                            "host", "primary.mysql.com",
                            "port", 3306
                    ),
                    "backup-databases", List.of(
                            Map.of("type", "postgres", "host", "backup1.postgres.com"),
                            Map.of("type", "mysql", "host", "backup2.mysql.com", "port", 3307)
                    ),
                    "payment-provider", Map.of(
                            "provider", "stripe",
                            "name", "Production Stripe",
                            "enabled", true
                    )
            );

            TestConfigWithPolymorphic result = (TestConfigWithPolymorphic) valueConverter.convert(
                    complexConfig, TestConfigWithPolymorphic.class, ""
            );

            assertEquals("ComplexApp", result.appName());

            // Verify main database
            assertInstanceOf(TestMySQLConfig.class, result.database());
            TestMySQLConfig primaryDb = (TestMySQLConfig) result.database();
            assertEquals("primary.mysql.com", primaryDb.host());

            // Verify backup databases
            assertEquals(2, result.backupDatabases().size());
            assertInstanceOf(TestPostgreSQLConfig.class, result.backupDatabases().get(0));
            assertInstanceOf(TestMySQLConfig.class, result.backupDatabases().get(1));

            // Verify payment provider
            assertInstanceOf(TestStripeProvider.class, result.paymentProvider());
            TestStripeProvider stripe = (TestStripeProvider) result.paymentProvider();
            assertEquals("Production Stripe", stripe.name());
            assertTrue(stripe.enabled());
        }
    }
}