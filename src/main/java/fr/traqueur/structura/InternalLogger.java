package fr.traqueur.structura;

import fr.traqueur.structura.api.logging.Logger;

public class InternalLogger implements Logger {
    @Override
    public void info(String message) {
        System.out.println("[INFO] " + message);
    }

    @Override
    public void severe(String message) {
        System.out.println("[SEVERE] " + message);
    }
}
