package fr.traqueur.structura.integration;

import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.defaults.*;
import fr.traqueur.structura.annotations.validation.*;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.exceptions.ValidationException;
import fr.traqueur.structura.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Structura Integration Tests")
class IntegrationTest {

    @TempDir
    Path tempDir;

    public record DatabaseConfig(
        @Pattern(value = "^[a-zA-Z0-9.-]+$", message = "Invalid hostname") String host,
        @Min(value = 1, message = "Port must be positive") @Max(value = 65535, message = "Port must be valid") int port,
        @NotEmpty(message = "Database name required") String database,
        @DefaultString("admin") String username,
        @Options(optional = true) String password,
        @DefaultInt(10) @Min(1) @Max(100) int maxConnections
    ) implements Loadable {}

    public record ServerConfig(
        @Pattern(value = "^[a-zA-Z0-9.-]+$") String host,
        @DefaultInt(8080) @Min(80) @Max(65535) int port,
        @DefaultBool(false) boolean enableSsl,
        @DefaultString("/") String contextPath,
        @Size(min = 1, max = 10) List<String> allowedOrigins
    ) implements Loadable {}

    public record LoggingConfig(
        @Options(name = "log-level") LogLevel level,
        @DefaultString("logs/app.log") String filePath,
        @DefaultBool(true) boolean enableConsole,
        @Size(max = 5) Map<String, String> additionalSettings
    ) implements Loadable {}

    public record ApplicationConfig(
        @NotEmpty @Size(min = 3, max = 50) String appName,
        @DefaultString("1.0.0") String version,
        DatabaseConfig database,
        ServerConfig server,
        LoggingConfig logging,
        @Options(optional = true) List<String> features,
        @Options(optional = true) Map<String, String> customProperties
    ) implements Loadable {}

    public record KeyMappingConfig(
        @Options(isKey = true) String environment,
        String description,
        @DefaultBool(false) boolean active
    ) implements Loadable {}

    public record ComplexKeyConfig(
        @Options(isKey = true) ServerConfig server,
        String appName,
        @DefaultBool(false) boolean debugMode
    ) implements Loadable {}

    public enum LogLevel { DEBUG, INFO, WARN, ERROR }

    public enum EnvironmentType implements Loadable {
        DEVELOPMENT, STAGING, PRODUCTION;
        
        @DefaultString("unknown") public String description;
        @DefaultInt(0) @Min(0) @Max(10) public int priority;
        @Options(optional = true) public Map<String, String> settings;
    }

    @Nested
    @DisplayName("End-to-End Configuration Tests")
    class EndToEndConfigurationTest {

        @Test
        @DisplayName("Should parse complete application configuration")
        void shouldParseCompleteApplicationConfiguration() {
            String yaml = """
                app-name: "Production Service"
                version: "2.1.0"
                database:
                  host: "db.production.com"
                  port: 5432
                  database: "prod_db"
                  username: "prod_user"
                  password: "secure_password"
                  max-connections: 20
                server:
                  host: "api.production.com"
                  port: 8443
                  enable-ssl: true
                  context-path: "/api/v1"
                  allowed-origins:
                    - "https://frontend.production.com"
                    - "https://admin.production.com"
                logging:
                  log-level: INFO
                  file-path: "logs/production.log"
                  enable-console: false
                  additional-settings:
                    rotation: "daily"
                    max-size: "100MB"
                features:
                  - "feature-a"
                  - "feature-b"
                custom-properties:
                  cache.ttl: "3600"
                  monitoring.enabled: "true"
                """;

            ApplicationConfig config = Structura.parse(yaml, ApplicationConfig.class);

            // Verify main application settings
            assertEquals("Production Service", config.appName());
            assertEquals("2.1.0", config.version());

            // Verify database configuration
            assertNotNull(config.database());
            assertEquals("db.production.com", config.database().host());
            assertEquals(5432, config.database().port());
            assertEquals("prod_db", config.database().database());
            assertEquals("prod_user", config.database().username());
            assertEquals("secure_password", config.database().password());
            assertEquals(20, config.database().maxConnections());

            // Verify server configuration
            assertNotNull(config.server());
            assertEquals("api.production.com", config.server().host());
            assertEquals(8443, config.server().port());
            assertTrue(config.server().enableSsl());
            assertEquals("/api/v1", config.server().contextPath());
            assertEquals(2, config.server().allowedOrigins().size());

            // Verify logging configuration
            assertNotNull(config.logging());
            assertEquals(LogLevel.INFO, config.logging().level());
            assertEquals("logs/production.log", config.logging().filePath());
            assertFalse(config.logging().enableConsole());
            assertEquals(2, config.logging().additionalSettings().size());

            // Verify optional collections
            assertEquals(2, config.features().size());
            assertTrue(config.features().contains("feature-a"));
            assertEquals(2, config.customProperties().size());
            assertEquals("3600", config.customProperties().get("cache.ttl"));
        }

