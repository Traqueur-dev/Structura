package fr.traqueur.structura.references;

import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.api.Structura;
import fr.traqueur.structura.annotations.defaults.DefaultReference;
import fr.traqueur.structura.exceptions.StructuraException;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Reference Integration Tests")
class ReferenceIntegrationTest {

    // --------------- Domain types ---------------

    record Item(String id, String name) {}
    record Zone(String code, int capacity) {}

    record LoadableItem(String id, String label) implements Loadable {}

    // --------------- Config records ---------------

    record ItemConfig(Reference<Item> item, int count) implements Loadable {}
    record DualRefConfig(Reference<Item> item, Reference<Zone> zone) implements Loadable {}
    record ListRefConfig(List<Reference<Item>> items) implements Loadable {}
    record DefaultRefConfig(@DefaultReference(type = Item.class, key = "fallback-id") Reference<Item> item) implements Loadable {}
    record LoadableItemConfig(Reference<LoadableItem> target) implements Loadable {}

    // --------------- Setup ---------------

    @BeforeEach
    void setUp() {
        ReferenceRegistry.getInstance().clear();
    }

    @AfterEach
    void tearDown() {
        ReferenceRegistry.getInstance().clear();
    }

    // --------------- Tests ---------------

    @Nested
    @DisplayName("Single Reference field")
    class SingleReferenceTest {

        @Test
        @DisplayName("Should parse and resolve a single Reference<T> field")
        void shouldParseAndResolveSingleReference() {
            Item sword = new Item("sword", "Iron Sword");
            ReferenceRegistry.getInstance().install(Item.class, Item::id, () -> List.of(sword));

            String yaml = """
                    item: "sword"
                    count: 5
                    """;

            ItemConfig config = Structura.parse(yaml, ItemConfig.class);

            assertNotNull(config.item());
            assertEquals("sword", config.item().key());
            assertEquals(sword, config.item().element());
            assertEquals(5, config.count());
        }

        @Test
        @DisplayName("Should correctly return key() from parsed reference")
        void shouldReturnCorrectKey() {
            Item arrow = new Item("arrow", "Iron Arrow");
            ReferenceRegistry.getInstance().install(Item.class, Item::id, () -> List.of(arrow));

            String yaml = "item: \"arrow\"\ncount: 99\n";

            ItemConfig config = Structura.parse(yaml, ItemConfig.class);

            assertEquals("arrow", config.item().key());
        }
    }

    @Nested
    @DisplayName("Multiple Reference fields of different types")
    class MultipleReferenceTypesTest {

        @Test
        @DisplayName("Should parse and resolve multiple Reference<T> fields of different types")
        void shouldParseMultipleDifferentTypeReferences() {
            Item bow = new Item("bow", "Longbow");
            Zone arena = new Zone("arena-1", 50);

            ReferenceRegistry.getInstance().install(Item.class, Item::id, () -> List.of(bow));
            ReferenceRegistry.getInstance().install(Zone.class, Zone::code, () -> List.of(arena));

            String yaml = """
                    item: "bow"
                    zone: "arena-1"
                    """;

            DualRefConfig config = Structura.parse(yaml, DualRefConfig.class);

            assertEquals("bow", config.item().key());
            assertEquals(bow, config.item().element());

            assertEquals("arena-1", config.zone().key());
            assertEquals(arena, config.zone().element());
        }
    }

    @Nested
    @DisplayName("Reference<T> inside List")
    class ListOfReferencesTest {

        @Test
        @DisplayName("Should parse a List<Reference<T>> and resolve each element")
        void shouldParseListOfReferences() {
            Item sword = new Item("sword", "Iron Sword");
            Item bow = new Item("bow", "Longbow");
            Item axe = new Item("axe", "Battle Axe");

            ReferenceRegistry.getInstance().install(Item.class, Item::id, () -> List.of(sword, bow, axe));

            String yaml = """
                    items:
                      - "sword"
                      - "bow"
                      - "axe"
                    """;

            ListRefConfig config = Structura.parse(yaml, ListRefConfig.class);

            assertNotNull(config.items());
            assertEquals(3, config.items().size());

            assertEquals("sword", config.items().get(0).key());
            assertEquals(sword, config.items().get(0).element());

            assertEquals("bow", config.items().get(1).key());
            assertEquals(bow, config.items().get(1).element());

            assertEquals("axe", config.items().get(2).key());
            assertEquals(axe, config.items().get(2).element());
        }
    }

