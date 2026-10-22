package fr.traqueur.structura;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.annotations.Strict;
import fr.traqueur.structura.annotations.defaults.DefaultInt;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.exceptions.StructuraErrors;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.exceptions.StructuraException.Kind;
import fr.traqueur.structura.registries.PolymorphicRegistries;
import fr.traqueur.structura.registries.PolymorphicRegistry;
import fr.traqueur.structura.schema.Schema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code @Polymorphic(useKey = true, inline = true)}: the registered key present in the section is the sub-type.
 */
@DisplayName("Polymorphic selected by the key present (useKey + inline)")
class KeySelectedPolymorphicTest {

    @Polymorphic(useKey = true, inline = true)
    public sealed interface Control extends Loadable permits Toggle, Select, Step {}

    public record Toggle(String target) implements Control {}

    public record Select(@Options(inline = true) Map<String, String> choice) implements Control {}

    public record Step(@Options(inline = true) Map<String, Integer> by) implements Control {}

    @Strict
    public record Button(String label,
                         @Options(optional = true) String when,
                         @Options(inline = true, optional = true) Control control) implements Loadable {}

    public record Panel(List<Button> buttons) implements Loadable {}

    @Polymorphic(useKey = true, inline = true)
    public sealed interface StateDef extends Loadable permits Choice, Range, Flag {}

    public record Choice(List<String> choice, @Options(optional = true) String initial) implements StateDef {}

    public record Range(List<Integer> range, @DefaultInt(0) int initial) implements StateDef {}

    public record Flag(boolean flag) implements StateDef {}

    public record Menu(Map<String, StateDef> state) implements Loadable {}

    /** A sub-type with several components, which cannot be read from a single value. */
    @Polymorphic(useKey = true, inline = true)
    public sealed interface Effect extends Loadable permits Sound {}

    public record Sound(String name, double volume) implements Effect {}

    public record Trigger(String id, @Options(inline = true) Effect effect) implements Loadable {}

    public record Lever(String label, @Options(inline = true) Control control) implements Loadable {}

    public record Slot(Control control, List<Control> extras) implements Loadable {}

