package fr.traqueur.structura;

import fr.traqueur.structura.api.Structura;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static fr.traqueur.structura.fixtures.TestModels.*;
import static fr.traqueur.structura.helpers.TestHelpers.*;
import static fr.traqueur.structura.helpers.PolymorphicTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Refactored Polymorphic useKeyAsDiscriminator tests using common test models.
 * Tests key-as-discriminator feature for polymorphic types.
 */
@DisplayName("Polymorphic useKeyAsDiscriminator Feature Tests - Refactored")
class KeyAsDiscriminatorTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        clearAllRegistries();

        // Register ItemMetadata implementations
        createPolymorphicRegistry(ItemMetadata.class, registry -> {
            registry.register("food", FoodMetadata.class);
            registry.register("potion", PotionMetadata.class);
            registry.register("trim", TrimMetadata.class);
            registry.register("leather-armor", LeatherArmorMetadata.class);
        });
    }

    @AfterEach
    void tearDown() {
        clearAllRegistries();
    }

    @Nested
    @DisplayName("Simple Field Context")
    class SimpleFieldContextTest {

        @Test
        @DisplayName("Should use field name as discriminator for simple polymorphic field")
        void shouldUseFieldNameAsDiscriminator() throws IOException {
            String yaml = """
                    trim:
                      material: DIAMOND
                      pattern: VEX
                    """;

            Path yamlFile = createTempYamlFile(yaml);

            try {
                SimpleFieldConfig config = Structura.load(yamlFile, SimpleFieldConfig.class);

                assertNotNull(config);
                assertNotNull(config.trim());
                assertInstanceOf(TrimMetadata.class, config.trim());

                TrimMetadata trim = (TrimMetadata) config.trim();
                assertEquals("DIAMOND", trim.material());
                assertEquals("VEX", trim.pattern());
            } finally {
                deleteTempFile(yamlFile);
            }
        }

        @Test
        @DisplayName("Should work with different field names")
        void shouldWorkWithDifferentFieldNames() throws IOException {
            String yaml = """
                    food:
                      nutrition: 8
                      saturation: 9.6
                    """;

            Path yamlFile = createTempYamlFile(yaml);

            try {
                ConfigWithFood config = Structura.load(yamlFile, ConfigWithFood.class);

                assertNotNull(config);
                assertNotNull(config.food());
                assertInstanceOf(FoodMetadata.class, config.food());

                FoodMetadata food = (FoodMetadata) config.food();
                assertEquals(8, food.nutrition());
                assertEquals(9.6, food.saturation());
            } finally {
                deleteTempFile(yamlFile);
            }
        }
    }

    @Nested
    @DisplayName("List Field Context")
    class ListFieldContextTest {

        @Test
        @DisplayName("Should convert Map to List using keys as discriminators")
        void shouldConvertMapToListUsingKeysAsDiscriminators() throws IOException {
            String yaml = """
                    metadata:
                      food:
                        nutrition: 8
                        saturation: 9.6
                      potion:
                        color: "#FF0000"
                        base-potion-type: STRENGTH
                      trim:
                        material: NETHERITE
                        pattern: VEX
                    """;

            Path yamlFile = createTempYamlFile(yaml);

            try {
                ListFieldConfig config = Structura.load(yamlFile, ListFieldConfig.class);

                assertNotNull(config);
                assertNotNull(config.metadata());
                assertEquals(3, config.metadata().size());

                // Verify each element
                boolean hasFood = false, hasPotion = false, hasTrim = false;

                for (ItemMetadata item : config.metadata()) {
                    if (item instanceof FoodMetadata(int nutrition, double saturation)) {
                        hasFood = true;
                        assertEquals(8, nutrition);
                        assertEquals(9.6, saturation);
                    } else if (item instanceof PotionMetadata(String color, String basePotionType)) {
                        hasPotion = true;
                        assertEquals("#FF0000", color);
                        assertEquals("STRENGTH", basePotionType);
                    } else if (item instanceof TrimMetadata(String material, String pattern)) {
                        hasTrim = true;
                        assertEquals("NETHERITE", material);
                        assertEquals("VEX", pattern);
                    }
                }

                assertTrue(hasFood, "Should contain FoodMetadata");
                assertTrue(hasPotion, "Should contain PotionMetadata");
                assertTrue(hasTrim, "Should contain TrimMetadata");
            } finally {
                deleteTempFile(yamlFile);
            }
        }

        @Test
        @DisplayName("Should handle empty Map as empty List")
        void shouldHandleEmptyMapAsEmptyList() throws IOException {
            String yaml = """
                    metadata: {}
                    """;

            Path yamlFile = createTempYamlFile(yaml);

            try {
                ListFieldConfig config = Structura.load(yamlFile, ListFieldConfig.class);

                assertNotNull(config);
                assertNotNull(config.metadata());
                assertTrue(config.metadata().isEmpty());
            } finally {
                deleteTempFile(yamlFile);
            }
        }
    }

    @Nested
    @DisplayName("Map Field Context")
    class MapFieldContextTest {

        @Test
        @DisplayName("Should use map keys as discriminators for Map values")
        void shouldUseMapKeysAsDiscriminatorsForMapValues() throws IOException {
            String yaml = """
                    metadata:
                      food:
                        nutrition: 8
                        saturation: 9.6
                      leather-armor:
                        color: "#FF0000"
                    """;

            Path yamlFile = createTempYamlFile(yaml);

            try {
                MapFieldConfig config = Structura.load(yamlFile, MapFieldConfig.class);

                assertNotNull(config);
                assertNotNull(config.metadata());
                assertEquals(2, config.metadata().size());

                // Verify food entry
                assertTrue(config.metadata().containsKey("food"));
                ItemMetadata foodItem = config.metadata().get("food");
                assertInstanceOf(FoodMetadata.class, foodItem);
                FoodMetadata food = (FoodMetadata) foodItem;
                assertEquals(8, food.nutrition());
                assertEquals(9.6, food.saturation());

                // Verify leather-armor entry
                assertTrue(config.metadata().containsKey("leather-armor"));
                ItemMetadata armorItem = config.metadata().get("leather-armor");
                assertInstanceOf(LeatherArmorMetadata.class, armorItem);
                LeatherArmorMetadata armor = (LeatherArmorMetadata) armorItem;
                assertEquals("#FF0000", armor.color());
            } finally {
                deleteTempFile(yamlFile);
            }
        }

        @Test
        @DisplayName("Should handle empty Map")
        void shouldHandleEmptyMap() throws IOException {
            String yaml = """
                    metadata: {}
                    """;

            Path yamlFile = createTempYamlFile(yaml);

            try {
                MapFieldConfig config = Structura.load(yamlFile, MapFieldConfig.class);

                assertNotNull(config);
                assertNotNull(config.metadata());
                assertTrue(config.metadata().isEmpty());
            } finally {
                deleteTempFile(yamlFile);
            }
        }
    }

    @Nested
    @DisplayName("Combined Context Test")
    class CombinedContextTest {

        @Test
        @DisplayName("Should handle all three contexts in one config")
        void shouldHandleAllThreeContextsInOneConfig() throws IOException {
            String yaml = """
                    trim:
                      material: DIAMOND
                      pattern: VEX
                    items:
                      food:
                        nutrition: 8
                        saturation: 9.6
                      potion:
                        color: "#00FF00"
                        base-potion-type: HEALING
                    named-items:
                      food:
                        nutrition: 10
                        saturation: 12.0
                      trim:
                        material: NETHERITE
                        pattern: SILENCE
                    """;

            Path yamlFile = createTempYamlFile(yaml);

            try {
                ComplexKeyDiscriminatorConfig config = Structura.load(yamlFile, ComplexKeyDiscriminatorConfig.class);

                assertNotNull(config);

                // Verify simple field
                assertNotNull(config.trim());
                assertInstanceOf(TrimMetadata.class, config.trim());
                assertEquals("DIAMOND", ((TrimMetadata) config.trim()).material());

                // Verify list
                assertNotNull(config.items());
                assertEquals(2, config.items().size());

                // Verify map
                assertNotNull(config.namedItems());
                assertEquals(2, config.namedItems().size());
                assertTrue(config.namedItems().containsKey("food"));
                assertTrue(config.namedItems().containsKey("trim"));

                // Verify map contents
                assertInstanceOf(FoodMetadata.class, config.namedItems().get("food"));
                assertEquals(10, ((FoodMetadata) config.namedItems().get("food")).nutrition());
                assertInstanceOf(TrimMetadata.class, config.namedItems().get("trim"));
                assertEquals("NETHERITE", ((TrimMetadata) config.namedItems().get("trim")).material());
            } finally {
                deleteTempFile(yamlFile);
            }
        }
    }
}