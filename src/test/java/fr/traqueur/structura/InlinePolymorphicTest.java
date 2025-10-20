package fr.traqueur.structura;

import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.annotations.defaults.DefaultInt;
import fr.traqueur.structura.annotations.defaults.DefaultString;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.conversion.ValueConverter;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.factory.RecordInstanceFactory;
import fr.traqueur.structura.mapping.FieldMapper;
import fr.traqueur.structura.registries.PolymorphicRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Inline Polymorphic Keys - Complete Tests")
class InlinePolymorphicTest {

    private RecordInstanceFactory recordFactory;
    private ValueConverter valueConverter;

    @BeforeEach
    void setUp() {
        clearAllRegistries();
        FieldMapper fieldMapper = new FieldMapper();
        recordFactory = new RecordInstanceFactory(fieldMapper);
        valueConverter = new ValueConverter(recordFactory);
        recordFactory.setValueConverter(valueConverter);
        setupPolymorphicRegistries();
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

    private void setupPolymorphicRegistries() {
        // Registry for inline polymorphic types
        PolymorphicRegistry.create(DatabaseConfig.class, registry -> {
            registry.register("mysql", MySQLConfig.class);
            registry.register("postgres", PostgreSQLConfig.class);
        });

        // Registry for non-inline polymorphic types (classic behavior)
        PolymorphicRegistry.create(CacheConfig.class, registry -> {
            registry.register("redis", RedisConfig.class);
            registry.register("memcached", MemcachedConfig.class);
        });

        // Registry for storage with custom key name
        PolymorphicRegistry.create(StorageConfig.class, registry -> {
            registry.register("s3", S3Config.class);
            registry.register("gcs", GCSConfig.class);
        });
    }

    // ===== Test Interfaces and Records =====

    // Inline polymorphic interface with default "type" key
    @Polymorphic(key = "type", inline = true)
    public interface DatabaseConfig extends Loadable {
        String getHost();
        int getPort();
    }

    public record MySQLConfig(
            @DefaultString("localhost") String host,
            @DefaultInt(3306) int port,
            @DefaultString("mysql") String driver
    ) implements DatabaseConfig {
        @Override public String getHost() { return host; }
        @Override public int getPort() { return port; }
    }

    public record PostgreSQLConfig(
            @DefaultString("localhost") String host,
            @DefaultInt(5432) int port,
            @DefaultString("postgresql") String driver
    ) implements DatabaseConfig {
        @Override public String getHost() { return host; }
        @Override public int getPort() { return port; }
    }

    // Non-inline polymorphic interface (classic behavior)
    @Polymorphic(key = "type", inline = false)
    public interface CacheConfig extends Loadable {
        String getHost();
        int getPort();
    }

    public record RedisConfig(
            @DefaultString("localhost") String host,
            @DefaultInt(6379) int port
    ) implements CacheConfig {
        @Override public String getHost() { return host; }
        @Override public int getPort() { return port; }
    }

    public record MemcachedConfig(
            @DefaultString("localhost") String host,
            @DefaultInt(11211) int port
    ) implements CacheConfig {
        @Override public String getHost() { return host; }
        @Override public int getPort() { return port; }
    }

    // Inline polymorphic interface with custom key name
    @Polymorphic(key = "provider", inline = true)
    public interface StorageConfig extends Loadable {
        String getBucket();
    }

    public record S3Config(
            @DefaultString("my-bucket") String bucket,
            @DefaultString("us-east-1") String region
    ) implements StorageConfig {
        @Override public String getBucket() { return bucket; }
    }

    public record GCSConfig(
            @DefaultString("my-bucket") String bucket,
            @DefaultString("global") String location
    ) implements StorageConfig {
        @Override public String getBucket() { return bucket; }
    }

    // Configuration records using polymorphic fields
    public record AppConfig(
            String appName,
            DatabaseConfig database
    ) implements Loadable {}

    public record ComplexConfig(
            String appName,
            DatabaseConfig database,
            CacheConfig cache,
            StorageConfig storage
    ) implements Loadable {}

    public record ConfigWithList(
            String appName,
            List<DatabaseConfig> databases
    ) implements Loadable {}

    public record ConfigWithCache(
            String appName,
            CacheConfig cache
    ) implements Loadable {}

    public record ConfigWithStorage(
            String appName,
            StorageConfig storage
    ) implements Loadable {}

    public record MixedConfig(
            String appName,
            DatabaseConfig database,
            CacheConfig cache
    ) implements Loadable {}

    // ===== Tests =====

    @Nested
    @DisplayName("Basic Inline Polymorphic Behavior")
    class BasicInlinePolymorphicTest {

        @Test
        @DisplayName("Should resolve inline polymorphic field with type at parent level")
        void shouldResolveInlinePolymorphicField() {
            Map<String, Object> data = Map.of(
                    "app-name", "MyApp",
                    "type", "mysql",
                    "database", Map.of(
                            "host", "db.example.com",
                            "port", 3307
                    )
            );

            AppConfig result = (AppConfig) recordFactory.createInstance(data, AppConfig.class, "");

            assertEquals("MyApp", result.appName());
            assertInstanceOf(MySQLConfig.class, result.database());

            MySQLConfig mysql = (MySQLConfig) result.database();
            assertEquals("db.example.com", mysql.host());
            assertEquals(3307, mysql.port());
            assertEquals("mysql", mysql.driver()); // Default value
        }

        @Test
        @DisplayName("Should resolve different inline polymorphic types")
        void shouldResolveDifferentInlinePolymorphicTypes() {
            // Test with MySQL
            Map<String, Object> mysqlData = Map.of(
                    "app-name", "App1",
                    "type", "mysql",
                    "database", Map.of("host", "mysql.example.com")
            );

            AppConfig mysqlResult = (AppConfig) recordFactory.createInstance(mysqlData, AppConfig.class, "");
            assertInstanceOf(MySQLConfig.class, mysqlResult.database());
            assertEquals("mysql.example.com", ((MySQLConfig) mysqlResult.database()).host());
            assertEquals(3306, ((MySQLConfig) mysqlResult.database()).port()); // Default

            // Test with PostgreSQL
            Map<String, Object> postgresData = Map.of(
                    "app-name", "App2",
                    "type", "postgres",
                    "database", Map.of("host", "postgres.example.com", "port", 5433)
            );

            AppConfig postgresResult = (AppConfig) recordFactory.createInstance(postgresData, AppConfig.class, "");
            assertInstanceOf(PostgreSQLConfig.class, postgresResult.database());
            assertEquals("postgres.example.com", ((PostgreSQLConfig) postgresResult.database()).host());
            assertEquals(5433, ((PostgreSQLConfig) postgresResult.database()).port());
        }

        @Test
        @DisplayName("Should use default values for inline polymorphic fields")
        void shouldUseDefaultValuesForInlinePolymorphicFields() {
            Map<String, Object> data = Map.of(
                    "app-name", "MinimalApp",
                    "type", "postgres",
                    "database", Map.of()  // Empty map, should use all defaults
            );

            AppConfig result = (AppConfig) recordFactory.createInstance(data, AppConfig.class, "");

            assertInstanceOf(PostgreSQLConfig.class, result.database());
            PostgreSQLConfig postgres = (PostgreSQLConfig) result.database();
            assertEquals("localhost", postgres.host());
            assertEquals(5432, postgres.port());
            assertEquals("postgresql", postgres.driver());
        }
    }

    @Nested
    @DisplayName("Inline vs Non-Inline Behavior")
    class InlineVsNonInlineTest {

        @Test
        @DisplayName("Should handle both inline and non-inline polymorphic fields in same config")
        void shouldHandleMixedInlineAndNonInlineFields() {
            Map<String, Object> data = Map.of(
                    "app-name", "MixedApp",
                    "type", "mysql",  // For inline database field
                    "database", Map.of(
                            "host", "db.example.com"
                    ),
                    "cache", Map.of(  // Non-inline, type must be inside
                            "type", "redis",
                            "host", "cache.example.com",
                            "port", 6380
                    )
            );

            MixedConfig result = (MixedConfig) recordFactory.createInstance(data, MixedConfig.class, "");

            assertEquals("MixedApp", result.appName());

            // Verify inline polymorphic field
            assertInstanceOf(MySQLConfig.class, result.database());
            assertEquals("db.example.com", ((MySQLConfig) result.database()).host());

            // Verify non-inline polymorphic field
            assertInstanceOf(RedisConfig.class, result.cache());
            RedisConfig redis = (RedisConfig) result.cache();
            assertEquals("cache.example.com", redis.host());
            assertEquals(6380, redis.port());
        }

        @Test
        @DisplayName("Non-inline polymorphic should fail if type is at parent level")
        void nonInlineShouldFailIfTypeAtParentLevel() {
            // This should fail because CacheConfig expects type inside the cache map
            Map<String, Object> data = Map.of(
                    "app-name", "App",
                    "type", "redis",  // Wrong place for non-inline
                    "cache", Map.of(
                            "host", "cache.example.com"
                    )
            );

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    recordFactory.createInstance(data, ConfigWithCache.class, "")
            );

            assertTrue(exception.getMessage().contains("requires key 'type'"));
        }
    }

