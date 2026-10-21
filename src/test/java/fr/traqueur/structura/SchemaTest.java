package fr.traqueur.structura;

import fr.traqueur.structura.annotations.Description;
import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.annotations.defaults.DefaultInt;
import fr.traqueur.structura.annotations.defaults.DefaultString;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.registries.PolymorphicRegistries;
import fr.traqueur.structura.schema.Schema;
import fr.traqueur.structura.schema.Schema.Discriminator;
import fr.traqueur.structura.schema.Schema.Shape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structura.schema describes what a record reads, with the reader's own rules.
 */
@DisplayName("Schema")
class SchemaTest {

    public enum Mode { FAST, SAFE }

    public record Limits(@Description("Purchases per player and day") int perDay,
                         @Options(optional = true, description = "Purchases per week") Integer perWeek) implements Loadable {}

    public record Host(String host, @DefaultInt(25565) int port) implements Loadable {}

    @Polymorphic
    public interface Reward extends Loadable {}

    @Description("Gives money")
    public record MoneyReward(int amount) implements Reward {}

    public record ItemReward(String item, @DefaultInt(1) int count) implements Reward {}

    public record Offer(String item, int price) implements Loadable {}

    public record Named(@Options(isKey = true) String id, int port) implements Loadable {}

    public record Node(String name, @Options(optional = true) List<Node> children) implements Loadable {}

    @Description("A shop")
    public record Shop(@DefaultString("Shop") String name,
                       @Options(name = "max_items") @DefaultInt(27) int maxItems,
                       Mode mode,
                       Limits limits,
                       @Options(inline = true) Host address,
                       List<Offer> offers,
                       Map<String, Integer> prices,
                       Reward reward,
                       List<Named> servers,
                       @Options(inline = true) Map<String, String> extra) implements Loadable {}

    private static final PolymorphicRegistries TYPES = PolymorphicRegistries.create();

    @BeforeAll
    static void registerRewards() {
        TYPES.register(Reward.class, registry -> {
            registry.register("money", MoneyReward.class);
            registry.register("item", ItemReward.class);
        });
    }

    private static Schema shop() {
        return Structura.builder().polymorphics(TYPES).build().schema(Shop.class);
    }

    private static Schema property(Schema schema, String key) {
        return schema.properties().stream().filter(property -> key.equals(property.key())).findFirst()
                .orElseThrow(() -> new AssertionError("no property " + key + " in " + schema.properties()));
    }

    @Nested
    @DisplayName("Tree")
    class Tree {

        @Test
        @DisplayName("keys as the reader reads them, inline components flattened")
        void keys() {
            assertEquals(List.of("name", "max_items", "mode", "limits", "host", "port", "offers", "prices",
                            "reward", "servers"),
                    shop().properties().stream().map(Schema::key).toList());
        }

        @Test
        @DisplayName("types, required keys, defaults and descriptions")
        void details() {
            Schema shop = shop();

            assertEquals(Shape.SECTION, shop.shape());
            assertEquals("A shop", shop.description().orElseThrow());
            assertEquals(Shape.TEXT, property(shop, "name").shape());
            assertFalse(property(shop, "name").required());
            assertEquals("Shop", property(shop, "name").defaultValue().orElseThrow());
            assertEquals(27, property(shop, "max_items").defaultValue().orElseThrow());
            assertEquals(25565, property(shop, "port").defaultValue().orElseThrow());
            assertTrue(property(shop, "host").required());

            Schema limits = property(shop, "limits");
            assertEquals(Shape.SECTION, limits.shape());
            assertEquals("Purchases per player and day", property(limits, "per-day").description().orElseThrow());
            assertEquals("Purchases per week", property(limits, "per-week").description().orElseThrow());
            assertTrue(property(limits, "per-day").required());
            assertFalse(property(limits, "per-week").required());
        }

