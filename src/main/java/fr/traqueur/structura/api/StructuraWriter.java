package fr.traqueur.structura.api;

import java.nio.file.Path;

/**
 * SPI contract for serializing {@link Loadable} records back to YAML.
 *
 * <p>Defined in the core module but <strong>not implemented here</strong>.
 * The optional {@code structura-writers} module provides an implementation
 * discovered at runtime via {@link java.util.ServiceLoader}.</p>
 *
 * <p>If {@code structura-writers} is absent from the classpath,
 * calls to {@link Structura#write} and {@link Structura#saveDefault}
 * throw a {@link fr.traqueur.structura.exceptions.StructuraException}.</p>
 */
public interface StructuraWriter {

    /**
     * Serializes {@code config} to YAML and writes it to {@code file}.
     *
     * @param file   destination path (created or overwritten)
     * @param config the record to serialize
     */
    void write(Path file, Loadable config);

    /**
     * Builds a default instance of {@code configClass} from its {@code @Default*}
     * annotations, then writes it to {@code file}.
     *
     * <p>Does <em>not</em> check whether {@code file} already exists —
     * that responsibility belongs to the caller.</p>
     *
     * @param file        destination path
     * @param configClass the record class to instantiate with default values
     * @param <T>         a record type implementing {@link Loadable}
     */
    <T extends Loadable> void saveDefault(Path file, Class<T> configClass);
}
