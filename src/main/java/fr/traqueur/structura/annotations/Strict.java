package fr.traqueur.structura.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Makes a record strict: a key of its node that none of its components reads is an error
 * ({@code UNKNOWN_KEY}) instead of being silently ignored, and so is any such key in the records
 * read beneath it.
 *
 * <p>The same as {@code Structura.builder().strictKeys(true)}, limited to the part of the document
 * this record reads. A typo then no longer goes unnoticed with the default value applied:</p>
 * <pre>
 * 3:3: unknown key 'max-item' under 'shop' (did you mean 'max-items'?)
 * </pre>
 *
 * <p>On a configurable enum, a key that is no constant and a key of a constant that none of its
 * fields reads are both errors.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Strict {
}