    public record Crowded(String label, @Options(inline = true) Control control,
                          @Options(inline = true) Map<String, String> rest) implements Loadable {}

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
        PolymorphicRegistry.create(Effect.class, r -> r.register("sound", Sound.class));
    }

    @AfterAll
    static void unregister() {
        PolymorphicRegistries.global().unregister(Control.class);
        PolymorphicRegistries.global().unregister(StateDef.class);
        PolymorphicRegistries.global().unregister(Effect.class);
    }

    private final StructuraProcessor strict = Structura.builder().strictKeys(true).build();

    /** The 1-based column of a token on a 1-based line of a text. */
    private static int column(String yaml, int line, String token) {
        String text = yaml.lines().toList().get(line - 1);
        int index = text.indexOf(token);
        assertTrue(index >= 0, token + " not on line " + line);
        return index + 1;
    }

    private static void assertAt(StructuraException error, Kind kind, String path, int line, int column) {
        assertEquals(kind, error.kind(), error.getMessage());
        assertEquals(path, error.path().orElse(null), error.getMessage());
        assertEquals(line, error.line().orElse(-1), error.getMessage());
        assertEquals(column, error.column().orElse(-1), error.getMessage());
    }

    private static final String BUTTONS = """
            buttons:
              - { label: "Joueurs", when: tab=chat, toggle: view.chat-players }
              - { label: "Tchat", select: { tab: chat } }
              - { label: "+1", step: { quantity: 1 } }
              - { label: "Décor" }
            """;

    @Nested
    @DisplayName("Inline component: the key sits next to the other keys")
    class InlineComponent {

        @Test
        @DisplayName("the key present selects the sub-type; its value is the sub-type's node")
        void readsEachForm() {
            List<Button> buttons = strict.parse(BUTTONS, Panel.class).buttons();

            assertEquals(new Button("Joueurs", "tab=chat", new Toggle("view.chat-players")), buttons.get(0));
            assertEquals(new Button("Tchat", null, new Select(Map.of("tab", "chat"))), buttons.get(1));
            assertEquals(new Button("+1", null, new Step(Map.of("quantity", 1))), buttons.get(2));
            assertEquals(new Button("Décor", null, null), buttons.get(3));
        }

        @Test
        @DisplayName("a section under the key works as well as a single value")
        void sectionForm() {
            Button button = strict.parse("""
                    buttons:
                      - { label: "Joueurs", toggle: { target: view.chat } }
                    """, Panel.class).buttons().getFirst();

            assertEquals(new Toggle("view.chat"), button.control());
        }

        @Test
        @DisplayName("no registered key on a required component is MISSING, at the section")
        void missingOnRequired() {
            String yaml = """
                    label: Lever
                    """;

            StructuraException error = assertThrows(StructuraException.class, () -> Structura.parse(yaml, Lever.class));

            assertAt(error, Kind.MISSING, "control", 1, 1);
            assertEquals("1:1: 'control' is required but not provided: set one of toggle, select, step (control)",
                    error.getMessage());
        }

        @Test
        @DisplayName("two registered keys are an error at the second one")
        void twoKeys() {
            String yaml = """
                    buttons:
                      - { label: "Joueurs", toggle: view.chat, select: { tab: chat } }
                    """;

            StructuraException error = assertThrows(StructuraException.class, () -> strict.parse(yaml, Panel.class));

            assertAt(error, Kind.INVALID, "buttons[0].select", 2, column(yaml, 2, "select"));
            assertEquals("2:" + column(yaml, 2, "select")
                    + ": 'toggle' and 'select' cannot both be set under 'buttons[0]': a Control is one of toggle, select, step",
                    error.getMessage());
        }

        @Test
        @DisplayName("a single value is read into the only component; several components refuse it")
        void singleValueNeedsSingleComponent() {
            String yaml = """
                    id: door
                    sound: creak
                    """;

            StructuraException error = assertThrows(StructuraException.class, () -> Structura.parse(yaml, Trigger.class));

            assertAt(error, Kind.WRONG_TYPE, "sound", 2, 8);
            assertEquals("2:8: 'creak' cannot be read as a Sound, which reads name, volume: "
                    + "write a section under 'sound' (sound)", error.getMessage());
            assertEquals(new Sound("creak", 0.5), Structura.parse("""
                    id: door
                    sound: { name: creak, volume: 0.5 }
                    """, Trigger.class).effect());
        }

        @Test
        @DisplayName("a single value is refused by a sub-type whose only component is inline")
        void singleValueRefusedByInlineComponent() {
            String yaml = """
                    buttons:
                      - { label: "Tchat", select: chat }
                    """;

            StructuraException error = assertThrows(StructuraException.class, () -> strict.parse(yaml, Panel.class));

            assertAt(error, Kind.WRONG_TYPE, "buttons[0].select", 2, column(yaml, 2, "chat }"));
            assertTrue(error.getMessage().contains("'chat' cannot be read as a Select, which reads choice"),
                    error.getMessage());
        }
    }

    @Nested
    @DisplayName("Map value: the sub-type reads its own section")
    class MapValue {

        @Test
        @DisplayName("a sub-type with a component named like the key reads the whole section")
        void wholeSection() {
            Menu menu = strict.parse("""
                    state:
                      tab:      { choice: [chat, notifications], initial: chat }
                      quantity: { range: [1, 64], initial: 1 }
                      preview:  { flag: false }
                      page:     { range: [1, 3] }
                    """, Menu.class);

            assertEquals(new Choice(List.of("chat", "notifications"), "chat"), menu.state().get("tab"));
            assertEquals(new Range(List.of(1, 64), 1), menu.state().get("quantity"));
            assertEquals(new Flag(false), menu.state().get("preview"));
            assertEquals(new Range(List.of(1, 3), 0), menu.state().get("page"));
        }

        @Test
        @DisplayName("no registered key is MISSING at the value")
        void noKey() {
            String yaml = """
                    state:
                      tab: { initial: chat }
                    """;

            StructuraException error = assertThrows(StructuraException.class, () -> Structura.parse(yaml, Menu.class));

            assertAt(error, Kind.MISSING, "state.tab", 2, 8);
            assertEquals("2:8: a StateDef needs one of choice, range, flag (state.tab)", error.getMessage());
        }

        @Test
        @DisplayName("two registered keys are an error at the second one")
        void twoKeys() {
            String yaml = """
                    state:
                      tab: { choice: [chat], flag: true }
                    """;

            StructuraException error = assertThrows(StructuraException.class, () -> Structura.parse(yaml, Menu.class));

            assertAt(error, Kind.INVALID, "state.tab.flag", 2, column(yaml, 2, "flag"));
            assertTrue(error.getMessage().contains(
                    "'choice' and 'flag' cannot both be set under 'state.tab': a StateDef is one of choice, range, flag"),
                    error.getMessage());
        }

        @Test
        @DisplayName("a single value where a section is expected")
        void scalarValue() {
            String yaml = """
                    state:
                      tab: chat
                    """;

            StructuraException error = assertThrows(StructuraException.class, () -> Structura.parse(yaml, Menu.class));

            assertAt(error, Kind.WRONG_TYPE, "state.tab", 2, 8);
            assertTrue(error.getMessage().contains("expected a section with one of choice, range, flag but got 'chat'"),
                    error.getMessage());
        }
    }

    @Nested
    @DisplayName("Other places: a plain component and list elements")
    class OtherPlaces {

        @Test
        @DisplayName("the value's own section holds the key")
        void plainComponentAndList() {
            Slot slot = strict.parse("""
                    control: { toggle: view.chat }
                    extras:
                      - { step: { quantity: 2 } }
                      - select: { tab: shop }
                    """, Slot.class);

            assertEquals(new Toggle("view.chat"), slot.control());
            assertEquals(List.of(new Step(Map.of("quantity", 2)), new Select(Map.of("tab", "shop"))), slot.extras());
        }
    }

    @Nested
    @DisplayName("Strict mode")
    class StrictMode {

        @Test
        @DisplayName("a typo of a registered key is suggested among them")
        void typoOfRegisteredKey() {
            String yaml = """
                    buttons:
                      - { label: "Joueurs", toogle: view.chat }
                    """;

            StructuraException error = assertThrows(StructuraException.class, () -> strict.parse(yaml, Panel.class));

            assertAt(error, Kind.UNKNOWN_KEY, "buttons[0].toogle", 2, column(yaml, 2, "toogle"));
            assertTrue(error.getMessage().endsWith("unknown key 'toogle' under 'buttons[0]' (did you mean 'toggle'?)"),
                    error.getMessage());
        }

        @Test
        @DisplayName("an unknown neighbour stays an error, with @Strict alone")
        void unknownNeighbour() {
            String yaml = """
                    buttons:
                      - { labl: "Joueurs", toggle: view.chat }
                    """;

            StructuraException error = assertThrows(StructuraException.class, () -> Structura.parse(yaml, Panel.class));

            assertAt(error, Kind.UNKNOWN_KEY, "buttons[0].labl", 2, column(yaml, 2, "labl"));
            assertTrue(error.getMessage().contains("did you mean 'label'?"), error.getMessage());
        }

        @Test
        @DisplayName("the section under the key is checked as the sub-type's")
        void sectionUnderKey() {
            String yaml = """
                    buttons:
                      - { label: "Joueurs", toggle: { target: a, targte: b } }
                    """;

            StructuraException error = assertThrows(StructuraException.class, () -> strict.parse(yaml, Panel.class));

            assertAt(error, Kind.UNKNOWN_KEY, "buttons[0].toggle.targte", 2, column(yaml, 2, "targte"));
            assertTrue(error.getMessage().contains("did you mean 'target'?"), error.getMessage());
        }

        @Test
        @DisplayName("a sub-type reading the whole section checks it, its key included")
        void wholeSectionChecked() {
            String yaml = """
                    state:
                      tab: { choice: [chat], intial: chat }
                    """;

            StructuraException error = assertThrows(StructuraException.class, () -> strict.parse(yaml, Menu.class));

            assertAt(error, Kind.UNKNOWN_KEY, "state.tab.intial", 2, column(yaml, 2, "intial"));
            assertTrue(error.getMessage().contains("did you mean 'initial'?"), error.getMessage());
        }

        @Test
        @DisplayName("next to the key of a value's own section, a neighbour is suggested among the registered keys")
        void neighbourOfValueKey() {
            String yaml = """
                    control: { toggle: view.chat, slect: x }
                    extras: []
                    """;

            StructuraException error = assertThrows(StructuraException.class, () -> strict.parse(yaml, Slot.class));

            assertEquals(Kind.UNKNOWN_KEY, error.kind());
            assertTrue(error.getMessage().contains("unknown key 'slect' under 'control' (did you mean 'select'?)"),
                    error.getMessage());
        }

        @Test
        @DisplayName("collects every error of the file")
        void collected() {
            StructuraProcessor processor = Structura.builder().strictKeys(true).collectErrors(true).build();

            StructuraErrors errors = assertThrows(StructuraErrors.class, () -> processor.parse("""
                    buttons:
                      - { label: "A", toggle: a, step: { q: 1 } }
                      - { label: "B", toogle: b }
                      - { lable: "C" }
                    """, Panel.class));

            assertEquals(List.of("buttons[0].step", "buttons[1].toogle", "buttons[2].label", "buttons[2].lable"),
                    errors.errors().stream().map(error -> error.path().orElseThrow()).toList());
        }
    }

    @Nested
    @DisplayName("Schema")
    class SchemaOf {

        @Test
        @DisplayName("an inline component is a choice among the registered keys, in the section")
        void inlineComponent() {
            Schema button = Structura.schema(Button.class);
            Schema control = button.inlinePolymorphic().orElseThrow();

            assertEquals(Schema.Discriminator.PRESENT_KEY, control.discriminatorPlacement().orElseThrow());
            assertTrue(control.discriminator().isEmpty());
            assertFalse(control.required());
            // Sorted by name, as every polymorphic value of a schema
            assertEquals(List.of("select", "step", "toggle"), List.copyOf(control.subtypes().keySet()));
        }

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("the JSON Schema offers each key with the value its sub-type reads, exactly one at a time")
        void jsonInline() {
            Map<String, Object> json = new Yaml().load(Structura.schema(Button.class).toJsonSchema());
            Map<String, Object> properties = (Map<String, Object>) json.get("properties");

            assertEquals(List.of("label", "when", "select", "step", "toggle"), List.copyOf(properties.keySet()));
            assertEquals(Map.of("anyOf", List.of(
                    Map.of("type", "string"),
                    Map.of("type", "object", "properties", Map.of("target", Map.of("type", "string")),
                            "required", List.of("target"), "additionalProperties", false))), properties.get("toggle"));
            assertEquals(Map.of("type", "object", "additionalProperties", Map.of("type", "string")), properties.get("select"));
            assertEquals(List.of(
                    Map.of("required", List.of("select")),
                    Map.of("required", List.of("step")),
                    Map.of("required", List.of("toggle")),
                    Map.of("not", Map.of("anyOf", List.of(
                            Map.of("required", List.of("select")),
                            Map.of("required", List.of("step")),
                            Map.of("required", List.of("toggle")))))), json.get("oneOf"));
            assertEquals(false, json.get("additionalProperties"));
        }

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("a map value is a section holding one of the keys; a sub-type reading the whole section lends its keys")
        void jsonMapValue() {
            Map<String, Object> json = new Yaml().load(Structura.schema(Menu.class).toJsonSchema());
            Map<String, Object> state = (Map<String, Object>) ((Map<String, Object>) json.get("properties")).get("state");
            Map<String, Object> value = (Map<String, Object>) state.get("additionalProperties");

            assertEquals(List.of("choice", "initial", "flag", "range"),
                    List.copyOf(((Map<String, Object>) value.get("properties")).keySet()));
            assertEquals(List.of(
                    Map.of("required", List.of("choice")),
                    Map.of("required", List.of("flag")),
                    Map.of("required", List.of("range"))), value.get("oneOf"));
        }
    }

    @Test
    @DisplayName("an inline catch-all map next to it is refused, as next to a fully inline polymorphic component")
    void refusedNextToInlineMap() {
        StructuraException error = assertThrows(StructuraException.class, () -> Structura.parse("""
                label: x
                toggle: y
                """, Crowded.class));

        assertTrue(error.getMessage().contains("cannot live next to the inline polymorphic component 'Crowded.control'"),
                error.getMessage());
    }
}
