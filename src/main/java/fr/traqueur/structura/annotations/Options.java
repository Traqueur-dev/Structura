package fr.traqueur.structura.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify options for a field or parameter.
 * This can be used to indicate whether the field is a key, its name, and if it is optional.
 */
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Options {
    boolean isKey() default false;
    String name() default "";
    boolean optional() default false;
}