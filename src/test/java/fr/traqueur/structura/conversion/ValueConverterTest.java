package fr.traqueur.structura.conversion;

import fr.traqueur.structura.annotations.defaults.DefaultInt;
import fr.traqueur.structura.annotations.defaults.DefaultString;

import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.factory.RecordInstanceFactory;
import fr.traqueur.structura.mapping.FieldMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ValueConverter - Type Conversion Logic Tests")
class ValueConverterTest {

    private ValueConverter valueConverter;

    @BeforeEach
    void setUp() {
        FieldMapper fieldMapper = new FieldMapper();
        RecordInstanceFactory recordFactory = new RecordInstanceFactory(fieldMapper);
        valueConverter = new ValueConverter(recordFactory);
        recordFactory.setValueConverter(valueConverter);
    }

    public record SimpleRecord(String name, int value) implements Loadable {}
    public record RecordWithDefaults(
            @DefaultString("defaultName") String name,
            @DefaultInt(0) int value
    ) implements Loadable {}
    public enum TestEnum { VALUE1, VALUE2, VALUE3 }

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
    }
}