package fr.traqueur.structura.conversion;

import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.factory.RecordInstanceFactory;
import fr.traqueur.structura.mapping.FieldMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ValueConverter Tests")
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
    
    public enum TestEnum { VALUE1, VALUE2, VALUE3 }

    @Nested
    @DisplayName("Primitive Conversion Tests")
    class PrimitiveConversionTest {

        @Test
        @DisplayName("Should convert strings")
        void shouldConvertStrings() {
            assertEquals("test", valueConverter.convert("test", String.class, ""));
            assertEquals("123", valueConverter.convert(123, String.class, ""));
        }

        @ParameterizedTest
        @ValueSource(strings = {"42", "0", "-10"})
        @DisplayName("Should convert to integers")
        void shouldConvertToIntegers(String input) {
            int expected = Integer.parseInt(input);
            assertEquals(expected, valueConverter.convert(input, int.class, ""));
            assertEquals(expected, valueConverter.convert(input, Integer.class, ""));
        }

        @ParameterizedTest
        @ValueSource(strings = {"true", "false", "TRUE", "FALSE", "True", "False"})
        @DisplayName("Should convert to booleans")
        void shouldConvertToBooleans(String input) {
            boolean expected = Boolean.parseBoolean(input);
            assertEquals(expected, valueConverter.convert(input, boolean.class, ""));
            assertEquals(expected, valueConverter.convert(input, Boolean.class, ""));
        }

        @ParameterizedTest
        @ValueSource(strings = {"123456789", "0", "-987654321"})
        @DisplayName("Should convert to longs")
        void shouldConvertToLongs(String input) {
            long expected = Long.parseLong(input);
            assertEquals(expected, valueConverter.convert(input, long.class, ""));
            assertEquals(expected, valueConverter.convert(input, Long.class, ""));
        }

        @ParameterizedTest
        @ValueSource(strings = {"3.14", "0.0", "-2.5"})
        @DisplayName("Should convert to doubles")
        void shouldConvertToDoubles(String input) {
            double expected = Double.parseDouble(input);
            assertEquals(expected, valueConverter.convert(input, double.class, ""));
            assertEquals(expected, valueConverter.convert(input, Double.class, ""));
        }

        @Test
        @DisplayName("Should convert to chars")
        void shouldConvertToChars() {
            assertEquals('a', valueConverter.convert("a", char.class, ""));
            assertEquals('A', valueConverter.convert("A", Character.class, ""));
        }

        @Test
        @DisplayName("Should throw exception for invalid char conversion")
        void shouldThrowForInvalidCharConversion() {
            assertThrows(StructuraException.class, () -> {
                valueConverter.convert("abc", char.class, "");
            });
        }
    }

    @Nested
    @DisplayName("Enum Conversion Tests")
    class EnumConversionTest {

        @Test
        @DisplayName("Should convert strings to enums")
        void shouldConvertStringsToEnums() {
            assertEquals(TestEnum.VALUE1, valueConverter.convert("VALUE1", TestEnum.class, ""));
            assertEquals(TestEnum.VALUE2, valueConverter.convert("value2", TestEnum.class, ""));
            assertEquals(TestEnum.VALUE3, valueConverter.convert("VALUE3", TestEnum.class, ""));
        }

        @Test
        @DisplayName("Should throw exception for invalid enum values")
        void shouldThrowForInvalidEnumValues() {
            StructuraException exception = assertThrows(StructuraException.class, () -> {
                valueConverter.convert("INVALID", TestEnum.class, "");
            });
            assertTrue(exception.getMessage().contains("Invalid enum value"));
            assertTrue(exception.getMessage().contains("Available values"));
        }

        @Test
        @DisplayName("Should throw exception for non-string enum conversion")
        void shouldThrowForNonStringEnumConversion() {
            assertThrows(StructuraException.class, () -> {
                valueConverter.convert(123, TestEnum.class, "");
            });
        }
    }

    @Nested
    @DisplayName("Collection Conversion Tests")
    class CollectionConversionTest {

        @Test
        @DisplayName("Should convert to List<String>")
        void shouldConvertToListOfStrings() throws Exception {
            Type listType = getClass().getMethod("getStringList").getGenericReturnType();
            List<String> input = List.of("a", "b", "c");
            
            @SuppressWarnings("unchecked")
            List<String> result = (List<String>) valueConverter.convert(input, listType, List.class, "");
            
            assertEquals(List.of("a", "b", "c"), result);
        }

        @Test
        @DisplayName("Should convert to Set<Integer>")
        void shouldConvertToSetOfIntegers() throws Exception {
            Type setType = getClass().getMethod("getIntegerSet").getGenericReturnType();
            List<String> input = List.of("1", "2", "3");
            
            @SuppressWarnings("unchecked")
            Set<Integer> result = (Set<Integer>) valueConverter.convert(input, setType, Set.class, "");
            
            assertEquals(Set.of(1, 2, 3), result);
        }

        @Test
        @DisplayName("Should handle empty collections")
        void shouldHandleEmptyCollections() throws Exception {
            Type listType = getClass().getMethod("getStringList").getGenericReturnType();
            List<String> input = List.of();
            
            @SuppressWarnings("unchecked")
            List<String> result = (List<String>) valueConverter.convert(input, listType, List.class, "");
            
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should throw exception for unsupported collection type")
        void shouldThrowForUnsupportedCollectionType() throws Exception {
            Type listType = getClass().getMethod("getStringList").getGenericReturnType();
            List<String> input = List.of("a", "b");
            
            // Test with an unsupported collection interface
            assertThrows(StructuraException.class, () -> {
                valueConverter.convert(input, listType, Collection.class, "");
            });
        }

        // Helper methods for reflection
        public List<String> getStringList() { return null; }
        public Set<Integer> getIntegerSet() { return null; }
    }

    @Nested
    @DisplayName("Map Conversion Tests")  
    class MapConversionTest {

        @Test
        @DisplayName("Should convert to Map<String, String>")
        void shouldConvertToStringStringMap() throws Exception {
            Type mapType = getClass().getMethod("getStringStringMap").getGenericReturnType();
            Map<String, Object> input = Map.of("key1", "value1", "key2", "value2");
            
            @SuppressWarnings("unchecked")
            Map<String, String> result = (Map<String, String>) valueConverter.convert(input, mapType, Map.class, "");
            
            assertEquals(Map.of("key1", "value1", "key2", "value2"), result);
        }

        @Test
        @DisplayName("Should convert to Map<String, Integer>")
        void shouldConvertToStringIntegerMap() throws Exception {
            Type mapType = getClass().getMethod("getStringIntegerMap").getGenericReturnType();
            Map<String, Object> input = Map.of("count", "42", "total", "100");
            
            @SuppressWarnings("unchecked")
            Map<String, Integer> result = (Map<String, Integer>) valueConverter.convert(input, mapType, Map.class, "");
            
            assertEquals(Map.of("count", 42, "total", 100), result);
        }

        @Test
        @DisplayName("Should throw exception for non-map input")
        void shouldThrowForNonMapInput() throws Exception {
            Type mapType = getClass().getMethod("getStringStringMap").getGenericReturnType();
            
            assertThrows(StructuraException.class, () -> {
                valueConverter.convert("not a map", mapType, Map.class, "");
            });
        }

        @Test
        @DisplayName("Should throw exception for wrong number of type parameters")
        void shouldThrowForWrongTypeParameters() {
            ParameterizedType invalidType = new ParameterizedType() {
                @Override
                public Type[] getActualTypeArguments() {
                    return new Type[]{String.class};
                }
                @Override
                public Type getRawType() { return Map.class; }
                @Override
                public Type getOwnerType() { return null; }
            };
            
            assertThrows(StructuraException.class, () -> {
                valueConverter.convert(Map.of(), invalidType, Map.class, "");
            });
        }

        // Helper methods for reflection
        public Map<String, String> getStringStringMap() { return null; }
        public Map<String, Integer> getStringIntegerMap() { return null; }
    }

    @Nested
    @DisplayName("Record Conversion Tests")
    class RecordConversionTest {

        @Test
        @DisplayName("Should convert Map to record")
        void shouldConvertMapToRecord() {
            Map<String, Object> input = Map.of("name", "test", "value", 42);
            
            SimpleRecord result = (SimpleRecord) valueConverter.convert(input, SimpleRecord.class, "");
            
            assertEquals("test", result.name());
            assertEquals(42, result.value());
        }

        @Test
        @DisplayName("Should handle null values")
        void shouldHandleNullValues() {
            assertNull(valueConverter.convert(null, String.class, ""));
        }

        @Test
        @DisplayName("Should return same value for compatible types")
        void shouldReturnSameValueForCompatibleTypes() {
            String input = "test";
            String result = (String) valueConverter.convert(input, String.class, "");
            assertSame(input, result);
        }
    }
}