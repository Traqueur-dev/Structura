package fr.traqueur.structura.writers;

import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.StructuraWriter;
import fr.traqueur.structura.writers.exceptions.StructuraWriterException;
import fr.traqueur.structura.writers.factory.DefaultInstanceFactory;
import fr.traqueur.structura.writers.serializer.LoadableSerializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * SPI implementation of {@link StructuraWriter} provided by the {@code structura-writers} module.
 *
 * <p>Registered via {@code META-INF/services/fr.traqueur.structura.api.StructuraWriter}
 * so that {@link fr.traqueur.structura.api.Structura} discovers it automatically through
 * {@link java.util.ServiceLoader} whenever this module is on the classpath.</p>
 *
 * <p>This class is the implementation behind the public write API; it is <strong>not</strong>
 * meant to be used directly. The only supported entry point is
 * {@link fr.traqueur.structura.api.Structura#write} / {@link fr.traqueur.structura.api.Structura#saveDefault}.</p>
 */
public final class YamlStructuraWriter implements StructuraWriter {

    private final LoadableSerializer serializer = new LoadableSerializer();
    private final DefaultInstanceFactory defaultFactory = new DefaultInstanceFactory();

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public YamlStructuraWriter() {}

    @Override
    public void write(Path file, Loadable config) {
        Objects.requireNonNull(file, "file cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        String yaml = serializer.toYaml(config);
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(file, yaml, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new StructuraWriterException("Failed to write configuration to: " + file.toAbsolutePath(), e);
        }
    }

    @Override
    public <T extends Loadable> void saveDefault(Path file, Class<T> configClass) {
        Objects.requireNonNull(file, "file cannot be null");
        Objects.requireNonNull(configClass, "configClass cannot be null");

        T defaultInstance = defaultFactory.createDefault(configClass);
        write(file, defaultInstance);
    }
}