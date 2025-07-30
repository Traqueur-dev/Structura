package fr.traqueur.structura.annotations.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify the size constraints for a field or parameter.
 * This can be used on parameters or fields to enforce validation rules.
 * Work with both collections and strings.
 */
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Size {
    /**
     * The minimum size that the annotated field or parameter must have.
     * Default is 0, meaning no minimum size.
     *
     * @return the minimum size
     */
    int min() default 0;

    /**
     * The maximum size that the annotated field or parameter can have.
     * Default is Integer.MAX_VALUE, meaning no maximum size.
     *
     * @return the maximum size
     */
    int max() default Integer.MAX_VALUE;

    /**
     * The message to be returned if the validation fails.
     * This message can include placeholders for the min and max values.
     *
     * @return the validation failure message
     */
    String message() default "Size must be between {min} and {max}";
}