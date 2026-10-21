package fr.traqueur.structura.exceptions;

/**
 * Custom exception class for validation errors in Structura API.
 * This exception is thrown when there is a validation error in the Structura API.
 * Its {@link #kind() kind} is always {@link Kind#INVALID}.
 */
public class ValidationException extends StructuraException {

    /**
     * Constructs a new ValidationException with the specified detail message.
     *
     * @param message the detail message
     */
    public ValidationException(String message) {
        super(Kind.INVALID, message, null);
    }

    /**
     * Constructs a new ValidationException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of the exception
     */
    public ValidationException(String message, Throwable cause) {
        super(Kind.INVALID, message, null, cause);
    }

    /**
     * Constructs a new ValidationException raised at a given path of a document.
     *
     * @param message the detail message
     * @param path    the path of the refused key in the document
     */
    public ValidationException(String message, String path) {
        super(Kind.INVALID, message, path);
    }
}