        @Test
        @DisplayName("Should handle minimal configuration with defaults")
        void shouldHandleMinimalConfigurationWithDefaults() {
            String yaml = """
                app-name: "Minimal App"
                database:
                  host: "localhost"
                  database: "test_db"
                  port: 7632
                server:
                  host: "localhost"
                  allowed-origins:
                    - "http://localhost:3000"
                logging:
                  log-level: DEBUG
                  additional-settings: {}
                """;

            ApplicationConfig config = Structura.parse(yaml, ApplicationConfig.class);

            assertEquals("Minimal App", config.appName());
            assertEquals("1.0.0", config.version()); // Default

            // Database with defaults
            assertEquals("admin", config.database().username()); // Default
            assertNull(config.database().password()); // Optional
            assertEquals(10, config.database().maxConnections()); // Default

            // Server with defaults
            assertEquals(8080, config.server().port()); // Default
            assertFalse(config.server().enableSsl()); // Default
            assertEquals("/", config.server().contextPath()); // Default

            // Logging with defaults
            assertEquals("logs/app.log", config.logging().filePath()); // Default
            assertTrue(config.logging().enableConsole()); // Default
        }
    }

    @Nested
    @DisplayName("File Loading Tests")
    class FileLoadingTest {

        @Test
        @DisplayName("Should load configuration from file")
        void shouldLoadConfigurationFromFile() throws IOException {
            String yaml = """
                app-name: "File Test App"
                database:
                  host: "localhost"
                  database: "file_test"
                  port: 9827
                server:
                  host: "localhost"
                  allowed-origins: ["http://localhost"]
                logging:
                  log-level: INFO
                  additional-settings: {}
                """;

            Path configFile = tempDir.resolve("config.yml");
            Files.writeString(configFile, yaml);

            ApplicationConfig config = Structura.load(configFile, ApplicationConfig.class);

            assertEquals("File Test App", config.appName());
            assertEquals("localhost", config.database().host());
        }

        @Test
        @DisplayName("Should load configuration from File object")
        void shouldLoadConfigurationFromFileObject() throws IOException {
            String yaml = """
                app-name: "File Object Test"
                database:
                  host: "localhost"
                  database: "file_obj_test"
                  port: 7633
                server:
                  host: "localhost"
                  allowed-origins: ["http://localhost"]
                logging:
                  log-level: WARN
                  additional-settings: {}
                """;

            File configFile = tempDir.resolve("config.yml").toFile();
            Files.writeString(configFile.toPath(), yaml);

            ApplicationConfig config = Structura.load(configFile, ApplicationConfig.class);

            assertEquals("File Object Test", config.appName());
            assertEquals(LogLevel.WARN, config.logging().level());
        }

        @Test
        @DisplayName("Should throw exception for non-existent file")
        void shouldThrowExceptionForNonExistentFile() {
            Path nonExistentFile = tempDir.resolve("non-existent.yml");

            StructuraException exception = assertThrows(StructuraException.class, () -> {
                Structura.load(nonExistentFile, ApplicationConfig.class);
            });

            assertTrue(exception.getMessage().contains("Unable to read file"));
        }
    }

    @Nested
    @DisplayName("Resource Loading Tests")
    class ResourceLoadingTest {

        @Test
        @DisplayName("Should handle missing resource gracefully")
        void shouldHandleMissingResourceGracefully() {
            StructuraException exception = assertThrows(StructuraException.class, () -> {
                Structura.loadFromResource("/non-existent-resource.yml", ApplicationConfig.class);
            });

            assertTrue(exception.getMessage().contains("Resource not found"));
        }
    }

    @Nested
    @DisplayName("Key Mapping Integration Tests")
    class KeyMappingIntegrationTest {

