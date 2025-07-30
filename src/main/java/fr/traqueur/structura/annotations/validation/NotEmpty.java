package fr.traqueur.structura.annotations.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify that a field or parameter must not be empty.
 * This can be used on parameters or fields to enforce validation rules.
 */
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotEmpty {
    String message() default "Value cannot be empty";
}