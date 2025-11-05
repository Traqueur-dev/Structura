package fr.traqueur.structura;

import fr.traqueur.structura.api.Structura;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static fr.traqueur.structura.fixtures.TestModels.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Concrete Type Section Succession Tests")
class ConcreteTypeSectionTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("List Conversion Tests")
    class ListConversionTest {

        @Test
        @DisplayName("Should convert multiple YAML sections to List of concrete records")
        void shouldConvertMultipleSectionsToList() throws IOException {
            String yaml = """
                    items:
                      item1:
                        nutrition: 8
                        saturation: 9.6
                      item2:
                        nutrition: 10
                        saturation: 12.0
                      item3:
                        nutrition: 15
                        saturation: 18.5
                    """;

            Path yamlFile = tempDir.resolve("config.yml");
            Files.writeString(yamlFile, yaml);

            ConfigWithFoodList config = Structura.load(yamlFile, ConfigWithFoodList.class);

            assertNotNull(config);
            assertNotNull(config.items());
            assertEquals(3, config.items().size(), "Should have 3 items from 3 sections");

            // Verify values are correctly parsed
            boolean hasNutrition8 = config.items().stream()
                    .anyMatch(item -> item.nutrition() == 8 && item.saturation() == 9.6);
            boolean hasNutrition10 = config.items().stream()
                    .anyMatch(item -> item.nutrition() == 10 && item.saturation() == 12.0);
            boolean hasNutrition15 = config.items().stream()
                    .anyMatch(item -> item.nutrition() == 15 && item.saturation() == 18.5);

            assertTrue(hasNutrition8, "Should contain item with nutrition=8");
            assertTrue(hasNutrition10, "Should contain item with nutrition=10");
            assertTrue(hasNutrition15, "Should contain item with nutrition=15");
        }

        @Test
        @DisplayName("Should handle single section as List with one element")
        void shouldHandleSingleSectionAsList() throws IOException {
            String yaml = """
                    items:
                      only-one:
                        nutrition: 8
                        saturation: 9.6
                    """;

            Path yamlFile = tempDir.resolve("config.yml");
            Files.writeString(yamlFile, yaml);

            ConfigWithFoodList config = Structura.load(yamlFile, ConfigWithFoodList.class);

            assertNotNull(config);
            assertNotNull(config.items());
            assertEquals(1, config.items().size(), "Should have exactly 1 item");

            FoodItem item = config.items().get(0);
            assertEquals(8, item.nutrition());
            assertEquals(9.6, item.saturation());
        }

        @Test
        @DisplayName("Should handle empty YAML map as empty List")
        void shouldHandleEmptyMapAsEmptyList() throws IOException {
            String yaml = """
                    items: {}
                    """;

            Path yamlFile = tempDir.resolve("config.yml");
            Files.writeString(yamlFile, yaml);

            ConfigWithFoodList config = Structura.load(yamlFile, ConfigWithFoodList.class);

            assertNotNull(config);
            assertNotNull(config.items());
            assertTrue(config.items().isEmpty(), "Should have empty list");
        }

        @Test
        @DisplayName("Should convert sections with different concrete types")
        void shouldConvertSectionsWithDifferentRecordType() throws IOException {
            String yaml = """
                    servers:
                      web:
                        host: "web.example.com"
                        port: 80
                      api:
                        host: "api.example.com"
                        port: 8080
                      db:
                        host: "db.example.com"
                        port: 5432
                    """;

            Path yamlFile = tempDir.resolve("config.yml");
            Files.writeString(yamlFile, yaml);

            ConfigWithServerList config = Structura.load(yamlFile, ConfigWithServerList.class);

            assertNotNull(config);
            assertNotNull(config.servers());
            assertEquals(3, config.servers().size());

            // Verify hosts
            boolean hasWeb = config.servers().stream()
                    .anyMatch(s -> s.host().equals("web.example.com") && s.port() == 80);
            boolean hasApi = config.servers().stream()
                    .anyMatch(s -> s.host().equals("api.example.com") && s.port() == 8080);
            boolean hasDb = config.servers().stream()
                    .anyMatch(s -> s.host().equals("db.example.com") && s.port() == 5432);

            assertTrue(hasWeb, "Should contain web server");
            assertTrue(hasApi, "Should contain api server");
            assertTrue(hasDb, "Should contain db server");
        }

        @Test
        @DisplayName("Should handle many sections efficiently")
        void shouldHandleManySectionsEfficiently() throws IOException {
            // Generate YAML with 15 sections
            StringBuilder yamlBuilder = new StringBuilder("items:\n");
            for (int i = 1; i <= 15; i++) {
                yamlBuilder.append("  section").append(i).append(":\n");
                yamlBuilder.append("    nutrition: ").append(i).append("\n");
                yamlBuilder.append("    saturation: ").append(i * 1.5).append("\n");
            }

            Path yamlFile = tempDir.resolve("config.yml");
            Files.writeString(yamlFile, yamlBuilder.toString());

            ConfigWithFoodList config = Structura.load(yamlFile, ConfigWithFoodList.class);

            assertNotNull(config);
            assertNotNull(config.items());
            assertEquals(15, config.items().size(), "Should have 15 items from 15 sections");

            // Verify all items are FoodItem instances
            for (FoodItem item : config.items()) {
                assertNotNull(item);
                assertTrue(item.nutrition() >= 1 && item.nutrition() <= 15);
            }
        }
    }

    @Nested
    @DisplayName("Map Conversion Tests")
    class MapConversionTest {

        @Test
        @DisplayName("Should convert multiple YAML sections to Map with correct keys")
        void shouldConvertMultipleSectionsToMap() throws IOException {
            String yaml = """
                    items:
                      breakfast:
                        nutrition: 8
                        saturation: 9.6
                      lunch:
                        nutrition: 10
                        saturation: 12.0
                      dinner:
                        nutrition: 15
                        saturation: 18.5
                    """;

            Path yamlFile = tempDir.resolve("config.yml");
            Files.writeString(yamlFile, yaml);

            ConfigWithFoodMap config = Structura.load(yamlFile, ConfigWithFoodMap.class);

            assertNotNull(config);
            assertNotNull(config.items());
            assertEquals(3, config.items().size(), "Should have 3 entries from 3 sections");

            // Verify all keys exist
            assertTrue(config.items().containsKey("breakfast"));
            assertTrue(config.items().containsKey("lunch"));
            assertTrue(config.items().containsKey("dinner"));

            // Verify values
            assertEquals(8, config.items().get("breakfast").nutrition());
            assertEquals(9.6, config.items().get("breakfast").saturation());

            assertEquals(10, config.items().get("lunch").nutrition());
            assertEquals(12.0, config.items().get("lunch").saturation());

            assertEquals(15, config.items().get("dinner").nutrition());
            assertEquals(18.5, config.items().get("dinner").saturation());
        }

        @Test
        @DisplayName("Should handle Map with different key names")
        void shouldHandleMapWithDifferentKeyNames() throws IOException {
            String yaml = """
                    servers:
                      primary:
                        host: "primary.example.com"
                        port: 80
                      secondary:
                        host: "secondary.example.com"
                        port: 8080
                      backup:
                        host: "backup.example.com"
                        port: 9090
                    """;

            Path yamlFile = tempDir.resolve("config.yml");
            Files.writeString(yamlFile, yaml);

            ConfigWithServerMap config = Structura.load(yamlFile, ConfigWithServerMap.class);

            assertNotNull(config);
            assertNotNull(config.servers());
            assertEquals(3, config.servers().size());

            // Verify keys and values
            assertTrue(config.servers().containsKey("primary"));
            assertTrue(config.servers().containsKey("secondary"));
            assertTrue(config.servers().containsKey("backup"));

            assertEquals("primary.example.com", config.servers().get("primary").host());
            assertEquals(80, config.servers().get("primary").port());

            assertEquals("secondary.example.com", config.servers().get("secondary").host());
            assertEquals(8080, config.servers().get("secondary").port());

            assertEquals("backup.example.com", config.servers().get("backup").host());
            assertEquals(9090, config.servers().get("backup").port());
        }

        @Test
        @DisplayName("Should handle empty YAML map as empty Map")
        void shouldHandleEmptyYamlAsEmptyMap() throws IOException {
            String yaml = """
                    items: {}
                    """;

            Path yamlFile = tempDir.resolve("config.yml");
            Files.writeString(yamlFile, yaml);

            ConfigWithFoodMap config = Structura.load(yamlFile, ConfigWithFoodMap.class);

            assertNotNull(config);
            assertNotNull(config.items());
            assertTrue(config.items().isEmpty(), "Should have empty map");
        }

        @Test
        @DisplayName("Should handle single entry Map")
        void shouldHandleSingleEntryMap() throws IOException {
            String yaml = """
                    items:
                      single:
                        nutrition: 8
                        saturation: 9.6
                    """;

            Path yamlFile = tempDir.resolve("config.yml");
            Files.writeString(yamlFile, yaml);

            ConfigWithFoodMap config = Structura.load(yamlFile, ConfigWithFoodMap.class);

            assertNotNull(config);
            assertNotNull(config.items());
            assertEquals(1, config.items().size());

            assertTrue(config.items().containsKey("single"));
            assertEquals(8, config.items().get("single").nutrition());
            assertEquals(9.6, config.items().get("single").saturation());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTest {

        @Test
        @DisplayName("Should distinguish between single record and multiple records")
        void shouldDistinguishSingleFromMultipleRecords() throws IOException {
            // This YAML represents a SINGLE FoodItem with fields nutrition and saturation
            String yamlSingle = """
                    items:
                      nutrition: 8
                      saturation: 9.6
                    """;

            Path yamlFileSingle = tempDir.resolve("config_single.yml");
            Files.writeString(yamlFileSingle, yamlSingle);

            ConfigWithFoodList configSingle = Structura.load(yamlFileSingle, ConfigWithFoodList.class);

            assertNotNull(configSingle);
            assertNotNull(configSingle.items());
            assertEquals(1, configSingle.items().size(), "Should have 1 item (single record)");
            assertEquals(8, configSingle.items().get(0).nutrition());
            assertEquals(9.6, configSingle.items().get(0).saturation());

            // This YAML represents MULTIPLE FoodItems
            String yamlMultiple = """
                    items:
                      item1:
                        nutrition: 8
                        saturation: 9.6
                      item2:
                        nutrition: 10
                        saturation: 12.0
                    """;

            Path yamlFileMultiple = tempDir.resolve("config_multiple.yml");
            Files.writeString(yamlFileMultiple, yamlMultiple);

            ConfigWithFoodList configMultiple = Structura.load(yamlFileMultiple, ConfigWithFoodList.class);

            assertNotNull(configMultiple);
            assertNotNull(configMultiple.items());
            assertEquals(2, configMultiple.items().size(), "Should have 2 items (multiple records)");
        }

        @Test
        @DisplayName("Should handle kebab-case keys matching field names")
        void shouldHandleKebabCaseKeysMatchingFieldNames() throws IOException {
            // Keys match field names → single record
            String yaml = """
                    servers:
                      host: "example.com"
                      port: 8080
                    """;

            Path yamlFile = tempDir.resolve("config.yml");
            Files.writeString(yamlFile, yaml);

            ConfigWithServerList config = Structura.load(yamlFile, ConfigWithServerList.class);

            assertNotNull(config);
            assertNotNull(config.servers());
            assertEquals(1, config.servers().size(), "Should have 1 server");
            assertEquals("example.com", config.servers().get(0).host());
            assertEquals(8080, config.servers().get(0).port());
        }
    }
}
