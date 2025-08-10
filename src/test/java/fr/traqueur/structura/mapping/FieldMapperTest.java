package fr.traqueur.structura.mapping;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.api.Loadable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FieldMapper Tests")
class FieldMapperTest {

    private FieldMapper fieldMapper;

    @BeforeEach
    void setUp() {
        fieldMapper = new FieldMapper();
    }

    @Nested
    @DisplayName("Name Conversion Tests")
    class NameConversionTest {

        @ParameterizedTest
        @ValueSource(strings = {
                "appName,app-name",
                "databaseUrl,database-url",
                "enableHttps,enable-https",
                "maxRetryCount,max-retry-count",
                "a,a",
                "simple,simple"
        })
        @DisplayName("Should convert camelCase to kebab-case")
        void shouldConvertCamelCaseToKebabCase(String input) {
            String[] parts = input.split(",");
            String camelCase = parts[0];
            String expected = parts[1];

            String result = fieldMapper.convertCamelCaseToKebabCase(camelCase);
            assertEquals(expected, result);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "MYSQL_DATABASE,mysql-database",
                "USER_NAME,user-name",
                "API_KEY,api-key",
                "HTTP_PORT,http-port",
                "SIMPLE,simple"
        })
        @DisplayName("Should convert snake_case to kebab-case")
        void shouldConvertSnakeCaseToKebabCase(String input) {
            String[] parts = input.split(",");
            String snakeCase = parts[0];
            String expected = parts[1];

            String result = fieldMapper.convertSnakeCaseToKebabCase(snakeCase);
            assertEquals(expected, result);
        }
    }

    @Nested
    @DisplayName("Path Operations Tests")
    class PathOperationsTest {

        @Test
        @DisplayName("Should build paths correctly")
        void shouldBuildPaths() {
            assertEquals("field", fieldMapper.buildPath("", "field"));
            assertEquals("prefix.field", fieldMapper.buildPath("prefix", "field"));
            assertEquals("deep.nested.field", fieldMapper.buildPath("deep.nested", "field"));
        }

        @Test
        @DisplayName("Should navigate through nested data")
        void shouldNavigateThroughNestedData() {
            Map<String, Object> data = Map.of(
                    "level1", Map.of(
                            "level2", Map.of(
                                    "field", "value"
                            )
                    )
            );

            Object result = fieldMapper.getValueFromPath(data, "level1.level2.field");
            assertEquals("value", result);
        }

        @Test
        @DisplayName("Should return null for non-existent paths")
        void shouldReturnNullForNonExistentPaths() {
            Map<String, Object> data = Map.of("existing", "value");

            assertNull(fieldMapper.getValueFromPath(data, "nonexistent"));
            assertNull(fieldMapper.getValueFromPath(data, "existing.nested"));
        }
    }

    // Test records for reflection tests
    public record TestRecord(
            String normalField,
            @Options(name = "custom-name") String customField,
            @Options(optional = true) String optionalField
    ) implements Loadable {}

    public record KeyRecord(
            @Options(isKey = true) String id,
            String value
    ) implements Loadable {}

    public record ComplexKeyRecord(
            @Options(isKey = true) TestRecord complex,
            String other
    ) implements Loadable {}

    // Record without key for testing
    public record NoKeyRecord(
            String field1,
            String field2
    ) implements Loadable {}

    @Nested
    @DisplayName("Record Analysis Tests")
    class RecordAnalysisTest {

        @Test
        @DisplayName("Should find key components")
        void shouldFindKeyComponents() {
            RecordComponent[] components = KeyRecord.class.getRecordComponents();
            RecordComponent keyComponent = fieldMapper.findKeyComponent(components);

            assertNotNull(keyComponent);
            assertEquals("id", keyComponent.getName());
        }

        @Test
        @DisplayName("Should return null when no key component exists")
        void shouldReturnNullWhenNoKeyComponent() {
            RecordComponent[] components = NoKeyRecord.class.getRecordComponents();
            RecordComponent keyComponent = fieldMapper.findKeyComponent(components);
            assertNull(keyComponent);
        }

        @Test
        @DisplayName("Should identify simple key mapping")
        void shouldIdentifySimpleKeyMapping() {
            RecordComponent[] components = KeyRecord.class.getRecordComponents();
            RecordComponent keyComponent = fieldMapper.findKeyComponent(components);

            Map<String, Object> simpleData = Map.of("key", Map.of("value", "test"));
            assertTrue(fieldMapper.isSimpleKeyMapping(simpleData, keyComponent));

            Map<String, Object> complexData = Map.of(
                    "field1", "value1",
                    "field2", "value2"
            );
            assertFalse(fieldMapper.isSimpleKeyMapping(complexData, keyComponent));
        }

        @Test
        @DisplayName("Should identify complex key mapping correctly")
        void shouldIdentifyComplexKeyMapping() {
            RecordComponent[] components = ComplexKeyRecord.class.getRecordComponents();
            RecordComponent keyComponent = fieldMapper.findKeyComponent(components);

            assertNotNull(keyComponent);
            assertEquals("complex", keyComponent.getName());

            // Complex key mapping: multiple fields, complex key type (record)
            Map<String, Object> complexData = Map.of(
                    "normal-field", "value1",
                    "custom-name", "value2",
                    "other", "value3"
            );
            assertFalse(fieldMapper.isSimpleKeyMapping(complexData, keyComponent));
        }

        @Test
        @DisplayName("Should get field names for complex key record")
        void shouldGetFieldNamesForComplexKeyRecord() {
            Set<String> fieldNames = fieldMapper.getRecordFieldNames(ComplexKeyRecord.class);

            // ComplexKeyRecord has only 2 direct fields: complex and other
            assertTrue(fieldNames.contains("complex"));
            assertTrue(fieldNames.contains("other"));
            assertEquals(2, fieldNames.size());
        }

        @Test
        @DisplayName("Should get field names for nested record used as key")
        void shouldGetFieldNamesForNestedRecordUsedAsKey() {
            Set<String> fieldNames = fieldMapper.getRecordFieldNames(TestRecord.class);

            // TestRecord has 3 fields with custom names
            assertTrue(fieldNames.contains("normal-field"));
            assertTrue(fieldNames.contains("custom-name"));
            assertTrue(fieldNames.contains("optional-field"));
            assertEquals(3, fieldNames.size());
        }

        @Test
        @DisplayName("Should get record field names")
        void shouldGetRecordFieldNames() {
            Set<String> fieldNames = fieldMapper.getRecordFieldNames(TestRecord.class);

            assertTrue(fieldNames.contains("normal-field"));
            assertTrue(fieldNames.contains("custom-name"));
            assertTrue(fieldNames.contains("optional-field"));
            assertEquals(3, fieldNames.size());
        }

        @Test
        @DisplayName("Should handle empty record")
        void shouldHandleEmptyRecord() {
            Set<String> fieldNames = fieldMapper.getRecordFieldNames(String.class); // Not a record
            assertTrue(fieldNames.isEmpty());
        }
    }
}