        @Test
        @DisplayName("Should handle simple key mapping with validation")
        void shouldHandleSimpleKeyMappingWithValidation() {
            String yaml = """
                production:
                  description: "Production environment configuration"
                  active: true
                """;

            KeyMappingConfig config = Structura.parse(yaml, KeyMappingConfig.class);

            assertEquals("production", config.environment());
            assertEquals("Production environment configuration", config.description());
            assertTrue(config.active());
        }

        @Test
        @DisplayName("Should handle complex key mapping with nested validation")
        void shouldHandleComplexKeyMappingWithNestedValidation() {
            String yaml = """
                host: "complex.example.com"
                port: 9443
                enable-ssl: true
                allowed-origins:
                  - "https://app1.com"
                  - "https://app2.com"
                app-name: "Complex Key App"
                debug-mode: true
                """;

            ComplexKeyConfig config = Structura.parse(yaml, ComplexKeyConfig.class);

            assertNotNull(config.server());
            assertEquals("complex.example.com", config.server().host());
            assertEquals(9443, config.server().port());
            assertTrue(config.server().enableSsl());
            assertEquals(2, config.server().allowedOrigins().size());
            assertEquals("Complex Key App", config.appName());
            assertTrue(config.debugMode());
        }
    }

    @Nested
    @DisplayName("Enum Configuration Integration Tests")
    class EnumConfigurationIntegrationTest {

        @Test
        @DisplayName("Should populate and validate enum constants")
        void shouldPopulateAndValidateEnumConstants() {
            String yaml = """
                development:
                  description: "Development environment"
                  priority: 1
                  settings:
                    debug: "true"
                    hot-reload: "enabled"
                staging:
                  description: "Staging environment"
                  priority: 5
                  settings:
                    debug: "false"
                    performance-monitoring: "enabled"
                production:
                  description: "Production environment"
                  priority: 10
                """;

            Structura.parseEnum(yaml, EnvironmentType.class);

            assertEquals("Development environment", EnvironmentType.DEVELOPMENT.description);
            assertEquals(1, EnvironmentType.DEVELOPMENT.priority);
            assertNotNull(EnvironmentType.DEVELOPMENT.settings);
            assertEquals("true", EnvironmentType.DEVELOPMENT.settings.get("debug"));

            assertEquals("Staging environment", EnvironmentType.STAGING.description);
            assertEquals(5, EnvironmentType.STAGING.priority);
            assertEquals("false", EnvironmentType.STAGING.settings.get("debug"));

            assertEquals("Production environment", EnvironmentType.PRODUCTION.description);
            assertEquals(10, EnvironmentType.PRODUCTION.priority);
            assertNull(EnvironmentType.PRODUCTION.settings); // Optional field not provided
        }

        @Test
        @DisplayName("Should validate enum field constraints")
        void shouldValidateEnumFieldConstraints() {
            String yaml = """
                development:
                  description: "Development environment"
                  priority: 15
                """;

            ValidationException exception = assertThrows(ValidationException.class, () -> {
                Structura.parseEnum(yaml, EnvironmentType.class);
            });

            assertTrue(exception.getMessage().contains("must not exceed"));
        }
    }

    @Nested
    @DisplayName("Validation Integration Tests")
    class ValidationIntegrationTest {

        @Test
        @DisplayName("Should validate complete configuration tree")
        void shouldValidateCompleteConfigurationTree() {
            String yaml = """
                app-name: "Test"
                database:
                  host: "invalid-host-name-with-spaces and special chars!"
                  port: 5432
                  database: "test_db"
                server:
                  host: "localhost"
                  allowed-origins: ["http://localhost"]
                logging:
                  log-level: INFO
                  additional-settings: {}
                """;

            ValidationException exception = assertThrows(ValidationException.class, () -> {
                Structura.parse(yaml, ApplicationConfig.class);
            });

            assertTrue(exception.getMessage().contains("Invalid hostname"));
        }


        public static record TestSizeConfig(
                @Size(min = 1, max = 3) List<String> items
        ) implements Loadable {}

        @Test
        @DisplayName("Should validate collection size constraints")
        void shouldValidateCollectionSizeConstraints() {
            TestSizeConfig config = new TestSizeConfig(List.of());

            ValidationException exception = assertThrows(ValidationException.class, () -> {
                Validator.INSTANCE.validate(config, "");
            });

            assertTrue(exception.getMessage().contains("Size must be between"));
        }

