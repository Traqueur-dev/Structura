package fr.traqueur.structura.writers;

import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.api.StructuraWriter;
import fr.traqueur.structura.registries.PolymorphicRegistries;
import fr.traqueur.structura.writers.registries.CustomWriterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Writers with registries of their own no longer share their writers or sub-types.
 */
@DisplayName("Writer registries per instance")
class WriterRegistriesTest {

    public record Label(String text) {}

    public record Sign(Label label) implements Loadable {}

    @Polymorphic
    public interface Effect extends Loadable {}

    public record Burn(int seconds) implements Effect {}

    public record Spell(Effect effect) implements Loadable {}

    @TempDir
    Path dir;

    private Map<String, Object> read(Path file) throws Exception {
        return new Yaml().load(Files.readString(file));
    }

    @Test
    @DisplayName("each writer uses its own writers, the global registry untouched")
    void writersPerInstance() throws Exception {
        CustomWriterRegistry upper = CustomWriterRegistry.create();
        upper.register(Label.class, label -> label.text().toUpperCase());
        CustomWriterRegistry lower = CustomWriterRegistry.create();
        lower.register(Label.class, label -> label.text().toLowerCase());

        YamlStructuraWriter.with(upper, null).write(dir.resolve("a.yml"), new Sign(new Label("Hello")));
        YamlStructuraWriter.with(lower, null).write(dir.resolve("b.yml"), new Sign(new Label("Hello")));

        assertEquals("HELLO", read(dir.resolve("a.yml")).get("label"));
        assertEquals("hello", read(dir.resolve("b.yml")).get("label"));
        assertFalse(CustomWriterRegistry.getInstance().hasWriter(Label.class));
    }

    @Test
    @DisplayName("the global registry stays the fallback")
    void globalFallback() throws Exception {
        CustomWriterRegistry.getInstance().register(Label.class, Label::text);
        try {
            YamlStructuraWriter.with(CustomWriterRegistry.create(), null).write(dir.resolve("a.yml"), new Sign(new Label("Hi")));

            assertEquals("Hi", read(dir.resolve("a.yml")).get("label"));
        } finally {
            CustomWriterRegistry.getInstance().unregister(Label.class);
        }
    }

    @Test
    @DisplayName("sub-types are named from the writer's own polymorphic registries")
    void polymorphicsPerInstance() throws Exception {
        PolymorphicRegistries types = PolymorphicRegistries.create();
        types.register(Effect.class, registry -> registry.register("fire", Burn.class));

        YamlStructuraWriter.with(null, types).write(dir.resolve("spell.yml"), new Spell(new Burn(3)));

        assertEquals(Map.of("effect", Map.of("type", "fire", "seconds", 3)), read(dir.resolve("spell.yml")));
        assertEquals(new Spell(new Burn(3)),
                Structura.builder().polymorphics(types).build().parse(Files.readString(dir.resolve("spell.yml")), Spell.class));
    }

    @Test
    @DisplayName("the shared global writer writes in parallel without corrupting its output")
    void sharedWriterInParallel() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2000; i++) {
                int index = i;
                results.add(pool.submit(() -> {
                    Path file = dir.resolve("shared-" + index + ".yml");
                    Structura.write(file, new Spell(null));
                    return Files.readString(file).equals("effect: null\n");
                }));
            }
            for (Future<Boolean> result : results) {
                assertTrue(result.get());
            }
        }
    }

    @Test
    @DisplayName("two writers write in parallel without stepping on each other")
    void parallelWrites() throws Exception {
        CustomWriterRegistry upper = CustomWriterRegistry.create();
        upper.register(Label.class, label -> label.text().toUpperCase());
        CustomWriterRegistry lower = CustomWriterRegistry.create();
        lower.register(Label.class, label -> label.text().toLowerCase());
        StructuraWriter first = YamlStructuraWriter.with(upper, null);
        StructuraWriter second = YamlStructuraWriter.with(lower, null);

        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                int index = i;
                results.add(pool.submit(() -> {
                    Path file = dir.resolve("sign-" + index + ".yml");
                    (index % 2 == 0 ? first : second).write(file, new Sign(new Label("Mixed")));
                    return read(file).get("label").equals(index % 2 == 0 ? "MIXED" : "mixed");
                }));
            }
            for (Future<Boolean> result : results) {
                assertTrue(result.get());
            }
        }
    }
}