        @Test
        @DisplayName("enums, lists, maps, inline maps and keyed records")
        void containers() {
            Schema shop = shop();

            assertEquals(List.of("FAST", "SAFE"), property(shop, "mode").enumValues());
            Schema offers = property(shop, "offers");
            assertEquals(Shape.LIST, offers.shape());
            assertEquals(List.of("item", "price"), offers.items().orElseThrow().properties().stream().map(Schema::key).toList());
            assertEquals(Shape.INTEGER, property(shop, "prices").values().orElseThrow().shape());
            assertEquals(Shape.TEXT, shop.remaining().orElseThrow().shape());
            assertEquals("id", property(shop, "servers").items().orElseThrow().keyProperty().orElseThrow().key());
        }

        @Test
        @DisplayName("polymorphic sub-types from the processor's registries")
        void polymorphic() {
            Schema reward = property(shop(), "reward");

            assertEquals(Shape.POLYMORPHIC, reward.shape());
            assertEquals("type", reward.discriminator().orElseThrow());
            assertEquals(Discriminator.NESTED, reward.discriminatorPlacement().orElseThrow());
            assertEquals(List.of("item", "money"), List.copyOf(reward.subtypes().keySet()));
            assertEquals("Gives money", reward.subtypes().get("money").description().orElseThrow());
            assertEquals(List.of("item", "count"),
                    reward.subtypes().get("item").properties().stream().map(Schema::key).toList());
        }

        @Test
        @DisplayName("a recursive record is described once")
        void recursive() {
            Schema node = Structura.schema(Node.class);
            Schema child = property(node, "children").items().orElseThrow();

            assertTrue(child.recursive());
            assertTrue(child.properties().isEmpty());
        }
    }

    @Nested
    @DisplayName("JSON Schema")
    class Json {

        @SuppressWarnings("unchecked")
        private Map<String, Object> json() {
            return new Yaml().load(shop().toJsonSchema());
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> at(Map<String, Object> json, String... path) {
            Map<String, Object> current = json;
            for (String key : path) {
                current = (Map<String, Object>) current.get(key);
            }
            return current;
        }

        @Test
        @DisplayName("is valid JSON with the keys, types, defaults and required keys")
        void structure() {
            Map<String, Object> json = json();

            assertEquals("https://json-schema.org/draft/2020-12/schema", json.get("$schema"));
            assertEquals("object", json.get("type"));
            assertEquals("A shop", json.get("description"));
            assertEquals(Map.of("type", "integer", "default", 27), at(json, "properties", "max_items"));
            assertEquals(List.of("mode", "limits", "host", "offers", "prices", "reward", "servers"), json.get("required"));
            assertEquals(Map.of("type", "string"), json.get("additionalProperties"));
            assertEquals(List.of("FAST", "fast", "SAFE", "safe"), at(json, "properties", "mode").get("enum"));
            assertEquals(false, at(json, "properties", "limits").get("additionalProperties"));
            assertEquals("array", at(json, "properties", "offers").get("type"));
        }

        @Test
        @DisplayName("a polymorphic value has one branch per sub-type with its discriminator")
        @SuppressWarnings("unchecked")
        void polymorphicBranches() {
            List<Map<String, Object>> branches = (List<Map<String, Object>>) at(json(), "properties", "reward").get("oneOf");

            assertEquals(2, branches.size());
            assertEquals(Map.of("const", "item"), at(branches.get(0), "properties", "type"));
            assertEquals(List.of("type", "item"), branches.get(0).get("required"));
            assertEquals(Map.of("const", "money"), at(branches.get(1), "properties", "type"));
        }

        @Test
        @DisplayName("a list of keyed records is a section of entries")
        void keyedList() {
            Map<String, Object> servers = at(json(), "properties", "servers");

            assertEquals("object", servers.get("type"));
            assertEquals(Map.of("port", Map.of("type", "integer")), at(servers, "additionalProperties", "properties"));
        }
    }
}
