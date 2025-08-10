package fr.traqueur.structura;

import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.defaults.*;
import fr.traqueur.structura.exceptions.StructuraException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StructuraProcessor Integration Tests")
class StructuraProcessorTest {

    private StructuraProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new StructuraProcessor(true);
    }

    // Test records and enums
    public record SimpleConfig(String name, int port, boolean enabled) implements Loadable {}

    public record ConfigWithDefaults(
        @DefaultString("default-app") String appName,
        @DefaultInt(8080) int serverPort,
        @DefaultBool(true) boolean debugMode,
        @DefaultLong(30000L) long timeout,
        @DefaultDouble(1.5) double multiplier
    ) implements Loadable {}

    public record DatabaseConfig(
        String host,
        @DefaultInt(5432) int port,
        String database,
        @DefaultString("postgres") String username,
        @Options(optional = true) String password
    ) implements Loadable {}

    public record ServerConfig(
        String host,
        @DefaultInt(8080) int port,
        @DefaultBool(false) boolean ssl
    ) implements Loadable {}

    public record ComplexConfig(
        String appName,
        DatabaseConfig database,
        ServerConfig server
    ) implements Loadable {}

    public record ConfigWithCollections(
        List<String> hosts,
        Set<Integer> ports,
        Map<String, String> properties,
        List<DatabaseConfig> databases
    ) implements Loadable {}

    public record ConfigWithOptions(
        @Options(name = "app-name") String applicationName,
        @Options(name = "server-config") ServerConfig serverConfig
    ) implements Loadable {}

    public record ConfigWithNullable(
        String required,
        @Options(optional = true) String optional,
        @Options(optional = true) @DefaultString("fallback") String optionalWithDefault
    ) implements Loadable {}

    public record SimpleKeyRecord(
        @Options(isKey = true) String id,
        @DefaultInt(0) int valueInt,
        @DefaultDouble(0.0) double valueDouble
    ) implements Loadable {}

    public record NestedKeyRecord(
        String host,
        @DefaultInt(8080) int port,
        @DefaultString("http") String protocol
    ) implements Loadable {}

    public record ComplexKeyConfig(
        @Options(isKey = true) NestedKeyRecord server,
        String appName,
        @DefaultBool(false) boolean debugMode
    ) implements Loadable {}

    public enum LogLevel { DEBUG, INFO, WARN, ERROR }
    public enum Environment { DEVELOPMENT, STAGING, PRODUCTION }

    public enum DatabaseType implements Loadable {
        MYSQL, POSTGRESQL, MONGO_DB_SOURCE;
        
        @Options(optional = true) public String driver;
        @DefaultInt(0) public int defaultPort;
        @Options(optional = true) public Map<String, String> properties;
    }

    public record ConfigWithEnums(
        LogLevel logLevel,
        Environment environment,
        List<DatabaseType> supportedDatabases
    ) implements Loadable {}

    @Nested
    @DisplayName("Basic Parsing Tests")
    class BasicParsingTest {

        @Test
        @DisplayName("Should parse simple configuration")
        void shouldParseSimpleConfiguration() {
            String yaml = """
                name: "MyApp"
                port: 8080
                enabled: true
                """;

            SimpleConfig config = processor.parse(yaml, SimpleConfig.class);

            assertEquals("MyApp", config.name());
            assertEquals(8080, config.port());
            assertTrue(config.enabled());
        }

        @Test
        @DisplayName("Should use default values when fields missing")
        void shouldUseDefaultValuesWhenFieldsMissing() {
            String yaml = "{}";

            ConfigWithDefaults config = processor.parse(yaml, ConfigWithDefaults.class);

            assertEquals("default-app", config.appName());
            assertEquals(8080, config.serverPort());
            assertTrue(config.debugMode());
            assertEquals(30000L, config.timeout());
            assertEquals(1.5, config.multiplier());
        }

        @Test
        @DisplayName("Should override defaults with provided values")
        void shouldOverrideDefaultsWithProvidedValues() {
            String yaml = """
                app-name: "CustomApp"
                server-port: 3000
                debug-mode: false
                """;

            ConfigWithDefaults config = processor.parse(yaml, ConfigWithDefaults.class);

            assertEquals("CustomApp", config.appName());
            assertEquals(3000, config.serverPort());
            assertFalse(config.debugMode());
            assertEquals(30000L, config.timeout());
            assertEquals(1.5, config.multiplier());
        }
    }

    @Nested
    @DisplayName("Nested Configuration Tests")
    class NestedConfigurationTest {

        @Test
        @DisplayName("Should parse nested configurations")
        void shouldParseNestedConfigurations() {
            String yaml = """
                app-name: "ComplexApp"
                database:
                  host: "db.example.com"
                  port: 5432
                  database: "mydb"
                  username: "admin"
                  password: "secret"
                server:
                  host: "api.example.com"
                  port: 8443
                  ssl: true
                """;

            ComplexConfig config = processor.parse(yaml, ComplexConfig.class);

            assertEquals("ComplexApp", config.appName());

            assertNotNull(config.database());
            assertEquals("db.example.com", config.database().host());
            assertEquals(5432, config.database().port());
            assertEquals("mydb", config.database().database());
            assertEquals("admin", config.database().username());
            assertEquals("secret", config.database().password());

            assertNotNull(config.server());
            assertEquals("api.example.com", config.server().host());
            assertEquals(8443, config.server().port());
            assertTrue(config.server().ssl());
        }
    }

    @Nested
    @DisplayName("Collection Handling Tests")
    class CollectionHandlingTest {

        @Test
        @DisplayName("Should parse collections correctly")
        void shouldParseCollectionsCorrectly() {
            String yaml = """
                hosts:
                  - "host1.example.com"
                  - "host2.example.com"
                  - "host3.example.com"
                ports:
                  - 8080
                  - 8443
                  - 9000
                properties:
                  env: "production"
                  debug: "false"
                  timeout: "30000"
                databases:
                  - host: "db1.example.com"
                    port: 5432
                    database: "app1"
                  - host: "db2.example.com"
                    port: 3306
                    database: "app2"
                """;

            ConfigWithCollections config = processor.parse(yaml, ConfigWithCollections.class);

            assertEquals(List.of("host1.example.com", "host2.example.com", "host3.example.com"), config.hosts());
            assertTrue(config.ports().containsAll(Set.of(8080, 8443, 9000)));
            assertEquals(3, config.properties().size());
            assertEquals("production", config.properties().get("env"));
            assertEquals(2, config.databases().size());
        }

        @Test
        @DisplayName("Should handle empty collections")
        void shouldHandleEmptyCollections() {
            String yaml = """
                hosts: []
                ports: []
                properties: {}
                databases: []
                """;

            ConfigWithCollections config = processor.parse(yaml, ConfigWithCollections.class);

            assertTrue(config.hosts().isEmpty());
            assertTrue(config.ports().isEmpty());
            assertTrue(config.properties().isEmpty());
            assertTrue(config.databases().isEmpty());
        }
    }

    @Nested
    @DisplayName("Custom Options Tests")
    class CustomOptionsTest {

        @Test
        @DisplayName("Should respect custom field names")
        void shouldRespectCustomFieldNames() {
            String yaml = """
                app-name: "MyApplication"
                server-config:
                  host: "localhost"
                  port: 8080
                  ssl: true
                """;

            ConfigWithOptions config = processor.parse(yaml, ConfigWithOptions.class);

            assertEquals("MyApplication", config.applicationName());
            assertNotNull(config.serverConfig());
            assertEquals("localhost", config.serverConfig().host());
            assertEquals(8080, config.serverConfig().port());
            assertTrue(config.serverConfig().ssl());
        }

        @Test
        @DisplayName("Should handle optional fields")
        void shouldHandleOptionalFields() {
            String yaml = """
                required: "value"
                optional: "present"
                optional-with-default: "custom"
                """;

            ConfigWithNullable config = processor.parse(yaml, ConfigWithNullable.class);

            assertEquals("value", config.required());
            assertEquals("present", config.optional());
            assertEquals("custom", config.optionalWithDefault());
        }

        @Test
        @DisplayName("Should handle missing optional fields")
        void shouldHandleMissingOptionalFields() {
            String yaml = "required: \"value\"";

            ConfigWithNullable config = processor.parse(yaml, ConfigWithNullable.class);

            assertEquals("value", config.required());
            assertNull(config.optional());
            assertEquals("fallback", config.optionalWithDefault());
        }
    }

    @Nested
    @DisplayName("Key Mapping Tests")
    class KeyMappingTest {

        @Test
        @DisplayName("Should handle simple key mapping")
        void shouldHandleSimpleKeyMapping() {
            String yaml = """
                monobjet:
                  value-int: 42
                  value-double: 3.14
                """;

            SimpleKeyRecord config = processor.parse(yaml, SimpleKeyRecord.class);

            assertEquals("monobjet", config.id());
            assertEquals(42, config.valueInt());
            assertEquals(3.14, config.valueDouble());
        }

        @Test
        @DisplayName("Should handle simple key mapping with empty data")
        void shouldHandleSimpleKeyMappingWithEmptyData() {
            String yaml = "testkey: {}";

            SimpleKeyRecord config = processor.parse(yaml, SimpleKeyRecord.class);

            assertEquals("testkey", config.id());
            assertEquals(0, config.valueInt());
            assertEquals(0.0, config.valueDouble());
        }

        @Test
        @DisplayName("Should handle complex object as key")
        void shouldHandleComplexObjectAsKey() {
            String yaml = """
                host: "api.example.com"
                port: 9000
                protocol: "https"
                app-name: "MyApp"
                debug-mode: true
                """;

            ComplexKeyConfig config = processor.parse(yaml, ComplexKeyConfig.class);

            assertNotNull(config.server());
            assertEquals("api.example.com", config.server().host());
            assertEquals(9000, config.server().port());
            assertEquals("https", config.server().protocol());
            assertEquals("MyApp", config.appName());
            assertTrue(config.debugMode());
        }

        @Test
        @DisplayName("Should handle complex key with defaults")
        void shouldHandleComplexKeyWithDefaults() {
            String yaml = """
                host: "localhost"
                app-name: "TestApp"
                """;

            ComplexKeyConfig config = processor.parse(yaml, ComplexKeyConfig.class);

            assertNotNull(config.server());
            assertEquals("localhost", config.server().host());
            assertEquals(8080, config.server().port());
            assertEquals("http", config.server().protocol());
            assertEquals("TestApp", config.appName());
            assertFalse(config.debugMode());
        }
    }

    @Nested
    @DisplayName("Enum Configuration Tests")
    class EnumConfigurationTest {

        @BeforeEach
        void resetEnumValues() {
            for (DatabaseType type : DatabaseType.values()) {
                type.driver = null;
                type.defaultPort = 0;
                type.properties = null;
            }
        }

        @Test
        @DisplayName("Should parse configuration with enums")
        void shouldParseConfigurationWithEnums() {
            String yaml = """
            log-level: INFO
            environment: PRODUCTION
            supported-databases:
              - MYSQL
              - POSTGRESQL
              - MONGO_DB_SOURCE
            """;

            ConfigWithEnums config = processor.parse(yaml, ConfigWithEnums.class);

            assertEquals(LogLevel.INFO, config.logLevel());
            assertEquals(Environment.PRODUCTION, config.environment());
            assertEquals(List.of(DatabaseType.MYSQL, DatabaseType.POSTGRESQL, DatabaseType.MONGO_DB_SOURCE),
                    config.supportedDatabases());
        }

        @Test
        @DisplayName("Should populate enum constants with parseEnum")
        void shouldPopulateEnumConstantsWithParseEnum() {
            String yaml = """
            mysql:
              driver: "com.mysql.cj.jdbc.Driver"
              default-port: 3306
              properties:
                useSSL: "true"
                serverTimezone: "UTC"
            postgresql:
              driver: "org.postgresql.Driver"
              default-port: 5432
              properties:
                ssl: "false"
                applicationName: "myapp"
            mongo-db-source:
              driver: "mongodb"
              default-port: 27017
              properties:
                authSource: "admin"
                retryWrites: "true"
            """;

            processor.parseEnum(yaml, DatabaseType.class);

            assertEquals("com.mysql.cj.jdbc.Driver", DatabaseType.MYSQL.driver);
            assertEquals(3306, DatabaseType.MYSQL.defaultPort);
            assertNotNull(DatabaseType.MYSQL.properties);
            assertEquals("true", DatabaseType.MYSQL.properties.get("useSSL"));

            assertEquals("org.postgresql.Driver", DatabaseType.POSTGRESQL.driver);
            assertEquals(5432, DatabaseType.POSTGRESQL.defaultPort);
            assertEquals("false", DatabaseType.POSTGRESQL.properties.get("ssl"));

            assertEquals("mongodb", DatabaseType.MONGO_DB_SOURCE.driver);
            assertEquals(27017, DatabaseType.MONGO_DB_SOURCE.defaultPort);
            assertEquals("admin", DatabaseType.MONGO_DB_SOURCE.properties.get("authSource"));
        }

        @Test
        @DisplayName("Should handle partial enum data")
        void shouldHandlePartialEnumData() {
            String yaml = """
            mysql:
              driver: "com.mysql.cj.jdbc.Driver"
            postgresql:
              default-port: 5432
            mongo-db-source:
              properties:
                authSource: "admin"
            """;

            processor.parseEnum(yaml, DatabaseType.class);

            assertEquals("com.mysql.cj.jdbc.Driver", DatabaseType.MYSQL.driver);
            assertEquals(0, DatabaseType.MYSQL.defaultPort);
            assertNull(DatabaseType.MYSQL.properties);

            assertNull(DatabaseType.POSTGRESQL.driver);
            assertEquals(5432, DatabaseType.POSTGRESQL.defaultPort);
            assertNull(DatabaseType.POSTGRESQL.properties);

            assertNull(DatabaseType.MONGO_DB_SOURCE.driver);
            assertEquals(0, DatabaseType.MONGO_DB_SOURCE.defaultPort);
            assertNotNull(DatabaseType.MONGO_DB_SOURCE.properties);
            assertEquals("admin", DatabaseType.MONGO_DB_SOURCE.properties.get("authSource"));
        }
    }

    @Nested
    @DisplayName("Type Conversion Tests")
    class TypeConversionTest {

        @ParameterizedTest
        @ValueSource(strings = {"true", "false", "TRUE", "FALSE", "True", "False"})
        @DisplayName("Should parse boolean values correctly")
        void shouldParseBooleanValuesCorrectly(String boolValue) {
            String yaml = "debug-mode: " + boolValue;

            ConfigWithDefaults config = processor.parse(yaml, ConfigWithDefaults.class);

            boolean expected = Boolean.parseBoolean(boolValue);
            assertEquals(expected, config.debugMode());
        }

        @Test
        @DisplayName("Should handle type conversions")
        void shouldHandleTypeConversions() {
            String yaml = """
                app-name: test
                server-port: "9000"
                debug-mode: "false"
                timeout: "60000"
                multiplier: "2.5"
                """;

            ConfigWithDefaults config = processor.parse(yaml, ConfigWithDefaults.class);

            assertEquals("test", config.appName());
            assertEquals(9000, config.serverPort());
            assertFalse(config.debugMode());
            assertEquals(60000L, config.timeout());
            assertEquals(2.5, config.multiplier());
        }

        @Test
        @DisplayName("Should handle enum case variations")
        void shouldHandleEnumCaseVariations() {
            String yaml = """
                log-level: info
                environment: production
                supported-databases:
                  - mysql
                  - postgresql
                """;

            ConfigWithEnums config = processor.parse(yaml, ConfigWithEnums.class);

            assertEquals(LogLevel.INFO, config.logLevel());
            assertEquals(Environment.PRODUCTION, config.environment());
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTest {

        @Test
        @DisplayName("Should throw exception for null YAML")
        void shouldThrowExceptionForNullYaml() {
            StructuraException exception = assertThrows(StructuraException.class, () -> {
                processor.parse(null, SimpleConfig.class);
            });

            assertTrue(exception.getMessage().contains("cannot be null or empty"));
        }

        @Test
        @DisplayName("Should throw exception for empty YAML")
        void shouldThrowExceptionForEmptyYaml() {
            StructuraException exception = assertThrows(StructuraException.class, () -> {
                processor.parse("", SimpleConfig.class);
            });

            assertTrue(exception.getMessage().contains("cannot be null or empty"));
        }

        @Test
        @DisplayName("Should throw exception for null class")
        void shouldThrowExceptionForNullClass() {
            StructuraException exception = assertThrows(StructuraException.class, () -> {
                processor.parse("name: test", null);
            });

            assertTrue(exception.getMessage().contains("cannot be null"));
        }

        @Test
        @DisplayName("Should throw exception for missing required fields")
        void shouldThrowExceptionForMissingRequiredFields() {
            String yaml = "optional: \"present\"";

            StructuraException exception = assertThrows(StructuraException.class, () -> {
                processor.parse(yaml, ConfigWithNullable.class);
            });

            assertTrue(exception.getMessage().contains("required"));
        }

        @Test
        @DisplayName("Should throw exception for invalid enum value")
        void shouldThrowExceptionForInvalidEnumValue() {
            String yaml = "log-level: INVALID";

            StructuraException exception = assertThrows(StructuraException.class, () -> {
                processor.parse(yaml, ConfigWithEnums.class);
            });

            assertTrue(exception.getMessage().contains("Invalid enum value"));
        }

        @Test
        @DisplayName("Should throw exception for missing enum constant in parseEnum")
        void shouldThrowExceptionForMissingEnumConstant() {
            String yaml = """
                mysql:
                  driver: "mysql"
                unknown-db:
                  driver: "unknown"
                """;

            StructuraException exception = assertThrows(StructuraException.class, () -> {
                processor.parseEnum(yaml, DatabaseType.class);
            });

            assertTrue(exception.getMessage().contains("Missing data for enum constant"));
        }

        @Test
        @DisplayName("Should throw exception for invalid YAML syntax")
        void shouldThrowExceptionForInvalidYamlSyntax() {
            String yaml = """
                name: "test
                port: [invalid
                """;

            assertThrows(Exception.class, () -> {
                processor.parse(yaml, SimpleConfig.class);
            });
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTest {

        public record EmptyConfig() implements Loadable {}
        public record SingleFieldConfig(String value) implements Loadable {}

        @Test
        @DisplayName("Should handle empty configuration")
        void shouldHandleEmptyConfiguration() {
            String yaml = "{}";

            EmptyConfig config = processor.parse(yaml, EmptyConfig.class);

            assertNotNull(config);
        }

        @Test
        @DisplayName("Should handle single field configuration")
        void shouldHandleSingleFieldConfiguration() {
            String yaml = "value: \"test\"";

            SingleFieldConfig config = processor.parse(yaml, SingleFieldConfig.class);

            assertEquals("test", config.value());
        }

        @Test
        @DisplayName("Should handle null YAML content that loads to null")
        void shouldHandleNullYamlContent() {
            String yaml = "null";

            EmptyConfig config = processor.parse(yaml, EmptyConfig.class);

            assertNotNull(config);
        }

        @Test
        @DisplayName("Should handle enum with empty data sections")
        void shouldHandleEnumWithEmptyDataSections() {
            String yaml = """
                mysql: {}
                postgresql: {}
                mongo-db-source: {}
                """;

            processor.parseEnum(yaml, DatabaseType.class);

            assertNull(DatabaseType.MYSQL.driver);
            assertEquals(0, DatabaseType.MYSQL.defaultPort);
            assertNull(DatabaseType.MYSQL.properties);
        }
    }

    @Nested
    @DisplayName("Performance and Large Configuration Tests")
    class PerformanceTest {

        public record LargeConfig(
            String field1, String field2, String field3, String field4, String field5,
            String field6, String field7, String field8, String field9, String field10,
            int int1, int int2, int int3, int int4, int int5,
            boolean bool1, boolean bool2, boolean bool3, boolean bool4, boolean bool5
        ) implements Loadable {}

        @Test
        @DisplayName("Should handle large configurations efficiently")
        void shouldHandleLargeConfigurations() {
            String yaml = """
                field1: "value1"
                field2: "value2"
                field3: "value3"
                field4: "value4"
                field5: "value5"
                field6: "value6"
                field7: "value7"
                field8: "value8"
                field9: "value9"
                field10: "value10"
                int1: 1
                int2: 2
                int3: 3
                int4: 4
                int5: 5
                bool1: true
                bool2: false
                bool3: true
                bool4: false
                bool5: true
                """;

            LargeConfig config = processor.parse(yaml, LargeConfig.class);

            assertNotNull(config);
            assertEquals("value1", config.field1());
            assertEquals("value10", config.field10());
            assertEquals(1, config.int1());
            assertEquals(5, config.int5());
            assertTrue(config.bool1());
            assertTrue(config.bool5());
        }

        @Test
        @DisplayName("Should handle repeated parsing without issues")
        void shouldHandleRepeatedParsing() {
            String yaml = """
                name: "test"
                port: 8080
                enabled: true
                """;

            for (int i = 0; i < 100; i++) {
                SimpleConfig config = processor.parse(yaml, SimpleConfig.class);
                assertEquals("test", config.name());
                assertEquals(8080, config.port());
                assertTrue(config.enabled());
            }
        }
    }
}