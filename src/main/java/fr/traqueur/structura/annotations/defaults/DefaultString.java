package fr.traqueur.structura.annotations.defaults;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify a default boolean value for a parameter or field.
 * This can be used in various contexts where a default boolean value is needed.
 */
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface DefaultString {
    /**
     * The default string value.
     *
     * @return the default string value
     */
    String value();
}