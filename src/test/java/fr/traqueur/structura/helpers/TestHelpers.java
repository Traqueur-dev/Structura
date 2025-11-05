package fr.traqueur.structura.helpers;

import fr.traqueur.structura.StructuraProcessor;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.conversion.ValueConverter;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.factory.RecordInstanceFactory;
import fr.traqueur.structura.mapping.FieldMapper;
import fr.traqueur.structura.registries.PolymorphicRegistry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Common helper methods for tests to reduce duplication and improve readability.
 */
public final class TestHelpers {

    private TestHelpers() {} // Prevent instantiation

    // ==================== Processor Creation ====================

    /**
     * Creates a StructuraProcessor with validation enabled.
     */
    public static StructuraProcessor createProcessor() {
        return new StructuraProcessor(true);
    }

    /**
     * Creates a StructuraProcessor with validation disabled.
     */
    public static StructuraProcessor createProcessorWithoutValidation() {
        return new StructuraProcessor(false);
    }

    // ==================== ValueConverter Creation ====================

    /**
     * Creates a ValueConverter with RecordInstanceFactory properly configured.
     * This is the standard setup used in most tests.
     */
    public static ValueConverter createValueConverter() {
        FieldMapper fieldMapper = new FieldMapper();
        RecordInstanceFactory recordFactory = new RecordInstanceFactory(fieldMapper);
        ValueConverter valueConverter = new ValueConverter(recordFactory);
        recordFactory.setValueConverter(valueConverter);
        return valueConverter;
    }

    /**
     * Clears all polymorphic registries.
     * Useful for test isolation.
     */
    public static void clearAllRegistries() {
        try {
            var field = PolymorphicRegistry.class.getDeclaredField("REGISTRIES");
            field.setAccessible(true);
            ((java.util.Map<?, ?>) field.get(null)).clear();
        } catch (Exception e) {
            fail("Failed to clear registries: " + e.getMessage());
        }
    }

    // ==================== Parsing Helpers ====================

    /**
     * Parses YAML and returns the result, asserting no exception is thrown.
     */
    public static <T extends Loadable> T parseSuccessfully(
            StructuraProcessor processor,
            String yaml,
            Class<T> configClass) {
        assertDoesNotThrow(() -> processor.parse(yaml, configClass),
            "Expected parsing to succeed but it threw an exception");
        return processor.parse(yaml, configClass);
    }

    /**
     * Parses YAML and expects a StructuraException with specific message.
     */
    public static void parseWithExpectedException(
            StructuraProcessor processor,
            String yaml,
            Class<? extends Loadable> configClass,
            String expectedMessagePart) {
        StructuraException exception = assertThrows(StructuraException.class,
            () -> processor.parse(yaml, configClass),
            "Expected StructuraException to be thrown");

        assertTrue(exception.getMessage().contains(expectedMessagePart),
            String.format("Expected exception message to contain '%s' but was '%s'",
                expectedMessagePart, exception.getMessage()));
    }

    /**
     * Parses YAML and expects any exception of the specified type.
     */
    public static <E extends Exception> E parseWithExpectedException(
            StructuraProcessor processor,
            String yaml,
            Class<? extends Loadable> configClass,
            Class<E> exceptionClass) {
        return assertThrows(exceptionClass,
            () -> processor.parse(yaml, configClass),
            "Expected " + exceptionClass.getSimpleName() + " to be thrown");
    }

    // ==================== Enum Parsing Helpers ====================

    /**
     * Parses enum YAML successfully.
     */
    public static <E extends Enum<E> & Loadable> void parseEnumSuccessfully(
            StructuraProcessor processor,
            String yaml,
            Class<E> enumClass) {
        assertDoesNotThrow(() -> processor.parseEnum(yaml, enumClass),
            "Expected enum parsing to succeed but it threw an exception");
    }

    /**
     * Parses enum YAML and expects a StructuraException.
     */
    public static void parseEnumWithExpectedException(
            StructuraProcessor processor,
            String yaml,
            Class<? extends Enum<?>> enumClass,
            String expectedMessagePart) {
        StructuraException exception = assertThrows(StructuraException.class,
            () -> processor.parseEnum(yaml, (Class) enumClass),
            "Expected StructuraException to be thrown");

        assertTrue(exception.getMessage().contains(expectedMessagePart),
            String.format("Expected exception message to contain '%s' but was '%s'",
                expectedMessagePart, exception.getMessage()));
    }

    // ==================== File Operations ====================

