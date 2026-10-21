package fr.traqueur.structura.writers.writer;

import fr.traqueur.structura.types.TypeToken;
import fr.traqueur.structura.writers.exceptions.StructuraWriterException;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * What a {@link ContextualWriter} is given besides the value: the type declared for it, its path in
 * the document, and Structura's own serialization for the values it holds.
 */
public interface WriteContext {

    /**
     * @return the type declared for the value, such as {@code Resolvable<Team>}; the runtime class of
     *         the value when nothing more precise is declared
     */
    TypeToken<?> target();

    /**
     * @return the path of the value in the document, such as {@code shop.offers[2].reward}
     */
    String path();

    /**
     * Serializes a value held by this one with Structura's own rules — a record, an enum, a list, a
     * value with its own writer — at the same path.
     *
     * @param value the value to serialize
     * @return its YAML form
     */
    Object write(Object value);

    /**
     * Serializes a value held by this one, as a value of the given declared type.
     *
     * @param value the value to serialize
     * @param type the type declared for it, such as {@link #typeArgument(int)}
     * @return its YAML form
     */
    Object write(Object value, Type type);

    /**
     * A type argument of the declared type: {@code Team} at index 0 of {@code Resolvable<Team>}.
     *
     * @param index the index of the type argument
     * @return the type argument
     * @throws StructuraWriterException if the declared type has no such argument
     */
    default Type typeArgument(int index) {
        Type type = target().getType();
        if (type instanceof ParameterizedType parameterized && index < parameterized.getActualTypeArguments().length) {
            return parameterized.getActualTypeArguments()[index];
        }
        throw new StructuraWriterException("Type " + type.getTypeName() + " has no type argument at index " + index);
    }
}
