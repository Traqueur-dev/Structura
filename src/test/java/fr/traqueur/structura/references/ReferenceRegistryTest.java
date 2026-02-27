package fr.traqueur.structura.references;

import fr.traqueur.structura.exceptions.StructuraException;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReferenceRegistry")
class ReferenceRegistryTest {

    private ReferenceRegistry registry;

    // Test types
    record Item(String id, String name) {}
    record Widget(String code, int value) {}

    @BeforeEach
    void setUp() {
        registry = ReferenceRegistry.getInstance();
        registry.clear();
    }

    @AfterEach
    void tearDown() {
        registry.clear();
    }

    @Nested
    @DisplayName("Singleton Pattern")
    class SingletonPatternTest {

        @Test
        @DisplayName("Should return the same instance on multiple calls")
        void shouldReturnSameInstance() {
            ReferenceRegistry instance1 = ReferenceRegistry.getInstance();
            ReferenceRegistry instance2 = ReferenceRegistry.getInstance();

            assertSame(instance1, instance2);
        }

        @Test
        @DisplayName("Should maintain state across getInstance calls")
        void shouldMaintainStateAcrossGetInstanceCalls() {
            List<Item> items = List.of(new Item("a", "Alpha"));

            ReferenceRegistry.getInstance().install(Item.class, Item::id, () -> items);

            assertTrue(ReferenceRegistry.getInstance().hasProvider(Item.class));
            assertEquals(1, ReferenceRegistry.getInstance().size());
        }
    }

    @Nested
    @DisplayName("install()")
    class InstallTest {

        @Test
        @DisplayName("Should successfully install a provider")
        void shouldSuccessfullyInstallProvider() {
            List<Item> items = List.of(new Item("x", "X-Item"));

            registry.install(Item.class, Item::id, () -> items);

            assertTrue(registry.hasProvider(Item.class));
            assertEquals(1, registry.size());
        }

        @Test
        @DisplayName("Should install providers for multiple types")
        void shouldInstallMultipleProviders() {
            List<Item> items = List.of(new Item("a", "Alpha"));
            List<Widget> widgets = List.of(new Widget("w1", 42));

            registry.install(Item.class, Item::id, () -> items);
            registry.install(Widget.class, Widget::code, () -> widgets);

            assertTrue(registry.hasProvider(Item.class));
            assertTrue(registry.hasProvider(Widget.class));
            assertEquals(2, registry.size());
        }

        @Test
        @DisplayName("Should throw StructuraException when type is null")
        void shouldThrowWhenTypeIsNull() {
            List<Item> items = List.of();

            StructuraException ex = assertThrows(StructuraException.class, () ->
                registry.install(null, Item::id, () -> items)
            );

            assertTrue(ex.getMessage().contains("null type"));
        }

        @Test
        @DisplayName("Should throw StructuraException when keyExtractor is null")
        void shouldThrowWhenKeyExtractorIsNull() {
            List<Item> items = List.of();

            StructuraException ex = assertThrows(StructuraException.class, () ->
                registry.install(Item.class, null, () -> items)
            );

            assertTrue(ex.getMessage().contains("null keyExtractor"));
        }

        @Test
        @DisplayName("Should throw StructuraException when provider is null")
        void shouldThrowWhenProviderIsNull() {
            StructuraException ex = assertThrows(StructuraException.class, () ->
                registry.install(Item.class, Item::id, null)
            );

            assertTrue(ex.getMessage().contains("null provider"));
        }

        @Test
        @DisplayName("Should throw StructuraException when installing duplicate provider")
        void shouldThrowWhenInstallingDuplicate() {
            List<Item> items = List.of(new Item("a", "Alpha"));

            registry.install(Item.class, Item::id, () -> items);

            StructuraException ex = assertThrows(StructuraException.class, () ->
                registry.install(Item.class, Item::id, () -> items)
            );

            assertTrue(ex.getMessage().contains("already registered"));
            assertTrue(ex.getMessage().contains(Item.class.getName()));
        }
    }

    @Nested
    @DisplayName("uninstall()")
    class UninstallTest {

        @Test
        @DisplayName("Should remove an installed provider and return true")
        void shouldRemoveProviderAndReturnTrue() {
            List<Item> items = List.of(new Item("a", "Alpha"));
            registry.install(Item.class, Item::id, () -> items);

            boolean result = registry.uninstall(Item.class);

            assertTrue(result);
            assertFalse(registry.hasProvider(Item.class));
            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("Should return false when no provider is registered")
        void shouldReturnFalseWhenNotRegistered() {
            boolean result = registry.uninstall(Item.class);

            assertFalse(result);
        }

        @Test
        @DisplayName("Should uninstall specific provider without affecting others")
        void shouldUninstallSpecificProviderOnly() {
            List<Item> items = List.of(new Item("a", "Alpha"));
            List<Widget> widgets = List.of(new Widget("w1", 42));

            registry.install(Item.class, Item::id, () -> items);
            registry.install(Widget.class, Widget::code, () -> widgets);

            registry.uninstall(Item.class);

            assertFalse(registry.hasProvider(Item.class));
            assertTrue(registry.hasProvider(Widget.class));
            assertEquals(1, registry.size());
        }
    }

    @Nested
    @DisplayName("hasProvider()")
    class HasProviderTest {

        @Test
        @DisplayName("Should return true when provider is installed")
        void shouldReturnTrueWhenInstalled() {
            registry.install(Item.class, Item::id, () -> List.of());

            assertTrue(registry.hasProvider(Item.class));
        }

        @Test
        @DisplayName("Should return false when provider is not installed")
        void shouldReturnFalseWhenNotInstalled() {
            assertFalse(registry.hasProvider(Item.class));
        }

