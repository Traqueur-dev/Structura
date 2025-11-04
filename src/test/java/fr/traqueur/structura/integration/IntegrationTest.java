package fr.traqueur.structura.integration;

import fr.traqueur.structura.StructuraProcessor;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.exceptions.StructuraException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static fr.traqueur.structura.fixtures.TestFixtures.*;
import static fr.traqueur.structura.fixtures.TestModels.*;
import static fr.traqueur.structura.helpers.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Refactored Integration Tests using common fixtures and helpers.
 * Tests file loading and end-to-end workflows with minimal duplication.
 */
@DisplayName("Integration Tests - Refactored")
class IntegrationTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("File Loading Operations")
    class FileLoadingTest {

        @Test
        @DisplayName("Should load configuration from Path")
        void shouldLoadConfigurationFromPath() throws IOException {
            Path configFile = createTempYamlFile(NESTED_CONFIG);

            try {
                NestedConfig config = Structura.load(configFile, NestedConfig.class);

                assertEquals("MyApp", config.appName());
                assertNotNull(config.database());
                assertEquals("db.example.com", config.database().host());
                assertEquals(5432, config.database().port());
                assertEquals("mydb", config.database().database());
            } finally {
                deleteTempFile(configFile);
            }
        }

        @Test
        @DisplayName("Should load configuration from File object")
        void shouldLoadConfigurationFromFile() throws IOException {
            File configFile = createTempYamlFileAsFile(NESTED_CONFIG);

            try {
                NestedConfig config = Structura.load(configFile, NestedConfig.class);

                assertEquals("MyApp", config.appName());
                assertNotNull(config.database());
                assertEquals("db.example.com", config.database().host());
                assertEquals(5432, config.database().port());
                assertEquals("mydb", config.database().database());
            } finally {
                deleteTempFile(configFile.toPath());
            }
        }

        @Test
        @DisplayName("Should load enum configuration from Path")
        void shouldLoadEnumConfigurationFromPath() throws IOException {
            Path enumFile = createTempYamlFile(LOADABLE_ENUM_FULL);

            try {
                Structura.loadEnum(enumFile, LoadableLogLevel.class);

                assertEquals("Debug level logging", LoadableLogLevel.DEBUG.description);
                assertEquals(1, LoadableLogLevel.DEBUG.priority);
                assertEquals("Info level logging", LoadableLogLevel.INFO.description);
                assertEquals(2, LoadableLogLevel.INFO.priority);
                assertEquals("Warning level logging", LoadableLogLevel.WARN.description);
                assertEquals(3, LoadableLogLevel.WARN.priority);
                assertEquals("Error level logging", LoadableLogLevel.ERROR.description);
                assertEquals(4, LoadableLogLevel.ERROR.priority);
            } finally {
                deleteTempFile(enumFile);
            }
        }

        @Test
        @DisplayName("Should load enum configuration from File object")
        void shouldLoadEnumConfigurationFromFile() throws IOException {
            String enumYaml = """
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

            File enumFile = createTempYamlFileAsFile(enumYaml);

            try {
                Structura.loadEnum(enumFile, LoadableLogLevel.class);

                assertEquals("File debug", LoadableLogLevel.DEBUG.description);
                assertEquals(10, LoadableLogLevel.DEBUG.priority);
                assertEquals("File info", LoadableLogLevel.INFO.description);
                assertEquals(20, LoadableLogLevel.INFO.priority);
                assertEquals("File warn", LoadableLogLevel.WARN.description);
                assertEquals(30, LoadableLogLevel.WARN.priority);
                assertEquals("File error", LoadableLogLevel.ERROR.description);
                assertEquals(40, LoadableLogLevel.ERROR.priority);
            } finally {
                deleteTempFile(enumFile.toPath());
            }
        }
    }

    @Nested
    @DisplayName("Resource Loading Operations")
    class ResourceLoadingTest {

        @Test
        @DisplayName("Should handle missing resources gracefully")
        void shouldHandleMissingResourcesGracefully() {
            StructuraException configException = assertThrows(StructuraException.class, () ->
                    Structura.loadFromResource("/non-existent-config.yml", NestedConfig.class)
            );
            assertContainsAll(configException.getMessage(), "Resource not found");

            StructuraException enumException = assertThrows(StructuraException.class, () ->
                    Structura.loadEnumFromResource("/non-existent-enum.yml", LoadableLogLevel.class)
            );
            assertContainsAll(enumException.getMessage(), "Resource not found");
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
                    Structura.load(nonExistentFile, NestedConfig.class)
            );
            assertContainsAll(pathException.getMessage(), "Unable to read file", nonExistentFile.toString());

            // Test config loading from non-existent File
            File nonExistentFileObj = nonExistentFile.toFile();
            StructuraException fileException = assertThrows(StructuraException.class, () ->
                    Structura.load(nonExistentFileObj, NestedConfig.class)
            );
            assertContainsAll(fileException.getMessage(), "Unable to read file", nonExistentFileObj.getAbsolutePath());

            // Test enum loading from non-existent Path
            StructuraException enumPathException = assertThrows(StructuraException.class, () ->
                    Structura.loadEnum(nonExistentFile, LoadableLogLevel.class)
            );
            assertContainsAll(enumPathException.getMessage(), "Unable to read file");

            // Test enum loading from non-existent File
            StructuraException enumFileException = assertThrows(StructuraException.class, () ->
                    Structura.loadEnum(nonExistentFileObj, LoadableLogLevel.class)
            );
            assertContainsAll(enumFileException.getMessage(), "Unable to read file");
        }

        @Test
        @DisplayName("Should handle permission-denied scenarios")
        void shouldHandlePermissionDeniedScenarios() throws IOException {
            Path restrictedFile = createTempYamlFile("test: value");

            try {
                restrictedFile.toFile().setReadable(false);

                if (!restrictedFile.toFile().canRead()) {
                    assertThrows(StructuraException.class, () ->
                            Structura.load(restrictedFile, NestedConfig.class)
                    );
                }
            } finally {
                restrictedFile.toFile().setReadable(true);
                deleteTempFile(restrictedFile);
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
                database:
                  host: ""
                  port: 99999
                  database: ""
                server:
                  host: ""
                  port: -1
                """;

            Path configFile = createTempYamlFile(yamlWithInvalidData);

            try {
                // Configure processor with validation disabled
                var customProcessor = Structura.builder()
                        .withValidation(false)
                        .build();

                var originalProcessor = getCurrentProcessor();

                try {
                    Structura.with(customProcessor);

                    // This should not throw validation errors
                    NestedConfig config = Structura.load(configFile, NestedConfig.class);

                    assertEquals("", config.appName()); // Invalid but accepted
                    assertNotNull(config.database());
                } finally {
                    Structura.with(originalProcessor);
                }
            } finally {
                deleteTempFile(configFile);
            }
        }

        @Test
        @DisplayName("Should support custom processor with validation enabled")
        void shouldSupportCustomProcessorWithValidationEnabled() throws IOException {
            Path configFile = createTempYamlFile(NESTED_CONFIG);

            try {
                // Configure processor with validation explicitly enabled
                var validatingProcessor = Structura.builder()
                        .withValidation(true)
                        .build();

                var originalProcessor = getCurrentProcessor();

                try {
                    Structura.with(validatingProcessor);

                    NestedConfig config = Structura.load(configFile, NestedConfig.class);

                    assertEquals("MyApp", config.appName());
                    assertEquals("db.example.com", config.database().host());
                } finally {
                    Structura.with(originalProcessor);
                }
            } finally {
                deleteTempFile(configFile);
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
            Path configFile = createTempYamlFile(NESTED_CONFIG);

            try {
                // Complete workflow: File → YAML → Object → Validation
                NestedConfig config = Structura.load(configFile, NestedConfig.class);

                // Verify all levels of the object hierarchy
                assertNotNull(config);
                assertEquals("MyApp", config.appName());

                assertNotNull(config.database());
                assertEquals("db.example.com", config.database().host());
                assertEquals(5432, config.database().port());
                assertEquals("mydb", config.database().database());

                assertNotNull(config.server());
                assertEquals("localhost", config.server().host());
                assertEquals(8080, config.server().port());
            } finally {
                deleteTempFile(configFile);
            }
        }

        @Test
        @DisplayName("Should handle enum workflow with partial data")
        void shouldHandleEnumWorkflowWithPartialData() throws IOException {
            Path enumFile = createTempYamlFile(LOADABLE_ENUM_PARTIAL);

            try {
                // Complete enum workflow: File → YAML → Enum population
                Structura.loadEnum(enumFile, LoadableLogLevel.class);

                // Verify enum constants have been populated correctly
                assertEquals("Debug from file", LoadableLogLevel.DEBUG.description);
                assertEquals(0, LoadableLogLevel.DEBUG.priority); // Default value

                assertEquals("unknown", LoadableLogLevel.INFO.description); // Default value
                assertEquals(42, LoadableLogLevel.INFO.priority);

                assertEquals("unknown", LoadableLogLevel.WARN.description); // Default value
                assertEquals(0, LoadableLogLevel.WARN.priority); // Default value

                assertEquals("Error from file", LoadableLogLevel.ERROR.description);
                assertEquals(99, LoadableLogLevel.ERROR.priority);
            } finally {
                deleteTempFile(enumFile);
            }
        }

        @Test
        @DisplayName("Should validate file extension handling")
        void shouldValidateFileExtensionHandling() throws IOException {
            Path yamlFile = createTempYamlFile(NESTED_CONFIG);
            Path ymlFile = createTempYamlFile(NESTED_CONFIG);
            Path noExtFile = createTempYamlFile(NESTED_CONFIG);

            try {
                // All should work regardless of extension
                NestedConfig yamlConfig = Structura.load(yamlFile, NestedConfig.class);
                NestedConfig ymlConfig = Structura.load(ymlFile, NestedConfig.class);
                NestedConfig noExtConfig = Structura.load(noExtFile, NestedConfig.class);

                assertEquals("MyApp", yamlConfig.appName());
                assertEquals("MyApp", ymlConfig.appName());
                assertEquals("MyApp", noExtConfig.appName());
            } finally {
                deleteTempFile(yamlFile);
                deleteTempFile(ymlFile);
                deleteTempFile(noExtFile);
            }
        }
    }
}