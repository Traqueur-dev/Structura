package fr.traqueur.structura.writers;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.registries.PolymorphicRegistries;
import fr.traqueur.structura.registries.PolymorphicRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The writer writes a type variable component as its bound, and the file reads back.
 */
@DisplayName("Writer: type variables")
class TypeVariableWriterTest {

    @Polymorphic(key = "surface", inline = true)
    public interface Surface extends Loadable {}

    public record Chest(int rows) implements Surface {}

    @Polymorphic
    public interface Reward extends Loadable {}

    public record Money(int amount) implements Reward {}

    public record Menu<S extends Surface, R extends Reward>(String id,
                                                           @Options(inline = true) S surface,
                                                           List<R> rewards) implements Loadable {}

    @BeforeAll
    static void register() {
        PolymorphicRegistry.create(Surface.class, r -> r.register("chest", Chest.class));
        PolymorphicRegistry.create(Reward.class, r -> r.register("money", Money.class));
    }

    @AfterAll
    static void unregister() {
        PolymorphicRegistries.global().unregister(Surface.class);
        PolymorphicRegistries.global().unregister(Reward.class);
    }

    @TempDir
    Path dir;

    @Test
    @DisplayName("writes the bound's form and reads back")
    void roundTrip() throws Exception {
        Path file = dir.resolve("menu.yml");
        Menu<Chest, Money> menu = new Menu<>("shop", new Chest(3), List.of(new Money(5)));

        Structura.write(file, menu);

        assertEquals(Map.of("id", "shop", "surface", "chest", "rows", 3,
                        "rewards", List.of(Map.of("type", "money", "amount", 5))),
                new Yaml().load(Files.readString(file)));
        assertEquals(menu, Structura.load(file, Menu.class));
    }
}
