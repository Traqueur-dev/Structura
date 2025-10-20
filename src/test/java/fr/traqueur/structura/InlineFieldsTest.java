package fr.traqueur.structura;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.defaults.DefaultBool;
import fr.traqueur.structura.annotations.defaults.DefaultInt;
import fr.traqueur.structura.annotations.defaults.DefaultString;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.conversion.ValueConverter;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.factory.RecordInstanceFactory;
import fr.traqueur.structura.mapping.FieldMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Inline Fields - Field Flattening Tests")
class InlineFieldsTest {

    private RecordInstanceFactory recordFactory;
    private ValueConverter valueConverter;

    @BeforeEach
    void setUp() {
        FieldMapper fieldMapper = new FieldMapper();
        recordFactory = new RecordInstanceFactory(fieldMapper);
        valueConverter = new ValueConverter(recordFactory);
        recordFactory.setValueConverter(valueConverter);
    }

    // ===== Test Records =====

    public record ServerInfo(
            @DefaultString("localhost") String host,
            @DefaultInt(8080) int port,
            @DefaultString("http") String protocol
    ) implements Loadable {}

    public record DatabaseInfo(
            @DefaultString("localhost") String host,
            @DefaultInt(5432) int port,
            @DefaultString("postgres") String database
    ) implements Loadable {}

    public record AuthInfo(
            @DefaultString("user") String username,
            @DefaultString("password") String password
    ) implements Loadable {}

    // Configuration with inline field
    public record AppConfigWithInline(
            String appName,
            @Options(inline = true) ServerInfo server,
            @DefaultBool(false) boolean debugMode
    ) implements Loadable {}

    // Configuration with multiple inline fields
    public record AppConfigWithMultipleInline(
            String appName,
            @Options(inline = true) ServerInfo server,
            @Options(inline = true) DatabaseInfo database
    ) implements Loadable {}

    // Configuration mixing inline and non-inline fields
    public record AppConfigMixed(
            String appName,
            @Options(inline = true) ServerInfo server,
            DatabaseInfo database  // Not inline
    ) implements Loadable {}

    // Configuration with inline field with custom name
    public record AppConfigWithCustomName(
            String appName,
            @Options(inline = true, name = "srv") ServerInfo server
    ) implements Loadable {}

    // Nested inline configuration
    public record NestedConfig(
            String name,
            @Options(inline = true) AuthInfo auth
    ) implements Loadable {}

    public record OuterConfig(
            @Options(inline = true) NestedConfig nested,
            @DefaultString("outer") String level
    ) implements Loadable {}

    // Edge case records
    public record AllDefaultsConfig(
            @DefaultString("DefaultApp") String appName,
            @Options(inline = true) ServerInfo server
    ) implements Loadable {}

    public record StrictServerInfo(
            String host,  // No default - required
            @DefaultInt(8080) int port
    ) implements Loadable {}

    public record StrictConfig(
            String appName,
            @Options(inline = true) StrictServerInfo server
    ) implements Loadable {}

    public record InvalidInlineConfig(
            String appName,
            @Options(inline = true) String value  // String is not a record
    ) implements Loadable {}

    // Backward compatibility records
    public record TraditionalConfig(
            String appName,
            ServerInfo server  // No @Options(inline = true)
    ) implements Loadable {}

    public record NoOptionsConfig(
            String appName,
            ServerInfo server  // No @Options
    ) implements Loadable {}

    @Nested
    @DisplayName("Basic Inline Field Behavior")
    class BasicInlineFieldTest {

        @Test
        @DisplayName("Should flatten inline record fields to parent level")
        void shouldFlattenInlineFieldsToParentLevel() {
            Map<String, Object> data = Map.of(
                    "app-name", "MyApp",
                    "host", "api.example.com",    // server.host
                    "port", 9000,                  // server.port
                    "protocol", "https",           // server.protocol
                    "debug-mode", true
            );

            AppConfigWithInline result = (AppConfigWithInline) recordFactory.createInstance(
                    data, AppConfigWithInline.class, ""
            );

            assertEquals("MyApp", result.appName());
            assertEquals("api.example.com", result.server().host());
            assertEquals(9000, result.server().port());
            assertEquals("https", result.server().protocol());
            assertTrue(result.debugMode());
        }

        @Test
        @DisplayName("Should use default values for missing inline fields")
        void shouldUseDefaultValuesForMissingInlineFields() {
            Map<String, Object> data = Map.of(
                    "app-name", "MinimalApp",
                    "host", "simple.example.com"
                    // port and protocol missing, should use defaults
            );

            AppConfigWithInline result = (AppConfigWithInline) recordFactory.createInstance(
                    data, AppConfigWithInline.class, ""
            );

            assertEquals("MinimalApp", result.appName());
            assertEquals("simple.example.com", result.server().host());
            assertEquals(8080, result.server().port());      // Default
            assertEquals("http", result.server().protocol()); // Default
            assertFalse(result.debugMode());                 // Default
        }

