package fr.traqueur.structura.api;

import fr.traqueur.structura.api.exceptions.StructuraException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Structura {

    private static final StructuraProcessor PROCESSOR = new StructuraProcessor();

    private Structura() {}

    public static <T extends Settings> T parse(String yamlContent, Class<T> configClass) {
        try {
            return PROCESSOR.parse(yamlContent, configClass);
        } catch (Exception e) {
            throw new StructuraException("Erreur lors du parsing de la configuration", e);
        }
    }

    public static <T extends Settings> T load(String filePath, Class<T> configClass) {
        try {
            String content = Files.readString(Paths.get(filePath));
            return parse(content, configClass);
        } catch (IOException e) {
            throw new StructuraException("Impossible de lire le fichier: " + filePath, e);
        }
    }

    public static <T extends Settings> T loadFromResource(String resourcePath, Class<T> configClass) {
        try (var stream = Structura.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new StructuraException("Resource not found: " + resourcePath);
            }
            String content = new String(stream.readAllBytes());
            return parse(content, configClass);
        } catch (IOException e) {
            throw new StructuraException("Unable to read ressource " + resourcePath, e);
        }
    }
}
