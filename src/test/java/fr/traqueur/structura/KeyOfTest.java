package fr.traqueur.structura;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structura.keyOf exposes the exact rule the reader uses.
 */
@DisplayName("Structura.keyOf")
class KeyOfTest {

    public record Names(int maxItems, String httpURL, String HTTPServer, String myHTTPServer,
                        int level2Cap, int level2cap, @Options(name = "max_slots") int maxSlots) implements Loadable {}

    public enum DatabaseType { MYSQL_DATABASE, POSTGRES }

    private static List<String> keys() {
        return Arrays.stream(Names.class.getRecordComponents()).map(Structura::keyOf).toList();
    }

    @Test
    @DisplayName("documents the rule for acronyms and digits")
    void rule() {
        assertEquals(List.of("max-items", "http-url", "httpserver", "my-httpserver",
                "level2cap", "level2cap", "max_slots"), keys());
    }

    @Test
    @DisplayName("is the rule the reader uses: every key it gives is read")
    void sameRuleAsTheReader() {
        // level2Cap and level2cap share a key: read through one of them only
        record Single(int maxItems, String httpURL, String myHTTPServer, int level2Cap,
                      @Options(name = "max_slots") int maxSlots) implements Loadable {}
        StringBuilder yaml = new StringBuilder();
        for (RecordComponent component : Single.class.getRecordComponents()) {
            String value = component.getType() == int.class ? "7" : "x";
            yaml.append(Structura.keyOf(component)).append(": ").append(value).append('\n');
        }

        Single read = Structura.builder().strictKeys(true).build().parse(yaml.toString(), Single.class);

        assertEquals(new Single(7, "x", "x", 7, 7), read);
    }

    @Test
    @DisplayName("names enum constants")
    void enumConstants() {
        assertEquals("mysql-database", Structura.keyOf(DatabaseType.MYSQL_DATABASE));
        assertEquals("postgres", Structura.keyOf(DatabaseType.POSTGRES));
    }
}
