package fr.traqueur.structura.writers;

import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.registries.CustomReaderRegistry;
import fr.traqueur.structura.writers.exceptions.StructuraWriterException;
import fr.traqueur.structura.writers.registries.CustomWriterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contextual writers know the declared type and the path, and hand inner values back to Structura.
 */
@DisplayName("Contextual writers")
class ContextualWriterTest {

    public enum Color { DARK_RED, BLUE }

    public record Team(String name, Color color) implements Loadable {}

    /** A value given literally or resolved later from a placeholder. */
    public record Resolvable<T>(T value, String placeholder) {
        static <T> Resolvable<T> of(T value) {
            return new Resolvable<>(value, null);
        }

        static <T> Resolvable<T> placeholder(String placeholder) {
            return new Resolvable<>(null, placeholder);
        }
    }

    public record Rules(Resolvable<Integer> maxPlayers,
                        Resolvable<Team> owner,
                        List<Resolvable<Color>> colors) implements Loadable {}

    @TempDir
    Path dir;

    @AfterEach
    void clear() {
        CustomWriterRegistry.getInstance().unregister(Resolvable.class);
        CustomReaderRegistry.getInstance().unregisterGeneric(Resolvable.class);
    }

    private static void registerResolvableWriter() {
        CustomWriterRegistry.getInstance().registerContextual(Resolvable.class, (resolvable, ctx) ->
                resolvable.placeholder() != null
                        ? resolvable.placeholder()
                        : ctx.write(resolvable.value(), ctx.typeArgument(0)));
    }

    @Test
    @DisplayName("one writer serves every Resolvable<T>, inner values written with Structura's rules")
    void innerValuesWithStructuraRules() throws Exception {
        registerResolvableWriter();
        Path file = dir.resolve("rules.yml");

        Structura.write(file, new Rules(Resolvable.placeholder("%max%"),
                Resolvable.of(new Team("Red", Color.DARK_RED)),
                List.of(Resolvable.of(Color.BLUE), Resolvable.placeholder("%color%"))));

        assertEquals(Map.of(
                "max-players", "%max%",
                "owner", Map.of("name", "Red", "color", "dark-red"),
                "colors", List.of("blue", "%color%")
        ), new Yaml().load(Files.readString(file)));
    }

    @Test
    @DisplayName("receives the declared type and the path of the value")
    void declaredTypeAndPath() throws Exception {
        List<String> seen = new ArrayList<>();
        List<Type> types = new ArrayList<>();
        CustomWriterRegistry.getInstance().registerContextual(Resolvable.class, (resolvable, ctx) -> {
            seen.add(ctx.path());
            types.add(ctx.target().getType());
            return "x";
        });

        Structura.write(dir.resolve("rules.yml"), new Rules(Resolvable.of(1), Resolvable.of(null),
                List.of(Resolvable.of(Color.BLUE))));

        assertEquals(List.of("max-players", "owner", "colors[0]"), seen);
        assertEquals(Rules.class.getRecordComponents()[0].getGenericType(), types.get(0));
        assertEquals(Rules.class.getRecordComponents()[1].getGenericType(), types.get(1));
    }

    @Test
    @DisplayName("round-trips with the generic reader")
    void roundTrip() throws Exception {
        registerResolvableWriter();
        CustomReaderRegistry.getInstance().registerGeneric(Resolvable.class, ctx ->
                ctx.raw() instanceof String text && text.startsWith("%")
                        ? Resolvable.placeholder(text)
                        : Resolvable.of(ctx.convert(ctx.typeArgument(0))));
        Path file = dir.resolve("rules.yml");
        Rules rules = new Rules(Resolvable.of(20), Resolvable.of(new Team("Red", Color.DARK_RED)),
                List.of(Resolvable.placeholder("%c%")));

        Structura.write(file, rules);

        assertEquals(rules, Structura.load(file, Rules.class));
    }

    @Test
    @DisplayName("a failing writer names the path of the value")
    void failureNamesPath() {
        CustomWriterRegistry.getInstance().registerContextual(Resolvable.class, (resolvable, ctx) -> {
            throw new IllegalStateException("cannot write");
        });

        StructuraWriterException error = assertThrows(StructuraWriterException.class, () ->
                Structura.write(dir.resolve("rules.yml"), new Rules(Resolvable.of(1), Resolvable.of(null), List.of())));

        assertTrue(error.getMessage().contains(" at max-players: cannot write"), error.getMessage());
    }
}
