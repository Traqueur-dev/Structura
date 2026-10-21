package fr.traqueur.structura;

import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.exceptions.StructuraException.Kind;
import fr.traqueur.structura.readers.ReadContext;
import fr.traqueur.structura.registries.CustomReaderRegistry;
import fr.traqueur.structura.types.TypeToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Readers see the value as YAML typed it, the full requested type and the path of the value.
 */
@DisplayName("Contextual readers")
class ContextualReaderTest {

    public record Amount(long value) {}

    public record Payment(Amount amount) implements Loadable {}

    public record Team(String name) {}

    /** A value that is either given literally or resolved later from a placeholder. */
    public record Resolvable<T>(T value, String placeholder) {
        static <T> Resolvable<T> of(T value) {
            return new Resolvable<>(value, null);
        }

        static <T> Resolvable<T> placeholder(String placeholder) {
            return new Resolvable<>(null, placeholder);
        }
    }

    public record Rules(Resolvable<Integer> maxPlayers,
                       Resolvable<Boolean> pvp,
                       Resolvable<Team> owner,
                       List<Resolvable<Integer>> limits) implements Loadable {}

    private final CustomReaderRegistry registry = CustomReaderRegistry.getInstance();

    @AfterEach
    void clear() {
        registry.unregister(Amount.class);
        registry.unregister(Team.class);
        registry.unregisterGeneric(Resolvable.class);
    }

    @Nested
    @DisplayName("A scalar reaches the reader as it is")
    class Scalars {

        @Test
        @DisplayName("amount: 100 and amount: \"100\" are read by the same plain reader")
        void plainReaderGetsNumbersToo() {
            registry.register(Amount.class, text -> new Amount(Long.parseLong(text)));

            assertEquals(new Amount(100), Structura.parse("amount: 100", Payment.class).amount());
            assertEquals(new Amount(100), Structura.parse("amount: \"100\"", Payment.class).amount());
        }

        @Test
        @DisplayName("a contextual reader sees the YAML type of the value, its text and its path")
        void contextualReaderSeesRawValue() {
            List<ReadContext> seen = new ArrayList<>();
            registry.registerContextual(Amount.class, ctx -> {
                seen.add(ctx);
                return new Amount(Long.parseLong(ctx.text()));
            });

            Structura.parse("amount: 100", Payment.class);
            Structura.parse("amount: \"100\"", Payment.class);

            assertEquals(Integer.class, seen.get(0).raw().getClass());
            assertEquals(String.class, seen.get(1).raw().getClass());
            assertEquals("100", seen.get(0).text());
            assertEquals("amount", seen.get(0).path());
            assertEquals(Amount.class, seen.get(0).target().getType());
        }

        @Test
        @DisplayName("the plain registry API keeps handing only strings to a reader")
        void legacyConvertUnchanged() {
            registry.register(Amount.class, text -> new Amount(Long.parseLong(text)));

            assertTrue(registry.convert(100, Amount.class).isEmpty());
        }
    }

    @Nested
    @DisplayName("Generic reader")
    class Generic {

        private void registerResolvable() {
            registry.registerContextual(Team.class, ctx -> new Team(ctx.text()));
            registry.registerGeneric(Resolvable.class, ctx ->
                    ctx.raw() instanceof String text && text.startsWith("%")
                            ? Resolvable.placeholder(text)
                            : Resolvable.of(ctx.convert(ctx.typeArgument(0))));
        }

        @Test
        @DisplayName("one reader serves every parameterization, reading the type argument with Structura's rules")
        void oneReaderForEveryParameterization() {
            registerResolvable();

            Rules rules = Structura.parse("""
                    max-players: 20
                    pvp: "%server_pvp%"
                    owner: red
                    limits: [1, "%daily%", 3]
                    """, Rules.class);

            assertEquals(Resolvable.of(20), rules.maxPlayers());
            assertEquals(Resolvable.placeholder("%server_pvp%"), rules.pvp());
            assertEquals(Resolvable.of(new Team("red")), rules.owner());
            assertEquals(List.of(Resolvable.of(1), Resolvable.placeholder("%daily%"), Resolvable.of(3)), rules.limits());
        }

        @Test
        @DisplayName("receives the full requested type")
        void receivesFullType() throws Exception {
            List<Type> targets = new ArrayList<>();
            registry.registerGeneric(Resolvable.class, ctx -> {
                targets.add(ctx.target().getType());
                return Resolvable.of(null);
            });

            Structura.parse("""
                    max-players: 20
                    pvp: true
                    owner: red
                    limits: []
                    """, Rules.class);

            assertEquals(List.of(
                    Rules.class.getRecordComponents()[0].getGenericType(),
                    Rules.class.getRecordComponents()[1].getGenericType(),
                    Rules.class.getRecordComponents()[2].getGenericType()
            ), targets);
        }

        @Test
        @DisplayName("a reader for the exact type wins over the generic one")
        void exactTypeWins() {
            registerResolvable();
            registry.registerContextual(new TypeToken<Resolvable<Boolean>>() {}, ctx -> Resolvable.of(false));

            Rules rules = Structura.parse("""
                    max-players: 20
                    pvp: true
                    owner: red
                    limits: []
                    """, Rules.class);

            assertEquals(Resolvable.of(false), rules.pvp());
            registry.unregister(new TypeToken<Resolvable<Boolean>>() {});
        }
    }

    @Test
    @DisplayName("an exception from a reader is a READER error with the path and the line")
    void readerFailure() {
        registry.register(Amount.class, text -> new Amount(Long.parseLong(text)));

        StructuraException error = assertThrows(StructuraException.class, () -> Structura.parse("""
                amount: lots
                """, Payment.class));

        assertEquals(Kind.READER, error.kind());
        assertEquals("amount", error.path().orElseThrow());
        assertEquals(1, error.line().orElseThrow());
        assertEquals(9, error.column().orElseThrow());
        assertTrue(error.getMessage().contains("For input string: \"lots\""), error.getMessage());
    }
}
