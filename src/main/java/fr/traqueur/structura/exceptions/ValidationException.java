package fr.traqueur.structura.exceptions;

/**
 * Custom exception class for validation errors in Structura API.
 * This exception is thrown when there is a validation error in the Structura API.
 */
public class ValidationException extends StructuraException {

    /**
     * Constructs a new ValidationException with the specified detail message.
     *
     * @param message the detail message
     */
    public ValidationException(String message) {
        super(message);
    }

    /**
     * Constructs a new ValidationException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of the exception
     */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}