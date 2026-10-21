package fr.traqueur.structura.writers;

import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.writers.registries.CustomWriterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A writer registered for an interface or a superclass serves the values whose runtime class is a
 * subtype, such as Adventure's Component and its internal implementations.
 */
@DisplayName("Writer lookup")
class WriterLookupTest {

    /** Stands for Adventure's Component: users only ever see the interface. */
    public interface Component {
        String plain();
    }

    /** Stands for the internal implementation the values really are. */
    static final class TextComponentImpl implements Component {
        private final String text;

        TextComponentImpl(String text) {
            this.text = text;
        }

        @Override
        public String plain() {
            return text;
        }
    }

    public abstract static class Shape {
        abstract int sides();
    }

    static final class Square extends Shape {
        @Override
        int sides() {
            return 4;
        }
    }

    public record Message(Component text, Shape shape) implements Loadable {}

    @TempDir
    Path dir;

    @AfterEach
    void clear() {
        CustomWriterRegistry.getInstance().unregister(Component.class);
        CustomWriterRegistry.getInstance().unregister(Shape.class);
        CustomWriterRegistry.getInstance().unregister(TextComponentImpl.class);
    }

    @Test
    @DisplayName("a writer for an interface or a superclass serves its implementations")
    void interfaceAndSuperclass() throws Exception {
        CustomWriterRegistry.getInstance().register(Component.class, component -> "<b>" + component.plain());
        CustomWriterRegistry.getInstance().register(Shape.class, shape -> shape.sides() + " sides");
        Path file = dir.resolve("message.yml");

        Structura.write(file, new Message(new TextComponentImpl("Hello"), new Square()));

        assertEquals(Map.of("text", "<b>Hello", "shape", "4 sides"), new Yaml().load(Files.readString(file)));
        assertTrue(CustomWriterRegistry.getInstance().hasWriter(TextComponentImpl.class));
    }

    @Test
    @DisplayName("the writer of the closest type wins")
    void closestWins() throws Exception {
        CustomWriterRegistry.getInstance().register(Component.class, component -> "interface");
        CustomWriterRegistry.getInstance().register(TextComponentImpl.class, component -> "exact");
        CustomWriterRegistry.getInstance().register(Shape.class, shape -> "shape");
        Path file = dir.resolve("message.yml");

        Structura.write(file, new Message(new TextComponentImpl("Hello"), new Square()));

        assertEquals("exact", new Yaml().<Map<String, Object>>load(Files.readString(file)).get("text"));
    }
}
