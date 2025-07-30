package fr.traqueur.structura.api.exceptions;

public class ValidationException extends StructuraException {
    public ValidationException(String message) {
        super(message);
    }
    
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}