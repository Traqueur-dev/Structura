package fr.traqueur.structura.writers.exceptions;

import fr.traqueur.structura.exceptions.StructuraException;

/** Thrown by the structura-writers module when serialization or instance creation fails. */
public class StructuraWriterException extends StructuraException {

    public StructuraWriterException(String message) {
        super(message);
    }

    public StructuraWriterException(String message, Throwable cause) {
        super(message, cause);
    }
}
