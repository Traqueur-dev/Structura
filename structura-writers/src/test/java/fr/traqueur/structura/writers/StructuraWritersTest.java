package fr.traqueur.structura.writers;

import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.writers.fixtures.WriterTestModels.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StructuraWritersTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldWriteAndReadBack() throws Exception {
        Path file = tempDir.resolve("config.yml");
        PlainConfig original = new PlainConfig("RoundTrip", 99);

        StructuraWriters.write(file, original);
        PlainConfig loaded = Structura.load(file, PlainConfig.class);

        assertEquals(original.name(), loaded.name());
        assertEquals(original.value(), loaded.value());
    }

    @Test
    void shouldWriteKebabCaseKeys() throws Exception {
        Path file = tempDir.resolve("config.yml");
        StructuraWriters.write(file, new CamelCaseConfig("srv", 9090));

        String content = Files.readString(file);
        assertTrue(content.contains("server-name:"));
        assertTrue(content.contains("http-port:"));
    }

    @Test
    void saveDefaultShouldGenerateFromAnnotations() throws Exception {
        Path file = tempDir.resolve("default.yml");
        StructuraWriters.saveDefault(file, SimpleDefaultConfig.class);

        SimpleDefaultConfig loaded = Structura.load(file, SimpleDefaultConfig.class);
        assertEquals("Afelia", loaded.serverName());
        assertEquals(25565, loaded.port());
        assertTrue(loaded.debug());
    }

    @Test
    void shouldSerializeNestedRecord() throws Exception {
        Path file = tempDir.resolve("nested.yml");
        StructuraWriters.write(file, new NestedDefaultConfig("MyApp", new ServerBlock("db.local", 5432)));

        String content = Files.readString(file);
        assertTrue(content.contains("app-name: MyApp"));
        assertTrue(content.contains("server:"));
        assertTrue(content.contains("host: db.local"));
    }
}
