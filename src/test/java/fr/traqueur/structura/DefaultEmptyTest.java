package fr.traqueur.structura;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.defaults.DefaultEmpty;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.exceptions.StructuraException.Kind;
import fr.traqueur.structura.schema.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code @DefaultEmpty}: an absent collection or map is empty, instead of missing or null.
 */
@DisplayName("@DefaultEmpty")
class DefaultEmptyTest {

    public record Body(String text) implements Loadable {}

    public record Input(String label) implements Loadable {}

    public record Dialog(String title,
                         @DefaultEmpty List<Body> body,
                         @DefaultEmpty Map<String, Input> inputs,
                         @DefaultEmpty Set<String> tags,
                         @DefaultEmpty Collection<Integer> sizes,
                         @DefaultEmpty @Options(optional = true) List<String> notes) implements Loadable {}

    public record Required(List<String> body) implements Loadable {}

    public record Misplaced(@DefaultEmpty String title) implements Loadable {}

    public enum Screen implements Loadable {
        HOME;

        @DefaultEmpty
        List<String> widgets;
    }

    @Test
    @DisplayName("an absent collection or map is empty and immutable; optional does not make it null")
    void absentIsEmpty() {
        Dialog dialog = Structura.builder().strictKeys(true).build().parse("title: Hello", Dialog.class);

        assertEquals(List.of(), dialog.body());
        assertEquals(Map.of(), dialog.inputs());
        assertEquals(Set.of(), dialog.tags());
        assertEquals(List.of(), dialog.sizes());
        assertEquals(List.of(), dialog.notes());
        assertThrows(UnsupportedOperationException.class, () -> dialog.body().add(new Body("x")));
        assertThrows(UnsupportedOperationException.class, () -> dialog.inputs().put("x", new Input("x")));
    }

    @Test
    @DisplayName("a present value is read as usual")
    void presentIsRead() {
        Dialog dialog = Structura.parse("""
                title: Hello
                body:
                  - text: Welcome
                inputs:
                  name: { label: Name }
                """, Dialog.class);

        assertEquals(List.of(new Body("Welcome")), dialog.body());
        assertEquals(Map.of("name", new Input("Name")), dialog.inputs());
    }

    @Test
    @DisplayName("without it, an absent collection is still missing")
    void withoutStaysMissing() {
        StructuraException error = assertThrows(StructuraException.class, () -> Structura.parse("{}", Required.class));

        assertEquals(Kind.MISSING, error.kind());
    }

    @Test
    @DisplayName("on a type that is not a collection or a map, it is a clear error, the key present or not")
    void misplaced() {
        for (String yaml : List.of("other: 1", "title: Hello")) {
            StructuraException error = assertThrows(StructuraException.class, () -> Structura.parse(yaml, Misplaced.class));

            assertEquals(Kind.OTHER, error.kind());
            assertEquals("title", error.path().orElseThrow());
            assertTrue(error.getMessage().contains(
                    "@DefaultEmpty on Misplaced.title: @DefaultEmpty applies to a List, Set, Collection or Map, not String"),
                    error.getMessage());
        }
    }

    @Test
    @DisplayName("an enum field is empty too")
    void enumField() {
        Structura.parseEnum("home: {}", Screen.class);

        assertEquals(List.of(), Screen.HOME.widgets);
    }

    @Test
    @DisplayName("the schema makes the key optional with an empty default")
    @SuppressWarnings("unchecked")
    void schema() {
        Schema dialog = Structura.schema(Dialog.class);
        Schema body = dialog.properties().stream().filter(p -> "body".equals(p.key())).findFirst().orElseThrow();

        assertFalse(body.required());
        assertEquals(List.of(), body.defaultValue().orElseThrow());

        Map<String, Object> json = new Yaml().load(dialog.toJsonSchema());
        Map<String, Object> properties = (Map<String, Object>) json.get("properties");
        assertEquals(List.of(), ((Map<String, Object>) properties.get("body")).get("default"));
        assertEquals(Map.of(), ((Map<String, Object>) properties.get("inputs")).get("default"));
        assertEquals(List.of("title"), json.get("required"));
    }
}
