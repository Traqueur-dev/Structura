package fr.traqueur.structura;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.annotations.validation.Min;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.exceptions.StructuraException.Kind;
import fr.traqueur.structura.registries.CustomReaderRegistry;
import fr.traqueur.structura.registries.PolymorphicRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every error raised while reading a document says where: its path, its line and column, its kind.
 */
@DisplayName("Located errors")
class LocatedErrorsTest {

    public record Limits(@Min(1) int perDay, @Options(optional = true) Integer perWeek) implements Loadable {}

    public record Shop(String name, Limits limits) implements Loadable {}

    public record Offer(String item, int price) implements Loadable {}

    public record Offers(List<Offer> offers) implements Loadable {}

    public record Currency(String symbol, int decimals) implements Loadable {}

    public record Currencies(Map<String, Currency> currencies) implements Loadable {}

    public record Range(int min, int max) implements Loadable {
        public Range {
            if (min > max) {
                throw new IllegalArgumentException("min must not exceed max");
            }
        }
    }

    public record Ranged(Range range) implements Loadable {}

    public record Host(String host, int port) implements Loadable {}

    public record Server(String name, @Options(inline = true) Host address) implements Loadable {}

    @Polymorphic
    public interface Reward extends Loadable {}

    public record MoneyReward(int amount) implements Reward {}

    public record ItemReward(String item) implements Reward {}

    public record Quest(Reward reward) implements Loadable {}

    public record Amount(int value) {}

    public record Payment(Amount amount) implements Loadable {}

    @BeforeAll
    static void registerRewards() {
        PolymorphicRegistry.create(Reward.class, registry -> {
            registry.register("money", MoneyReward.class);
            registry.register("item", ItemReward.class);
        });
    }

    @AfterEach
    void clearReaders() {
        CustomReaderRegistry.getInstance().unregister(Amount.class);
    }

    private static StructuraException fail(Executable executable) {
        return assertThrows(StructuraException.class, executable);
    }

    private static void assertLocated(StructuraException error, Kind kind, String path, int line, int column) {
        assertEquals(kind, error.kind(), error.getMessage());
        assertEquals(path, error.path().orElse(null), error.getMessage());
        assertEquals(line, error.line().orElse(-1), error.getMessage());
        assertEquals(column, error.column().orElse(-1), error.getMessage());
    }

    @Nested
    @DisplayName("Wrong type")
    class WrongType {

        @Test
        @DisplayName("names the value, the path and the position, with no wrapper hiding the reason")
        void wrongTypeInNestedRecord() {
            StructuraException error = fail(() -> Structura.parse("""
                    name: shop
                    limits:
                      per-day: many
                    """, Shop.class));

            assertLocated(error, Kind.WRONG_TYPE, "limits.per-day", 3, 12);
            assertEquals("3:12: 'many' is not a number (limits.per-day)", error.getMessage());
            assertFalse(error.getMessage().contains("Failed to parse YAML"));
        }

        @Test
        @DisplayName("in a list element")
        void wrongTypeInListElement() {
            StructuraException error = fail(() -> Structura.parse("""
                    offers:
                      - item: apple
                        price: 3
                      - item: pear
                        price: 4
                      - item: plum
                        price: cheap
                    """, Offers.class));

            assertLocated(error, Kind.WRONG_TYPE, "offers[2].price", 7, 12);
        }

        @Test
        @DisplayName("in a map value")
        void wrongTypeInMapValue() {
            StructuraException error = fail(() -> Structura.parse("""
                    currencies:
                      coins:
                        symbol: c
                        decimals: 0
                      gems:
                        symbol: g
                        decimals: two
                    """, Currencies.class));

            assertLocated(error, Kind.WRONG_TYPE, "currencies.gems.decimals", 7, 15);
        }

        @Test
        @DisplayName("a scalar where a section is expected")
        void scalarForSection() {
            StructuraException error = fail(() -> Structura.parse("""
                    name: shop
                    limits: 5
                    """, Shop.class));

            assertLocated(error, Kind.WRONG_TYPE, "limits", 2, 9);
            assertTrue(error.detail().contains("expected a section"), error.getMessage());
        }

