package fr.traqueur.structura.writers.writer;

import fr.traqueur.structura.writers.exceptions.StructuraWriterException;

/**
 * Converts a Java value to its YAML-serializable form.
 * Symmetric counterpart to {@link fr.traqueur.structura.readers.Reader}.
 *
 * @param <T> the Java type to serialize
 */
@FunctionalInterface
public interface Writer<T> {

    Object write(T value) throws StructuraWriterException;
}