    @Nested
    @DisplayName("@DefaultReference on a Reference<T> field")
    class DefaultValueTest {

        @Test
        @DisplayName("Should use @DefaultReference key when YAML field is absent")
        void shouldUseDefaultReferenceKey() {
            Item fallback = new Item("fallback-id", "Fallback Item");
            ReferenceRegistry.getInstance().install(Item.class, Item::id, () -> List.of(fallback));

            // No "item" field in YAML — default kicks in
            String yaml = "{}";

            DefaultRefConfig config = Structura.parse(yaml, DefaultRefConfig.class);

            assertNotNull(config.item());
            assertEquals("fallback-id", config.item().key());
            assertEquals(fallback, config.item().element());
        }
    }

    @Nested
    @DisplayName("Error cases")
    class ErrorCasesTest {

        @Test
        @DisplayName("Should throw StructuraException when no provider is registered for the type")
        void shouldThrowWhenNoProviderRegistered() {
            // No provider installed for Item
            String yaml = "item: \"sword\"\ncount: 1\n";

            StructuraException ex = assertThrows(StructuraException.class, () ->
                Structura.parse(yaml, ItemConfig.class)
            );

            assertTrue(ex.getMessage().contains("No ReferenceProvider registered"),
                "Expected message about missing provider, got: " + ex.getMessage());
            assertTrue(ex.getMessage().contains(Item.class.getName()),
                "Expected type name in message, got: " + ex.getMessage());
        }

        @Test
        @DisplayName("Should throw StructuraException when key is not found in provider")
        void shouldThrowWhenKeyNotFoundInProvider() {
            ReferenceRegistry.getInstance().install(Item.class, Item::id,
                () -> List.of(new Item("existing", "Existing Item")));

            String yaml = "item: \"missing-key\"\ncount: 1\n";

            ItemConfig config = Structura.parse(yaml, ItemConfig.class);

            // Exception is thrown lazily at element() call time
            StructuraException ex = assertThrows(StructuraException.class, () ->
                config.item().element()
            );

            assertTrue(ex.getMessage().contains("missing-key"),
                "Expected key in message, got: " + ex.getMessage());
            assertTrue(ex.getMessage().contains(Item.class.getSimpleName()),
                "Expected type name in message, got: " + ex.getMessage());
        }
    }

    @Nested
    @DisplayName("Lazy resolution")
    class LazyResolutionTest {

        @Test
        @DisplayName("Element added after parse is still resolved by element()")
        void shouldResolveElementAddedAfterParse() {
            List<Item> mutableItems = new ArrayList<>();
            ReferenceRegistry.getInstance().install(Item.class, Item::id, () -> mutableItems);

            String yaml = "item: \"late-item\"\ncount: 1\n";

            ItemConfig config = Structura.parse(yaml, ItemConfig.class);

            // Collection is still empty — reference was created but not yet resolved
            assertNotNull(config.item());
            assertEquals("late-item", config.item().key());

            // Now add the item to the live collection
            Item lateItem = new Item("late-item", "Late Item");
            mutableItems.add(lateItem);

            // element() resolves from the live collection
            assertEquals(lateItem, config.item().element());
        }
    }

    @Nested
    @DisplayName("Reference<T> where T is a Loadable record")
    class LoadableReferenceTest {

        @Test
        @DisplayName("Should parse Reference<T> where T implements Loadable")
        void shouldParseReferenceToLoadable() {
            LoadableItem target = new LoadableItem("config-item", "Config-managed Item");
            ReferenceRegistry.getInstance().install(
                LoadableItem.class,
                LoadableItem::id,
                () -> List.of(target)
            );

            String yaml = "target: \"config-item\"\n";

            LoadableItemConfig config = Structura.parse(yaml, LoadableItemConfig.class);

            assertNotNull(config.target());
            assertEquals("config-item", config.target().key());
            assertEquals(target, config.target().element());
            assertEquals("Config-managed Item", config.target().element().label());
        }
    }
}