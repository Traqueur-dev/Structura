package fr.traqueur.structura.annotations.defaults;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies a default reference for a {@code Reference<T>} field when the YAML key is absent.
 *
 * <p>Both the referenced type and the key must be provided:</p>
 * <pre>
 * public record GameConfig(
 *     &#64;DefaultReference(type = Arena.class, key = "default-arena") Reference&lt;Arena&gt; arena,
 *     int maxPlayers
 * ) implements Loadable {}
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface DefaultReference {

    /**
     * The type of the referenced object. Must match the generic type of the {@code Reference<T>} field.
     *
     * @return the referenced type
     */
    Class<?> type();

    /**
     * The string key used to resolve the default reference from the registered provider.
     *
     * @return the key
     */
    String key();
}