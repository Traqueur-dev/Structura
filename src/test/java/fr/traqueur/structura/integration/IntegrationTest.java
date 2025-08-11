package fr.traqueur.structura.integration;

import fr.traqueur.structura.StructuraProcessor;
import fr.traqueur.structura.annotations.defaults.DefaultInt;
import fr.traqueur.structura.annotations.defaults.DefaultString;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.exceptions.StructuraException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Integration Tests - File Loading and End-to-End Workflows")
class IntegrationTest {

    @TempDir
    Path tempDir;

    // Minimal test records for integration scenarios
    public record AppConfig(
            @DefaultString("TestApp") String appName,
            @DefaultInt(8080) int port,
            DatabaseConfig database
    ) implements Loadable {}

    public record DatabaseConfig(
            String host,
            @DefaultInt(5432) int port,
            String database
    ) implements Loadable {}

    public enum LogLevel implements Loadable {
        DEBUG, INFO, WARN, ERROR;

        @DefaultString("unknown") public String description;
        @DefaultInt(0) public int priority;
    }

    @Nested
    @DisplayName("File Loading Operations")
    class FileLoadingTest {

        @Test
        @DisplayName("Should load configuration from Path")
        void shouldLoadConfigurationFromPath() throws IOException {
            String yaml = """
                app-name: "PathApp"
                port: 9000
                database:
                  host: "localhost"
                  port: 5432
                  database: "pathdb"
                """;

            Path configFile = tempDir.resolve("config.yml");
            Files.writeString(configFile, yaml);

            AppConfig config = Structura.load(configFile, AppConfig.class);

            assertEquals("PathApp", config.appName());
            assertEquals(9000, config.port());
            assertEquals("localhost", config.database().host());
            assertEquals(5432, config.database().port());
            assertEquals("pathdb", config.database().database());
        }

        @Test
        @DisplayName("Should load configuration from File object")
        void shouldLoadConfigurationFromFile() throws IOException {
            String yaml = """
                app-name: "FileApp"
                port: 8443
                database:
                  host: "db.example.com"
                  port: 3306
                  database: "filedb"
                """;

            File configFile = tempDir.resolve("config.yml").toFile();
            Files.writeString(configFile.toPath(), yaml);

            AppConfig config = Structura.load(configFile, AppConfig.class);

            assertEquals("FileApp", config.appName());
            assertEquals(8443, config.port());
            assertEquals("db.example.com", config.database().host());
            assertEquals(3306, config.database().port());
            assertEquals("filedb", config.database().database());
        }

        @Test
        @DisplayName("Should load enum configuration from Path")
        void shouldLoadEnumConfigurationFromPath() throws IOException {
            String yaml = """
                debug:
                  description: "Debug level logging"
                  priority: 1
                info:
                  description: "Info level logging"
                  priority: 2
                warn:
                  description: "Warning level logging"
                  priority: 3
                error:
                  description: "Error level logging"
                  priority: 4
                """;

            Path enumFile = tempDir.resolve("loglevels.yml");
            Files.writeString(enumFile, yaml);

            Structura.loadEnum(enumFile, LogLevel.class);

            assertEquals("Debug level logging", LogLevel.DEBUG.description);
            assertEquals(1, LogLevel.DEBUG.priority);
            assertEquals("Info level logging", LogLevel.INFO.description);
            assertEquals(2, LogLevel.INFO.priority);
            assertEquals("Warning level logging", LogLevel.WARN.description);
            assertEquals(3, LogLevel.WARN.priority);
            assertEquals("Error level logging", LogLevel.ERROR.description);
            assertEquals(4, LogLevel.ERROR.priority);
        }

        @Test
        @DisplayName("Should load enum configuration from File object")
        void shouldLoadEnumConfigurationFromFile() throws IOException {
            String yaml = """
                debug:
                  description: "File debug"
                  priority: 10
                info:
                  description: "File info"
                  priority: 20
                warn:
                  description: "File warn"
                  priority: 30
                error:
                  description: "File error"
                  priority: 40
                """;

            File enumFile = tempDir.resolve("enum.yml").toFile();
            Files.writeString(enumFile.toPath(), yaml);

            Structura.loadEnum(enumFile, LogLevel.class);

            assertEquals("File debug", LogLevel.DEBUG.description);
            assertEquals(10, LogLevel.DEBUG.priority);
            assertEquals("File info", LogLevel.INFO.description);
            assertEquals(20, LogLevel.INFO.priority);
            assertEquals("File warn", LogLevel.WARN.description);
            assertEquals(30, LogLevel.WARN.priority);
            assertEquals("File error", LogLevel.ERROR.description);
            assertEquals(40, LogLevel.ERROR.priority);
        }
    }