        @Test
        @DisplayName("Should return false after uninstall")
        void shouldReturnFalseAfterUninstall() {
            registry.install(Item.class, Item::id, () -> List.of());
            registry.uninstall(Item.class);

            assertFalse(registry.hasProvider(Item.class));
        }
    }

    @Nested
    @DisplayName("resolve()")
    class ResolveTest {

        @Test
        @DisplayName("Should resolve key to matching element")
        void shouldResolveKeyToMatchingElement() {
            Item alpha = new Item("alpha", "Alpha Item");
            Item beta = new Item("beta", "Beta Item");
            registry.install(Item.class, Item::id, () -> List.of(alpha, beta));

            Reference<Item> ref = registry.resolve("alpha", Item.class);

            assertNotNull(ref);
            assertEquals("alpha", ref.key());
            assertEquals(alpha, ref.element());
        }

        @Test
        @DisplayName("Should resolve to correct element among multiple")
        void shouldResolveCorrectElement() {
            Item alpha = new Item("alpha", "Alpha Item");
            Item beta = new Item("beta", "Beta Item");
            registry.install(Item.class, Item::id, () -> List.of(alpha, beta));

            Reference<Item> ref = registry.resolve("beta", Item.class);

            assertEquals(beta, ref.element());
        }

        @Test
        @DisplayName("Should throw StructuraException when key is not found")
        void shouldThrowWhenKeyNotFound() {
            registry.install(Item.class, Item::id, () -> List.of(new Item("existing", "Existing")));

            Reference<Item> ref = registry.resolve("missing", Item.class);

            StructuraException ex = assertThrows(StructuraException.class, ref::element);
            assertTrue(ex.getMessage().contains("missing"));
            assertTrue(ex.getMessage().contains(Item.class.getSimpleName()));
        }

        @Test
        @DisplayName("Should throw StructuraException when no provider is registered")
        void shouldThrowWhenNoProviderRegistered() {
            StructuraException ex = assertThrows(StructuraException.class, () ->
                registry.resolve("any-key", Item.class)
            );

            assertTrue(ex.getMessage().contains("No ReferenceProvider registered"));
            assertTrue(ex.getMessage().contains(Item.class.getName()));
        }

        @Test
        @DisplayName("Should be lazy: resolution happens at element() call time")
        void shouldBeLazy() {
            List<Item> mutableList = new ArrayList<>();
            registry.install(Item.class, Item::id, () -> mutableList);

            // Resolve before item is in the list — no exception yet
            Reference<Item> ref = registry.resolve("late", Item.class);

            // Add item after resolve
            mutableList.add(new Item("late", "Late Item"));

            // element() now resolves successfully
            assertEquals("late", ref.element().id());
        }
    }

    @Nested
    @DisplayName("clear() and size()")
    class ClearAndSizeTest {

        @Test
        @DisplayName("Should start with size 0")
        void shouldStartEmpty() {
            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("Should reflect correct size after installs and uninstalls")
        void shouldReflectCorrectSize() {
            assertEquals(0, registry.size());

            registry.install(Item.class, Item::id, () -> List.of());
            assertEquals(1, registry.size());

            registry.install(Widget.class, Widget::code, () -> List.of());
            assertEquals(2, registry.size());

            registry.uninstall(Item.class);
            assertEquals(1, registry.size());

            registry.clear();
            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("Should clear all providers")
        void shouldClearAllProviders() {
            registry.install(Item.class, Item::id, () -> List.of());
            registry.install(Widget.class, Widget::code, () -> List.of());

            registry.clear();

            assertEquals(0, registry.size());
            assertFalse(registry.hasProvider(Item.class));
            assertFalse(registry.hasProvider(Widget.class));
        }
    }

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafetyTest {

        record TypeA(String id) {}
        record TypeB(String id) {}
        record TypeC(String id) {}
        record TypeD(String id) {}
        record TypeE(String id) {}
        record TypeF(String id) {}
        record TypeG(String id) {}
        record TypeH(String id) {}
        record TypeI(String id) {}
        record TypeJ(String id) {}

        @Test
        @DisplayName("Should handle concurrent installs of different types")
        void shouldHandleConcurrentInstalls() throws InterruptedException {
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];
            List<Throwable> errors = new CopyOnWriteArrayList<>();

            threads[0] = new Thread(() -> registry.install(TypeA.class, TypeA::id, List::of));
            threads[1] = new Thread(() -> registry.install(TypeB.class, TypeB::id, List::of));
            threads[2] = new Thread(() -> registry.install(TypeC.class, TypeC::id, List::of));
            threads[3] = new Thread(() -> registry.install(TypeD.class, TypeD::id, List::of));
            threads[4] = new Thread(() -> registry.install(TypeE.class, TypeE::id, List::of));
            threads[5] = new Thread(() -> registry.install(TypeF.class, TypeF::id, List::of));
            threads[6] = new Thread(() -> registry.install(TypeG.class, TypeG::id, List::of));
            threads[7] = new Thread(() -> registry.install(TypeH.class, TypeH::id, List::of));
            threads[8] = new Thread(() -> registry.install(TypeI.class, TypeI::id, List::of));
            threads[9] = new Thread(() -> registry.install(TypeJ.class, TypeJ::id, List::of));

            for (Thread t : threads) {
                t.setUncaughtExceptionHandler((thread, ex) -> errors.add(ex));
                t.start();
            }
            for (Thread t : threads) {
                t.join();
            }

            assertTrue(errors.isEmpty(), "No exceptions expected but got: " + errors);
            assertEquals(threadCount, registry.size());
        }
    }
}