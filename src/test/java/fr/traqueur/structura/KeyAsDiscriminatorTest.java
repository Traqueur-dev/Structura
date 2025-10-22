package fr.traqueur.structura;

import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.registries.PolymorphicRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Polymorphic useKeyAsDiscriminator Feature Tests")
class KeyAsDiscriminatorTest {

    @TempDir
    Path tempDir;

    // Test polymorphic interface with useKeyAsDiscriminator enabled
    @Polymorphic(useKey = true)
    public interface ItemMetadata extends Loadable {}

    // Implementations
    public record FoodMetadata(int nutrition, double saturation) implements ItemMetadata {}
    public record PotionMetadata(String color, String basePotionType) implements ItemMetadata {}
    public record TrimMetadata(String material, String pattern) implements ItemMetadata {}
    public record LeatherArmorMetadata(String color) implements ItemMetadata {}

    // Test records for different contexts
    public record SimpleFieldConfig(ItemMetadata trim) implements Loadable {}
    public record ListFieldConfig(List<ItemMetadata> metadata) implements Loadable {}
    public record MapFieldConfig(Map<String, ItemMetadata> metadata) implements Loadable {}

    @BeforeEach
    void setUp() {
        clearAllRegistries();

        // Register implementations
        PolymorphicRegistry.create(ItemMetadata.class, registry -> {
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

    private void clearAllRegistries() {
        try {
            var field = PolymorphicRegistry.class.getDeclaredField("REGISTRIES");
            field.setAccessible(true);
            ((java.util.Map<?, ?>) field.get(null)).clear();
        } catch (Exception e) {
            fail("Failed to clear registries: " + e.getMessage());
        }
    }

    @Nested
    @DisplayName("Simple Field Context")
    class SimpleFieldContextTest {

        // Test record with food field
        public record ConfigWithFoodLocal(ItemMetadata food) implements Loadable {}

        @Test
        @DisplayName("Should use field name as discriminator for simple polymorphic field")
        void shouldUseFieldNameAsDiscriminator() throws IOException {
            String yaml = """
                    trim:
                      material: DIAMOND
                      pattern: VEX
                    """;

            Path yamlFile = tempDir.resolve("config.yml");
            Files.writeString(yamlFile, yaml);

            SimpleFieldConfig config = Structura.load(yamlFile, SimpleFieldConfig.class);

            assertNotNull(config);
            assertNotNull(config.trim());
            assertInstanceOf(TrimMetadata.class, config.trim());

            TrimMetadata trim = (TrimMetadata) config.trim();
            assertEquals("DIAMOND", trim.material());
            assertEquals("VEX", trim.pattern());
        }

        @Test
        @DisplayName("Should work with different field names")
        void shouldWorkWithDifferentFieldNames() throws IOException {
            String yaml = """
                    food:
                      nutrition: 8
                      saturation: 9.6
                    """;

            Path yamlFile = tempDir.resolve("config.yml");
            Files.writeString(yamlFile, yaml);

            ConfigWithFoodLocal config = Structura.load(yamlFile, ConfigWithFoodLocal.class);

            assertNotNull(config);
            assertNotNull(config.food());
            assertInstanceOf(FoodMetadata.class, config.food());

            FoodMetadata food = (FoodMetadata) config.food();
            assertEquals(8, food.nutrition());
            assertEquals(9.6, food.saturation());
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

            Path yamlFile = tempDir.resolve("config.yml");
            Files.writeString(yamlFile, yaml);

            ListFieldConfig config = Structura.load(yamlFile, ListFieldConfig.class);

            assertNotNull(config);
            assertNotNull(config.metadata());
            assertEquals(3, config.metadata().size());

            // Verify each element
            boolean hasFood = false, hasPotion = false, hasTrim = false;

            for (ItemMetadata item : config.metadata()) {
                if (item instanceof FoodMetadata food) {
                    hasFood = true;
                    assertEquals(8, food.nutrition());
                    assertEquals(9.6, food.saturation());
                } else if (item instanceof PotionMetadata potion) {
                    hasPotion = true;
                    assertEquals("#FF0000", potion.color());
                    assertEquals("STRENGTH", potion.basePotionType());
                } else if (item instanceof TrimMetadata trim) {
                    hasTrim = true;
                    assertEquals("NETHERITE", trim.material());
                    assertEquals("VEX", trim.pattern());
                }
            }

            assertTrue(hasFood, "Should contain FoodMetadata");
            assertTrue(hasPotion, "Should contain PotionMetadata");
            assertTrue(hasTrim, "Should contain TrimMetadata");
        }

        @Test
        @DisplayName("Should handle empty Map as empty List")
        void shouldHandleEmptyMapAsEmptyList() throws IOException {
            String yaml = """
                    metadata: {}
                    """;

            Path yamlFile = tempDir.resolve("config.yml");
            Files.writeString(yamlFile, yaml);

            ListFieldConfig config = Structura.load(yamlFile, ListFieldConfig.class);

            assertNotNull(config);
            assertNotNull(config.metadata());
            assertTrue(config.metadata().isEmpty());
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

            Path yamlFile = tempDir.resolve("config.yml");
            Files.writeString(yamlFile, yaml);

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
        }

        @Test
        @DisplayName("Should handle empty Map")
        void shouldHandleEmptyMap() throws IOException {
            String yaml = """
                    metadata: {}
                    """;

            Path yamlFile = tempDir.resolve("config.yml");
            Files.writeString(yamlFile, yaml);

            MapFieldConfig config = Structura.load(yamlFile, MapFieldConfig.class);

            assertNotNull(config);
            assertNotNull(config.metadata());
            assertTrue(config.metadata().isEmpty());
        }
    }

    @Nested
    @DisplayName("Combined Context Test")
    class CombinedContextTest {

        public record ComplexConfig(
            ItemMetadata trim,
            List<ItemMetadata> items,
            Map<String, ItemMetadata> namedItems
        ) implements Loadable {}

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

            Path yamlFile = tempDir.resolve("config.yml");
            Files.writeString(yamlFile, yaml);

            ComplexConfig config = Structura.load(yamlFile, ComplexConfig.class);

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
        }
    }
}
