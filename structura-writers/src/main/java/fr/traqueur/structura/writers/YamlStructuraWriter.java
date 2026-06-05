package fr.traqueur.structura.writers;

import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.StructuraWriter;

import java.nio.file.Path;

/**
 * SPI implementation of {@link StructuraWriter} provided by the {@code structura-writers} module.
 *
 * <p>Registered via {@code META-INF/services/fr.traqueur.structura.api.StructuraWriter}
 * so that {@link fr.traqueur.structura.api.Structura} discovers it automatically through
 * {@link java.util.ServiceLoader} whenever this module is on the classpath.</p>
 *
 * <p>All actual logic is delegated to {@link StructuraWriters}, keeping this class
 * as a thin bridge between the SPI contract and the existing public API.</p>
 */
public final class YamlStructuraWriter implements StructuraWriter {

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public YamlStructuraWriter() {}

    @Override
    public void write(Path file, Loadable config) {
        StructuraWriters.write(file, config);
    }

    @Override
    public <T extends Loadable> void saveDefault(Path file, Class<T> configClass) {
        StructuraWriters.saveDefault(file, configClass);
    }
}
