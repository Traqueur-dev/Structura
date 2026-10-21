package fr.traqueur.structura;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.annotations.Strict;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.exceptions.StructuraErrors;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.exceptions.StructuraException.Kind;
import fr.traqueur.structura.registries.PolymorphicRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * In strict mode, a key that no component reads is an error, with the closest known key suggested.
 */
@DisplayName("Strict keys")
class StrictKeysTest {

    public record Limits(int perDay, @Options(optional = true) Integer perWeek) implements Loadable {}

    public record Shop(String name, @Options(optional = true) Integer maxItems, Limits limits) implements Loadable {}

    public record Offer(String item, int price) implements Loadable {}

    public record Offers(List<Offer> offers) implements Loadable {}

    public record Currency(String symbol) implements Loadable {}

    public record Currencies(Map<String, Currency> currencies) implements Loadable {}

    public record Host(String host, int port) implements Loadable {}

    public record Server(String name, @Options(inline = true) Host address) implements Loadable {}

    public record Named(@Options(isKey = true) String id, int port) implements Loadable {}

    public record Cluster(List<Named> servers) implements Loadable {}

    public record Endpoint(@Options(isKey = true) Host address, String label) implements Loadable {}

    public record Messages(String prefix, @Options(inline = true) Map<String, String> byLocale) implements Loadable {}

    public record Renamed(@Options(name = "max_slots") int maxSlots) implements Loadable {}

    @Strict
    public record StrictShop(String name, Limits limits) implements Loadable {}

    public record Wrapper(StrictShop shop, @Options(optional = true) String note) implements Loadable {}

    @Polymorphic
    public interface Reward extends Loadable {}

    public record MoneyReward(int amount) implements Reward {}

    public record Quest(String title, Reward reward) implements Loadable {}

    @Polymorphic(inline = true)
    public interface Storage extends Loadable {}

    public record FileStorage(String path) implements Storage {}

    public record Backend(Storage storage) implements Loadable {}

    @Polymorphic(inline = true)
    public interface Engine extends Loadable {}

    public record DiskEngine(String folder, int threads) implements Engine {}

    public record Database(String name, @Options(inline = true) Engine engine) implements Loadable {}

    @Polymorphic(useKey = true)
    public interface Bonus extends Loadable {}

    public record SpeedBonus(int level) implements Bonus {}

    public record Bonuses(List<Bonus> bonuses) implements Loadable {}

    @BeforeAll
    static void registerTypes() {
        PolymorphicRegistry.create(Reward.class, registry -> registry.register("money", MoneyReward.class));
        PolymorphicRegistry.create(Storage.class, registry -> registry.register("file", FileStorage.class));
        PolymorphicRegistry.create(Engine.class, registry -> registry.register("disk", DiskEngine.class));
        PolymorphicRegistry.create(Bonus.class, registry -> registry.register("speed", SpeedBonus.class));
    }

    private final StructuraProcessor strict = Structura.builder().strictKeys(true).build();

    private StructuraException unknown(String yaml, Class<? extends Loadable> type) {
        StructuraException error = assertThrows(StructuraException.class, () -> strict.parse(yaml, type));
        assertEquals(Kind.UNKNOWN_KEY, error.kind(), error.getMessage());
        return error;
    }

    @Nested
    @DisplayName("Where a typo is caught")
    class Typos {

        @Test
        @DisplayName("in a nested record, before the key it hides is reported missing")
        void nestedRecord() {
            StructuraException error = unknown("""
                    name: shop
                    limits:
                      per-dya: 3
                    """, Shop.class);

            assertEquals("limits.per-dya", error.path().orElseThrow());
            assertEquals("3:3: unknown key 'per-dya' under 'limits' (did you mean 'per-day'?)", error.getMessage());
        }

        @Test
        @DisplayName("at the root, where an optional value would silently keep its default")
        void root() {
            StructuraException error = unknown("""
                    name: shop
                    max-item: 54
                    limits:
                      per-day: 3
                    """, Shop.class);

            assertEquals("2:1: unknown key 'max-item' (did you mean 'max-items'?)", error.getMessage());
        }

        @Test
        @DisplayName("in a list element")
        void listElement() {
            StructuraException error = unknown("""
                    offers:
                      - item: apple
                        price: 3
                      - item: pear
                        prise: 4
                        price: 4
                    """, Offers.class);

            assertEquals("offers[1].prise", error.path().orElseThrow());
            assertEquals(5, error.line().orElseThrow());
            assertTrue(error.getMessage().contains("did you mean 'price'?"), error.getMessage());
        }

        @Test
        @DisplayName("in a map value, the map's own keys being free")
        void mapValue() {
            StructuraException error = unknown("""
                    currencies:
                      coins:
                        symbol: c
                      gems:
                        symbol: g
                        simbol: g
                    """, Currencies.class);

            assertEquals("currencies.gems.simbol", error.path().orElseThrow());
            assertEquals(6, error.line().orElseThrow());
        }

        @Test
        @DisplayName("in a polymorphic sub-type, its discriminator being known")
        void polymorphicSubType() {
            StructuraException error = unknown("""
                    title: first
                    reward:
                      type: money
                      amout: 3
                    """, Quest.class);

            assertEquals("reward.amout", error.path().orElseThrow());
            assertTrue(error.getMessage().contains("did you mean 'amount'?"), error.getMessage());
        }

