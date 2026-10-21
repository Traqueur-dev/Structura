package fr.traqueur.structura;

import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.registries.CustomReaderRegistry;
import fr.traqueur.structura.registries.PolymorphicRegistries;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Registries attached to a processor: two processors no longer share their readers or sub-types.
 */
@DisplayName("Registries per processor")
class InstanceRegistriesTest {

    public record Label(String text) {}

    public record Sign(Label label) implements Loadable {}

    @Polymorphic
    public interface Effect extends Loadable {}

    public record Burn(int seconds) implements Effect {}

    public record Freeze(int seconds) implements Effect {}

    public record Spell(Effect effect) implements Loadable {}

    @Test
    @DisplayName("each processor reads with its own reader, the global registry untouched")
    void readersPerProcessor() {
        CustomReaderRegistry upper = CustomReaderRegistry.create();
        upper.register(Label.class, text -> new Label(text.toUpperCase()));
        CustomReaderRegistry lower = CustomReaderRegistry.create();
        lower.register(Label.class, text -> new Label(text.toLowerCase()));

        StructuraProcessor first = Structura.builder().readers(upper).build();
        StructuraProcessor second = Structura.builder().readers(lower).build();

        assertEquals(new Label("HELLO"), first.parse("label: Hello", Sign.class).label());
        assertEquals(new Label("hello"), second.parse("label: Hello", Sign.class).label());
        assertFalse(CustomReaderRegistry.getInstance().hasReader(Label.class));
        assertThrows(StructuraException.class, () -> Structura.parse("label: Hello", Sign.class));
    }

    @Test
    @DisplayName("the global registry stays the fallback")
    void globalFallback() {
        CustomReaderRegistry.getInstance().register(Label.class, Label::new);
        try {
            StructuraProcessor processor = Structura.builder().readers(CustomReaderRegistry.create()).build();

            assertEquals(new Label("Hello"), processor.parse("label: Hello", Sign.class).label());
        } finally {
            CustomReaderRegistry.getInstance().unregister(Label.class);
        }
    }

    @Test
    @DisplayName("each processor resolves sub-types with its own polymorphic registries")
    void polymorphicsPerProcessor() {
        PolymorphicRegistries fire = PolymorphicRegistries.create();
        fire.register(Effect.class, registry -> registry.register("magic", Burn.class));
        PolymorphicRegistries ice = PolymorphicRegistries.create();
        ice.register(Effect.class, registry -> registry.register("magic", Freeze.class));

        String yaml = "effect:\n  type: magic\n  seconds: 3\n";

        assertEquals(new Burn(3), Structura.builder().polymorphics(fire).build().parse(yaml, Spell.class).effect());
        assertEquals(new Freeze(3), Structura.builder().polymorphics(ice).build().parse(yaml, Spell.class).effect());
        assertTrue(PolymorphicRegistries.global().find(Effect.class).isEmpty());
    }

    @Test
    @DisplayName("two processors read in parallel without stepping on each other")
    void parallelReads() throws Exception {
        CustomReaderRegistry upper = CustomReaderRegistry.create();
        upper.register(Label.class, text -> new Label(text.toUpperCase()));
        CustomReaderRegistry lower = CustomReaderRegistry.create();
        lower.register(Label.class, text -> new Label(text.toLowerCase()));
        StructuraProcessor first = Structura.builder().readers(upper).build();
        StructuraProcessor second = Structura.builder().readers(lower).build();

        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            Callable<Boolean> readFirst = () -> first.parse("label: Mixed", Sign.class).label().text().equals("MIXED");
            Callable<Boolean> readSecond = () -> second.parse("label: Mixed", Sign.class).label().text().equals("mixed");
            java.util.List<Future<Boolean>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 200; i++) {
                results.add(pool.submit(i % 2 == 0 ? readFirst : readSecond));
            }
            for (Future<Boolean> result : results) {
                assertTrue(result.get());
            }
        }
    }
}
