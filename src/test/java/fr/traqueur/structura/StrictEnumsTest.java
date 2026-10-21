package fr.traqueur.structura;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.Strict;
import fr.traqueur.structura.annotations.defaults.DefaultInt;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.exceptions.StructuraErrors;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.exceptions.StructuraException.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Strict mode on configurable enums: unknown constants and unknown keys of a constant are errors.
 */
@DisplayName("Strict enums")
class StrictEnumsTest {

    public enum Rank implements Loadable {
        BRONZE, SILVER, GOLD_PLUS;

        String display;
        @DefaultInt(0)
        int threshold;
    }

    @Strict
    public enum Flag implements Loadable {
        PVP;

        boolean enabled;
    }

    public record Style(String color, boolean bold) implements Loadable {}

    public enum Styled implements Loadable {
        TITLE;

        String text;
        @Options(inline = true)
        Style style;
    }

    public enum Translated implements Loadable {
        WELCOME;

        String comment;
        @Options(inline = true)
        Map<String, String> byLocale;
    }

    private static final String RANKS = """
            bronze:
              display: Bronze
            silver:
              display: Silver
            gold-plus:
              display: Gold+
            """;

    private final StructuraProcessor strict = Structura.builder().strictKeys(true).build();

    @Test
    @DisplayName("a key that is no constant is reported with the closest constant")
    void unknownConstant() {
        StructuraException error = assertThrows(StructuraException.class,
                () -> strict.parseEnum(RANKS + "gold-pluss:\n  display: x\n", Rank.class));

        assertEquals(Kind.UNKNOWN_KEY, error.kind());
        assertEquals("7:1: unknown key 'gold-pluss' (did you mean 'gold-plus'?)", error.getMessage());
    }

    @Test
    @DisplayName("a key of a constant that no field reads is reported under the constant")
    void unknownField() {
        StructuraException error = assertThrows(StructuraException.class, () -> strict.parseEnum("""
                bronze:
                  display: Bronze
                  treshold: 10
                silver:
                  display: Silver
                gold-plus:
                  display: Gold+
                """, Rank.class));

        assertEquals("3:3: unknown key 'treshold' under 'bronze' (did you mean 'threshold'?)", error.getMessage());
    }

    @Test
    @DisplayName("an inline record field reads its keys at the constant's level")
    void inlineRecord() {
        assertDoesNotThrow(() -> strict.parseEnum("""
                title:
                  text: Hello
                  color: red
                  bold: true
                """, Styled.class));

        StructuraException error = assertThrows(StructuraException.class, () -> strict.parseEnum("""
                title:
                  text: Hello
                  colour: red
                  bold: true
                """, Styled.class));
        assertEquals("title.colour", error.path().orElseThrow());
    }

    @Test
    @DisplayName("an inline map absorbs every remaining key")
    void inlineMap() {
        assertDoesNotThrow(() -> strict.parseEnum("""
                welcome:
                  comment: shown on join
                  fr_FR: Bienvenue
                  en_US: Welcome
                """, Translated.class));
    }

    @Test
    @DisplayName("@Strict on the enum, without strict mode")
    void annotation() {
        StructuraException error = assertThrows(StructuraException.class, () -> Structura.parseEnum("""
                pvp:
                  enabled: true
                  enabeld: false
                """, Flag.class));

        assertEquals(Kind.UNKNOWN_KEY, error.kind());
        assertEquals("pvp.enabeld", error.path().orElseThrow());
    }

    @Test
    @DisplayName("collects every unknown key with collectErrors")
    void collected() {
        StructuraProcessor processor = Structura.builder().strictKeys(true).collectErrors(true).build();

        StructuraErrors errors = assertThrows(StructuraErrors.class, () -> processor.parseEnum("""
                bronze:
                  display: Bronze
                  colour: brown
                silver:
                  display: Silver
                gold-plus:
                  display: Gold+
                platinum:
                  display: Platinum
                """, Rank.class));

        assertEquals(List.of("bronze.colour", "platinum"),
                errors.errors().stream().map(error -> error.path().orElseThrow()).toList());
    }

    @Test
    @DisplayName("without strict mode, unknown keys are still ignored")
    void notStrictByDefault() {
        assertDoesNotThrow(() -> Structura.parseEnum(RANKS + "platinum:\n  display: x\n", Rank.class));
    }
}
