package fr.traqueur.structura.exceptions;

/**
 * Custom exception class for validation errors in Structura API.
 * This exception is thrown when there is a validation error in the Structura API.
 */
public class ValidationException extends StructuraException {
    public ValidationException(String message) {
        super(message);
    }
    
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}