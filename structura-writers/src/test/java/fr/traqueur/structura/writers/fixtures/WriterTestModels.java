package fr.traqueur.structura.writers.fixtures;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.annotations.defaults.*;
import fr.traqueur.structura.api.Loadable;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    // =========================================================================
    // @Polymorphic(useKey = true) — discriminator value becomes the YAML key
    // =========================================================================

    @Polymorphic(key = "type", useKey = true)
    public interface ItemMeta extends Loadable {}

    public record FoodMeta(
        @DefaultInt(8) int nutrition
    ) implements ItemMeta {}

    public record PotionMeta(
        @DefaultString("#FF0000") String color
    ) implements ItemMeta {}

    /** Single useKey polymorphic field — field name is replaced by the discriminator value. */
    public record UseKeyItemConfig(
        @DefaultString("Apple") String  name,
        ItemMeta                        meta
    ) implements Loadable {}

    /** List of useKey polymorphic elements — serialized as a YAML map. */
    public record UseKeyItemListConfig(
        List<ItemMeta> metadata
    ) implements Loadable {}

    /** Map whose values are useKey polymorphic — map key IS the discriminator. */
    public record UseKeyItemMapConfig(
        Map<String, ItemMeta> bySlot
    ) implements Loadable {}

    // =========================================================================
    // @Options(isKey = true) — record field as map key in collections
    // =========================================================================

    /** Record where 'id' is the map key when serialized inside a collection. */
    public record Permission(
        @Options(isKey = true) String id,
        @DefaultInt(1)         int    level
    ) implements Loadable {}

    /** Wraps a list of Permission — serialized as a map keyed by id. */
    public record PermissionConfig(
        @DefaultString("MyApp") String           appName,
        List<Permission>                         permissions
    ) implements Loadable {}

    /** Record with two non-key fields — verifies the full nested map is preserved. */
    public record Route(
        @Options(isKey = true) String  path,
        @DefaultString("GET")  String  method,
        @DefaultBool(true)     boolean enabled
    ) implements Loadable {}

    public record RouteConfig(
        List<Route> routes
    ) implements Loadable {}

    // =========================================================================
    // @Options(optional = true) — null field omission
    // =========================================================================

    public record MixedOptionalConfig(
        @DefaultString("required")   String  required,
        @Options(optional = true)    String  maybeNull,
        @Options(optional = true)    Integer maybeInt
    ) implements Loadable {}

    // =========================================================================
    // @Options(name = "...") — custom YAML key
    // =========================================================================

    public record CustomNameConfig(
        @Options(name = "server-address") String serverAddress,
        @DefaultInt(8080)                 int    port
    ) implements Loadable {}

    // =========================================================================
    // Enum serialization
    // =========================================================================

    public enum Environment { DEVELOPMENT, STAGING, PRODUCTION_READY }

    public record EnvConfig(
        @DefaultString("App") String      name,
        Environment                        env
    ) implements Loadable {}

    // =========================================================================
    // LocalDate / LocalDateTime serialization
    // =========================================================================

    public record ScheduleConfig(
        LocalDate     startDate,
        LocalDateTime createdAt
    ) implements Loadable {}

    // =========================================================================
    // @Options(isKey = true) inside a Map value
    // =========================================================================

    /** Same as Route but used to test Map<String, Route> serialization. */
    public record Endpoint(
        @Options(isKey = true) String  path,
        @DefaultString("GET")  String  method,
        @DefaultInt(200)       int     statusCode
    ) implements Loadable {}

    public record EndpointMapConfig(
        Map<String, Endpoint> endpoints
    ) implements Loadable {}

    // =========================================================================
    // saveDefault with optional fields (no @Default* annotation)
    // =========================================================================

    public record OptionalOnlyConfig(
        @DefaultString("required")  String required,
        @Options(optional = true)   String neverDefault
    ) implements Loadable {}
}
