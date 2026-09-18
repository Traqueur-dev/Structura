package fr.traqueur.structura.writers;

import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.registries.PolymorphicRegistry;
import fr.traqueur.structura.writers.fixtures.WriterTestModels.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StructuraWritersTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setupRegistries() {
        tryCreate(Animal.class,   r -> { r.register("dog", Dog.class); r.register("cat", Cat.class); });
        tryCreate(DbEngine.class, r -> { r.register("mysql", MySQLEngine.class); r.register("postgres", PostgreSQLEngine.class); });
    }

    @AfterAll
    static void tearDownRegistries() throws Exception {
        Field f = PolymorphicRegistry.class.getDeclaredField("REGISTRIES");
        f.setAccessible(true);
        ((Map<?, ?>) f.get(null)).clear();
    }

    // ── Basic write / read-back ───────────────────────────────────────────────

    @Test
    void shouldWriteAndReadBack() throws Exception {
        Path file = tempDir.resolve("config.yml");
        PlainConfig original = new PlainConfig("RoundTrip", 99);

        Structura.write(file, original);
        PlainConfig loaded = Structura.load(file, PlainConfig.class);

        assertEquals(original.name(), loaded.name());
        assertEquals(original.value(), loaded.value());
    }

    @Test
    void shouldWriteKebabCaseKeys() throws Exception {
        Path file = tempDir.resolve("config.yml");
        Structura.write(file, new CamelCaseConfig("srv", 9090));

        String content = Files.readString(file);
        assertTrue(content.contains("server-name:"));
        assertTrue(content.contains("http-port:"));
    }

    @Test
    void shouldSerializeNestedRecord() throws Exception {
        Path file = tempDir.resolve("nested.yml");
        Structura.write(file, new NestedDefaultConfig("MyApp", new ServerBlock("db.local", 5432)));

        String content = Files.readString(file);
        assertTrue(content.contains("app-name: MyApp"));
        assertTrue(content.contains("server:"));
        assertTrue(content.contains("host: db.local"));
    }

    @Test
    void shouldRoundTripInlineCatchAllMap() throws Exception {
        Path file = tempDir.resolve("catch-all.yml");
        CatchAllMapConfig original = new CatchAllMapConfig(
                "greeting", Map.of("fr-FR", "Bienvenue", "en-US", "Welcome"));

        Structura.write(file, original);

        String content = Files.readString(file);
        assertFalse(content.contains("by-locale"), "the field name must not appear in the file");
        assertTrue(content.contains("fr-FR: Bienvenue"));

        CatchAllMapConfig loaded = Structura.load(file, CatchAllMapConfig.class);
        assertEquals(original.comment(), loaded.comment());
        assertEquals(original.byLocale(), loaded.byLocale());
    }

    // ── Parent directory creation ─────────────────────────────────────────────

    @Test
    void writeCreatesParentDirectoriesIfAbsent() throws Exception {
        Path file = tempDir.resolve("a/b/c/config.yml");
        assertFalse(Files.exists(file.getParent()), "parent must not exist before write");

        Structura.write(file, new PlainConfig("deep", 1));

        assertTrue(Files.exists(file), "file must be created including parent dirs");
    }

    // ── saveDefault ───────────────────────────────────────────────────────────

    @Test
    void saveDefaultShouldGenerateFromAnnotations() throws Exception {
        Path file = tempDir.resolve("default.yml");
        Structura.saveDefault(file, SimpleDefaultConfig.class);

        SimpleDefaultConfig loaded = Structura.load(file, SimpleDefaultConfig.class);
        assertEquals("Afelia", loaded.serverName());
        assertEquals(25565, loaded.port());
        assertTrue(loaded.debug());
    }

    @Test
    void saveDefaultOmitsOptionalFieldsWithNoDefault() throws Exception {
        Path file = tempDir.resolve("optional.yml");
        Structura.saveDefault(file, OptionalOnlyConfig.class);

        String content = Files.readString(file);
        assertTrue(content.contains("required:"));
        assertFalse(content.contains("never-default:"), "optional field with no default must be absent");
    }

    @Test
    void configurableEnumRoundTrip() throws Exception {
        Path file = tempDir.resolve("databases.yml");

        Structura.writeEnum(file, ConfigurableDatabase.class);
        String content = Files.readString(file);

        assertTrue(content.contains("mysql:"));
        assertTrue(content.contains("postgresql:"));
        assertTrue(content.contains("default-port: 3306"));
        assertFalse(content.contains("description: null"));

        Structura.loadEnum(file, ConfigurableDatabase.class);
        assertEquals("mysql.Driver", ConfigurableDatabase.MYSQL.driver());
        assertEquals(5432, ConfigurableDatabase.POSTGRESQL.defaultPort());
        assertEquals("database-host", ConfigurableDatabase.POSTGRESQL.description());
        assertEquals("true", ConfigurableDatabase.MYSQL.properties().get("ssl"));
    }

    @Test
    void saveDefaultEnumFillsNullFieldsFromAnnotations() throws Exception {
        Path file = tempDir.resolve("profiles.yml");

        Structura.saveDefaultEnum(file, DefaultableProfile.class);
        String content = Files.readString(file);

        assertTrue(content.contains("host: localhost"), "null field filled from @DefaultString");
        assertTrue(content.contains("host: remote.host"), "existing value preserved");
    }

    // ── Polymorphic round-trip ────────────────────────────────────────────────

    @Test
    void polymorphicStandardRoundTrip() throws Exception {
        Path file = tempDir.resolve("animal.yml");
        Structura.write(file, new AnimalConfig("Farm", new Dog("Buddy", "poodle")));

        AnimalConfig loaded = Structura.load(file, AnimalConfig.class);
        assertInstanceOf(Dog.class, loaded.pet());
        assertEquals("Buddy", ((Dog) loaded.pet()).name());
        assertEquals("poodle", ((Dog) loaded.pet()).breed());
    }

    @Test
    void polymorphicInlineRoundTrip() throws Exception {
        Path file = tempDir.resolve("db.yml");
        Structura.write(file, new InlineDbConfig("App", new MySQLEngine("db.local", 3306)));

        InlineDbConfig loaded = Structura.load(file, InlineDbConfig.class);
        assertInstanceOf(MySQLEngine.class, loaded.db());
        assertEquals("db.local", ((MySQLEngine) loaded.db()).host());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static <T extends fr.traqueur.structura.api.Loadable> void tryCreate(
            Class<T> clazz, java.util.function.Consumer<PolymorphicRegistry<T>> cfg) {
        try {
            PolymorphicRegistry.get(clazz);
        } catch (Exception ignored) {
            PolymorphicRegistry.create(clazz, cfg);
        }
    }
}
