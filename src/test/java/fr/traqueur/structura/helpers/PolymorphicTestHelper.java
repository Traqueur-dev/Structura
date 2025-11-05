package fr.traqueur.structura.helpers;

import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.registries.PolymorphicRegistry;

import static fr.traqueur.structura.fixtures.TestModels.*;

/**
 * Helper class to set up polymorphic registries for tests.
 * Centralizes registry configuration to eliminate duplication.
 */
public final class PolymorphicTestHelper {

    private PolymorphicTestHelper() {} // Prevent instantiation

    /**
     * Sets up the PaymentMethod polymorphic registry.
     * Call this in @BeforeEach or @BeforeAll to ensure registry is configured.
     */
    public static void setupPaymentMethodRegistry() {
        try {
            PolymorphicRegistry.get(PaymentMethod.class);
            // Registry already exists, skip setup
        } catch (Exception e) {
            // Registry doesn't exist, create it
            PolymorphicRegistry.create(PaymentMethod.class, registry -> {
                registry.register("credit-card", CreditCard.class);
                registry.register("pay-pal", PayPal.class);
                registry.register("bank-transfer", BankTransfer.class);
            });
        }
    }

    /**
     * Sets up the InlinePaymentMethod polymorphic registry.
     */
    public static void setupInlinePaymentMethodRegistry() {
        try {
            PolymorphicRegistry.get(InlinePaymentMethod.class);
        } catch (Exception e) {
            PolymorphicRegistry.create(InlinePaymentMethod.class, registry -> {
                registry.register("inline-credit-card", InlineCreditCard.class);
                registry.register("inline-pay-pal", InlinePayPal.class);
            });
        }
    }

    /**
     * Sets up the KeyAsDiscriminatorDB polymorphic registry.
     */
    public static void setupKeyAsDiscriminatorDBRegistry() {
        try {
            PolymorphicRegistry.get(KeyAsDiscriminatorDB.class);
        } catch (Exception e) {
            PolymorphicRegistry.create(KeyAsDiscriminatorDB.class, registry -> {
                registry.register("my-sql", MySQL.class);
                registry.register("postgre-sql", PostgreSQL.class);
            });
        }
    }

    /**
     * Sets up the TestDatabaseConfig polymorphic registry for ValueConverter tests.
     */
    public static void setupTestDatabaseConfigRegistry() {
        try {
            PolymorphicRegistry.get(TestDatabaseConfig.class);
        } catch (Exception e) {
            PolymorphicRegistry.create(TestDatabaseConfig.class, registry -> {
                registry.register("mysql", TestMySQLConfig.class);
                registry.register("postgres", TestPostgreSQLConfig.class);
            });
        }
    }

    /**
     * Sets up the TestPaymentProvider polymorphic registry for ValueConverter tests.
     */
    public static void setupTestPaymentProviderRegistry() {
        try {
            PolymorphicRegistry.get(TestPaymentProvider.class);
        } catch (Exception e) {
            PolymorphicRegistry.create(TestPaymentProvider.class, registry -> {
                registry.register("stripe", TestStripeProvider.class);
                registry.register("paypal", TestPayPalProvider.class);
            });
        }
    }

    /**
     * Sets up the InlineDatabaseConfig polymorphic registry.
     */
    public static void setupInlineDatabaseConfigRegistry() {
        try {
            PolymorphicRegistry.get(InlineDatabaseConfig.class);
        } catch (Exception e) {
            PolymorphicRegistry.create(InlineDatabaseConfig.class, registry -> {
                registry.register("mysql", InlineMySQLConfig.class);
                registry.register("postgres", InlinePostgreSQLConfig.class);
            });
        }
    }

    /**
     * Sets up the CacheConfig polymorphic registry.
     */
    public static void setupCacheConfigRegistry() {
        try {
            PolymorphicRegistry.get(CacheConfig.class);
        } catch (Exception e) {
            PolymorphicRegistry.create(CacheConfig.class, registry -> {
                registry.register("redis", RedisConfig.class);
                registry.register("memcached", MemcachedConfig.class);
            });
        }
    }

    /**
     * Sets up the StorageConfig polymorphic registry.
     */
    public static void setupStorageConfigRegistry() {
        try {
            PolymorphicRegistry.get(StorageConfig.class);
        } catch (Exception e) {
            PolymorphicRegistry.create(StorageConfig.class, registry -> {
                registry.register("s3", S3Config.class);
                registry.register("gcs", GCSConfig.class);
            });
        }
    }

    /**
     * Sets up the ItemMetadata polymorphic registry.
     */
    public static void setupItemMetadataRegistry() {
        try {
            PolymorphicRegistry.get(ItemMetadata.class);
        } catch (Exception e) {
            PolymorphicRegistry.create(ItemMetadata.class, registry -> {
                registry.register("food", FoodMetadata.class);
                registry.register("potion", PotionMetadata.class);
                registry.register("trim", TrimMetadata.class);
                registry.register("leather-armor", LeatherArmorMetadata.class);
            });
        }
    }

    /**
     * Sets up all common polymorphic registries used in tests.
     * Call this in a test class with @BeforeAll to set up everything at once.
     */
    public static void setupAllRegistries() {
        setupPaymentMethodRegistry();
        setupInlinePaymentMethodRegistry();
        setupKeyAsDiscriminatorDBRegistry();
        setupTestDatabaseConfigRegistry();
        setupTestPaymentProviderRegistry();
        setupInlineDatabaseConfigRegistry();
        setupCacheConfigRegistry();
        setupStorageConfigRegistry();
        setupItemMetadataRegistry();
    }

    /**
     * Creates a custom polymorphic registry for test-specific interfaces.
     *
     * @param interfaceClass the polymorphic interface
     * @param name1 first implementation name
     * @param impl1 first implementation class
     * @param name2 second implementation name
     * @param impl2 second implementation class
     */
    public static <T extends Loadable> void setupCustomRegistry(
            Class<T> interfaceClass,
            String name1, Class<? extends T> impl1,
            String name2, Class<? extends T> impl2) {
        try {
            PolymorphicRegistry.get(interfaceClass);
        } catch (Exception e) {
            PolymorphicRegistry.create(interfaceClass, registry -> {
                registry.register(name1, impl1);
                registry.register(name2, impl2);
            });
        }
    }

    /**
     * Creates a custom polymorphic registry with three implementations.
     */
    public static <T extends Loadable> void setupCustomRegistry(
            Class<T> interfaceClass,
            String name1, Class<? extends T> impl1,
            String name2, Class<? extends T> impl2,
            String name3, Class<? extends T> impl3) {
        try {
            PolymorphicRegistry.get(interfaceClass);
        } catch (Exception e) {
            PolymorphicRegistry.create(interfaceClass, registry -> {
                registry.register(name1, impl1);
                registry.register(name2, impl2);
                registry.register(name3, impl3);
            });
        }
    }

    /**
     * Checks if a polymorphic registry exists for the given interface.
     */
    public static boolean registryExists(Class<? extends Loadable> interfaceClass) {
        try {
            PolymorphicRegistry.get(interfaceClass);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets an existing registry or creates a new one with the given setup.
     */
    public static <T extends Loadable> PolymorphicRegistry<T> getOrCreateRegistry(
            Class<T> interfaceClass,
            java.util.function.Consumer<PolymorphicRegistry<T>> configurator) {
        try {
            return PolymorphicRegistry.get(interfaceClass);
        } catch (Exception e) {
            PolymorphicRegistry.create(interfaceClass, configurator);
            return PolymorphicRegistry.get(interfaceClass);
        }
    }
}
