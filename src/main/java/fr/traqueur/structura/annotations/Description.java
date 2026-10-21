package fr.traqueur.structura.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes a component, or a record, for the {@link fr.traqueur.structura.schema.Schema schema}
 * of a configuration: shown as the documentation of the key in an editor's autocompletion.
 *
 * <pre>
 * public record ShopSettings(
 *         &#64;Description("Items shown on one page of the shop") &#64;DefaultInt(27) int maxItems
 * ) implements Loadable {}
 * </pre>
 *
 * <p>{@code @Options(description = "...")} says the same; {@code @Description} wins when both are given.</p>
 */
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Description {

    /**
     * @return the description
     */
    String value();
}
