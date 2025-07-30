package fr.traqueur.structura.annotations.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify the maximum value for a numeric field.
 * This can be used on parameters or fields to enforce validation rules.
 */
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Max {
    long value();
    String message() default "Value must not exceed {value}";
}