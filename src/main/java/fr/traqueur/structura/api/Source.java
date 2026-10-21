package fr.traqueur.structura.api;

import fr.traqueur.structura.exceptions.StructuraException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A YAML text and the name it is shown under in error positions, read as one layer of a document.
 *
 * <pre>
 * ShopSettings settings = processor.parse(List.of(
 *         Source.ofResource("/config.yml"),          // the default shipped in the jar
 *         Source.of(dataFolder.resolve("config.yml")) // the server's file, on top
 * ), ShopSettings.class);
 * </pre>
 *
 * @param name the name shown in error positions, such as {@code config.yml (jar)}
 * @param text the YAML content; may be empty, an empty layer changes nothing
 */
public record Source(String name, String text) {

    /**
     * @param name the name shown in error positions
     * @param text the YAML content
     */
    public Source {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(text, "text cannot be null");
    }

    /**
     * @param name the name shown in error positions, such as {@code config.yml (jar)}
     * @param text the YAML content
     * @return the source
     */
    public static Source of(String name, String text) {
        return new Source(name, text);
    }

    /**
     * Reads a file, shown under its path as given.
     *
     * @param file the file
     * @return the source
     * @throws StructuraException if the file cannot be read
     */
    public static Source of(Path file) {
        try {
            return new Source(file.toString(), Files.readString(file));
        } catch (IOException e) {
            throw new StructuraException("Unable to read file: " + file.toAbsolutePath(), e);
        }
    }

    /**
     * Reads a resource of the classpath, shown as {@code path (jar)}.
     *
     * @param resourcePath the resource path, such as {@code /config.yml}
     * @return the source
     * @throws StructuraException if the resource cannot be found or read
     */
    public static Source ofResource(String resourcePath) {
        try (InputStream stream = Source.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new StructuraException("Resource not found: " + resourcePath);
            }
            String name = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
            return new Source(name + " (jar)", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new StructuraException("Unable to read resource: " + resourcePath, e);
        }
    }
}