        @Test
        @DisplayName("Should work with all fields from defaults")
        void shouldWorkWithAllFieldsFromDefaults() {
            Map<String, Object> data = Map.of(
                    "app-name", "DefaultApp"
                    // All server fields missing, should use defaults
            );

            AppConfigWithInline result = (AppConfigWithInline) recordFactory.createInstance(
                    data, AppConfigWithInline.class, ""
            );

            assertEquals("DefaultApp", result.appName());
            assertEquals("localhost", result.server().host());
            assertEquals(8080, result.server().port());
            assertEquals("http", result.server().protocol());
        }
    }

    @Nested
    @DisplayName("Multiple Inline Fields")
    class MultipleInlineFieldsTest {

        @Test
        @DisplayName("Should handle multiple inline fields without conflicts")
        void shouldHandleMultipleInlineFields() {
            Map<String, Object> data = Map.of(
                    "app-name", "MultiInlineApp",
                    // ServerInfo fields
                    "host", "server.example.com",
                    "port", 9090,
                    "protocol", "https",
                    // DatabaseInfo fields - note: also has host, port, and database
                    "database", "production_db"
            );

            // This should work because ServerInfo gets host/port/protocol
            // and DatabaseInfo gets host/port/database
            // They share the same host and port values from the YAML
            AppConfigWithMultipleInline result = (AppConfigWithMultipleInline) recordFactory.createInstance(
                    data, AppConfigWithMultipleInline.class, ""
            );

            assertEquals("MultiInlineApp", result.appName());

            // Both should get the same host and port from the flattened data
            assertEquals("server.example.com", result.server().host());
            assertEquals(9090, result.server().port());
            assertEquals("https", result.server().protocol());

            assertEquals("server.example.com", result.database().host());
            assertEquals(9090, result.database().port());
            assertEquals("production_db", result.database().database());
        }

        @Test
        @DisplayName("Should handle partial data with multiple inline fields")
        void shouldHandlePartialDataWithMultipleInlineFields() {
            Map<String, Object> data = Map.of(
                    "app-name", "PartialApp",
                    "protocol", "https",
                    "database", "my_database"
                    // host and port missing - should use defaults
            );

            AppConfigWithMultipleInline result = (AppConfigWithMultipleInline) recordFactory.createInstance(
                    data, AppConfigWithMultipleInline.class, ""
            );

            assertEquals("PartialApp", result.appName());

            // Server gets defaults for host/port
            assertEquals("localhost", result.server().host());
            assertEquals(8080, result.server().port());
            assertEquals("https", result.server().protocol());

            // Database gets defaults for host/port
            assertEquals("localhost", result.database().host());
            assertEquals(5432, result.database().port());
            assertEquals("my_database", result.database().database());
        }
    }

    @Nested
    @DisplayName("Mixed Inline and Non-Inline Fields")
    class MixedInlineFieldsTest {

        @Test
        @DisplayName("Should handle both inline and nested fields correctly")
        void shouldHandleMixedInlineAndNestedFields() {
            Map<String, Object> data = Map.of(
                    "app-name", "MixedApp",
                    // Inline server fields at root level
                    "host", "api.example.com",
                    "port", 8443,
                    "protocol", "https",
                    // Non-inline database under its own key
                    "database", Map.of(
                            "host", "db.example.com",
                            "port", 5433,
                            "database", "app_db"
                    )
            );

            AppConfigMixed result = (AppConfigMixed) recordFactory.createInstance(
                    data, AppConfigMixed.class, ""
            );

            assertEquals("MixedApp", result.appName());

            // Inline server from root level
            assertEquals("api.example.com", result.server().host());
            assertEquals(8443, result.server().port());
            assertEquals("https", result.server().protocol());

            // Non-inline database from nested structure
            assertEquals("db.example.com", result.database().host());
            assertEquals(5433, result.database().port());
            assertEquals("app_db", result.database().database());
        }

        @Test
        @DisplayName("Should use defaults for inline field when data missing")
        void shouldUseDefaultsForInlineFieldWhenMissing() {
            Map<String, Object> data = Map.of(
                    "app-name", "OnlyDBApp",
                    "database", Map.of(
                            "host", "db.example.com",
                            "database", "my_db"
                    )
                    // No server fields at root level
            );

            AppConfigMixed result = (AppConfigMixed) recordFactory.createInstance(
                    data, AppConfigMixed.class, ""
            );

            assertEquals("OnlyDBApp", result.appName());

            // Server should use all defaults
            assertEquals("localhost", result.server().host());
            assertEquals(8080, result.server().port());
            assertEquals("http", result.server().protocol());

            // Database from nested data
            assertEquals("db.example.com", result.database().host());
            assertEquals(5432, result.database().port()); // Default
            assertEquals("my_db", result.database().database());
        }
    }

