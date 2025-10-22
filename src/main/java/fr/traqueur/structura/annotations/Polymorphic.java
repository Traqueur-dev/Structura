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

    /**
     * Determines whether the YAML key should be used as the discriminator value.
     * This enables a different syntax where the map key identifies the type.
     *
     * When useKeyAsDiscriminator = false (default):
     * metadata:
     *   - type: food
     *     nutrition: 8
     *   - type: potion
     *     color: "#FF0000"
     *
     * When useKeyAsDiscriminator = true:
     * For simple fields (ItemMetadata trim):
     * trim:              # "trim" is the discriminator value
     *   material: DIAMOND
     *   pattern: VEX
     *
     * For collections (List&lt;ItemMetadata&gt; metadata):
     * metadata:
     *   food:            # "food" is the discriminator value
     *     nutrition: 8
     *   potion:          # "potion" is the discriminator value
     *     color: "#FF0000"
     *
     * For maps (Map&lt;String, ItemMetadata&gt; metadata):
     * metadata:
     *   food:            # "food" is both the map key and discriminator value
     *     nutrition: 8
     *   potion:          # "potion" is both the map key and discriminator value
     *     color: "#FF0000"
     *
     * @return true if YAML keys should be used as discriminator values, false otherwise
     */
    boolean useKey() default false;

}
