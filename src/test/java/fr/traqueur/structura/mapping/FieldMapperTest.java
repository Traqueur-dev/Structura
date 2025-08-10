package fr.traqueur.structura.mapping;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.api.Loadable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FieldMapper - Name Conversion and Key Logic Tests")
class FieldMapperTest {

    private FieldMapper fieldMapper;

    @BeforeEach
    void setUp() {
        fieldMapper = new FieldMapper();
    }

    // Test records
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

    @Nested
    @DisplayName("Name Conversion Logic")
    class NameConversionTest {

        @ParameterizedTest
        @CsvSource({
                "appName,app-name",
                "databaseUrl,database-url",
                "enableHttps,enable-https",
                "maxRetryCount,max-retry-count",
                "simpleField,simple-field",
                "a,a",
                "APIKey,apikey"
        })
        @DisplayName("Should convert camelCase to kebab-case correctly")
        void shouldConvertCamelCaseToKebabCase(String camelCase, String expected) {
            assertEquals(expected, fieldMapper.convertCamelCaseToKebabCase(camelCase));
        }

        @ParameterizedTest
        @CsvSource({
                "MYSQL_DATABASE,mysql-database",
                "USER_NAME,user-name",
                "API_KEY,api-key",
                "HTTP_PORT,http-port",
                "SIMPLE,simple"
        })
        @DisplayName("Should convert snake_case to kebab-case correctly")
        void shouldConvertSnakeCaseToKebabCase(String snakeCase, String expected) {
            assertEquals(expected, fieldMapper.convertSnakeCaseToKebabCase(snakeCase));
        }

        @Test
        @DisplayName("Should respect custom field names from @Options annotation")
        void shouldRespectCustomFieldNames() throws Exception {
            var constructor = TestRecord.class.getDeclaredConstructors()[0];
            var parameters = constructor.getParameters();

            // Normal field without annotation
            String normalName = fieldMapper.getEffectiveFieldName(parameters[0], "normalField");
            assertEquals("normal-field", normalName);

            // Field with custom name
            String customName = fieldMapper.getEffectiveFieldName(parameters[1], "customField");
            assertEquals("custom-name", customName);

            // Optional field without custom name
            String optionalName = fieldMapper.getEffectiveFieldName(parameters[2], "optionalField");
            assertEquals("optional-field", optionalName);
        }
    }

    @Nested
    @DisplayName("Key Component Detection")
    class KeyComponentDetectionTest {

        @Test
        @DisplayName("Should identify key components correctly")
        void shouldIdentifyKeyComponents() {
            RecordComponent[] keyComponents = KeyRecord.class.getRecordComponents();
            RecordComponent keyComponent = fieldMapper.findKeyComponent(keyComponents);

            assertNotNull(keyComponent);
            assertEquals("id", keyComponent.getName());

            RecordComponent[] noKeyComponents = TestRecord.class.getRecordComponents();
            RecordComponent noKeyComponent = fieldMapper.findKeyComponent(noKeyComponents);
            assertNull(noKeyComponent);
        }

        @Test
        @DisplayName("Should distinguish simple vs complex key mapping")
        void shouldDistinguishSimpleVsComplexKeyMapping() {
            RecordComponent[] keyComponents = KeyRecord.class.getRecordComponents();
            RecordComponent keyComponent = fieldMapper.findKeyComponent(keyComponents);

            // Simple key mapping: single key with primitive type
            Map<String, Object> simpleData = Map.of("key", Map.of("value", "test"));
            assertTrue(fieldMapper.isSimpleKeyMapping(simpleData, keyComponent));

            // Complex key mapping: multiple fields
            Map<String, Object> complexData = Map.of(
                    "field1", "value1",
                    "field2", "value2"
            );
            assertFalse(fieldMapper.isSimpleKeyMapping(complexData, keyComponent));
        }

        @Test
        @DisplayName("Should handle complex record key mapping")
        void shouldHandleComplexRecordKeyMapping() {
            RecordComponent[] components = ComplexKeyRecord.class.getRecordComponents();
            RecordComponent keyComponent = fieldMapper.findKeyComponent(components);

            assertNotNull(keyComponent);
            assertEquals("complex", keyComponent.getName());

            // Complex key with record type should always be complex mapping
            Map<String, Object> data = Map.of("single", "value");
            assertFalse(fieldMapper.isSimpleKeyMapping(data, keyComponent));
        }
    }

    @Nested
    @DisplayName("Record Field Analysis")
    class RecordFieldAnalysisTest {

        @Test
        @DisplayName("Should extract all field names with proper conversion")
        void shouldExtractFieldNamesWithConversion() {
            Set<String> fieldNames = fieldMapper.getRecordFieldNames(TestRecord.class);

            assertEquals(3, fieldNames.size());
            assertTrue(fieldNames.contains("normal-field"));
            assertTrue(fieldNames.contains("custom-name"));
            assertTrue(fieldNames.contains("optional-field"));
        }

        @Test
        @DisplayName("Should return empty set for non-record classes")
        void shouldReturnEmptySetForNonRecordClasses() {
            Set<String> fieldNames = fieldMapper.getRecordFieldNames(String.class);
            assertTrue(fieldNames.isEmpty());
        }
    }

    @Nested
    @DisplayName("Path Navigation")
    class PathNavigationTest {

        @Test
        @DisplayName("Should navigate nested data paths correctly")
        void shouldNavigateNestedDataPaths() {
            Map<String, Object> nestedData = Map.of(
                    "level1", Map.of(
                            "level2", Map.of(
                                    "field", "value"
                            ),
                            "direct", "directValue"
                    )
            );

            assertEquals("value", fieldMapper.getValueFromPath(nestedData, "level1.level2.field"));
            assertEquals("directValue", fieldMapper.getValueFromPath(nestedData, "level1.direct"));
            assertNull(fieldMapper.getValueFromPath(nestedData, "level1.nonexistent"));
            assertNull(fieldMapper.getValueFromPath(nestedData, "nonexistent"));
        }

        @Test
        @DisplayName("Should build paths correctly")
        void shouldBuildPathsCorrectly() {
            assertEquals("field", fieldMapper.buildPath("", "field"));
            assertEquals("prefix.field", fieldMapper.buildPath("prefix", "field"));
            assertEquals("deep.nested.field", fieldMapper.buildPath("deep.nested", "field"));
        }
    }

    @Nested
    @DisplayName("Optional Field Handling")
    class OptionalFieldHandlingTest {

        @Test
        @DisplayName("Should correctly identify optional fields")
        void shouldIdentifyOptionalFields() throws Exception {
            var constructor = TestRecord.class.getDeclaredConstructors()[0];
            var parameters = constructor.getParameters();

            assertFalse(fieldMapper.isOptional(parameters[0])); // normalField
            assertFalse(fieldMapper.isOptional(parameters[1])); // customField
            assertTrue(fieldMapper.isOptional(parameters[2]));  // optionalField
        }
    }
}