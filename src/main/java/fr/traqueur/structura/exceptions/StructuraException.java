package fr.traqueur.structura.exceptions;

/**
 * Custom exception class for Structura API.
 * This exception is thrown when there is an error related to the Structura API.
 */
public class StructuraException extends RuntimeException {
    public StructuraException(String message) {
        super(message);
    }
    public StructuraException(String message, Throwable cause) {
        super(message, cause);
    }
}
