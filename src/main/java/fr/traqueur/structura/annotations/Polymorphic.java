package fr.traqueur.structura.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Indicates that a class uses polymorphic serialization/deserialization.
 * The 'key' element specifies the field name used to determine the type.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Polymorphic {

    /**
     * The key used to identify the type in the serialized data.
     * Default is "type".
     *
     * @return the key name
     */
    String key() default "type";

    /**
     * Determines whether the discriminator key is inline (at the same level as the field)
     * or nested (inside the field value).
     *
     * When inline = false (default):
     * database:
     *   type: mysql
     *   host: localhost
     *
     * When inline = true:
     * type: mysql
     * database:
     *   host: localhost
     *
     * @return true if the discriminator key is inline, false otherwise
     */
    boolean inline() default false;

}
