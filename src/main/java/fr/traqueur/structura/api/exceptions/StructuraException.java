package fr.traqueur.structura.api.exceptions;

public class StructuraException extends RuntimeException {
    public StructuraException(String message) {
        super(message);
    }
    public StructuraException(String message, Throwable cause) {
        super(message, cause);
    }
}
