package fr.traqueur.config.logger;

public class InternalLogger implements Logger{
    @Override
    public void info(String message) {
        System.out.println("[INFO] " + message);
    }

    @Override
    public void severe(String message) {
        System.out.println("[SEVERE] " + message);
    }
}