    /**
     * Creates a temporary YAML file with the given content.
     */
    public static Path createTempYamlFile(String content) throws IOException {
        Path tempFile = Files.createTempFile("test-config-", ".yaml");
        Files.writeString(tempFile, content);
        return tempFile;
    }

    /**
     * Creates a temporary YAML file and returns it as a File object.
     */
    public static File createTempYamlFileAsFile(String content) throws IOException {
        return createTempYamlFile(content).toFile();
    }

    /**
     * Deletes a temporary file, ignoring errors.
     */
    public static void deleteTempFile(Path path) {
        try {
            if (path != null) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Ignore cleanup errors
        }
    }

    // ==================== Polymorphic Registry Helpers ====================

    /**
     * Creates a polymorphic registry with the given implementations.
     * Cleans up any existing registry first.
     */
    public static <T extends Loadable> void createPolymorphicRegistry(
            Class<T> interfaceClass,
            Consumer<PolymorphicRegistry<T>> configurator) {
        // Note: We can't easily clean up existing registries due to static storage
        // Tests should use unique interface types or be careful with ordering
        PolymorphicRegistry.create(interfaceClass, configurator);
    }

    /**
     * Safely creates a polymorphic registry, catching errors if it already exists.
     * Returns the registry after ensuring it exists.
     */
    public static <T extends Loadable> PolymorphicRegistry<T> getOrCreatePolymorphicRegistry(
            Class<T> interfaceClass,
            Consumer<PolymorphicRegistry<T>> configurator) {
        try {
            return PolymorphicRegistry.get(interfaceClass);
        } catch (StructuraException e) {
            // Registry doesn't exist, create it
            PolymorphicRegistry.create(interfaceClass, configurator);
            return PolymorphicRegistry.get(interfaceClass);
        }
    }

    // ==================== Assertion Helpers ====================

    /**
     * Asserts that all fields of the config match the expected values.
     */
    public static void assertConfigEquals(
            Object expected,
            Object actual,
            String message) {
        assertEquals(expected, actual, message);
    }

    /**
     * Asserts that a string contains all specified substrings.
     */
    public static void assertContainsAll(String actual, String... expectedSubstrings) {
        for (String expected : expectedSubstrings) {
            assertTrue(actual.contains(expected),
                String.format("Expected string to contain '%s' but was '%s'", expected, actual));
        }
    }

    /**
     * Asserts that a nullable value is present and equals the expected value.
     */
    public static <T> void assertPresentAndEquals(T expected, T actual, String fieldName) {
        assertNotNull(actual, fieldName + " should not be null");
        assertEquals(expected, actual, fieldName + " should equal expected value");
    }

    /**
     * Asserts that a collection has the expected size.
     */
    public static void assertCollectionSize(int expectedSize, java.util.Collection<?> collection, String collectionName) {
        assertNotNull(collection, collectionName + " should not be null");
        assertEquals(expectedSize, collection.size(),
            String.format("%s should have %d elements but has %d", collectionName, expectedSize, collection.size()));
    }

    /**
     * Asserts that a map has the expected size.
     */
    public static void assertMapSize(int expectedSize, java.util.Map<?, ?> map, String mapName) {
        assertNotNull(map, mapName + " should not be null");
        assertEquals(expectedSize, map.size(),
            String.format("%s should have %d entries but has %d", mapName, expectedSize, map.size()));
    }

    // ==================== Performance Helpers ====================

    /**
     * Measures the execution time of a task.
     */
    public static long measureExecutionTime(Runnable task) {
        long start = System.nanoTime();
        task.run();
        long end = System.nanoTime();
        return (end - start) / 1_000_000; // Convert to milliseconds
    }

    /**
     * Asserts that a task completes within the specified time (in milliseconds).
     */
    public static void assertCompletesWithin(long maxMillis, Runnable task, String taskDescription) {
        long actualTime = measureExecutionTime(task);
        assertTrue(actualTime <= maxMillis,
            String.format("%s should complete within %dms but took %dms",
                taskDescription, maxMillis, actualTime));
    }

    // ==================== Validation Helpers ====================

    /**
     * Asserts that validation fails with a specific message.
     */
    public static void assertValidationFails(
            Runnable validationTask,
            String expectedMessagePart) {
        Exception exception = assertThrows(Exception.class, validationTask::run,
            "Expected validation to fail");

        assertTrue(exception.getMessage().contains(expectedMessagePart),
            String.format("Expected validation message to contain '%s' but was '%s'",
                expectedMessagePart, exception.getMessage()));
    }
}
