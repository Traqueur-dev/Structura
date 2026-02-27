package fr.traqueur.structura.references;

import java.util.Collection;

/**
 * Provides the collection of objects from which a {@link Reference} will be resolved.
 *
 * @param <T> the type of the provided objects
 */
@FunctionalInterface
public interface ReferenceProvider<T> {

    /**
     * Provides the collection of objects from which a {@link Reference} will be resolved.
     *
     * @return the collection of objects
     */
    Collection<T> provide();
}