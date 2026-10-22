package fr.traqueur.structura.writers;

import fr.traqueur.structura.annotations.defaults.DefaultEmpty;
import fr.traqueur.structura.annotations.defaults.DefaultString;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The writer and saveDefault follow {@code @DefaultEmpty}.
 */
@DisplayName("Writer: @DefaultEmpty")
class DefaultEmptyWriterTest {

    public record Input(String label) implements Loadable {}

    public record Dialog(@DefaultString("Hello") String title,
                         @DefaultEmpty List<String> body,
                         @DefaultEmpty Map<String, Input> inputs) implements Loadable {}

    public record Sizes(Collection<Integer> sizes) implements Loadable {}

    @TempDir
    Path dir;

    @Test
    @DisplayName("saveDefault writes empty collections, which read back")
    void saveDefault() throws Exception {
        Path file = dir.resolve("dialog.yml");

        Structura.saveDefault(file, Dialog.class);

        assertEquals(Map.of("title", "Hello", "body", List.of(), "inputs", Map.of()),
                new Yaml().load(Files.readString(file)));
        assertEquals(new Dialog("Hello", List.of(), Map.of()), Structura.load(file, Dialog.class));
    }

    @Test
    @DisplayName("writeOverrides leaves out a collection still empty, as its default")
    void overrides() throws Exception {
        Path file = dir.resolve("dialog.yml");
        Dialog defaults = Structura.parse("title: Hello", Dialog.class);

        Structura.writeOverrides(file, new Dialog("Hi", List.of(), Map.of("name", new Input("Name"))), defaults);

        assertEquals(Map.of("title", "Hi", "inputs", Map.of("name", Map.of("label", "Name"))),
                new Yaml().load(Files.readString(file)));
    }

    @Test
    @DisplayName("a plain Collection component is written as a list and read back")
    void collectionRoundTrip() throws Exception {
        Path file = dir.resolve("sizes.yml");

        Structura.write(file, new Sizes(List.of(3, 1, 2)));

        assertEquals(Map.of("sizes", List.of(3, 1, 2)), new Yaml().load(Files.readString(file)));
        assertEquals(List.of(3, 1, 2), List.copyOf(Structura.load(file, Sizes.class).sizes()));
    }

    @Test
    @DisplayName("write and read give the same record back")
    void roundTrip() throws Exception {
        Path file = dir.resolve("dialog.yml");
        Dialog dialog = new Dialog("Hi", List.of("a", "b"), Map.of());

        Structura.write(file, dialog);

        assertEquals(dialog, Structura.load(file, Dialog.class));
    }
}
