package fr.traqueur.structura.writers.factory;

import fr.traqueur.structura.writers.exceptions.StructuraWriterException;
import fr.traqueur.structura.writers.fixtures.WriterTestModels.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class DefaultInstanceFactoryTest {

    private DefaultInstanceFactory factory;

    @BeforeEach
    void setUp() {
        factory = new DefaultInstanceFactory();
    }

    @Test
    void shouldResolveDefaultAnnotations() {
        SimpleDefaultConfig config = factory.createDefault(SimpleDefaultConfig.class);
        assertEquals("Afelia", config.serverName());
        assertEquals(25565, config.port());
        assertTrue(config.debug());
    }

    @Test
    void shouldCreateNestedDefaults() {
        NestedDefaultConfig config = factory.createDefault(NestedDefaultConfig.class);
        assertNotNull(config.server());
        assertEquals("localhost", config.server().host());
    }

    @Test
    void shouldThrowForNonRecord() {
        var nonRecord = new fr.traqueur.structura.api.Loadable() {};
        @SuppressWarnings("unchecked")
        Class<? extends fr.traqueur.structura.api.Loadable> clazz =
            (Class<? extends fr.traqueur.structura.api.Loadable>) nonRecord.getClass();

        assertThrows(StructuraWriterException.class, () -> factory.createDefault(clazz));
    }
}
