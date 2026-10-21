package fr.traqueur.structura;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.defaults.DefaultInt;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Source;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.exceptions.StructuraErrors;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.exceptions.StructuraException.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A document read in layers: a default, then an override on top, merged without rewriting any text.
 */
@DisplayName("Layered sources")
class LayeredSourcesTest {

    public record Limits(int perDay, int perWeek) implements Loadable {}

    public record Price(String currency, int amount) implements Loadable {}

    public record Shop(String name,
                       @DefaultInt(27) int maxItems,
                       Limits limits,
                       Map<String, Price> prices,
                       List<String> categories,
                       @Options(optional = true) String motd) implements Loadable {}

    private static final String DEFAULTS = """
            name: Shop
            max-items: 27
            limits:
              per-day: 5
              per-week: 20
            prices:
              apple:
                currency: coins
                amount: 3
              pear:
                currency: coins
                amount: 4
            categories: [food, tools, blocks]
            """;

    private static Source defaults() {
        return Source.of("config.yml (jar)", DEFAULTS);
    }

    @Nested
    @DisplayName("Merge")
    class Merge {

        @Test
        @DisplayName("sections merge key by key at every depth; lists and values are replaced")
        void mergeRules() {
            Shop shop = Structura.parse(List.of(defaults(), Source.of("config.yml", """
                    name: Market
                    limits:
                      per-day: 8
                    prices:
                      pear:
                        amount: 6
                      plum:
                        currency: gems
                        amount: 1
                    categories: [food]
                    """)), Shop.class);

            assertEquals("Market", shop.name());
            assertEquals(27, shop.maxItems());
            assertEquals(new Limits(8, 20), shop.limits());
            assertEquals(new Price("coins", 3), shop.prices().get("apple"));
            assertEquals(new Price("coins", 6), shop.prices().get("pear"));
            assertEquals(new Price("gems", 1), shop.prices().get("plum"));
            assertEquals(List.of("food"), shop.categories());
        }

        @Test
        @DisplayName("an empty layer changes nothing")
        void emptyLayer() {
            Shop shop = Structura.parse(List.of(defaults(), Source.of("config.yml", "")), Shop.class);

            assertEquals("Shop", shop.name());
            assertEquals(new Limits(5, 20), shop.limits());
        }

        @Test
        @DisplayName("three layers stack in order")
        void threeLayers() {
            Shop shop = Structura.parse(List.of(defaults(),
                    Source.of("network.yml", "limits:\n  per-week: 50\n"),
                    Source.of("config.yml", "limits:\n  per-day: 9\n")), Shop.class);

            assertEquals(new Limits(9, 50), shop.limits());
        }

        @Test
        @DisplayName("a single layer reads like parse")
        void singleLayer() {
            assertEquals(Structura.parse(DEFAULTS, Shop.class), Structura.parse(List.of(defaults()), Shop.class));
        }
    }

    @Nested
    @DisplayName("Errors name their layer")
    class Errors {

        @Test
        @DisplayName("a faulty value in the upper layer names that file and its line")
        void faultInUpperLayer() {
            StructuraException error = assertThrows(StructuraException.class, () -> Structura.parse(List.of(
                    defaults(),
                    Source.of("config.yml", """
                            name: Market
                            limits:
                              per-day: many
                            """)), Shop.class));

            assertEquals(Kind.WRONG_TYPE, error.kind());
            assertEquals("config.yml", error.source().orElseThrow());
            assertEquals("config.yml:3:12: 'many' is not a number (limits.per-day)", error.getMessage());
        }

        @Test
        @DisplayName("a faulty value kept from the lower layer names the default")
        void faultInLowerLayer() {
            StructuraException error = assertThrows(StructuraException.class, () -> Structura.parse(List.of(
                    Source.of("config.yml (jar)", """
                            name: Shop
                            limits:
                              per-day: 5
                              per-week: lots
                            prices: {}
                            categories: []
                            """),
                    Source.of("config.yml", """
                            limits:
                              per-day: 8
                            """)), Shop.class));

            assertEquals("config.yml (jar)", error.source().orElseThrow());
            assertEquals(4, error.line().orElseThrow());
            assertEquals("limits.per-week", error.path().orElseThrow());
        }

        @Test
        @DisplayName("strict mode checks every layer, each unknown key named in its own file")
        void strictInEveryLayer() {
            StructuraProcessor processor = Structura.builder().strictKeys(true).collectErrors(true).build();

            StructuraErrors errors = assertThrows(StructuraErrors.class, () -> processor.parse(List.of(
                    Source.of("config.yml (jar)", DEFAULTS + "colour: red\n"),
                    Source.of("config.yml", """
                            limits:
                              per-dya: 8
                            """)), Shop.class));

            List<String> found = errors.errors().stream()
                    .map(error -> error.kind() + " " + error.source().orElseThrow() + ":" + error.line().orElseThrow()
                            + " " + error.path().orElseThrow())
                    .sorted()
                    .toList();
            assertEquals(List.of(
                    "UNKNOWN_KEY config.yml (jar):14 colour",
                    "UNKNOWN_KEY config.yml:2 limits.per-dya"
            ), found);
        }

        @Test
        @DisplayName("a syntax error names its layer")
        void syntaxErrorInLayer() {
            StructuraException error = assertThrows(StructuraException.class, () -> Structura.parse(List.of(
                    defaults(), Source.of("config.yml", "limits: [1\n")), Shop.class));

            assertEquals(Kind.SYNTAX, error.kind());
            assertEquals("config.yml", error.source().orElseThrow());
        }

        @Test
        @DisplayName("at least one source is required")
        void noSource() {
            assertThrows(StructuraException.class, () -> Structura.parse(List.of(), Shop.class));
        }
    }
}
