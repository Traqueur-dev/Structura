package fr.traqueur.structura.writers.serializer;

import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.registries.PolymorphicRegistry;
import fr.traqueur.structura.writers.fixtures.WriterTestModels.*;
import fr.traqueur.structura.writers.registries.CustomWriterRegistry;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LoadableSerializerTest {

    private LoadableSerializer serializer;

    @BeforeAll
    static void setupRegistries() {
        tryCreate(Animal.class,   r -> { r.register("dog", Dog.class); r.register("cat", Cat.class); });
        tryCreate(DbEngine.class, r -> { r.register("mysql", MySQLEngine.class); r.register("postgres", PostgreSQLEngine.class); });
        tryCreate(ItemMeta.class, r -> { r.register("food", FoodMeta.class); r.register("potion", PotionMeta.class); });
    }

    @AfterAll
    static void tearDownRegistries() throws Exception {
        Field f = PolymorphicRegistry.class.getDeclaredField("REGISTRIES");
        f.setAccessible(true);
        ((Map<?, ?>) f.get(null)).clear();
    }

    @BeforeEach
    void setUp() {
        serializer = new LoadableSerializer();
    }

    // ── Basic ────────────────────────────────────────────────────────────────

    @Test
    void camelCaseToKebabCase() {
        String yaml = serializer.toYaml(new CamelCaseConfig("test", 9090));
        assertTrue(yaml.contains("server-name:"));
        assertFalse(yaml.contains("serverName:"));
    }

    @Test
    void nestedRecord() {
        String yaml = serializer.toYaml(new NestedDefaultConfig("MyApp", new ServerBlock("db.local", 5432)));
        assertTrue(yaml.contains("app-name: MyApp"));
        assertTrue(yaml.contains("server:"));
        assertTrue(yaml.contains("host: db.local"));
    }

    @Test
    void plainList() {
        String yaml = serializer.toYaml(new CollectionConfig("x", List.of("alpha", "beta"), Set.of(), Map.of()));
        assertTrue(yaml.contains("alpha"));
        assertTrue(yaml.contains("beta"));
    }

    @Test
    void customWriterInvoked() {
        CustomWriterRegistry.getInstance().register(Color.class, c -> c.r() + "," + c.g() + "," + c.b());
        String yaml = serializer.toYaml(new ColorConfig("red", new Color(255, 0, 0)));
        assertTrue(yaml.contains("255,0,0") || yaml.contains("'255,0,0'"));
        CustomWriterRegistry.getInstance().unregister(Color.class);
    }

    // ── @Options(inline = true) ───────────────────────────────────────────────

    @Test
    void inlineConcreteRecordFlattensFields() {
        String yaml = serializer.toYaml(new InlineConfig("MyApp", new ConnectionBlock("db.local", 5432)));

        assertTrue(yaml.contains("app-name: MyApp"));
        assertTrue(yaml.contains("host: db.local"),  "host must be flattened to root");
        assertTrue(yaml.contains("port: 5432"),      "port must be flattened to root");
        assertFalse(yaml.contains("connection:"),    "'connection' key must not appear when inline");
    }

    // ── @Polymorphic standard ─────────────────────────────────────────────────

    @Test
    void polymorphicDiscriminatorWrittenInsideNestedMap() {
        String yaml = serializer.toYaml(new AnimalConfig("Farm", new Dog("Buddy", "poodle")));

        assertTrue(yaml.contains("pet:"),   "field key must be present");
        assertTrue(yaml.contains("kind:"),  "discriminator must be inside nested map");
        assertTrue(yaml.contains("dog"),    "discriminator value must be present");
        assertTrue(yaml.contains("name: Buddy"));
    }

    @Test
    void polymorphicList() {
        String yaml = serializer.toYaml(new AnimalListConfig(
            List.of(new Dog("Rex", "lab"), new Cat("Mia", true))
        ));

        assertTrue(yaml.contains("kind: dog"));
        assertTrue(yaml.contains("kind: cat"));
    }

    @Test
    void polymorphicMap() {
        String yaml = serializer.toYaml(new AnimalMapConfig(
            Map.of("a", new Dog("Rex", "lab"), "b", new Cat("Luna", false))
        ));

        assertTrue(yaml.contains("kind: dog"));
        assertTrue(yaml.contains("kind: cat"));
    }

    // ── @Polymorphic(inline = true) ───────────────────────────────────────────

    @Test
    void inlinePolymorphicDiscriminatorAtParentLevel() {
        String yaml = serializer.toYaml(new InlineDbConfig("App", new MySQLEngine("db.local", 3306)));

        assertTrue(yaml.contains("engine: mysql"), "discriminator must be at root level");
        assertTrue(yaml.contains("db:"),           "concrete fields nested under 'db'");
        assertTrue(yaml.contains("host: db.local"));
    }

    @Test
    void fullyInlinePolymorphicFlattensEverything() {
        String yaml = serializer.toYaml(new FullyInlineDbConfig("App", new MySQLEngine("db.local", 3306)));

        assertTrue(yaml.contains("engine: mysql"), "discriminator at root");
        assertTrue(yaml.contains("host: db.local"), "concrete field at root");
        assertFalse(yaml.contains("db:"), "'db' key must not appear when fully inline");
    }

    // ── @Polymorphic(useKey = true) ───────────────────────────────────────────

    @Test
    void useKeyPolymorphicSingleFieldDropsFieldName() {
        String yaml = serializer.toYaml(new UseKeyItemConfig("Apple", new FoodMeta(10)));

        // discriminator value "food" becomes the key; field name "meta" must not appear
        assertTrue(yaml.contains("food:"),      "discriminator value must be the YAML key");
        assertTrue(yaml.contains("nutrition: 10"));
        assertFalse(yaml.contains("meta:"),     "'meta' field name must not appear with useKey");
    }

    @Test
    void useKeyPolymorphicListBecomesYamlMap() {
        String yaml = serializer.toYaml(new UseKeyItemListConfig(
            List.of(new FoodMeta(8), new PotionMeta("#00FF00"))
        ));

        assertTrue(yaml.contains("food:"),        "food discriminator key missing");
        assertTrue(yaml.contains("nutrition: 8"));
        assertTrue(yaml.contains("potion:"),      "potion discriminator key missing");
        assertTrue(yaml.contains("#00FF00"));
        // must not contain the standard list-item discriminator pattern "type: food"
        assertFalse(yaml.contains("type: food"), "standard discriminator must not appear with useKey");
    }

    @Test
    void useKeyPolymorphicMapOmitsExtraDiscriminator() {
        String yaml = serializer.toYaml(new UseKeyItemMapConfig(
            Map.of("slot1", new FoodMeta(5), "slot2", new PotionMeta("#0000FF"))
        ));

        assertTrue(yaml.contains("slot1:"));
        assertTrue(yaml.contains("slot2:"));
        assertTrue(yaml.contains("nutrition: 5"));
        assertTrue(yaml.contains("#0000FF"));
        // concrete fields must NOT be wrapped with a redundant "type:" key
        assertFalse(yaml.contains("type: food"),   "useKey map must not embed type discriminator");
        assertFalse(yaml.contains("type: potion"), "useKey map must not embed type discriminator");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static <T extends Loadable> void tryCreate(
            Class<T> clazz, java.util.function.Consumer<PolymorphicRegistry<T>> cfg) {
        try {
            PolymorphicRegistry.get(clazz);
        } catch (Exception ignored) {
            PolymorphicRegistry.create(clazz, cfg);
        }
    }
}