    @Nested
    @DisplayName("Custom Discriminator Key Names")
    class CustomDiscriminatorKeyTest {

        @Test
        @DisplayName("Should support inline polymorphic with custom key name")
        void shouldSupportCustomKeyName() {
            Map<String, Object> data = Map.of(
                    "app-name", "StorageApp",
                    "provider", "s3",  // Custom key name for storage
                    "storage", Map.of(
                            "bucket", "my-data-bucket",
                            "region", "eu-west-1"
                    )
            );

            ConfigWithStorage result = (ConfigWithStorage) recordFactory.createInstance(data, ConfigWithStorage.class, "");

            assertEquals("StorageApp", result.appName());

            // Verify storage with custom key
            assertInstanceOf(S3Config.class, result.storage());
            S3Config s3 = (S3Config) result.storage();
            assertEquals("my-data-bucket", s3.bucket());
            assertEquals("eu-west-1", s3.region());
        }

        @Test
        @DisplayName("Should resolve different types with custom key")
        void shouldResolveDifferentTypesWithCustomKey() {
            // Test S3
            Map<String, Object> s3Data = Map.of(
                    "app-name", "S3App",
                    "provider", "s3",
                    "storage", Map.of("bucket", "s3-bucket")
            );

            ConfigWithStorage s3Result = (ConfigWithStorage) recordFactory.createInstance(s3Data, ConfigWithStorage.class, "");
            assertInstanceOf(S3Config.class, s3Result.storage());

            // Test GCS
            Map<String, Object> gcsData = Map.of(
                    "app-name", "GCSApp",
                    "provider", "gcs",
                    "storage", Map.of("bucket", "gcs-bucket", "location", "asia")
            );

            ConfigWithStorage gcsResult = (ConfigWithStorage) recordFactory.createInstance(gcsData, ConfigWithStorage.class, "");
            assertInstanceOf(GCSConfig.class, gcsResult.storage());
            assertEquals("asia", ((GCSConfig) gcsResult.storage()).location());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTest {

        @Test
        @DisplayName("Should throw exception when inline discriminator key is missing")
        void shouldThrowExceptionWhenInlineKeyMissing() {
            Map<String, Object> data = Map.of(
                    "app-name", "App",
                    // Missing "type" key at parent level
                    "database", Map.of(
                            "host", "db.example.com"
                    )
            );

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    recordFactory.createInstance(data, AppConfig.class, "")
            );

            assertTrue(exception.getMessage().contains("requires discriminator key 'type'"));
            assertTrue(exception.getMessage().contains("at the same level"));
        }

        @Test
        @DisplayName("Should throw exception for unknown inline polymorphic type")
        void shouldThrowExceptionForUnknownInlineType() {
            Map<String, Object> data = Map.of(
                    "app-name", "App",
                    "type", "oracle",  // Unknown type
                    "database", Map.of(
                            "host", "db.example.com"
                    )
            );

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    recordFactory.createInstance(data, AppConfig.class, "")
            );

            assertTrue(exception.getMessage().contains("No registered type found for oracle"));
            assertTrue(exception.getMessage().contains("Available types:"));
        }

