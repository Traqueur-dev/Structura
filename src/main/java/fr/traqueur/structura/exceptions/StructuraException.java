package fr.traqueur.structura.exceptions;

/**
 * Custom exception class for Structura API.
 * This exception is thrown when there is an error related to the Structura API.
 */
public class StructuraException extends RuntimeException {

    /**
     * Constructs a new StructuraException with the specified detail message.
     *
     * @param message the detail message
     */
    public StructuraException(String message) {
        super(message);
    }

    /**
     * Constructs a new StructuraException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of the exception
     */
    public StructuraException(String message, Throwable cause) {
        super(message, cause);
    }
}
