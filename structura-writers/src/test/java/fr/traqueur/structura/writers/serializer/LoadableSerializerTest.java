package fr.traqueur.structura.writers.serializer;

import fr.traqueur.structura.writers.fixtures.WriterTestModels.*;
import fr.traqueur.structura.writers.registries.CustomWriterRegistry;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoadableSerializerTest {

    private LoadableSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new LoadableSerializer();
    }

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
        String yaml = serializer.toYaml(new CollectionConfig("x", List.of("alpha", "beta"), java.util.Set.of(), java.util.Map.of()));
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
}
