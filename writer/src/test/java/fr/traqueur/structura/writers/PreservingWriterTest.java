package fr.traqueur.structura.writers;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.defaults.DefaultInt;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Source;
import fr.traqueur.structura.api.Structura;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The writer keeps what a person wrote, writes only what differs from the default, and uses the
 * reader's key names.
 */
@DisplayName("Writer: comments, overrides and keys")
class PreservingWriterTest {

    public record Limits(int perDay, int perWeek) implements Loadable {}

    public record Price(String currency, int amount) implements Loadable {}

    public record Shop(String name,
                       @DefaultInt(27) int maxItems,
                       Limits limits,
                       Map<String, Price> prices,
                       List<String> categories,
                       @Options(optional = true) String motd) implements Loadable {}

    public record Names(String httpURL, int level2Cap, @Options(name = "max_slots") int maxSlots) implements Loadable {}

    private static final String JAR_DEFAULT = """
            name: Shop
            max-items: 27
            limits:
              per-day: 5
              per-week: 20
            prices:
              apple:
                currency: coins
                amount: 3
            categories:
              - food
              - tools
            """;

    @TempDir
    Path dir;

    private static Shop defaults() {
        return Structura.parse(JAR_DEFAULT, Shop.class);
    }

    @Nested
    @DisplayName("update keeps the file as written")
    class Update {

        private static final String WRITTEN = """
                # Shop settings
                name: 'Market'   # shown in the title

                # Limits per player
                limits:
                  per-day: 5    # resets at midnight
                  # weekly cap
                  per-week: 20
                max-items: 27
                prices:
                  apple:
                    currency: coins
                    amount: 3
                categories:
                  - food    # edible
                  - tools
                # end of file
                """;

        @Test
        @DisplayName("comments, key order and quoting survive a change of one value")
        void changeOneValue() throws Exception {
            Path file = dir.resolve("shop.yml");
            Files.writeString(file, WRITTEN);
            Shop shop = Structura.load(file, Shop.class);

            Structura.update(file, new Shop(shop.name(), shop.maxItems(), new Limits(8, 20),
                    shop.prices(), shop.categories(), null));

            assertEquals("""
                    # Shop settings
                    name: 'Market' # shown in the title

                    # Limits per player
                    limits:
                      per-day: 8 # resets at midnight
                      # weekly cap
                      per-week: 20
                    max-items: 27
                    prices:
                      apple:
                        currency: coins
                        amount: 3
                    categories:
                      - food # edible
                      - tools
                    # end of file
                    """, Files.readString(file));
        }

        @Test
        @DisplayName("an unchanged configuration gives back the same file, but for the spaces before comments")
        void unchanged() throws Exception {
            Path file = dir.resolve("shop.yml");
            Files.writeString(file, WRITTEN);

            Structura.update(file, Structura.load(file, Shop.class));

            assertEquals(WRITTEN.replaceAll("(?<=\\S) +#", " #"), Files.readString(file));
        }

        @Test
        @DisplayName("a new key goes after the existing ones; a key no longer there is removed")
        void addAndRemove() throws Exception {
            Path file = dir.resolve("shop.yml");
            Files.writeString(file, """
                    # Shop
                    name: Market
                    motd: Hello   # greeting
                    max-items: 27
                    limits: {per-day: 5, per-week: 20}
                    prices: {}
                    categories: []
                    """);

            Structura.update(file, new Shop("Market", 27, new Limits(5, 20),
                    Map.of("pear", new Price("gems", 2)), List.of(), null));

            String written = Files.readString(file);
            assertFalse(written.contains("motd"), written);
            assertTrue(written.startsWith("# Shop\nname: Market\nmax-items: 27\n"), written);
            assertEquals(new Price("gems", 2), Structura.parse(written, Shop.class).prices().get("pear"));
        }

        @Test
        @DisplayName("creates the file when there is none")
        void createsFile() throws Exception {
            Path file = dir.resolve("new/shop.yml");
            Shop shop = defaults();

            Structura.update(file, shop);

            assertEquals(shop, Structura.load(file, Shop.class));
        }
    }

    @Nested
    @DisplayName("writeOverrides writes only what differs from the default")
    class Overrides {

        @Test
        @DisplayName("sections key by key, lists and values as a whole")
        void onlyDifferences() throws Exception {
            Path file = dir.resolve("config.yml");
            Shop edited = new Shop("Market", 27, new Limits(8, 20),
                    Map.of("apple", new Price("coins", 4)), List.of("food", "tools"), null);

            Structura.writeOverrides(file, edited, defaults());

            assertEquals(Map.of(
                    "name", "Market",
                    "limits", Map.of("per-day", 8),
                    "prices", Map.of("apple", Map.of("amount", 4))
            ), new Yaml().load(Files.readString(file)));
        }

        @Test
        @DisplayName("read over the default, the file gives the configuration back")
        void roundTripThroughLayers() throws Exception {
            Path file = dir.resolve("config.yml");
            Shop edited = new Shop("Market", 54, new Limits(5, 30),
                    Map.of("apple", new Price("gems", 3), "plum", new Price("coins", 1)), List.of("food"), null);

            Structura.writeOverrides(file, edited, defaults());

            Shop read = Structura.parse(List.of(Source.of("config.yml (jar)", JAR_DEFAULT), Source.of(file)), Shop.class);
            assertEquals(edited.name(), read.name());
            assertEquals(edited.maxItems(), read.maxItems());
            assertEquals(edited.limits(), read.limits());
            assertEquals(edited.prices(), read.prices());
            assertEquals(edited.categories(), read.categories());
        }

        @Test
        @DisplayName("keeps the comments of the server file and drops what went back to the default")
        void keepsServerComments() throws Exception {
            Path file = dir.resolve("config.yml");
            Files.writeString(file, """
                    # Our shop
                    name: Market   # renamed for the event
                    limits:
                      # doubled for the weekend
                      per-day: 10
                    """);

            Structura.writeOverrides(file, new Shop("Market", 27, new Limits(5, 20),
                    Map.of("apple", new Price("coins", 3)), List.of("food", "tools"), null), defaults());

            assertEquals("""
                    # Our shop
                    name: Market # renamed for the event
                    """, Files.readString(file));
        }

        @Test
        @DisplayName("nothing to override gives an empty file")
        void nothingToOverride() throws Exception {
            Path file = dir.resolve("config.yml");

            Structura.writeOverrides(file, defaults(), defaults());

            assertEquals("", Files.readString(file));
        }
    }

    @Test
    @DisplayName("keys are written with the names the reader reads")
    void sameKeysAsReader() throws Exception {
        Path file = dir.resolve("names.yml");
        Names names = new Names("http://x", 2, 3);

        Structura.write(file, names);

        Set<String> expected = Set.copyOf(Arrays.stream(Names.class.getRecordComponents()).map(Structura::keyOf).toList());
        Map<String, Object> written = new Yaml().load(Files.readString(file));
        assertEquals(expected, written.keySet());
        assertEquals(names, Structura.builder().strictKeys(true).build().parse(Files.readString(file), Names.class));
    }
}
