package fr.traqueur.structura.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify options for a field or parameter.
 * This can be used to indicate whether the field is a key, its name, and if it is optional.
 */
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Options {
    /**
     * Indicates whether the annotated field or parameter is a key.
     * Defaults to false.
     *
     * @return true if it is a key, false otherwise
     */
    boolean isKey() default false;

    /**
     * Specifies the name of the field or parameter.
     * If not provided, the default is an empty string.
     *
     * @return the name of the field or parameter
     */
    String name() default "";

    /**
     * Indicates whether the annotated field or parameter is optional.
     * Defaults to false.
     *
     * @return true if it is optional, false otherwise
     */
    boolean optional() default false;
}