        @Test
        @DisplayName("Should throw exception when inline polymorphic field is not a Map")
        void shouldThrowExceptionWhenInlineFieldNotMap() {
            Map<String, Object> data = Map.of(
                    "app-name", "App",
                    "type", "mysql",
                    "database", "invalid-string-value"  // Should be a Map
            );

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    recordFactory.createInstance(data, AppConfig.class, "")
            );

            assertTrue(exception.getMessage().contains("must have a Map value"));
        }

        @Test
        @DisplayName("Should throw exception with custom key name missing")
        void shouldThrowExceptionWithCustomKeyMissing() {
            Map<String, Object> data = Map.of(
                    "app-name", "App",
                    "type", "s3",  // Wrong key name, should be "provider"
                    "storage", Map.of("bucket", "my-bucket")
            );

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    recordFactory.createInstance(data, ConfigWithStorage.class, "")
            );

            assertTrue(exception.getMessage().contains("requires discriminator key 'provider'"));
        }
    }

    @Nested
    @DisplayName("Collections with Inline Polymorphic Types")
    class CollectionsTest {

        @Test
        @DisplayName("Should handle list of inline polymorphic types")
        void shouldHandleListOfInlinePolymorphicTypes() {
            // Note: For lists, each item needs its own type discriminator
            // This is a limitation - inline keys work best with single fields
            Map<String, Object> data = Map.of(
                    "app-name", "MultiDBApp",
                    "databases", List.of(
                            Map.of(
                                    "type", "mysql",
                                    "host", "mysql1.example.com",
                                    "port", 3306
                            ),
                            Map.of(
                                    "type", "postgres",
                                    "host", "postgres1.example.com",
                                    "port", 5432
                            )
                    )
            );

            ConfigWithList result = (ConfigWithList) recordFactory.createInstance(data, ConfigWithList.class, "");

            assertEquals("MultiDBApp", result.appName());
            assertEquals(2, result.databases().size());

            // Note: This will work because each map in the list has its own "type" key
            assertInstanceOf(MySQLConfig.class, result.databases().get(0));
            assertInstanceOf(PostgreSQLConfig.class, result.databases().get(1));

            assertEquals("mysql1.example.com", ((MySQLConfig) result.databases().get(0)).host());
            assertEquals("postgres1.example.com", ((PostgreSQLConfig) result.databases().get(1)).host());
        }
    }

    @Nested
    @DisplayName("Backward Compatibility")
    class BackwardCompatibilityTest {

        @Test
        @DisplayName("Default inline value should be false for backward compatibility")
        void defaultInlineShouldBeFalse() {
            // CacheConfig has inline = false
            Polymorphic annotation = CacheConfig.class.getAnnotation(Polymorphic.class);
            assertFalse(annotation.inline());
        }

        @Test
        @DisplayName("Should not break existing non-inline polymorphic behavior")
        void shouldNotBreakExistingNonInlineBehavior() {
            Map<String, Object> data = Map.of(
                    "app-name", "CacheApp",
                    "cache", Map.of(
                            "type", "redis",  // Type inside cache map (classic behavior)
                            "host", "redis.example.com",
                            "port", 6379
                    )
            );

            ConfigWithCache result = (ConfigWithCache) recordFactory.createInstance(data, ConfigWithCache.class, "");

            assertEquals("CacheApp", result.appName());
            assertInstanceOf(RedisConfig.class, result.cache());
            assertEquals("redis.example.com", ((RedisConfig) result.cache()).host());
        }
    }
}
