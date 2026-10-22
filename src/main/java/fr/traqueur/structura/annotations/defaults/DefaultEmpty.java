package fr.traqueur.structura.annotations.defaults;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Makes a {@code List}, {@code Set}, {@code Collection} or {@code Map} component empty when its key is
 * absent, instead of missing (or null when it is optional). The empty value is immutable.
 *
 * <pre>
 * public record Dialog(&#64;DefaultEmpty List&lt;Body&gt; body, &#64;DefaultEmpty Map&lt;String, Input&gt; inputs) implements Loadable {}
 * </pre>
 *
 * <p>On any other type it is an error, reported when the record is read.</p>
 */
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface DefaultEmpty {
}
