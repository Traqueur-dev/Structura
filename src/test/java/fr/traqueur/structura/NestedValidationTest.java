package fr.traqueur.structura;

import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.annotations.validation.Min;
import fr.traqueur.structura.annotations.validation.NotEmpty;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.exceptions.StructuraErrors;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.exceptions.StructuraException.Kind;
import fr.traqueur.structura.registries.PolymorphicRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The records held by lists, maps and polymorphic components are validated (on by default since 2.8.0).
 */
@DisplayName("Nested validation")
class NestedValidationTest {

    public record Offer(String item, @Min(1) int price) implements Loadable {}

    public record Currency(@NotEmpty String symbol) implements Loadable {}

    @Polymorphic
    public interface Reward extends Loadable {}

    public record MoneyReward(@Min(1) int amount) implements Reward {}

    public record Shop(List<Offer> offers,
                       Map<String, Currency> currencies,
                       Reward reward,
                       List<List<Offer>> pages) implements Loadable {}

    public enum Tier implements Loadable {
        GOLD;

        List<Offer> offers;
    }

    private static final PolymorphicRegistries TYPES = PolymorphicRegistries.create();

    @BeforeAll
    static void registerRewards() {
        TYPES.register(Reward.class, registry -> registry.register("money", MoneyReward.class));
    }

    private static final String YAML = """
            offers:
              - item: apple
                price: 3
              - item: pear
                price: 0
            currencies:
              gems:
                symbol: ""
            reward:
              type: money
              amount: 0
            pages:
              - - item: plum
                  price: -2
            """;

    @Test
    @DisplayName("every nested record is validated, each error at its document path and line")
    void everyNestedRecord() {
        StructuraProcessor processor = Structura.builder()
                .polymorphics(TYPES).validateNested(true).collectErrors(true).build();

        StructuraErrors errors = assertThrows(StructuraErrors.class, () -> processor.parse(YAML, Shop.class));

        assertEquals(List.of(
                "offers[1].price @5",
                "currencies.gems.symbol @8",
                "reward.amount @11",
                "pages[0][0].price @14"
        ), errors.errors().stream()
                .map(error -> error.path().orElseThrow() + " @" + error.line().orElseThrow())
                .toList());
        assertTrue(errors.errors().stream().allMatch(error -> error.kind() == Kind.INVALID));
    }

    @Test
    @DisplayName("is on by default")
    void onByDefault() {
        StructuraException error = assertThrows(StructuraException.class,
                () -> Structura.builder().polymorphics(TYPES).build().parse(YAML, Shop.class));

        assertEquals("offers[1].price", error.path().orElseThrow());
    }

    @Test
    @DisplayName("validateNested(false) validates only the components declared as records, as before 2.8.0")
    void canBeTurnedOff() {
        Shop shop = Structura.builder().polymorphics(TYPES).validateNested(false).build().parse(YAML, Shop.class);

        assertEquals(0, shop.offers().get(1).price());
    }

    @Test
    @DisplayName("stops at the first nested error when errors are not collected")
    void firstError() {
        StructuraException error = assertThrows(StructuraException.class, () -> Structura.builder()
                .polymorphics(TYPES).validateNested(true).build().parse(YAML, Shop.class));

        assertEquals("offers[1].price", error.path().orElseThrow());
        assertEquals("5:12: Value must be at least 1 (offers[1].price)", error.getMessage());
    }

    @Test
    @DisplayName("descends into the lists of an enum constant")
    void enumConstants() {
        StructuraException error = assertThrows(StructuraException.class, () -> Structura.builder()
                .validateNested(true).build().parseEnum("""
                        gold:
                          offers:
                            - item: apple
                              price: 0
                        """, Tier.class));

        assertEquals("gold.offers[0].price", error.path().orElseThrow());
        assertEquals(4, error.line().orElseThrow());
    }
}