        @Test
        @DisplayName("an unknown polymorphic type points at the discriminator")
        void unknownPolymorphicType() {
            StructuraException error = fail(() -> Structura.parse("""
                    reward:
                      type: gold
                      amount: 3
                    """, Quest.class));

            assertLocated(error, Kind.WRONG_TYPE, "reward.type", 2, 9);
        }
    }

    @Nested
    @DisplayName("Missing key")
    class Missing {

        @Test
        @DisplayName("points at the section where the key should be")
        void missingNestedKey() {
            StructuraException error = fail(() -> Structura.parse("""
                    name: shop
                    limits:
                      per-week: 5
                    """, Shop.class));

            assertLocated(error, Kind.MISSING, "limits.per-day", 3, 3);
            assertEquals("3:3: 'per-day' is required but not provided (limits.per-day)", error.getMessage());
        }

        @Test
        @DisplayName("of an inline component is read at the parent level")
        void missingInlineKey() {
            StructuraException error = fail(() -> Structura.parse("""
                    name: lobby
                    host: localhost
                    """, Server.class));

            assertLocated(error, Kind.MISSING, "port", 1, 1);
        }

        @Test
        @DisplayName("of a polymorphic discriminator")
        void missingDiscriminator() {
            StructuraException error = fail(() -> Structura.parse("""
                    reward:
                      amount: 3
                    """, Quest.class));

            assertEquals(Kind.MISSING, error.kind());
            assertEquals("reward", error.path().orElseThrow());
            assertEquals(2, error.line().orElseThrow());
        }
    }

    @Nested
    @DisplayName("Invalid value")
    class Invalid {

        @Test
        @DisplayName("a validation rule names the document key and its line")
        void validationError() {
            StructuraException error = fail(() -> Structura.parse("""
                    name: shop
                    limits:
                      per-day: 0
                    """, Shop.class));

            assertLocated(error, Kind.INVALID, "limits.per-day", 3, 12);
        }

        @Test
        @DisplayName("a record refusing its values in its constructor")
        void recordConstructorRefuses() {
            StructuraException error = fail(() -> Structura.parse("""
                    range:
                      min: 5
                      max: 1
                    """, Ranged.class));

            assertLocated(error, Kind.INVALID, "range", 2, 3);
            assertTrue(error.getMessage().contains("min must not exceed max"), error.getMessage());
        }
    }

    @Nested
    @DisplayName("Reader and syntax")
    class ReaderAndSyntax {

        @Test
        @DisplayName("a failing custom reader is a READER error at its path")
        void readerError() {
            CustomReaderRegistry.getInstance().register(Amount.class, text -> new Amount(Integer.parseInt(text)));

            StructuraException error = fail(() -> Structura.parse("""
                    amount: "lots"
                    """, Payment.class));

            assertLocated(error, Kind.READER, "amount", 1, 9);
        }

        @Test
        @DisplayName("invalid YAML is a SYNTAX error with its position")
        void syntaxError() {
            StructuraException error = fail(() -> Structura.parse("""
                    name: shop
                    limits:
                      per-day: [1, 2
                    """, Shop.class));

            assertEquals(Kind.SYNTAX, error.kind());
            assertTrue(error.line().isPresent(), error.getMessage());
        }
    }

    @Nested
    @DisplayName("Source")
    class Source {

        @Test
        @DisplayName("the source name prefixes the position")
        void sourceName() {
            StructuraException error = fail(() -> Structura.builder().build().parse("""
                    name: shop
                    limits:
                      per-day: many
                    """, Shop.class, "shop.yml"));

            assertEquals("shop.yml", error.source().orElseThrow());
            assertEquals("shop.yml:3:12: 'many' is not a number (limits.per-day)", error.getMessage());
        }

        @Test
        @DisplayName("an error outside a document keeps its plain message")
        void plainMessageOutsideDocument() {
            StructuraException error = new StructuraException("plain");

            assertEquals("plain", error.getMessage());
            assertEquals(Kind.OTHER, error.kind());
            assertTrue(error.path().isEmpty());
            assertTrue(error.line().isEmpty());
        }
    }
}
