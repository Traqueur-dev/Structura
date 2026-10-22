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
     * <p><b>With {@code inline = true}</b>, the sub-type is the one registered key present in the section,
     * next to the other keys, and there is no discriminator key ({@link #key()} is not used):</p>
     * <pre>
     * &#64;Polymorphic(useKey = true, inline = true)
     * public sealed interface Control extends Loadable permits Toggle, Select {}
     * public record Toggle(String target) implements Control {}
     * public record Select(&#64;Options(inline = true) Map&lt;String, String&gt; choice) implements Control {}
     *
     * public record Button(String label, &#64;Options(inline = true, optional = true) Control control) implements Loadable {}
     *
     * buttons:
     *   - { label: Players, toggle: view.chat-players }   # Toggle: a single value fills the only component
     *   - { label: Chat, select: { tab: chat } }          # Select: the value of the key is its section
     *   - { label: Decor }                                # control is null: it is optional
     * </pre>
     * <p>Which node the sub-type reads: when it has a component named like the key
     * ({@code Choice(List<String> choice, String initial)} registered as {@code choice}), the whole section
     * ({@code tab: {choice: [chat, shop], initial: chat}}); otherwise the value of the key — a section, or a
     * single value read into its only component. Exactly one registered key must be present: none is
     * {@code MISSING} (or null for an optional inline component), two are an error at the second one.
     * Outside an inline component (a map value, a list element, a plain component), the value's own section
     * holds the key. In strict mode the registered keys are known, so a typo is suggested one of them.</p>
     *
     * @return true if YAML keys should be used as discriminator values, false otherwise
     */
    boolean useKey() default false;

}
