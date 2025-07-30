package fr.traqueur.structura.api;

import fr.traqueur.structura.api.exceptions.StructuraException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Structura {

    private static final StructuraProcessor PROCESSOR = new StructuraProcessor();

    private Structura() {}

    public static <E extends Enum<E> & Settings> void parseEnum(String yamlContent, Class<E> enumClass) {
        try {
            PROCESSOR.parseEnum(yamlContent, enumClass);
        } catch (Exception e) {
            throw new StructuraException("Erreur lors du parsing de l'enum", e);
        }
    }

    public static <E extends Enum<E> & Settings> void loadEnum(Path filePath, Class<E> enumClass) {
        try {
            String content = Files.readString(filePath);
            parseEnum(content, enumClass);
        } catch (IOException e) {
            throw new StructuraException("Impossible de lire le fichier: " + filePath.toAbsolutePath(), e);
        }
    }

    public static <E extends Enum<E> & Settings> void loadEnum(File file, Class<E> enumClass) {
        try {
            String content = Files.readString(file.toPath());
            parseEnum(content, enumClass);
        } catch (IOException e) {
            throw new StructuraException("Impossible de lire le fichier: " + file.getAbsolutePath(), e);
        }
    }

    public static <E extends Enum<E> & Settings> void loadEnumFromResource(String resourcePath, Class<E> enumClass) {
        try (var stream = Structura.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new StructuraException("Resource not found: " + resourcePath);
            }
            String content = new String(stream.readAllBytes());
            parseEnum(content, enumClass);
        } catch (IOException e) {
            throw new StructuraException("Unable to read ressource " + resourcePath, e);
        }
    }

    public static <T extends Settings> T parse(String yamlContent, Class<T> configClass) {
        try {
            return PROCESSOR.parse(yamlContent, configClass);
        } catch (Exception e) {
            throw new StructuraException("Erreur lors du parsing de la configuration", e);
        }
    }

    public static <T extends Settings> T load(Path filePath, Class<T> configClass) {
        try {
            String content = Files.readString(filePath);
            return parse(content, configClass);
        } catch (IOException e) {
            throw new StructuraException("Impossible de lire le fichier: " + filePath.toAbsolutePath(), e);
        }
    }

    public static <T extends Settings> T load(File file, Class<T> configClass) {
        try {
            String content = Files.readString(file.toPath());
            return parse(content, configClass);
        } catch (IOException e) {
            throw new StructuraException("Impossible de lire le fichier: " + file.getAbsolutePath(), e);
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
