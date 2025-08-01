package fr.traqueur.structura.annotations.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify that a field or parameter must match a specific regex pattern.
 * This can be used on parameters or fields to enforce validation rules.
 */
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Pattern {
    /**
     * The regex pattern that the annotated field or parameter must match.
     *
     * @return the regex pattern
     */
    String value();

    /**
     * The message to be returned if the validation fails.
     * This message can include placeholders for the value.
     *
     * @return the validation failure message
     */
    String message() default "Value must match pattern {value}";
}