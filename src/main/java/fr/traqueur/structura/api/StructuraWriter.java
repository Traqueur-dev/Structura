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
     * Serializes all constants of a configurable enum to YAML.
     *
     * @param file      destination path
     * @param enumClass enum type implementing {@link Loadable}
     */
    <E extends Enum<E> & Loadable> void writeEnum(Path file, Class<E> enumClass);

    /**
     * Serializes all constants of a configurable enum to YAML, filling any
     * {@code null} field from its {@code @Default*} annotation before writing.
     *
     * <p>The enum counterpart of {@link #saveDefault}: produces a complete,
     * editable default template rather than the enum's current field state.</p>
     *
     * @param file      destination path
     * @param enumClass enum type implementing {@link Loadable}
     * @param <E>       a configurable enum type implementing {@link Loadable}
     */
    <E extends Enum<E> & Loadable> void saveDefaultEnum(Path file, Class<E> enumClass);

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

    /**
     * Rewrites {@code file} with {@code config}, keeping what a person wrote in it: comments, the order
     * of the keys, the quoting of unchanged values. A key the configuration no longer has is removed,
     * a new key goes after the existing ones. Creates the file if it does not exist.
     *
     * @param file the file to rewrite
     * @param config the configuration to write
     */
    default void update(Path file, Loadable config) {
        throw new UnsupportedOperationException("This writer does not support update()");
    }

    /**
     * Writes into {@code file} only what {@code config} changes from {@code defaults} — the shape of a
     * server file read over the default shipped in a jar — keeping the comments and the order of the
     * keys already in the file. Sections are compared key by key, lists and values as a whole, exactly
     * as layered reading merges them.
     *
     * @param file the file to rewrite
     * @param config the configuration to write
     * @param defaults the defaults it is read over, of the same type
     */
    default void writeOverrides(Path file, Loadable config, Loadable defaults) {
        throw new UnsupportedOperationException("This writer does not support writeOverrides()");
    }
}
