package fr.traqueur.structura.readers;

import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.types.TypeToken;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * What a {@link ContextualReader} is given: the value as YAML typed it, the type requested and the
 * path of the value in the document.
 */
public interface ReadContext {

    /**
     * @return the value as YAML typed it: an {@code Integer}, {@code Long}, {@code Double},
     *         {@code Boolean}, {@code String}… or, for a section or a list, a {@code Map} or a {@code List}
     */
    Object raw();

    /**
     * @return the text form of the value ({@code "100"} for both {@code 100} and {@code "100"})
     */
    String text();

    /**
     * @return the full type requested, such as {@code Resolvable<Integer>} or {@code List<Team>}
     */
    TypeToken<?> target();

    /**
     * @return the path of the value in the document, such as {@code shop.offers[2].price}
     */
    String path();

    /**
     * Converts the same value to another type with Structura's own rules, so that a generic reader
     * can read its type argument as any other value would be read.
     *
     * @param type the type to convert to
     * @return the converted value
     * @throws StructuraException if the value cannot be converted
     */
    Object convert(Type type);

    /**
     * A type argument of the requested type: {@code Integer} at index 0 of {@code Resolvable<Integer>}.
     *
     * @param index the index of the type argument
     * @return the type argument
     * @throws StructuraException if the requested type has no such argument
     */
    default Type typeArgument(int index) {
        Type type = target().getType();
        if (type instanceof ParameterizedType parameterized && index < parameterized.getActualTypeArguments().length) {
            return parameterized.getActualTypeArguments()[index];
        }
        throw new StructuraException("Type " + type.getTypeName() + " has no type argument at index " + index);
    }
}
