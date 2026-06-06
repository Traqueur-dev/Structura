package fr.traqueur.structura.writers.serializer;

import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.registries.PolymorphicRegistry;
import fr.traqueur.structura.writers.fixtures.WriterTestModels.*;
import fr.traqueur.structura.writers.registries.CustomWriterRegistry;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    // ── @Options(optional = true) ────────────────────────────────────────────

    @Test
    void optionalNullFieldIsOmitted() {
        String yaml = serializer.toYaml(new MixedOptionalConfig("hello", null, null));
        assertTrue(yaml.contains("required: hello"));
        assertFalse(yaml.contains("maybe-null:"), "null optional field must not appear in YAML");
        assertFalse(yaml.contains("maybe-int:"),  "null optional field must not appear in YAML");
    }

    @Test
    void optionalPresentFieldIsWritten() {
        String yaml = serializer.toYaml(new MixedOptionalConfig("hello", "world", 42));
        assertTrue(yaml.contains("maybe-null: world"));
        assertTrue(yaml.contains("maybe-int: 42"));
    }

    // ── @Options(name = "...") ────────────────────────────────────────────────

    @Test
    void customFieldNameOverridesDefault() {
        String yaml = serializer.toYaml(new CustomNameConfig("192.168.1.1", 9090));
        assertTrue(yaml.contains("server-address: 192.168.1.1"), "custom name must be used as YAML key");
        assertFalse(yaml.contains("server-address-field:"));
        assertFalse(yaml.contains("serverAddress:"), "camelCase field name must not appear");
    }

    // ── Enum serialization ────────────────────────────────────────────────────

    @Test
    void enumFieldIsConvertedToKebabCase() {
        String yaml = serializer.toYaml(new EnvConfig("App", Environment.PRODUCTION_READY));
        assertTrue(yaml.contains("production-ready"), "enum value must be written in kebab-case");
        assertFalse(yaml.contains("PRODUCTION_READY"), "raw enum name must not appear");
    }

    // ── LocalDate / LocalDateTime serialization ───────────────────────────────

    @Test
    void localDateIsIsoFormatted() {
        String yaml = serializer.toYaml(new ScheduleConfig(
            LocalDate.of(2024, 6, 15), LocalDateTime.of(2024, 6, 15, 10, 30, 0)
        ));
        assertTrue(yaml.contains("2024-06-15"),        "LocalDate must use ISO-8601 date format");
        assertTrue(yaml.contains("2024-06-15T10:30"), "LocalDateTime must use ISO-8601 datetime format");
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

    // ── @Options(isKey = true) ────────────────────────────────────────────────

    @Test
    void isKeyListBecomesYamlMap() {
        String yaml = serializer.toYaml(new PermissionConfig("MyApp",
            List.of(new Permission("admin", 10), new Permission("user", 1))
        ));

        assertTrue(yaml.contains("admin:"),   "isKey value must become the YAML key");
        assertTrue(yaml.contains("user:"),    "isKey value must become the YAML key");
        assertTrue(yaml.contains("level: 10"));
        assertTrue(yaml.contains("level: 1"));
        // the key field itself must not appear as a nested field
        assertFalse(yaml.contains("id: admin"), "'id' must not be written as a nested field");
        assertFalse(yaml.contains("id: user"),  "'id' must not be written as a nested field");
    }

    @Test
    void isKeyComplexKeyRecordFlattensSubRecordFields() {
        String yaml = serializer.toYaml(new ComplexKeyEntry(
            new ServerCoordinates("db.example.com", 5432), "primary", true
        ));

        assertTrue(yaml.contains("host: db.example.com"), "host must be at root level");
        assertTrue(yaml.contains("port: 5432"),           "port must be at root level");
        assertTrue(yaml.contains("label: primary"));
        assertTrue(yaml.contains("active: true"));
        assertFalse(yaml.contains("coords:"), "coords field name must not appear when flattened");
    }

    @Test
    void isKeyComplexKeyListProducesYamlListWithFlattenedFields() {
        String yaml = serializer.toYaml(new ComplexKeyListConfig(List.of(
            new ComplexKeyEntry(new ServerCoordinates("primary.db", 5432), "primary", true),
            new ComplexKeyEntry(new ServerCoordinates("replica.db", 5433), "replica", false)
        )));

        // Each entry is a YAML list item with the key sub-record flattened at item level
        assertTrue(yaml.contains("host: primary.db"));
        assertTrue(yaml.contains("host: replica.db"));
        assertTrue(yaml.contains("port: 5432"));
        assertTrue(yaml.contains("port: 5433"));
        assertTrue(yaml.contains("label: primary"));
        assertTrue(yaml.contains("label: replica"));
        // coords key must not appear (sub-record is flattened)
        assertFalse(yaml.contains("coords:"));
        // Must be a list (dashes), not a map — complex keys cannot act as unique map keys
        assertTrue(yaml.contains("- host:") || yaml.contains("-\n") || yaml.contains("- "),
                   "output must be a YAML list, not a map");
    }

    @Test
    void isKeyInsideMapValueStripsKeyField() {
        String yaml = serializer.toYaml(new EndpointMapConfig(Map.of(
            "/health", new Endpoint("/health", "GET", 200),
            "/login",  new Endpoint("/login",  "POST", 201)
        )));

        assertTrue(yaml.contains("/health:"));
        assertTrue(yaml.contains("/login:"));
        assertTrue(yaml.contains("method: GET"));
        assertTrue(yaml.contains("method: POST"));
        assertTrue(yaml.contains("status-code: 200"));
        assertFalse(yaml.contains("path:"), "'path' must not appear as a nested field");
    }

    @Test
    void isKeyRecordWithMultipleNonKeyFieldsKeepsNestedMap() {
        String yaml = serializer.toYaml(new RouteConfig(
            List.of(new Route("/api/users", "GET", true), new Route("/api/admin", "POST", false))
        ));

        assertTrue(yaml.contains("/api/users:"));
        assertTrue(yaml.contains("/api/admin:"));
        assertTrue(yaml.contains("method: GET"));
        assertTrue(yaml.contains("method: POST"));
        assertTrue(yaml.contains("enabled: true"));
        assertTrue(yaml.contains("enabled: false"));
        assertFalse(yaml.contains("path:"), "'path' must not appear as a nested field");
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
