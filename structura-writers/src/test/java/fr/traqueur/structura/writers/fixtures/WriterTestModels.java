package fr.traqueur.structura.writers.fixtures;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.annotations.defaults.*;
import fr.traqueur.structura.api.Loadable;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WriterTestModels {

    private WriterTestModels() {}

    // =========================================================================
    // Basic models (from initial commit)
    // =========================================================================

    public record PlainConfig(String name, int value) implements Loadable {}

    public record SimpleDefaultConfig(
        @DefaultString("Afelia")  String  serverName,
        @DefaultInt(25565)        int     port,
        @DefaultBool(true)        boolean debug
    ) implements Loadable {}

    public record AllDefaultTypesConfig(
        @DefaultString("hello")  String  str,
        @DefaultInt(42)          int     intVal,
        @DefaultLong(1000L)      long    longVal,
        @DefaultDouble(3.14)     double  doubleVal,
        @DefaultBool(false)      boolean flag
    ) implements Loadable {}

    public record ServerBlock(
        @DefaultString("localhost") String host,
        @DefaultInt(8080)           int    port
    ) implements Loadable {}

    public record NestedDefaultConfig(
        @DefaultString("MyApp") String      appName,
        ServerBlock                          server
    ) implements Loadable {}

    public record CamelCaseConfig(
        @DefaultString("test") String serverName,
        @DefaultInt(9090)      int    httpPort
    ) implements Loadable {}

    public record OptionalFieldConfig(
        @DefaultString("required") String required,
        @Options(optional = true)  String optional
    ) implements Loadable {}

    public record ConnectionBlock(
        @DefaultString("db.local") String host,
        @DefaultInt(5432)          int    port
    ) implements Loadable {}

    public record CollectionConfig(
        @DefaultString("test") String      name,
        List<String>                       tags,
        Set<Integer>                       ports,
        Map<String, String>                properties
    ) implements Loadable {}

    public static final class Color {
        private final int r, g, b;
        public Color(int r, int g, int b) { this.r = r; this.g = g; this.b = b; }
        public int r() { return r; }
        public int g() { return g; }
        public int b() { return b; }
    }

    public record ColorConfig(
        @DefaultString("palette") String name,
        Color                             color
    ) implements Loadable {}

    // =========================================================================
    // @Options(inline = true) — concrete record flattening
    // =========================================================================

    public record InlineConfig(
        @DefaultString("InlineApp")             String          appName,
        @Options(inline = true) ConnectionBlock connection
    ) implements Loadable {}

    // =========================================================================
    // @Polymorphic standard (discriminator inside the nested map)
    // =========================================================================

    @Polymorphic(key = "kind")
    public interface Animal extends Loadable {}

    public record Dog(
        @DefaultString("Rex") String name,
        @DefaultString("lab") String breed
    ) implements Animal {}

    public record Cat(
        @DefaultString("Whiskers") String  name,
        @DefaultBool(true)         boolean indoor
    ) implements Animal {}

    public record AnimalConfig(
        @DefaultString("Zoo") String appName,
        Animal pet
    ) implements Loadable {}

    public record AnimalListConfig(
        List<Animal> animals
    ) implements Loadable {}

    public record AnimalMapConfig(
        Map<String, Animal> animalsByName
    ) implements Loadable {}

    // =========================================================================
    // @Polymorphic(inline = true) — discriminator at parent level
    // =========================================================================

    @Polymorphic(key = "engine", inline = true)
    public interface DbEngine extends Loadable {}

    public record MySQLEngine(
        @DefaultString("localhost") String host,
        @DefaultInt(3306)           int    port
    ) implements DbEngine {}

    public record PostgreSQLEngine(
        @DefaultString("localhost") String host,
        @DefaultInt(5432)           int    port
    ) implements DbEngine {}

    /** Discriminator at parent level, concrete fields nested under "db". */
    public record InlineDbConfig(
        @DefaultString("App") String  appName,
        DbEngine                       db
    ) implements Loadable {}

    /** Both inline flags — ALL fields (including discriminator) at parent level. */
    public record FullyInlineDbConfig(
        @DefaultString("App")            String   appName,
        @Options(inline = true) DbEngine db
    ) implements Loadable {}
}
