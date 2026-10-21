package fr.traqueur.structura;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.validation.Min;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.exceptions.StructuraErrors;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.exceptions.StructuraException.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * With collectErrors(true), a read carries on past an error and reports every error of the document.
 */
@DisplayName("Collected errors")
class CollectErrorsTest {

    public record Limits(@Min(1) int perDay, int perWeek) implements Loadable {}

    public record Offer(String item, @Min(1) int price) implements Loadable {}

    public record Currency(String symbol, int decimals) implements Loadable {}

    public record Shop(String name,
                       Limits limits,
                       List<Offer> offers,
                       Map<String, Currency> currencies,
                       @Options(optional = true) Integer slots) implements Loadable {}

    public enum Rank implements Loadable {
        BRONZE, SILVER;

        @Min(0)
        int threshold;
    }

    private final StructuraProcessor collecting = Structura.builder().collectErrors(true).build();

    @Test
    @DisplayName("reports every error of the file, in the order of the text")
    void severalErrorsInOneFile() {
        StructuraErrors errors = assertThrows(StructuraErrors.class, () -> collecting.parse("""
                name: shop
                limits:
                  per-day: many
                  per-week: 0
                offers:
                  - item: apple
                    price: 0
                  - price: 3
                currencies:
                  gems:
                    decimals: two
                slots: lots
                """, Shop.class, "shop.yml"));

        List<String> found = errors.errors().stream()
                .map(error -> error.kind() + " " + error.path().orElseThrow() + " @" + error.line().orElseThrow())
                .toList();

        assertEquals(List.of(
                "WRONG_TYPE limits.per-day @3",
                "MISSING offers[1].item @8",
                "MISSING currencies.gems.symbol @11",
                "WRONG_TYPE currencies.gems.decimals @11",
                "WRONG_TYPE slots @12"
        ), found);
        assertTrue(errors.getMessage().startsWith("5 errors in shop.yml:"), errors.getMessage());
        assertTrue(errors.getMessage().contains("shop.yml:3:12: 'many' is not a number (limits.per-day)"),
                errors.getMessage());
    }

    @Test
    @DisplayName("collects validation errors of every component once the values are read")
    void severalValidationErrors() {
        StructuraErrors errors = assertThrows(StructuraErrors.class, () -> collecting.parse("""
                name: shop
                limits:
                  per-day: 0
                  per-week: 1
                offers: []
                currencies: {}
                """, Shop.class));

        assertEquals(1, errors.errors().size());
        StructuraException error = errors.errors().getFirst();
        assertEquals(Kind.INVALID, error.kind());
        assertEquals("limits.per-day", error.path().orElseThrow());
        assertEquals(3, error.line().orElseThrow());
    }

    @Test
    @DisplayName("the first error is also the exception's own kind, path and position")
    void firstErrorIsTheExceptionItself() {
        StructuraErrors errors = assertThrows(StructuraErrors.class, () -> collecting.parse("""
                name: shop
                limits:
                  per-day: many
                  per-week: soon
                offers: []
                currencies: {}
                """, Shop.class));

        assertEquals(2, errors.errors().size());
        assertEquals(Kind.WRONG_TYPE, errors.kind());
        assertEquals("limits.per-day", errors.path().orElseThrow());
        assertEquals(3, errors.line().orElseThrow());
    }

    @Test
    @DisplayName("a syntax error is reported the same way")
    void syntaxError() {
        StructuraErrors errors = assertThrows(StructuraErrors.class, () -> collecting.parse("""
                name: [shop
                """, Shop.class));

        assertEquals(Kind.SYNTAX, errors.errors().getFirst().kind());
    }

    @Test
    @DisplayName("collects errors across enum constants")
    void enumConstants() {
        StructuraErrors errors = assertThrows(StructuraErrors.class, () -> collecting.parseEnum("""
                bronze:
                  threshold: none
                silver:
                  threshold: -1
                """, Rank.class));

        assertEquals(2, errors.errors().size());
        assertEquals(List.of(2, 4), errors.errors().stream().map(error -> error.line().orElseThrow()).toList());
    }

    @Test
    @DisplayName("without the option, the read stops at the first error")
    void defaultStopsAtFirstError() {
        StructuraException error = assertThrows(StructuraException.class, () -> Structura.parse("""
                name: shop
                limits:
                  per-day: many
                  per-week: soon
                offers: []
                currencies: {}
                """, Shop.class));

        assertFalse(error instanceof StructuraErrors);
        assertEquals("limits.per-day", error.path().orElseThrow());
    }

    @Test
    @DisplayName("a valid document reads normally")
    void validDocument() {
        Shop shop = collecting.parse("""
                name: shop
                limits:
                  per-day: 2
                  per-week: 10
                offers:
                  - item: apple
                    price: 3
                currencies:
                  gems:
                    symbol: g
                    decimals: 0
                """, Shop.class);

        assertEquals(3, shop.offers().getFirst().price());
        assertEquals("g", shop.currencies().get("gems").symbol());
    }
}
