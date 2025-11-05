package fr.traqueur.structura.api;

import fr.traqueur.structura.StructuraProcessor;
import fr.traqueur.structura.Updater;
import fr.traqueur.structura.exceptions.StructuraException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Structura is a library for parsing and validating YAML configurations.
 * It provides methods to parse enums and configuration classes from YAML content,
 * files, or resources, with optional validation.
 *
 * <p>Usage example:</p>
 * <pre>
 * Structura.parseEnum(yamlContent, MyEnum.class);
 * Structura.loadEnum(filePath, MyEnum.class);
 * Structura.loadEnumFromResource("/path/to/resource.yaml", MyEnum.class);
 * Structura.parse(yamlContent, MyConfig.class);
 * Structura.load(filePath, MyConfig.class);
 * Structura.loadFromResource("/path/to/resource.yaml", MyConfig.class);
 * </pre>
 * <p>To customize the behavior of Structura, you can create a custom {@link StructuraProcessor} using the {@link StructuraBuilder}:</p>
 * <pre>
 * StructuraProcessor customProcessor = Structura.builder()
 *     .withValidation(false) // Disable validation if needed
 *     .build();
 * Structura.with(customProcessor);
 * </pre>
 * * <p>Note: All methods throw {@link StructuraException} in case of errors during parsing or loading.</p>
 */
public class Structura {

    private static StructuraProcessor PROCESSOR = builder().build();

    static {
        Updater.checkUpdates();
    }

    /**
     * Private constructor to prevent instantiation.
     * Use the {@link StructuraBuilder} to create an instance of {@link StructuraProcessor}.
     */
    private Structura() {}

    /**
     * Creates a new {@link StructuraBuilder} to configure and build a {@link StructuraProcessor}.
     *
     * @return a new instance of {@link StructuraBuilder}
     */
    public static StructuraBuilder builder() {
        return new StructuraBuilder();
    }

    /**
     * Builder class for creating a {@link StructuraProcessor} with custom configurations.
     *
     * <p>Default settings:</p>
     * <ul>
     *   <li>Validation is enabled during parsing</li>
     * </ul>
     */
    public static class StructuraBuilder {
        private boolean validateOnParse = true;

        /**
         * Sets whether to validate the configuration during parsing.
         *
         * @param validate true to enable validation, false to disable it
         * @return this builder instance for method chaining
         */
        public StructuraBuilder withValidation(boolean validate) {
            this.validateOnParse = validate;
            return this;
        }

        /**
         * Builds a new {@link StructuraProcessor} with the specified configurations.
         *
         * @return a new instance of {@link StructuraProcessor}
         */
        public StructuraProcessor build() {
            return new StructuraProcessor(validateOnParse);
        }
    }

    /**
     * Sets the {@link StructuraProcessor} to be used by Structura.
     * This allows you to customize the behavior of Structura globally.
     *
     * @param processor the {@link StructuraProcessor} to use
     */
    public static void with(StructuraProcessor processor) {
        PROCESSOR = processor;
    }

    /**
     * Reads content from various sources (Path, File, or resource).
     *
     * @param source the source to read from (Path, File, or String for resources)
     * @return the content as a String
     * @throws IOException if reading fails
     */
    private static String readContent(Object source) throws IOException {
        return switch (source) {
            case Path path -> Files.readString(path);
            case File file -> Files.readString(file.toPath());
            case String resourcePath -> {
                try (var stream = Structura.class.getResourceAsStream(resourcePath)) {
                    if (stream == null) {
                        throw new StructuraException("Resource not found: " + resourcePath);
                    }
                    yield new String(stream.readAllBytes());
                }
            }
            default -> throw new IllegalArgumentException("Unsupported source type: " + source.getClass().getName());
        };
    }

    /**
     * Gets a user-friendly name for a source object.
     *
     * @param source the source object
     * @return a descriptive name for error messages
     */
    private static String getSourceName(Object source) {
        return switch (source) {
            case Path p -> p.toAbsolutePath().toString();
            case File f -> f.getAbsolutePath();
            case String s -> s;
            default -> "unknown";
        };
    }

    /**
     * Parses a YAML content string into an enum of type E.
     *
     * @param yamlContent the YAML content as a string
     * @param enumClass   the class of the enum to parse
     * @param <E>         the type of the enum, which must implement {@link Loadable}
     * @throws StructuraException if there is an error during parsing
     */
    public static <E extends Enum<E> & Loadable> void parseEnum(String yamlContent, Class<E> enumClass) {
        PROCESSOR.parseEnum(yamlContent, enumClass);
    }

