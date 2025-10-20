package fr.traqueur.structura.readers;

import fr.traqueur.structura.exceptions.StructuraException;

/**
 * A functional interface for custom type conversion from String values.
 * Readers enable Structura to convert YAML string values into custom types
 * that are not natively supported by the library.
 *
 * <p>This is particularly useful for external libraries like Adventure API,
 * where YAML strings need to be converted to complex objects.</p>
 *
 * <p>Example usage with Adventure API:</p>
 * <pre>
 * CustomReaderRegistry.getInstance().register(
 *     Component.class,
 *     str -&gt; MiniMessage.miniMessage().deserialize(str)
 * );
 * </pre>
 *
 * @param <T> the target type to convert to
 */
@FunctionalInterface
public interface Reader<T> {

    /**
     * Reads a string value and converts it to the target type.
     *
     * @param value the string value to convert
     * @return the converted object of type T
     * @throws StructuraException if conversion fails for any reason
     */
    T read(String value) throws StructuraException;
}