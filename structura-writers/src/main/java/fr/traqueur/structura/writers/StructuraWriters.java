package fr.traqueur.structura.writers;

import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.writers.exceptions.StructuraWriterException;
import fr.traqueur.structura.writers.factory.DefaultInstanceFactory;
import fr.traqueur.structura.writers.serializer.LoadableSerializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Main entry point for the structura-writers module.
 * Provides static methods to write a {@link Loadable} instance to a YAML file,
 * or to generate a default file from {@code @Default*} annotations.
 */
public final class StructuraWriters {

    private static final LoadableSerializer SERIALIZER = new LoadableSerializer();
    private static final DefaultInstanceFactory DEFAULT_FACTORY = new DefaultInstanceFactory();

    private StructuraWriters() {}

    /**
     * Serializes {@code config} to YAML and writes it to {@code file}.
     * Creates the file if it does not exist, overwrites it otherwise.
     *
     * @throws StructuraWriterException if the write fails
     */
    public static void write(Path file, Loadable config) {
        Objects.requireNonNull(file, "file cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        String yaml = SERIALIZER.toYaml(config);
        try {
            Files.writeString(file, yaml, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new StructuraWriterException("Failed to write configuration to: " + file.toAbsolutePath(), e);
        }
    }

    /**
     * Creates a default instance of {@code configClass} using {@code @Default*} annotations,
     * then writes it to {@code file}. Does not check whether the file already exists.
     *
     * @throws StructuraWriterException if instance creation or the write fails
     */
    public static <T extends Loadable> void saveDefault(Path file, Class<T> configClass) {
        Objects.requireNonNull(file, "file cannot be null");
        Objects.requireNonNull(configClass, "configClass cannot be null");

        T defaultInstance = DEFAULT_FACTORY.createDefault(configClass);
        write(file, defaultInstance);
    }
}