    /**
     * Loads an enum of type E from a YAML file.
     *
     * @param filePath  the path to the YAML file
     * @param enumClass the class of the enum to load
     * @param <E>       the type of the enum, which must implement {@link Loadable}
     * @throws StructuraException if there is an error during file reading or parsing
     */
    public static <E extends Enum<E> & Loadable> void loadEnum(Path filePath, Class<E> enumClass) {
        loadEnumInternal(filePath, enumClass);
    }

    /**
     * Loads an enum of type E from a YAML file.
     *
     * @param file      the YAML file
     * @param enumClass the class of the enum to load
     * @param <E>       the type of the enum, which must implement {@link Loadable}
     * @throws StructuraException if there is an error during file reading or parsing
     */
    public static <E extends Enum<E> & Loadable> void loadEnum(File file, Class<E> enumClass) {
        loadEnumInternal(file, enumClass);
    }

    /**
     * Loads an enum of type E from a YAML resource.
     *
     * @param resourcePath the path to the YAML resource
     * @param enumClass    the class of the enum to load
     * @param <E>          the type of the enum, which must implement {@link Loadable}
     * @throws StructuraException if there is an error during resource reading or parsing
     */
    public static <E extends Enum<E> & Loadable> void loadEnumFromResource(String resourcePath, Class<E> enumClass) {
        loadEnumInternal(resourcePath, enumClass);
    }

    /**
     * Internal implementation for loading enums from various sources.
     *
     * @param source the source to load from (Path, File, or String for resources)
     * @param enumClass the class of the enum to load
     * @param <E> the type of the enum, which must implement {@link Loadable}
     * @throws StructuraException if there is an error during reading or parsing
     */
    private static <E extends Enum<E> & Loadable> void loadEnumInternal(Object source, Class<E> enumClass) {
        try {
            String content = readContent(source);
            parseEnum(content, enumClass);
        } catch (IOException e) {
            throw new StructuraException("Unable to read file: " + getSourceName(source), e);
        }
    }

    /**
     * Parses a YAML content string into a configuration class of type T.
     *
     * @param yamlContent the YAML content as a string
     * @param configClass the class of the configuration to parse
     * @param <T>         the type of the configuration, which must implement {@link Loadable}
     * @return an instance of T populated with the parsed data
     * @throws StructuraException if there is an error during parsing or validation
     */
    public static <T extends Loadable> T parse(String yamlContent, Class<T> configClass) {
        return PROCESSOR.parse(yamlContent, configClass);
    }

    /**
     * Loads a configuration class of type T from a YAML file.
     *
     * @param filePath   the path to the YAML file
     * @param configClass the class of the configuration to load
     * @param <T>        the type of the configuration, which must implement {@link Loadable}
     * @return an instance of T populated with the parsed data
     * @throws StructuraException if there is an error during file reading or parsing
     */
    public static <T extends Loadable> T load(Path filePath, Class<T> configClass) {
        return loadInternal(filePath, configClass);
    }

    /**
     * Loads a configuration class of type T from a YAML file.
     *
     * @param file       the YAML file
     * @param configClass the class of the configuration to load
     * @param <T>        the type of the configuration, which must implement {@link Loadable}
     * @return an instance of T populated with the parsed data
     * @throws StructuraException if there is an error during file reading or parsing
     */
    public static <T extends Loadable> T load(File file, Class<T> configClass) {
        return loadInternal(file, configClass);
    }

    /**
     * Loads a configuration class of type T from a YAML resource.
     *
     * @param resourcePath the path to the YAML resource
     * @param configClass  the class of the configuration to load
     * @param <T>          the type of the configuration, which must implement {@link Loadable}
     * @return an instance of T populated with the parsed data
     * @throws StructuraException if there is an error during resource reading or parsing
     */
    public static <T extends Loadable> T loadFromResource(String resourcePath, Class<T> configClass) {
        return loadInternal(resourcePath, configClass);
    }

    /**
     * Internal implementation for loading configurations from various sources.
     *
     * @param source the source to load from (Path, File, or String for resources)
     * @param configClass the class of the configuration to load
     * @param <T> the type of the configuration, which must implement {@link Loadable}
     * @return an instance of T populated with the parsed data
     * @throws StructuraException if there is an error during reading or parsing
     */
    private static <T extends Loadable> T loadInternal(Object source, Class<T> configClass) {
        try {
            String content = readContent(source);
            return parse(content, configClass);
        } catch (IOException e) {
            throw new StructuraException("Unable to read file: " + getSourceName(source), e);
        }
    }
}