    @Nested
    @DisplayName("Nested Inline Configurations")
    class NestedInlineTest {

        @Test
        @DisplayName("Should handle nested inline configurations")
        void shouldHandleNestedInlineConfigurations() {
            Map<String, Object> data = Map.of(
                    // OuterConfig has inline NestedConfig
                    // NestedConfig has inline AuthInfo
                    // So all fields should be at root level
                    "name", "MyNestedConfig",
                    "username", "admin",
                    "password", "secret123",
                    "level", "production"
            );

            OuterConfig result = (OuterConfig) recordFactory.createInstance(
                    data, OuterConfig.class, ""
            );

            assertEquals("MyNestedConfig", result.nested().name());
            assertEquals("admin", result.nested().auth().username());
            assertEquals("secret123", result.nested().auth().password());
            assertEquals("production", result.level());
        }

        @Test
        @DisplayName("Should use defaults in nested inline configurations")
        void shouldUseDefaultsInNestedInlineConfigurations() {
            Map<String, Object> data = Map.of(
                    "name", "MinimalNested"
                    // username, password, level missing
            );

            OuterConfig result = (OuterConfig) recordFactory.createInstance(
                    data, OuterConfig.class, ""
            );

            assertEquals("MinimalNested", result.nested().name());
            assertEquals("user", result.nested().auth().username());     // Default
            assertEquals("password", result.nested().auth().password()); // Default
            assertEquals("outer", result.level());                        // Default
        }
    }

    @Nested
    @DisplayName("Edge Cases and Validation")
    class EdgeCasesTest {

        @Test
        @DisplayName("Should work with empty YAML when all fields have defaults")
        void shouldWorkWithEmptyYAMLWhenAllHaveDefaults() {
            Map<String, Object> data = Map.of();

            AllDefaultsConfig result = (AllDefaultsConfig) recordFactory.createInstance(
                    data, AllDefaultsConfig.class, ""
            );

            assertEquals("DefaultApp", result.appName());
            assertEquals("localhost", result.server().host());
            assertEquals(8080, result.server().port());
            assertEquals("http", result.server().protocol());
        }

        @Test
        @DisplayName("Should fail when required field in inline record is missing")
        void shouldFailWhenRequiredInlineFieldMissing() {
            Map<String, Object> data = Map.of(
                    "app-name", "StrictApp"
                    // Missing "host" which is required
            );

            assertThrows(StructuraException.class, () ->
                    recordFactory.createInstance(data, StrictConfig.class, "")
            );
        }

        @Test
        @DisplayName("Should not apply inline to non-record types")
        void shouldNotApplyInlineToNonRecordTypes() {
            // inline should only work for records implementing Loadable
            // For other types, it should be ignored
            Map<String, Object> data = Map.of(
                    "app-name", "TestApp",
                    "value", "test-value"
            );

            InvalidInlineConfig result = (InvalidInlineConfig) recordFactory.createInstance(
                    data, InvalidInlineConfig.class, ""
            );

            assertEquals("TestApp", result.appName());
            assertEquals("test-value", result.value());  // Should work normally
        }
    }

    @Nested
    @DisplayName("Backward Compatibility")
    class BackwardCompatibilityTest {

        @Test
        @DisplayName("Should not affect existing configurations without inline")
        void shouldNotAffectExistingConfigurations() {
            Map<String, Object> data = Map.of(
                    "app-name", "TraditionalApp",
                    "server", Map.of(
                            "host", "traditional.example.com",
                            "port", 8080,
                            "protocol", "http"
                    )
            );

            TraditionalConfig result = (TraditionalConfig) recordFactory.createInstance(
                    data, TraditionalConfig.class, ""
            );

            assertEquals("TraditionalApp", result.appName());
            assertEquals("traditional.example.com", result.server().host());
            assertEquals(8080, result.server().port());
            assertEquals("http", result.server().protocol());
        }

        @Test
        @DisplayName("Default inline value should be false")
        void defaultInlineValueShouldBeFalse() {
            // Should work with nested structure (inline = false by default)
            Map<String, Object> data = Map.of(
                    "app-name", "DefaultBehavior",
                    "server", Map.of(
                            "host", "default.example.com"
                    )
            );

            NoOptionsConfig result = (NoOptionsConfig) recordFactory.createInstance(
                    data, NoOptionsConfig.class, ""
            );

            assertEquals("DefaultBehavior", result.appName());
            assertEquals("default.example.com", result.server().host());
        }
    }
}
