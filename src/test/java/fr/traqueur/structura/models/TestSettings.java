
package fr.traqueur.structura.models;

import fr.traqueur.structura.annotations.defaults.*;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.annotations.Options;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test's settings for Structura tests.
 * This class contains various configurations to test the functionality of Structura.
 */
public class TestSettings {

    // === SIMPLE CONFIGURATION ===
    public record SimpleConfig(
            String name,
            int port,
            boolean enabled
    ) implements Loadable {}

    // === CONFIGURATION WITH DEFAULTS ===
    public record ConfigWithDefaults(
            @DefaultString("default-app") String appName,
            @DefaultInt(8080) int serverPort,
            @DefaultBool(true) boolean debugMode,
            @DefaultLong(30000L) long timeout,
            @DefaultDouble(1.5) double multiplier
    ) implements Loadable {}

    // === CONFIGURATION WITH OPTIONS ===
    public record ConfigWithOptions(
            @Options(name = "app-name") String applicationName,
            @Options(name = "server-config") ServerConfig serverConfig
    ) implements Loadable {}

    // === CONFIGURATION WITH NULLABLE ===
    public record ConfigWithNullable(
            String required,
            @Options(optional = true) String optional,
            @Options(optional = true) @DefaultString("fallback") String optionalWithDefault
    ) implements Loadable {}

    // === CONFIGURATION NESTED ===
    public record DatabaseConfig(
            String host,
            int port,
            String database,
            @DefaultString("root") String username,
            @Options(optional = true) String password
    ) implements Loadable {}

    public record ServerConfig(
            String host,
            int port,
            @DefaultBool(false) boolean ssl
    ) implements Loadable {}

    public record ComplexConfig(
            String appName,
            DatabaseConfig database,
            ServerConfig server
    ) implements Loadable {}

    // === CONFIGURATION WITH COLLECTIONS ===
    public record ConfigWithCollections(
            List<String> hosts,
            Set<Integer> ports,
            Map<String, String> properties,
            List<DatabaseConfig> databases
    ) implements Loadable {}

    // === ENUMS ===
    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    public enum Environment {
        DEVELOPMENT,
        STAGING, 
        PRODUCTION;
    }

    public enum DatabaseType implements Loadable {
        MYSQL,
        POSTGRESQL,
        MONGODB;
        
        @Options(optional = true) public String driver;
        @DefaultInt(0) public int defaultPort;
        @Options(optional = true) public Map<String, String> properties;
    }

    // === CONFIGURATION WITH ENUMS ===
    public record ConfigWithEnums(
            LogLevel logLevel,
            Environment environment,
            List<DatabaseType> supportedDatabases
    ) implements Loadable {}

    // === EDGE CASES ===
    public record EmptyConfig() implements Loadable {}

    public record SingleFieldConfig(String value) implements Loadable {}

    public record ConfigWithSpecialChars(
            @Options(name = "field-with-dashes") String fieldWithDashes,
            @Options(name = "field_with_underscores") String fieldWithUnderscores
    ) implements Loadable {}

    // === CONFIGURATION FOR PERF ===
    public record LargeConfig(
            String field1, String field2, String field3, String field4, String field5,
            String field6, String field7, String field8, String field9, String field10,
            int int1, int int2, int int3, int int4, int int5,
            boolean bool1, boolean bool2, boolean bool3, boolean bool4, boolean bool5
    ) implements Loadable {}
}