    @Nested
    @DisplayName("Resource Loading Operations")
    class ResourceLoadingTest {

        @Test
        @DisplayName("Should handle missing resources gracefully")
        void shouldHandleMissingResourcesGracefully() {
            StructuraException configException = assertThrows(StructuraException.class, () ->
                    Structura.loadFromResource("/non-existent-config.yml", AppConfig.class)
            );
            assertTrue(configException.getMessage().contains("Resource not found"));

            StructuraException enumException = assertThrows(StructuraException.class, () ->
                    Structura.loadEnumFromResource("/non-existent-enum.yml", LogLevel.class)
            );
            assertTrue(enumException.getMessage().contains("Resource not found"));
        }
    }

    @Nested
    @DisplayName("File System Error Handling")
    class FileSystemErrorHandlingTest {

        @Test
        @DisplayName("Should throw meaningful exceptions for file system errors")
        void shouldThrowMeaningfulExceptionsForFileSystemErrors() {
            Path nonExistentFile = tempDir.resolve("non-existent.yml");

            // Test config loading from non-existent Path
            StructuraException pathException = assertThrows(StructuraException.class, () ->
                    Structura.load(nonExistentFile, AppConfig.class)
            );
            assertTrue(pathException.getMessage().contains("Unable to read file"));
            assertTrue(pathException.getMessage().contains(nonExistentFile.toString()));

            // Test config loading from non-existent File
            File nonExistentFileObj = nonExistentFile.toFile();
            StructuraException fileException = assertThrows(StructuraException.class, () ->
                    Structura.load(nonExistentFileObj, AppConfig.class)
            );
            assertTrue(fileException.getMessage().contains("Unable to read file"));
            assertTrue(fileException.getMessage().contains(nonExistentFileObj.getAbsolutePath()));

            // Test enum loading from non-existent Path
            StructuraException enumPathException = assertThrows(StructuraException.class, () ->
                    Structura.loadEnum(nonExistentFile, LogLevel.class)
            );
            assertTrue(enumPathException.getMessage().contains("Unable to read file"));

            // Test enum loading from non-existent File
            StructuraException enumFileException = assertThrows(StructuraException.class, () ->
                    Structura.loadEnum(nonExistentFileObj, LogLevel.class)
            );
            assertTrue(enumFileException.getMessage().contains("Unable to read file"));
        }

        @Test
        @DisplayName("Should handle permission-denied scenarios")
        void shouldHandlePermissionDeniedScenarios() throws IOException {
            // Create a file and make it unreadable (Unix-like systems only)
            Path restrictedFile = tempDir.resolve("restricted.yml");
            Files.writeString(restrictedFile, "test: value");

            // Note: This test may not work on all systems due to permission handling differences
            // The main goal is to ensure the exception handling path is tested
            try {
                restrictedFile.toFile().setReadable(false);

                if (!restrictedFile.toFile().canRead()) {
                    assertThrows(StructuraException.class, () ->
                            Structura.load(restrictedFile, AppConfig.class)
                    );
                }
            } finally {
                // Restore permissions for cleanup
                restrictedFile.toFile().setReadable(true);
            }
        }
    }

    @Nested
    @DisplayName("Custom Processor Configuration")
    class CustomProcessorConfigurationTest {

        @Test
        @DisplayName("Should support custom processor with validation disabled")
        void shouldSupportCustomProcessorWithValidationDisabled() throws IOException {
            String yamlWithInvalidData = """
                app-name: ""
                port: -1
                database:
                  host: ""
                  port: 99999
                  database: ""
                """;

            Path configFile = tempDir.resolve("invalid-config.yml");
            Files.writeString(configFile, yamlWithInvalidData);

            // Configure processor with validation disabled
            var customProcessor = Structura.builder()
                    .withValidation(false)
                    .build();

            var originalProcessor = getCurrentProcessor();

            try {
                Structura.with(customProcessor);

                // This should not throw validation errors
                AppConfig config = Structura.load(configFile, AppConfig.class);

                assertEquals("", config.appName()); // Invalid but accepted
                assertEquals(-1, config.port()); // Invalid but accepted
            } finally {
                // Restore original processor
                Structura.with(originalProcessor);
            }
        }