        @Test
        @DisplayName("Should validate nested object constraints")
        void shouldValidateNestedObjectConstraints() {
            String yaml = """
                app-name: "Port Test App"
                database:
                  host: "localhost"
                  port: 99999
                  database: "test"
                server:
                  host: "localhost"
                  allowed-origins: ["http://localhost"]
                logging:
                  log-level: INFO
                  additional-settings: {}
                """;

            ValidationException exception = assertThrows(ValidationException.class, () -> {
                Structura.parse(yaml, ApplicationConfig.class);
            });

            assertTrue(exception.getMessage().contains("Port must be valid"));
        }
    }

    @Nested
    @DisplayName("Error Recovery and Robustness Tests")
    class ErrorRecoveryTest {

        @Test
        @DisplayName("Should provide clear error messages for missing required fields")
        void shouldProvideClearErrorMessagesForMissingRequiredFields() {
            String yaml = """
            app-name: "Missing Port Test"
            database:
              host: "localhost"
              database: "test"
            server:
              host: "localhost"
              allowed-origins: ["http://localhost"]
            logging:
              log-level: INFO
              additional-settings: {}
            """;

            // Missing port field should throw StructuraException
            StructuraException exception = assertThrows(StructuraException.class, () -> {
                Structura.parse(yaml, ApplicationConfig.class);
            });

            assertTrue(exception.getMessage().contains("port is required"));
        }

        @Test
        @DisplayName("Should handle malformed YAML gracefully")
        void shouldHandleMalformedYamlGracefully() {
            String malformedYaml = """
            app-name: "Test
            database:
              host: localhost
              port: [invalid yaml structure
            """;

            assertThrows(Exception.class, () -> {
                Structura.parse(malformedYaml, ApplicationConfig.class);
            });
        }

        @Test
        @DisplayName("Should handle type mismatches gracefully")
        void shouldHandleTypeMismatchesGracefully() {
            String yaml = """
            app-name: "Type Test"
            database:
              host: "localhost"
              port: "not_a_number"
              database: "test"
            server:
              host: "localhost"
              port: 8080
              allowed-origins: ["http://localhost"]
            logging:
              log-level: INFO
              additional-settings: {}
            """;

            assertThrows(Exception.class, () -> {
                Structura.parse(yaml, ApplicationConfig.class);
            });
        }

        @Test
        @DisplayName("Should handle invalid enum values gracefully")
        void shouldHandleInvalidEnumValuesGracefully() {
            String yaml = """
            app-name: "Enum Test"
            database:
              host: "localhost"
              port: 5432
              database: "test"
            server:
              host: "localhost"
              port: 8080
              allowed-origins: ["http://localhost"]
            logging:
              log-level: INVALID_LEVEL
              additional-settings: {}
            """;

            assertThrows(Exception.class, () -> {
                Structura.parse(yaml, ApplicationConfig.class);
            });
        }

        @Test
        @DisplayName("Should provide meaningful error messages")
        void shouldProvideMeaningfulErrorMessages() {
            String yaml = """
            app-name: "Error Message Test"
            database:
              host: "localhost"
              database: "test"
              # port manquant
            server:
              host: "localhost"
              port: 8080
              allowed-origins: ["http://localhost"]
            logging:
              log-level: INFO
              additional-settings: {}
            """;

            StructuraException exception = assertThrows(StructuraException.class, () -> {
                Structura.parse(yaml, ApplicationConfig.class);
            });

            assertNotNull(exception.getMessage());
            assertFalse(exception.getMessage().isEmpty());
            assertTrue(exception.getMessage().contains("port"));
        }
    }

    @Nested
    @DisplayName("Custom Processor Configuration Tests")
    class CustomProcessorConfigurationTest {

        @Test
        @DisplayName("Should allow disabling validation")
        void shouldAllowDisablingValidation() {
            String yaml = """
                app-name: ""
                database:
                  host: "invalid host!"
                  port: 99999
                  database: ""
                server:
                  host: "localhost"
                  allowed-origins: []
                logging:
                  log-level: INFO
                  additional-settings: {}
                """;

            // This should not throw validation errors
            var processor = Structura.builder()
                .withValidation(false)
                .build();

            Structura.with(processor);

            ApplicationConfig config = Structura.parse(yaml, ApplicationConfig.class);

            assertEquals("", config.appName()); // Invalid but accepted
            assertEquals("invalid host!", config.database().host()); // Invalid but accepted

            // Reset to default processor
            Structura.with(Structura.builder().build());
        }
    }
}