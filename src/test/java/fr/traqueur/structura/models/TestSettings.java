
package fr.traqueur.structura.models;

import fr.traqueur.structura.api.Settings;
import fr.traqueur.structura.api.annotations.Options;
import fr.traqueur.structura.api.annotations.defaults.*;

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
    ) implements Settings {}

    // === CONFIGURATION WITH DEFAULTS ===
    public record ConfigWithDefaults(
            @DefaultString("default-app") String appName,
            @DefaultInt(8080) int serverPort,
            @DefaultBool(true) boolean debugMode,
            @DefaultLong(30000L) long timeout,
            @DefaultDouble(1.5) double multiplier
    ) implements Settings {}

    // === CONFIGURATION WITH OPTIONS ===
    public record ConfigWithOptions(
            @Options(name = "app-name") String applicationName,
            @Options(name = "server-config") ServerConfig serverConfig
    ) implements Settings {}

    // === CONFIGURATION WITH NULLABLE ===
    public record ConfigWithNullable(
            String required,
            @Options(optional = true) String optional,
            @Options(optional = true) @DefaultString("fallback") String optionalWithDefault
    ) implements Settings {}

    // === CONFIGURATION NESTED ===
    public record DatabaseConfig(
            String host,
            int port,
            String database,
            @DefaultString("root") String username,
            @Options(optional = true) String password
    ) implements Settings {}

    public record ServerConfig(
            String host,
            int port,
            @DefaultBool(false) boolean ssl
    ) implements Settings {}

    public record ComplexConfig(
            String appName,
            DatabaseConfig database,
            ServerConfig server
    ) implements Settings {}

    // === CONFIGURATION WITH COLLECTIONS ===
    public record ConfigWithCollections(
            List<String> hosts,
            Set<Integer> ports,
            Map<String, String> properties,
            List<DatabaseConfig> databases
    ) implements Settings {}

    // === ENUMS ===
    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    public enum Environment {
        DEVELOPMENT,
        STAGING, 
        PRODUCTION;
    }

    public enum DatabaseType implements Settings {
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
    ) implements Settings {}

    // === EDGE CASES ===
    public record EmptyConfig() implements Settings {}

    public record SingleFieldConfig(String value) implements Settings {}

    public record ConfigWithSpecialChars(
            @Options(name = "field-with-dashes") String fieldWithDashes,
            @Options(name = "field_with_underscores") String fieldWithUnderscores
    ) implements Settings {}

    // === CONFIGURATION FOR PERF ===
    public record LargeConfig(
            String field1, String field2, String field3, String field4, String field5,
            String field6, String field7, String field8, String field9, String field10,
            int int1, int int2, int int3, int int4, int int5,
            boolean bool1, boolean bool2, boolean bool3, boolean bool4, boolean bool5
    ) implements Settings {}
}