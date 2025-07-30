package fr.traqueur.structura.annotations.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify the minimum value for a numeric field.
 * This can be used on parameters or fields to enforce validation rules.
 */
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Min {
    /**
     * The minimum value that the annotated field or parameter must have.
     *
     * @return the minimum value
     */
    long value();

    /**
     * The message to be returned if the validation fails.
     * This message can include placeholders for the value.
     *
     * @return the validation failure message
     */
    String message() default "Value must be at least {value}";
}








