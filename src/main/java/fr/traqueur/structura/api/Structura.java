package fr.traqueur.structura.api;

import fr.traqueur.structura.StructuraProcessor;
import fr.traqueur.structura.Updater;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.mapping.FieldMapper;
import fr.traqueur.structura.registries.CustomReaderRegistry;
import fr.traqueur.structura.registries.PolymorphicRegistries;
import fr.traqueur.structura.schema.Schema;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

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
     * Lazy, thread-safe holder for the optional {@link StructuraWriter} SPI.
     * The JVM guarantees that the inner class is initialized only once,
     * and only when first accessed.
     */
    private static final class WriterHolder {
        static final StructuraWriter INSTANCE = ServiceLoader
                .load(StructuraWriter.class, Structura.class.getClassLoader())
                .findFirst()
                .orElse(null);
    }

    /** Returns the {@link StructuraWriter} resolved by the SPI, or throws if absent. */
    private static StructuraWriter requireWriter() {
        StructuraWriter writer = WriterHolder.INSTANCE;
        if (writer == null) {
            throw new StructuraException(
                    "Structura write operations require the 'structura-writers' module on the classpath. " +
                    "Add it as a dependency to use Structura.write() or Structura.saveDefault()."
            );
        }
        return writer;
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
     *   <li>A read stops at the first error</li>
     *   <li>A key that no component reads is ignored</li>
     * </ul>
     */
    public static class StructuraBuilder {
        private boolean validateOnParse = true;
        private boolean collectErrors = false;
        private boolean strictKeys = false;
        private CustomReaderRegistry readers;
        private PolymorphicRegistries polymorphics;
        private boolean validateNested = false;

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
         * Sets whether a read carries on past an error to report every error of the document at once.
         *
         * <p>When enabled, a document with errors throws a {@link fr.traqueur.structura.exceptions.StructuraErrors}
         * listing them all, each with its path and position, so that a whole file is fixed in one go.</p>
         *
         * @param collect true to collect every error, false to stop at the first one (the default)
         * @return this builder instance for method chaining
         */
        public StructuraBuilder collectErrors(boolean collect) {
            this.collectErrors = collect;
            return this;
        }

        /**
         * Sets whether a key that no component reads is an error rather than silently ignored.
         *
         * <p>When enabled, a typo such as {@code max-item: 54} is reported as
         * {@code unknown key 'max-item' under 'shop' (did you mean 'max-items'?)} instead of leaving
         * the default value applied without anyone knowing. Inline components, key components,
         * polymorphic sub-types, map values and list elements are all checked; the keys of a map are
         * free, and an inline map absorbs every remaining key. A record can also be made strict on
         * its own with {@link fr.traqueur.structura.annotations.Strict @Strict}.</p>
         *
         * @param strict true to refuse unknown keys, false to ignore them (the default)
         * @return this builder instance for method chaining
         */
        public StructuraBuilder strictKeys(boolean strict) {
            this.strictKeys = strict;
            return this;
        }

        /**
         * Sets whether validation also checks the records held by lists, sets, maps and polymorphic
         * components: a {@code @Min(1)} on the price of each element of a {@code List<Offer>} is then
         * enforced, reported as {@code offers[2].price}.
         *
         * <p>Off by default so that configurations accepted before 2.7.0 stay accepted.</p>
         *
         * @param nested true to validate nested records too
         * @return this builder instance for method chaining
         */
        public StructuraBuilder validateNested(boolean nested) {
            this.validateNested = nested;
            return this;
        }

        /**
         * Gives the processor a reader registry of its own, looked up before the global one, so that two
         * plugins of the same server, or two tests running in parallel, do not share their readers.
         *
         * <pre>
         * CustomReaderRegistry readers = CustomReaderRegistry.create();
         * readers.register(Component.class, MiniMessage.miniMessage()::deserialize);
         * StructuraProcessor processor = Structura.builder().readers(readers).build();
         * </pre>
         *
         * @param registry the registry, from {@link CustomReaderRegistry#create()}
         * @return this builder instance for method chaining
         */
        public StructuraBuilder readers(CustomReaderRegistry registry) {
            this.readers = registry;
            return this;
        }

        /**
         * Gives the processor polymorphic registries of its own, looked up before the global ones.
         *
         * <pre>
         * PolymorphicRegistries types = PolymorphicRegistries.create();
         * types.register(Reward.class, registry -&gt; registry.register("money", MoneyReward.class));
         * StructuraProcessor processor = Structura.builder().polymorphics(types).build();
         * </pre>
         *
         * @param registries the registries, from {@link PolymorphicRegistries#create()}
         * @return this builder instance for method chaining
         */
        public StructuraBuilder polymorphics(PolymorphicRegistries registries) {
            this.polymorphics = registries;
            return this;
        }

        /**
         * Builds a new {@link StructuraProcessor} with the specified configurations.
         *
         * @return a new instance of {@link StructuraProcessor}
         */
        public StructuraProcessor build() {
            return new StructuraProcessor(validateOnParse, collectErrors, strictKeys, readers, polymorphics,
                    validateNested);
        }
    }

    /**
     * The key a record component is read from — the exact rule Structura uses everywhere, for tools
     * (a key checker, a schema generator) that must land on the same keys.
     *
     * <p>{@code @Options(name = "...")} when given; otherwise a hyphen goes between a lowercase letter
     * and the uppercase letter that follows it, then the whole name is lowercased. Nothing else splits
     * a name — not a digit, not a run of capitals:</p>
     * <pre>
     * maxItems     → max-items
     * httpURL      → http-url
     * HTTPServer   → httpserver
     * myHTTPServer → my-httpserver
     * level2Cap    → level2cap
     * </pre>
     *
     * @param component the record component
     * @return its key in the document
     */
    public static String keyOf(RecordComponent component) {
        return FieldMapper.keyOf(component);
    }

    /**
     * The key an enum constant is read from: its name, snake_case converted to kebab-case
     * ({@code MYSQL_DATABASE → mysql-database}).
     *
     * @param constant the enum constant
     * @return its key in the document
     */
    public static String keyOf(Enum<?> constant) {
        return FieldMapper.keyOf(constant);
    }

    /**
     * Describes what a record reads, without reimplementing the walk: each key as written in the
     * document, its type, whether it is required, its default value and its description
     * ({@link fr.traqueur.structura.annotations.Description @Description} or {@code @Options(description)}),
     * the sub-types of a polymorphic value, the elements of a list and the values of a map.
     *
     * <pre>
     * Files.writeString(Path.of("shop.schema.json"), Structura.schema(ShopSettings.class).toJsonSchema());
     * </pre>
     *
     * @param type a record implementing Loadable, or an enum implementing Loadable
     * @return its schema
     */
    public static Schema schema(Class<?> type) {
        return PROCESSOR.schema(type);
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
     * Gets the name a source is shown under in the position of an error: the path as given.
     *
     * @param source the source object
     * @return its name, such as {@code plugins/shop/config.yml}
     */
    private static String getDisplayName(Object source) {
        return switch (source) {
            case Path p -> p.toString();
            case File f -> f.getPath();
            case String s -> s;
            default -> null;
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

    public static <E extends Enum<E> & Loadable> void writeEnum(Path file, Class<E> enumClass) {
        requireWriter().writeEnum(file, enumClass);
    }

    /**
     * Serializes all constants of a configurable enum to YAML, filling any
     * {@code null} field from its {@code @Default*} annotation before writing.
     *
     * <p>The enum counterpart of {@link #saveDefault}: produces a complete,
     * editable default template rather than the enum's current field state.</p>
     *
     * <p>Requires the {@code structura-writers} module to be present on the classpath.
     * Throws {@link StructuraException} if the module is absent.</p>
     *
     * @param file      destination path
     * @param enumClass enum type implementing {@link Loadable}
     * @param <E>       a configurable enum type implementing {@link Loadable}
     * @throws StructuraException if the writers module is absent or the write fails
     */
    public static <E extends Enum<E> & Loadable> void saveDefaultEnum(Path file, Class<E> enumClass) {
        requireWriter().saveDefaultEnum(file, enumClass);
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
            PROCESSOR.parseEnum(content, enumClass, getDisplayName(source));
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
     * Parses a document read in layers, the last one on top — a default shipped in a jar, then the
     * server's file — without rewriting any text. Sections are merged key by key at every depth;
     * a list or a single value of an upper layer replaces the one below. Each error names the layer,
     * and the line in that layer, of the faulty value.
     *
     * <pre>
     * ShopSettings settings = Structura.parse(List.of(
     *         Source.ofResource("/config.yml"),
     *         Source.of(dataFolder.resolve("config.yml"))
     * ), ShopSettings.class);
     * </pre>
     *
     * @param sources     the layers, from the bottom (defaults) to the top (overrides)
     * @param configClass the class of the configuration to parse
     * @param <T>         the type of the configuration, which must implement {@link Loadable}
     * @return an instance of T populated with the merged data
     * @throws StructuraException if there is an error during parsing or validation
     */
    public static <T extends Loadable> T parse(List<Source> sources, Class<T> configClass) {
        return PROCESSOR.parse(sources, configClass);
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
            return PROCESSOR.parse(content, configClass, getDisplayName(source));
        } catch (IOException e) {
            throw new StructuraException("Unable to read file: " + getSourceName(source), e);
        }
    }

    // -------------------------------------------------------------------------
    // Write API  (requires structura-writers on the classpath)
    // -------------------------------------------------------------------------

    /**
     * Serializes {@code config} to YAML and writes it to {@code file}.
     * Creates the file if it does not exist, overwrites it otherwise.
     *
     * <p>Requires the {@code structura-writers} module to be present on the classpath.
     * Throws {@link StructuraException} if the module is absent.</p>
     *
     * @param file   destination path
     * @param config the {@link Loadable} record to serialize
     * @throws StructuraException if the writers module is absent or the write fails
     */
    public static void write(Path file, Loadable config) {
        requireWriter().write(file, config);
    }

    /**
     * Rewrites {@code file} with {@code config} while keeping what a person wrote in it: comments, the
     * order of the keys, the quoting of unchanged values. A key the configuration no longer has is
     * removed, a new key goes after the existing ones. Creates the file if it does not exist. Keys are
     * written with the same names the reader reads ({@link #keyOf(RecordComponent)}).
     *
     * <p>Limits, from SnakeYAML's comment support: the comments attached to a removed key go with it,
     * and the spaces before a comment at the end of a line are reduced to one.</p>
     *
     * <p>Requires the {@code structura-writer} module on the classpath.</p>
     *
     * @param file   the file to rewrite
     * @param config the configuration to write
     * @throws StructuraException if the writers module is absent or the write fails
     */
    public static void update(Path file, Loadable config) {
        requireWriter().update(file, config);
    }

    /**
     * Writes into {@code file} only what {@code config} changes from {@code defaults} — the shape of a
     * server file read over the default shipped in a jar ({@link #parse(List, Class)}) — keeping the
     * comments and the order of the keys already in the file. Sections are compared key by key, lists
     * and values as a whole, as layered reading merges them: reading the file over the defaults gives
     * {@code config} back.
     *
     * <pre>
     * ShopSettings defaults = Structura.parse(List.of(Source.ofResource("/config.yml")), ShopSettings.class);
     * Structura.writeOverrides(dataFolder.resolve("config.yml"), edited, defaults);
     * </pre>
     *
     * <p>Requires the {@code structura-writer} module on the classpath.</p>
     *
     * @param file     the server file to rewrite
     * @param config   the configuration to write
     * @param defaults the defaults it is read over, of the same type
     * @throws StructuraException if the writers module is absent or the write fails
     */
    public static void writeOverrides(Path file, Loadable config, Loadable defaults) {
        requireWriter().writeOverrides(file, config, defaults);
    }

    /**
     * Builds a default instance of {@code configClass} from its {@code @Default*} annotations,
     * then writes it to {@code file}.
     *
     * <p>Does <em>not</em> check whether {@code file} already exists —
     * that responsibility belongs to the caller:</p>
     * <pre>
     * if (!Files.exists(configFile)) {
     *     Structura.saveDefault(configFile, MyConfig.class);
     * }
     * </pre>
     *
     * <p>Requires the {@code structura-writers} module to be present on the classpath.
     * Throws {@link StructuraException} if the module is absent.</p>
     *
     * @param file        destination path
     * @param configClass the record class to instantiate with default values
     * @param <T>         a record type implementing {@link Loadable}
     * @throws StructuraException if the writers module is absent or the write fails
     */
    public static <T extends Loadable> void saveDefault(Path file, Class<T> configClass) {
        requireWriter().saveDefault(file, configClass);
    }
}
