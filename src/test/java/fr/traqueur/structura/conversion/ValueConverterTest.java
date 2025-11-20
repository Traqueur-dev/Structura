package fr.traqueur.structura.conversion;

import fr.traqueur.structura.exceptions.StructuraException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static fr.traqueur.structura.fixtures.TestModels.*;
import static fr.traqueur.structura.helpers.TestHelpers.*;
import static fr.traqueur.structura.helpers.PolymorphicTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Refactored ValueConverter tests using common fixtures and helpers.
 * Tests complete type conversion logic including primitives, collections, maps, and polymorphic types.
 */
@DisplayName("ValueConverter - Refactored Tests")
class ValueConverterTest {

    private ValueConverter valueConverter;

    @BeforeAll
    static void setUpRegistries() {
        clearAllRegistries();
    }

    @BeforeEach
    void setUp() {
        clearAllRegistries();
        valueConverter = createValueConverter();
        setupTestDatabaseConfigRegistry();
        setupTestPaymentProviderRegistry();
    }

    // Helper methods for reflection-based generic type testing
    public List<String> getStringList() { return null; }
    public Set<Integer> getIntegerSet() { return null; }
    public Map<String, String> getStringStringMap() { return null; }
    public Map<String, Integer> getStringIntegerMap() { return null; }
    public List<TestDatabaseConfig> getDatabaseConfigList() { return null; }

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


        @Test
        @DisplayName("Should handle LocalDate conversion from string")
        void shouldHandleLocalDateConversion() {
            var localDate = java.time.LocalDate.of(2023, 10, 5);
            assertEquals(localDate, valueConverter.convert("2023-10-05", java.time.LocalDate.class, "test"));
        }

        @Test
        @DisplayName("Should handle LocalDateTime conversion from string")
        void shouldHandleLocalDateTimeConversion() {
            var localDate = LocalDateTime.of(2023, 10, 5, 14, 30, 0);
            assertEquals(localDate, valueConverter.convert("2023-10-05T14:30:00", LocalDateTime.class, "test"));
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

            assertContainsAll(exception.getMessage(),
                    "Invalid enum value: INVALID_VALUE",
                    "Available values:",
                    "VALUE1, VALUE2, VALUE3");
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
            Type listType = ValueConverterTest.class.getMethod("getStringList").getGenericReturnType();
            List<String> inputList = List.of("a", "b", "c");

            @SuppressWarnings("unchecked")
            List<String> result = (List<String>) valueConverter.convert(inputList, listType, List.class, "test");
            assertEquals(List.of("a", "b", "c"), result);

            // Set<Integer> conversion with type conversion
            Type setType = ValueConverterTest.class.getMethod("getIntegerSet").getGenericReturnType();
            List<String> stringNumbers = List.of("1", "2", "3", "1"); // Note duplicate

            @SuppressWarnings("unchecked")
            Set<Integer> integerSet = (Set<Integer>) valueConverter.convert(stringNumbers, setType, Set.class, "test");
            assertEquals(Set.of(1, 2, 3), integerSet); // Duplicates removed
        }

        @Test
        @DisplayName("Should handle single item to collection conversion")
        void shouldHandleSingleItemToCollectionConversion() throws Exception {
            Type listType = ValueConverterTest.class.getMethod("getStringList").getGenericReturnType();

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
            Type listType = ValueConverterTest.class.getMethod("getStringList").getGenericReturnType();

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    valueConverter.convert(List.of("a"), listType, Collection.class, "test")
            );
            assertTrue(exception.getMessage().contains("Unsupported collection type"));
        }
    }

    @Nested
    @DisplayName("Map Type Conversions")
    class MapConversionsTest {

        @Test
        @DisplayName("Should convert maps with type conversion of keys and values")
        void shouldConvertMapsWithTypeConversion() throws Exception {
            Type mapType = ValueConverterTest.class.getMethod("getStringIntegerMap").getGenericReturnType();
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
                public Type[] getActualTypeArguments() { return new Type[]{String.class}; }
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
            Type mapType = ValueConverterTest.class.getMethod("getStringStringMap").getGenericReturnType();

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    valueConverter.convert("not a map", mapType, Map.class, "test")
            );
            assertTrue(exception.getMessage().contains("Cannot convert"));
        }
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
                    "provider", "stripe",
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
            assertEquals(5432, postgres.port()); // Default
            assertEquals("postgresql", postgres.driver()); // Default
        }

        @Test
        @DisplayName("Should convert collections of polymorphic interfaces")
        void shouldConvertCollectionsOfPolymorphicInterfaces() throws Exception {
            List<Map<String, Object>> databaseList = List.of(
                    Map.of("type", "mysql", "host", "mysql1.com", "port", 3306),
                    Map.of("type", "postgres", "host", "postgres1.com", "port", 5432)
            );

            Type listType = ValueConverterTest.class.getMethod("getDatabaseConfigList").getGenericReturnType();

            @SuppressWarnings("unchecked")
            List<TestDatabaseConfig> result = (List<TestDatabaseConfig>) valueConverter.convert(
                    databaseList, listType, List.class, ""
            );

            assertCollectionSize(2, result, "database list");
            assertInstanceOf(TestMySQLConfig.class, result.get(0));
            assertInstanceOf(TestPostgreSQLConfig.class, result.get(1));

            assertEquals("mysql1.com", result.get(0).getHost());
            assertEquals("postgres1.com", result.get(1).getHost());
        }

        @Test
        @DisplayName("Should throw exception for missing polymorphic key")
        void shouldThrowExceptionForMissingPolymorphicKey() {
            Map<String, Object> dataWithoutType = Map.of(
                    "host", "some.host.com",
                    "port", 3306
            );

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    valueConverter.convert(dataWithoutType, TestDatabaseConfig.class, "test")
            );

            assertTrue(exception.getMessage().contains("requires key 'type'"));
        }

        @Test
        @DisplayName("Should throw exception for unknown polymorphic type")
        void shouldThrowExceptionForUnknownPolymorphicType() {
            Map<String, Object> dataWithUnknownType = Map.of(
                    "type", "oracle",
                    "host", "oracle.host.com"
            );

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    valueConverter.convert(dataWithUnknownType, TestDatabaseConfig.class, "test")
            );

            assertContainsAll(exception.getMessage(),
                    "No registered type found for oracle",
                    "Available types: mysql, postgres");
        }

        @Test
        @DisplayName("Should throw exception for non-Map polymorphic data")
        void shouldThrowExceptionForNonMapPolymorphicData() {
            StructuraException exception = assertThrows(StructuraException.class, () ->
                    valueConverter.convert("invalid-string", TestDatabaseConfig.class, "test")
            );

            assertTrue(exception.getMessage().contains("requires a Map value for conversion"));
        }
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
            StructuraException exception = assertThrows(StructuraException.class, () ->
                    valueConverter.convert("invalid", Thread.class, "test")
            );

            assertContainsAll(exception.getMessage(), "Unsupported conversion", "Thread");
        }

        @Test
        @DisplayName("Should throw exception for invalid numeric conversions")
        void shouldThrowExceptionForInvalidNumericConversions() {
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
            assertEquals("primary.mysql.com", result.database().getHost());

            // Verify backup databases
            assertCollectionSize(2, result.backupDatabases(), "backup databases");
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