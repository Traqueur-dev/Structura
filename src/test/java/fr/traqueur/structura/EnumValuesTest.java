package fr.traqueur.structura;

import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An enum value is read in every form a person, or the writer, writes it.
 */
@DisplayName("Enum values")
class EnumValuesTest {

    public enum Color { DARK_RED, BLUE }

    public record Banner(Color color) implements Loadable {}

    @Test
    @DisplayName("DARK_RED, dark_red and dark-red (the writer's form) all read as DARK_RED")
    void everyForm() {
        for (String form : new String[]{"DARK_RED", "dark_red", "dark-red", "Dark-Red"}) {
            assertEquals(Color.DARK_RED, Structura.parse("color: " + form, Banner.class).color(), form);
        }
    }

    @Test
    @DisplayName("an unknown value is still refused")
    void unknownRefused() {
        assertThrows(Exception.class, () -> Structura.parse("color: dark-green", Banner.class));
    }
}
