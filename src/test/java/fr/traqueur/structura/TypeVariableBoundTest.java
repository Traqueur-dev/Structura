package fr.traqueur.structura;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.registries.PolymorphicRegistries;
import fr.traqueur.structura.registries.PolymorphicRegistry;
import fr.traqueur.structura.schema.Schema;
import fr.traqueur.structura.types.TypeToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A component whose type is a type variable is read as its bound.
 */
@DisplayName("Type variables read by their bound")
class TypeVariableBoundTest {

    @Polymorphic(key = "surface", inline = true)
    public interface Surface extends Loadable {}

    public record Chest(int rows) implements Surface {}

    public record Hopper(int slots) implements Surface {}

    public record Menu<S extends Surface>(String id, @Options(inline = true) S surface) implements Loadable {}

    public record Label(String text) implements Loadable {}

    public record Holder<T extends Label>(T label, List<T> others) implements Loadable {}

    @BeforeAll
    static void register() {
        PolymorphicRegistry.create(Surface.class, r -> {
            r.register("chest", Chest.class);
            r.register("hopper", Hopper.class);
        });
    }

    @AfterAll
    static void unregister() {
        PolymorphicRegistries.global().unregister(Surface.class);
    }

    @Test
    @DisplayName("an inline polymorphic type variable is read as its bound, in strict mode")
    void inlinePolymorphicBound() {
        Menu<?> menu = Structura.builder().strictKeys(true).build().parse("""
                id: shop
                surface: chest
                rows: 3
                """, Menu.class);

        assertEquals(new Menu<>("shop", new Chest(3)), menu);
    }

    @Test
    @DisplayName("strict mode still reports an unknown key of the selected sub-type")
    void strictThroughBound() {
        StructuraException error = assertThrows(StructuraException.class, () ->
                Structura.builder().strictKeys(true).build().parse("""
                        id: shop
                        surface: chest
                        rowz: 3
                        """, Menu.class));

        assertEquals("3:1: unknown key 'rowz' (did you mean 'rows'?)", error.getMessage());
    }

    @Test
    @DisplayName("a plain type variable and a list of it are read as their bound")
    void plainAndList() {
        Holder<?> holder = Structura.parse("""
                label: { text: a }
                others:
                  - text: b
                """, Holder.class);

        assertEquals(new Label("a"), holder.label());
        assertEquals(List.of(new Label("b")), holder.others());
    }

    @Test
    @DisplayName("the schema describes the bound")
    void schema() {
        Schema menu = Structura.schema(Menu.class);

        assertEquals(List.of("chest", "hopper"), List.copyOf(menu.inlinePolymorphic().orElseThrow().subtypes().keySet()));
    }

    @Test
    @DisplayName("TypeToken.rawTypeOf resolves type variables and wildcards to their first bound")
    void rawTypeOf() throws Exception {
        assertEquals(Surface.class, TypeToken.rawTypeOf(Menu.class.getTypeParameters()[0]));
        assertEquals(Label.class, TypeToken.rawTypeOf(Holder.class.getRecordComponents()[0].getGenericType()));
    }
}
