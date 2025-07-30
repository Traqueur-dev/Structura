package fr.traqueur.structura.api;

import fr.traqueur.structura.api.exceptions.StructuraException;
import fr.traqueur.structura.models.TestSettings.*;
import org.junit.jupiter.api.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StructuraProcessor - Tests unitaires")
class StructuraProcessorTest {

    private StructuraProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new StructuraProcessor();
    }

    @Nested
    @DisplayName("Configuration simple")
    class SimpleConfigurationTest {

        @Test
        @DisplayName("Devrait parser une configuration simple avec succès")
        void shouldParseSimpleConfiguration() {
            String yaml = """
                name: "MyApp"
                port: 8080
                enabled: true
                """;

            SimpleConfig config = processor.parse(yaml, SimpleConfig.class);

            assertNotNull(config);
            assertEquals("MyApp", config.name());
            assertEquals(8080, config.port());
            assertTrue(config.enabled());
        }

        @Test
        @DisplayName("Devrait gérer les conversions de types automatiques")
        void shouldHandleAutomaticTypeConversion() {
            String yaml = """
                name: MyApp
                port: "9000"
                enabled: "false"
                """;

            SimpleConfig config = processor.parse(yaml, SimpleConfig.class);

            assertEquals("MyApp", config.name());
            assertEquals(9000, config.port());
            assertFalse(config.enabled());
        }
    }

    @Nested
    @DisplayName("Valeurs par défaut")
    class DefaultValuesTest {

        @Test
        @DisplayName("Devrait utiliser les valeurs par défaut quand les champs sont absents")
        void shouldUseDefaultValuesWhenFieldsAreMissing() {
            String yaml = "{}";

            ConfigWithDefaults config = processor.parse(yaml, ConfigWithDefaults.class);

            assertEquals("default-app", config.appName());
            assertEquals(8080, config.serverPort());
            assertTrue(config.debugMode());
            assertEquals(30000L, config.timeout());
            assertEquals(1.5, config.multiplier());
        }

        @Test
        @DisplayName("Devrait privilégier les valeurs fournies sur les valeurs par défaut")
        void shouldPreferProvidedValuesOverDefaults() {
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

        @ParameterizedTest
        @ValueSource(strings = {"true", "false", "TRUE", "FALSE", "True", "False"})
        @DisplayName("Devrait parser correctement les valeurs booléennes")
        void shouldParseBooleanValuesCorrectly(String boolValue) {
            String yaml = "debug-mode: " + boolValue;

            ConfigWithDefaults config = processor.parse(yaml, ConfigWithDefaults.class);

            boolean expected = Boolean.parseBoolean(boolValue);
            assertEquals(expected, config.debugMode());
        }
    }

    @Nested
    @DisplayName("Options personnalisées")
    class CustomOptionsTest {

        @Test
        @DisplayName("Devrait respecter les noms de champs personnalisés")
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
    }

    @Nested
    @DisplayName("Champs nullable")
    class NullableFieldsTest {

        @Test
        @DisplayName("Devrait gérer les champs nullable absents")
        void shouldHandleMissingNullableFields() {
            String yaml = "required: \"value\"";

            ConfigWithNullable config = processor.parse(yaml, ConfigWithNullable.class);

            assertEquals("value", config.required());
            assertNull(config.optional());
            assertEquals("fallback", config.optionalWithDefault());
        }

        @Test
        @DisplayName("Devrait gérer les champs nullable présents")
        void shouldHandlePresentNullableFields() {
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
        @DisplayName("Devrait lever une exception pour les champs requis manquants")
        void shouldThrowExceptionForMissingRequiredFields() {
            String yaml = "optional: \"present\"";

            StructuraException exception = assertThrows(StructuraException.class, () -> {
                processor.parse(yaml, ConfigWithNullable.class);
            });

            assertTrue(exception.getMessage().contains("required"));
        }
    }

    @Nested
    @DisplayName("Configuration imbriquée")
    class NestedConfigurationTest {

        @Test
        @DisplayName("Devrait parser une configuration imbriquée avec enum avec succès")
        void shouldParseConfigWithEnums() {
            String yaml = """
            log-level: INFO
            environment: PRODUCTION
            supported-databases:
              - MYSQL
              - POSTGRESQL
              - MONGODB
            """;

            ConfigWithEnums config = processor.parse(yaml, ConfigWithEnums.class);

            assertNotNull(config);
            assertEquals(LogLevel.INFO, config.logLevel());
            assertEquals(Environment.PRODUCTION, config.environment());
            assertEquals(List.of(DatabaseType.MYSQL, DatabaseType.POSTGRESQL, DatabaseType.MONGODB),
                    config.supportedDatabases());
        }

        @Test
        @DisplayName("Devrait parser une configuration imbriquée complexe")
        void shouldParseComplexNestedConfiguration() {
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
    @DisplayName("Collections")
    class CollectionsTest {

        @Test
        @DisplayName("Devrait parser les listes correctement")
        void shouldParseListsCorrectly() {
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
            assertTrue(config.ports().containsAll(List.of(8080, 8443, 9000)));
            assertEquals(3, config.properties().size());
            assertEquals("production", config.properties().get("env"));
            assertEquals("false", config.properties().get("debug"));
            assertEquals("30000", config.properties().get("timeout"));
            assertEquals(2, config.databases().size());
        }

        @Test
        @DisplayName("Devrait gérer les collections vides")
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
    @DisplayName("Gestion d'erreurs")
    class ErrorHandlingTest {

        @Test
        @DisplayName("Devrait lever une exception pour YAML invalide")
        void shouldThrowExceptionForInvalidYaml() {
            String yaml = """
                name: "test
                port: [invalid
                """;

            assertThrows(Exception.class, () -> {
                processor.parse(yaml, SimpleConfig.class);
            });
        }

        @Test
        @DisplayName("Devrait lever une exception pour YAML null")
        void shouldThrowExceptionForNullYaml() {
            StructuraException exception = assertThrows(StructuraException.class, () -> {
                processor.parse(null, SimpleConfig.class);
            });

            assertTrue(exception.getMessage().contains("cannot be null or empty"));
        }

        @Test
        @DisplayName("Devrait lever une exception pour classe null")
        void shouldThrowExceptionForNullClass() {
            String yaml = "name: test";

            StructuraException exception = assertThrows(StructuraException.class, () -> {
                processor.parse(yaml, null);
            });

            assertTrue(exception.getMessage().contains("cannot be null"));
        }

        @Test
        @DisplayName("Devrait lever une exception pour YAML vide")
        void shouldThrowExceptionForEmptyYaml() {
            StructuraException exception = assertThrows(StructuraException.class, () -> {
                processor.parse("", SimpleConfig.class);
            });

            assertTrue(exception.getMessage().contains("cannot be null or empty"));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTest {

        @Test
        @DisplayName("Devrait gérer une configuration vide")
        void shouldHandleEmptyConfiguration() {
            String yaml = "{}";

            EmptyConfig config = processor.parse(yaml, EmptyConfig.class);

            assertNotNull(config);
        }

        @Test
        @DisplayName("Devrait gérer une configuration avec un seul champ")
        void shouldHandleSingleFieldConfiguration() {
            String yaml = "value: \"test\"";

            SingleFieldConfig config = processor.parse(yaml, SingleFieldConfig.class);

            assertEquals("test", config.value());
        }

        @Test
        @DisplayName("Devrait gérer les caractères spéciaux dans les noms de champs")
        void shouldHandleSpecialCharactersInFieldNames() {
            String yaml = """
                field-with-dashes: "dashes"
                field_with_underscores: "underscores"
                """;

            ConfigWithSpecialChars config = processor.parse(yaml, ConfigWithSpecialChars.class);

            assertEquals("dashes", config.fieldWithDashes());
            assertEquals("underscores", config.fieldWithUnderscores());
        }
    }

    @Nested
    @DisplayName("Configuration avec beaucoup de champs")
    class LargeConfigurationTest {

        @Test
        @DisplayName("Devrait parser une configuration avec de nombreux champs")
        void shouldParseLargeConfiguration() {
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

            // Vérifier les champs String
            assertEquals("value1", config.field1());
            assertEquals("value2", config.field2());
            assertEquals("value3", config.field3());
            assertEquals("value4", config.field4());
            assertEquals("value5", config.field5());
            assertEquals("value6", config.field6());
            assertEquals("value7", config.field7());
            assertEquals("value8", config.field8());
            assertEquals("value9", config.field9());
            assertEquals("value10", config.field10());

            // Vérifier les champs int
            assertEquals(1, config.int1());
            assertEquals(2, config.int2());
            assertEquals(3, config.int3());
            assertEquals(4, config.int4());
            assertEquals(5, config.int5());

            // Vérifier les champs boolean
            assertTrue(config.bool1());
            assertFalse(config.bool2());
            assertTrue(config.bool3());
            assertFalse(config.bool4());
            assertTrue(config.bool5());
        }

        @Test
        @DisplayName("Devrait gérer les conversions de types dans une large configuration")
        void shouldHandleTypeConversionsInLargeConfiguration() {
            String yaml = """
                field1: value1
                field2: value2
                field3: value3
                field4: value4
                field5: value5
                field6: value6
                field7: value7
                field8: value8
                field9: value9
                field10: value10
                int1: "10"
                int2: "20"
                int3: "30"
                int4: "40"
                int5: "50"
                bool1: "true"
                bool2: "false"
                bool3: "TRUE"
                bool4: "FALSE"
                bool5: "True"
                """;

            LargeConfig config = processor.parse(yaml, LargeConfig.class);

            assertNotNull(config);

            // Vérifier les conversions int
            assertEquals(10, config.int1());
            assertEquals(20, config.int2());
            assertEquals(30, config.int3());
            assertEquals(40, config.int4());
            assertEquals(50, config.int5());

            // Vérifier les conversions boolean
            assertTrue(config.bool1());
            assertFalse(config.bool2());
            assertTrue(config.bool3());
            assertFalse(config.bool4());
            assertTrue(config.bool5());
        }
    }

    @Nested
    @DisplayName("Tests pour DatabaseType enum avec Settings")
    class DatabaseTypeEnumTest {

        @Test
        @DisplayName("Devrait populer toutes les constantes d'un enum avec parse()")
        void shouldPopulateAllEnumConstantsWithParse() {
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
                mongodb:
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
            assertEquals("UTC", DatabaseType.MYSQL.properties.get("serverTimezone"));

            // Vérifier que POSTGRESQL a été populé
            assertEquals("org.postgresql.Driver", DatabaseType.POSTGRESQL.driver);
            assertEquals(5432, DatabaseType.POSTGRESQL.defaultPort);
            assertNotNull(DatabaseType.POSTGRESQL.properties);
            assertEquals("false", DatabaseType.POSTGRESQL.properties.get("ssl"));
            assertEquals("myapp", DatabaseType.POSTGRESQL.properties.get("applicationName"));

            // Vérifier que MONGODB a été populé
            assertEquals("mongodb", DatabaseType.MONGODB.driver);
            assertEquals(27017, DatabaseType.MONGODB.defaultPort);
            assertNotNull(DatabaseType.MONGODB.properties);
            assertEquals("admin", DatabaseType.MONGODB.properties.get("authSource"));
            assertEquals("true", DatabaseType.MONGODB.properties.get("retryWrites"));
        }

        @Test
        @DisplayName("Devrait ignorer les clés non correspondantes")
        void shouldIgnoreNonMatchingKeys() {
            String yaml = """
                mysql:
                  driver: "com.mysql.cj.jdbc.Driver"
                unknown-database:
                  driver: "unknown"
                postgresql:
                  driver: "org.postgresql.Driver"
                mongodb:
                  driver: "mongodb"
                  default-port: 27017
                """;

            // Ne devrait pas lever d'exception
            assertDoesNotThrow(() -> {
                processor.parseEnum(yaml, DatabaseType.class);
            });

            // Les constantes valides devraient être populées
            assertEquals("com.mysql.cj.jdbc.Driver", DatabaseType.MYSQL.driver);
            assertEquals("org.postgresql.Driver", DatabaseType.POSTGRESQL.driver);
            assertEquals("mongodb", DatabaseType.MONGODB.driver);
            assertEquals(27017, DatabaseType.MONGODB.defaultPort);
        }

        @Test
        @DisplayName("Devrait lever une exception pour constante enum non trouvée avec parseEnum")
        void shouldThrowExceptionForUnknownEnumConstant() {
            String yaml = """
                unknown-db:
                  driver: "unknown"
                  default-port: 1234
                """;

            StructuraException exception = assertThrows(StructuraException.class, () -> {
                processor.parseEnum(yaml, DatabaseType.class);
            });
            System.out.println(exception.getMessage());
            assertTrue(exception.getMessage().contains("Missing data for enum constant"));
        }

        @Test
        @DisplayName("Devrait gérer les propriétés partielles pour enum")
        void shouldHandlePartialPropertiesForEnum() {
            String yaml = """
                mysql:
                  driver: "com.mysql.cj.jdbc.Driver"
                mongodb:
                  default-port: 27017
                postgresql:
                  properties:
                    ssl: "true"
                """;

            processor.parseEnum(yaml, DatabaseType.class);

            assertEquals("com.mysql.cj.jdbc.Driver", DatabaseType.MYSQL.driver);
            assertEquals(0,  DatabaseType.MYSQL.defaultPort); // valeur par défaut pour int
            assertNull( DatabaseType.MYSQL.properties); // null car pas fourni
        }

        @Test
        @DisplayName("Devrait gérer enum sans données additionnelles")
        void shouldHandleEnumWithoutAdditionalData() {
            String yaml = """
                mysql: {}
                postgresql: {}
                mongodb: {}
                """;

            for (DatabaseType value : DatabaseType.values()) {
                value.driver = null;
                value.defaultPort = 0;
                value.properties = null;
            }

            processor.parseEnum(yaml, DatabaseType.class);

            assertNull(DatabaseType.MYSQL.driver);
            assertEquals(0, DatabaseType.MYSQL.defaultPort);
            assertNull(DatabaseType.MYSQL.properties);
        }
    }
}