        @Test
        @DisplayName("Should support custom processor with validation enabled")
        void shouldSupportCustomProcessorWithValidationEnabled() throws IOException {
            String yamlWithValidData = """
                app-name: "ValidApp"
                port: 8080
                database:
                  host: "localhost"
                  port: 5432
                  database: "validdb"
                """;

            Path configFile = tempDir.resolve("valid-config.yml");
            Files.writeString(configFile, yamlWithValidData);

            // Configure processor with validation explicitly enabled
            var validatingProcessor = Structura.builder()
                    .withValidation(true)
                    .build();

            var originalProcessor = getCurrentProcessor();

            try {
                Structura.with(validatingProcessor);

                AppConfig config = Structura.load(configFile, AppConfig.class);

                assertEquals("ValidApp", config.appName());
                assertEquals(8080, config.port());
                assertEquals("localhost", config.database().host());
            } finally {
                // Restore original processor
                Structura.with(originalProcessor);
            }
        }

        private StructuraProcessor getCurrentProcessor() {
            return Structura.builder().build();
        }
    }

    @Nested
    @DisplayName("End-to-End Workflow Validation")
    class EndToEndWorkflowTest {

        @Test
        @DisplayName("Should handle complete file-to-object workflow with nested structures")
        void shouldHandleCompleteFileToObjectWorkflow() throws IOException {
            String complexYaml = """
                app-name: "ComplexWorkflowApp"
                port: 8080
                database:
                  host: "complex.db.example.com"
                  port: 5432
                  database: "complex_workflow_db"
                """;

            Path configFile = tempDir.resolve("complex-workflow.yml");
            Files.writeString(configFile, complexYaml);

            // Complete workflow: File → YAML → Object → Validation
            AppConfig config = Structura.load(configFile, AppConfig.class);

            // Verify all levels of the object hierarchy
            assertNotNull(config);
            assertEquals("ComplexWorkflowApp", config.appName());
            assertEquals(8080, config.port());

            assertNotNull(config.database());
            assertEquals("complex.db.example.com", config.database().host());
            assertEquals(5432, config.database().port());
            assertEquals("complex_workflow_db", config.database().database());
        }

        @Test
        @DisplayName("Should handle enum workflow with partial data")
        void shouldHandleEnumWorkflowWithPartialData() throws IOException {
            String partialEnumYaml = """
                debug:
                  description: "Debug from file"
                info:
                  priority: 42
                warn: {}
                error:
                  description: "Error from file"
                  priority: 99
                """;

            Path enumFile = tempDir.resolve("partial-enum.yml");
            Files.writeString(enumFile, partialEnumYaml);

            // Complete enum workflow: File → YAML → Enum population
            Structura.loadEnum(enumFile, LogLevel.class);

            // Verify enum constants have been populated correctly
            assertEquals("Debug from file", LogLevel.DEBUG.description);
            assertEquals(0, LogLevel.DEBUG.priority); // Default value

            assertEquals("unknown", LogLevel.INFO.description); // Default value
            assertEquals(42, LogLevel.INFO.priority);

            assertEquals("unknown", LogLevel.WARN.description); // Default value
            assertEquals(0, LogLevel.WARN.priority); // Default value

            assertEquals("Error from file", LogLevel.ERROR.description);
            assertEquals(99, LogLevel.ERROR.priority);
        }

        @Test
        @DisplayName("Should validate file extension handling")
        void shouldValidateFileExtensionHandling() throws IOException {
            String yaml = """
                app-name: "ExtensionTest"
                port: 3000
                database:
                  host: "localhost"
                  port: 5432
                  database: "testdb"
                """;

            Path yamlFile = tempDir.resolve("config.yaml");
            Path ymlFile = tempDir.resolve("config.yml");
            Path noExtFile = tempDir.resolve("config");

            Files.writeString(yamlFile, yaml);
            Files.writeString(ymlFile, yaml);
            Files.writeString(noExtFile, yaml);

            // All should work regardless of extension
            AppConfig yamlConfig = Structura.load(yamlFile, AppConfig.class);
            AppConfig ymlConfig = Structura.load(ymlFile, AppConfig.class);
            AppConfig noExtConfig = Structura.load(noExtFile, AppConfig.class);

            assertEquals("ExtensionTest", yamlConfig.appName());
            assertEquals("ExtensionTest", ymlConfig.appName());
            assertEquals("ExtensionTest", noExtConfig.appName());
        }
    }
}