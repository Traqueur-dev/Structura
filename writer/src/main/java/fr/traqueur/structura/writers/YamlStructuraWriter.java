package fr.traqueur.structura.writers;

import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.StructuraWriter;
import fr.traqueur.structura.writers.exceptions.StructuraWriterException;
import fr.traqueur.structura.registries.PolymorphicRegistries;
import fr.traqueur.structura.writers.factory.DefaultInstanceFactory;
import fr.traqueur.structura.writers.registries.CustomWriterRegistry;
import fr.traqueur.structura.writers.serializer.DocumentUpdater;
import fr.traqueur.structura.writers.serializer.LoadableSerializer;
import fr.traqueur.structura.writers.serializer.Overrides;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Objects;

/**
 * SPI implementation of {@link StructuraWriter} provided by the {@code structura-writers} module.
 *
 * <p>Registered via {@code META-INF/services/fr.traqueur.structura.api.StructuraWriter}
 * so that {@link fr.traqueur.structura.api.Structura} discovers it automatically through
 * {@link java.util.ServiceLoader} whenever this module is on the classpath.</p>
 *
 * <p>This class is the implementation behind the public write API
 * ({@link fr.traqueur.structura.api.Structura#write}, {@code update}, {@code writeOverrides}...). Use it
 * directly only through {@link #with} to get a writer with registries of its own.</p>
 */
public final class YamlStructuraWriter implements StructuraWriter {

    private final LoadableSerializer serializer;
    private final DefaultInstanceFactory defaultFactory = new DefaultInstanceFactory();
    private final DocumentUpdater updater = new DocumentUpdater();

    /** No-arg constructor required by {@link java.util.ServiceLoader}: the global registries only. */
    public YamlStructuraWriter() {
        this(null, null);
    }

    private YamlStructuraWriter(CustomWriterRegistry writers, PolymorphicRegistries polymorphics) {
        this.serializer = new LoadableSerializer(writers, polymorphics);
    }

    /**
     * A writer with registries of its own, looked up before the global ones, so that two plugins of the
     * same server, or two tests running in parallel, do not share their writers or sub-types:
     * <pre>
     * CustomWriterRegistry writers = CustomWriterRegistry.create();
     * writers.register(Component.class, MiniMessage.miniMessage()::serialize);
     * StructuraWriter writer = YamlStructuraWriter.with(writers, null);
     * writer.update(file, config);
     * </pre>
     *
     * @param writers the writer registry, from {@link CustomWriterRegistry#create()}, or null for the global one only
     * @param polymorphics the polymorphic registries, from {@code PolymorphicRegistries.create()}, or null
     * @return the writer
     */
    public static StructuraWriter with(CustomWriterRegistry writers, PolymorphicRegistries polymorphics) {
        return new YamlStructuraWriter(writers, polymorphics);
    }

    @Override
    public void write(Path file, Loadable config) {
        Objects.requireNonNull(file, "file cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        writeYaml(file, serializer.toYaml(config));
    }

    @Override
    public <T extends Loadable> void saveDefault(Path file, Class<T> configClass) {
        Objects.requireNonNull(file, "file cannot be null");
        Objects.requireNonNull(configClass, "configClass cannot be null");

        T defaultInstance = defaultFactory.createDefault(configClass);
        write(file, defaultInstance);
    }

    public <E extends Enum<E> & Loadable> void writeEnum(Path file, Class<E> enumClass) {
        Objects.requireNonNull(file, "file cannot be null");
        Objects.requireNonNull(enumClass, "enumClass cannot be null");
        writeYaml(file, serializer.toYamlEnum(enumClass));
    }

    @Override
    public <E extends Enum<E> & Loadable> void saveDefaultEnum(Path file, Class<E> enumClass) {
        Objects.requireNonNull(file, "file cannot be null");
        Objects.requireNonNull(enumClass, "enumClass cannot be null");
        writeYaml(file, serializer.toYamlEnumDefault(enumClass));
    }

    @Override
    public void update(Path file, Loadable config) {
        Objects.requireNonNull(file, "file cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        writeYaml(file, updater.update(readExisting(file), serializer.toData(config)));
    }

    @Override
    public void writeOverrides(Path file, Loadable config, Loadable defaults) {
        Objects.requireNonNull(file, "file cannot be null");
        Objects.requireNonNull(config, "config cannot be null");
        Objects.requireNonNull(defaults, "defaults cannot be null");
        if (config.getClass() != defaults.getClass()) {
            throw new StructuraWriterException("The configuration (" + config.getClass().getName()
                    + ") and its defaults (" + defaults.getClass().getName() + ") must be of the same type");
        }

        Map<String, Object> overrides = Overrides.of(serializer.toData(config), serializer.toData(defaults));
        writeYaml(file, updater.update(readExisting(file), overrides));
    }

    private static String readExisting(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new StructuraWriterException("Failed to read configuration from: " + file.toAbsolutePath(), e);
        }
    }

    private void writeYaml(Path file, String yaml) {
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(file, yaml, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new StructuraWriterException("Failed to write configuration to: " + file.toAbsolutePath(), e);
        }
    }
}