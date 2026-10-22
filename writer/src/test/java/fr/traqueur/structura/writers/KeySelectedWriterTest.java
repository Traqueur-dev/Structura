package fr.traqueur.structura.writers;

import fr.traqueur.structura.StructuraProcessor;
import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.annotations.defaults.DefaultInt;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.registries.PolymorphicRegistries;
import fr.traqueur.structura.registries.PolymorphicRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The writer writes a {@code @Polymorphic(useKey = true, inline = true)} value as the reader reads it:
 * the sub-type's key, then its value.
 */
@DisplayName("Writer: polymorphic selected by the key present")
class KeySelectedWriterTest {

    @Polymorphic(useKey = true, inline = true)
    public sealed interface Control extends Loadable permits Toggle, Select, Step {}

    public record Toggle(String target) implements Control {}

    public record Select(@Options(inline = true) Map<String, String> choice) implements Control {}

    public record Step(@Options(inline = true) Map<String, Integer> by) implements Control {}

    public record Button(String label,
                         @Options(optional = true) String when,
                         @Options(inline = true, optional = true) Control control) implements Loadable {}

    @Polymorphic(useKey = true, inline = true)
    public sealed interface StateDef extends Loadable permits Choice, Range, Flag {}

    public record Choice(List<String> choice, @Options(optional = true) String initial) implements StateDef {}

    public record Range(List<Integer> range, @DefaultInt(0) int initial) implements StateDef {}

    public record Flag(boolean flag) implements StateDef {}

    public record Menu(List<Button> buttons, Map<String, StateDef> state, Control main, List<Control> extras)
            implements Loadable {}

    @BeforeAll
    static void register() {
        PolymorphicRegistry.create(Control.class, r -> {
            r.register("toggle", Toggle.class);
            r.register("select", Select.class);
            r.register("step", Step.class);
        });
        PolymorphicRegistry.create(StateDef.class, r -> {
            r.register("choice", Choice.class);
            r.register("range", Range.class);
            r.register("flag", Flag.class);
        });
    }

    @AfterAll
    static void unregister() {
        PolymorphicRegistries.global().unregister(Control.class);
        PolymorphicRegistries.global().unregister(StateDef.class);
    }

    @TempDir
    Path dir;

    private static Menu menu() {
        return new Menu(
                List.of(new Button("Joueurs", "tab=chat", new Toggle("view.chat-players")),
                        new Button("Tchat", null, new Select(Map.of("tab", "chat"))),
                        new Button("+1", null, new Step(Map.of("quantity", 1))),
                        new Button("Décor", null, null)),
                Map.of("tab", new Choice(List.of("chat", "notifications"), "chat"),
                        "quantity", new Range(List.of(1, 64), 1),
                        "preview", new Flag(false)),
                new Toggle("main"),
                List.of(new Step(Map.of("q", 2)), new Select(Map.of("tab", "shop"))));
    }

    @Test
    @DisplayName("writes the sub-type's key, then its value: the single value of a one-component sub-type")
    void writtenForm() throws Exception {
        Path file = dir.resolve("menu.yml");

        Structura.write(file, menu());

        Map<String, Object> written = new Yaml().load(Files.readString(file));
        assertEquals(List.of(
                Map.of("label", "Joueurs", "when", "tab=chat", "toggle", "view.chat-players"),
                Map.of("label", "Tchat", "select", Map.of("tab", "chat")),
                Map.of("label", "+1", "step", Map.of("quantity", 1)),
                Map.of("label", "Décor")
        ), written.get("buttons"));
        assertEquals(Map.of(
                "tab", Map.of("choice", List.of("chat", "notifications"), "initial", "chat"),
                "quantity", Map.of("range", List.of(1, 64), "initial", 1),
                "preview", Map.of("flag", false)
        ), written.get("state"));
        assertEquals(Map.of("toggle", "main"), written.get("main"));
        assertEquals(List.of(Map.of("step", Map.of("q", 2)), Map.of("select", Map.of("tab", "shop"))),
                written.get("extras"));
    }

    @Test
    @DisplayName("reads back, in strict mode, to the same configuration")
    void roundTrip() throws Exception {
        Path file = dir.resolve("menu.yml");
        Menu menu = menu();

        Structura.write(file, menu);

        StructuraProcessor strict = Structura.builder().strictKeys(true).build();
        assertEquals(menu, strict.parse(Files.readString(file), Menu.class));
    }
}