        @Test
        @DisplayName("in a polymorphic sub-type selected by its key")
        void polymorphicByKey() {
            StructuraException error = unknown("""
                    bonuses:
                      speed:
                        levle: 2
                    """, Bonuses.class);

            assertEquals("bonuses.speed.levle", error.path().orElseThrow());
        }

        @Test
        @DisplayName("in the keys of a fully inline polymorphic sub-type, read at the parent level")
        void fullyInlinePolymorphic() {
            StructuraException error = unknown("""
                    name: main
                    type: disk
                    folder: data
                    treads: 4
                    """, Database.class);

            assertEquals("treads", error.path().orElseThrow());
            assertTrue(error.getMessage().contains("did you mean 'threads'?"), error.getMessage());
        }

        @Test
        @DisplayName("next to an inline component, whose keys are read at the parent level")
        void inlineComponent() {
            StructuraException error = unknown("""
                    name: lobby
                    host: localhost
                    port: 25565
                    prot: 1
                    """, Server.class);

            assertEquals("prot", error.path().orElseThrow());
        }

        @Test
        @DisplayName("in the entries of a key component (simple mapping)")
        void simpleKeyMapping() {
            StructuraException error = unknown("""
                    servers:
                      lobby:
                        port: 1
                        prot: 2
                    """, Cluster.class);

            assertEquals("servers.lobby.prot", error.path().orElseThrow());
        }

        @Test
        @DisplayName("next to a key record whose components are flattened (complex mapping)")
        void complexKeyMapping() {
            StructuraException error = unknown("""
                    host: localhost
                    port: 80
                    label: web
                    lable: web
                    """, Endpoint.class);

            assertEquals("lable", error.path().orElseThrow());
        }
    }

    @Nested
    @DisplayName("What is accepted")
    class Accepted {

        @Test
        @DisplayName("every shape above, written correctly")
        void validDocuments() {
            assertDoesNotThrow(() -> strict.parse("""
                    name: lobby
                    host: localhost
                    port: 25565
                    """, Server.class));
            assertDoesNotThrow(() -> strict.parse("""
                    servers:
                      lobby:
                        port: 1
                    """, Cluster.class));
            assertDoesNotThrow(() -> strict.parse("""
                    host: localhost
                    port: 80
                    label: web
                    """, Endpoint.class));
            assertDoesNotThrow(() -> strict.parse("""
                    title: first
                    reward:
                      type: money
                      amount: 3
                    """, Quest.class));
            assertDoesNotThrow(() -> strict.parse("""
                    type: file
                    storage:
                      path: data.db
                    """, Backend.class));
            assertDoesNotThrow(() -> strict.parse("""
                    name: main
                    type: disk
                    folder: data
                    threads: 4
                    """, Database.class));
            assertDoesNotThrow(() -> strict.parse("""
                    bonuses:
                      speed:
                        level: 2
                    """, Bonuses.class));
            assertDoesNotThrow(() -> strict.parse("""
                    max_slots: 3
                    """, Renamed.class));
        }

        @Test
        @DisplayName("an inline map absorbs every remaining key")
        void inlineMapAbsorbs() {
            Messages messages = strict.parse("""
                    prefix: "[shop]"
                    fr_FR: Bienvenue
                    en_US: Welcome
                    """, Messages.class);

            assertEquals(2, messages.byLocale().size());
        }

        @Test
        @DisplayName("without strict mode, unknown keys are still ignored")
        void notStrictByDefault() {
            Shop shop = Structura.parse("""
                    name: shop
                    max-item: 54
                    limits:
                      per-day: 3
                    """, Shop.class);

            assertNull(shop.maxItems());
        }
    }

    @Nested
    @DisplayName("@Strict")
    class StrictAnnotation {

        @Test
        @DisplayName("makes a record and what it reads strict, without strict mode")
        void annotatedRecord() {
            StructuraException error = assertThrows(StructuraException.class, () -> Structura.parse("""
                    shop:
                      name: shop
                      limits:
                        per-dya: 3
                    """, Wrapper.class));

            assertEquals(Kind.UNKNOWN_KEY, error.kind());
            assertEquals("shop.limits.per-dya", error.path().orElseThrow());
        }

        @Test
        @DisplayName("leaves the records around it lenient")
        void parentStaysLenient() {
            assertDoesNotThrow(() -> Structura.parse("""
                    shop:
                      name: shop
                      limits:
                        per-day: 3
                    extra: ignored
                    """, Wrapper.class));
        }
    }

    @Test
    @DisplayName("with collected errors, every unknown key is reported")
    void collectedUnknownKeys() {
        StructuraProcessor processor = Structura.builder().strictKeys(true).collectErrors(true).build();

        StructuraErrors errors = assertThrows(StructuraErrors.class, () -> processor.parse("""
                name: shop
                max-item: 54
                limits:
                  per-dya: 3
                """, Shop.class));

        assertEquals(List.of("max-item", "limits.per-dya", "limits.per-day"),
                errors.errors().stream().map(error -> error.path().orElseThrow()).toList());
        assertEquals(List.of(Kind.UNKNOWN_KEY, Kind.UNKNOWN_KEY, Kind.MISSING),
                errors.errors().stream().map(StructuraException::kind).toList());
    }
}
