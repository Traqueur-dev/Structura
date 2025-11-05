package fr.traqueur.structura.mapping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.Set;

import static fr.traqueur.structura.fixtures.TestModels.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Refactored FieldMapper tests using common test models.
 * Tests name conversion, key component detection, and field analysis.
 */
@DisplayName("FieldMapper - Refactored Tests")
class FieldMapperTest {

    private FieldMapper fieldMapper;

    @BeforeEach
    void setUp() {
        fieldMapper = new FieldMapper();
    }

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
            // Using ConfigWithCustomNames which has @Options(name = "app-name") and @Options(name = "server-config")
            var constructor = ConfigWithCustomNames.class.getDeclaredConstructors()[0];
            var parameters = constructor.getParameters();

            // Field with custom name "app-name"
            String appName = fieldMapper.getEffectiveFieldName(parameters[0], "applicationName");
            assertEquals("app-name", appName);

            // Field with custom name "server-config"
            String serverConfig = fieldMapper.getEffectiveFieldName(parameters[1], "serverConfig");
            assertEquals("server-config", serverConfig);
        }

        @Test
        @DisplayName("Should handle optional fields")
        void shouldHandleOptionalFields() throws Exception {
            // Using ConfigWithOptionalFields
            var constructor = ConfigWithOptionalFields.class.getDeclaredConstructors()[0];
            var parameters = constructor.getParameters();

            // Required field
            String requiredName = fieldMapper.getEffectiveFieldName(parameters[0], "required");
            assertEquals("required", requiredName);

            // Optional field
            String optionalName = fieldMapper.getEffectiveFieldName(parameters[1], "optional");
            assertEquals("optional", optionalName);
        }
    }

    @Nested
    @DisplayName("Key Component Detection")
    class KeyComponentDetectionTest {

        @Test
        @DisplayName("Should identify key components correctly")
        void shouldIdentifyKeyComponents() {
            // Using SimpleKeyRecord which has @Options(isKey = true) on id field
            RecordComponent[] keyComponents = SimpleKeyRecord.class.getRecordComponents();
            RecordComponent keyComponent = fieldMapper.findKeyComponent(keyComponents);

            assertNotNull(keyComponent);
            assertEquals("id", keyComponent.getName());

            // ConfigWithCustomNames has no key component
            RecordComponent[] noKeyComponents = ConfigWithCustomNames.class.getRecordComponents();
            RecordComponent noKeyComponent = fieldMapper.findKeyComponent(noKeyComponents);
            assertNull(noKeyComponent);
        }

        @Test
        @DisplayName("Should distinguish simple vs complex key mapping")
        void shouldDistinguishSimpleVsComplexKeyMapping() {
            RecordComponent[] keyComponents = SimpleKeyRecord.class.getRecordComponents();
            RecordComponent keyComponent = fieldMapper.findKeyComponent(keyComponents);

            // Simple key mapping: single key with primitive type
            Map<String, Object> simpleData = Map.of("key", Map.of("value-int", 42));
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
            // Using ComplexKeyConfig which has @Options(isKey = true) on NestedKeyRecord
            RecordComponent[] components = ComplexKeyConfig.class.getRecordComponents();
            RecordComponent keyComponent = fieldMapper.findKeyComponent(components);

            assertNotNull(keyComponent);
            assertEquals("server", keyComponent.getName());

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
            // Using ConfigWithCustomNames
            Set<String> fieldNames = fieldMapper.getRecordFieldNames(ConfigWithCustomNames.class);

            assertEquals(2, fieldNames.size());
            assertTrue(fieldNames.contains("app-name"));
            assertTrue(fieldNames.contains("server-config"));
        }

        @Test
        @DisplayName("Should handle fields with defaults and optional")
        void shouldHandleFieldsWithDefaultsAndOptional() {
            // Using ConfigWithOptionalFields
            Set<String> fieldNames = fieldMapper.getRecordFieldNames(ConfigWithOptionalFields.class);

            assertEquals(3, fieldNames.size());
            assertTrue(fieldNames.contains("required"));
            assertTrue(fieldNames.contains("optional"));
            assertTrue(fieldNames.contains("optional-with-default"));
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
            var constructor = ConfigWithOptionalFields.class.getDeclaredConstructors()[0];
            var parameters = constructor.getParameters();

            assertFalse(fieldMapper.isOptional(parameters[0])); // required
            assertTrue(fieldMapper.isOptional(parameters[1]));  // optional
            assertTrue(fieldMapper.isOptional(parameters[2]));  // optionalWithDefault
        }
    }
}