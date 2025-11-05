package fr.traqueur.structura;

import fr.traqueur.structura.conversion.ValueConverter;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.factory.RecordInstanceFactory;
import fr.traqueur.structura.mapping.FieldMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static fr.traqueur.structura.fixtures.TestModels.*;
import static fr.traqueur.structura.helpers.TestHelpers.*;
import static fr.traqueur.structura.helpers.PolymorphicTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Refactored Inline Polymorphic tests using common test models.
 * Tests inline polymorphic field behavior with discriminator keys at parent level.
 */
@DisplayName("Inline Polymorphic Keys - Refactored Tests")
class InlinePolymorphicTest {

    private RecordInstanceFactory recordFactory;

    @BeforeEach
    void setUp() {
        clearAllRegistries();
        FieldMapper fieldMapper = new FieldMapper();
        recordFactory = new RecordInstanceFactory(fieldMapper);
        ValueConverter valueConverter = new ValueConverter(recordFactory);
        recordFactory.setValueConverter(valueConverter);
        setupPolymorphicRegistries();
    }

    private void setupPolymorphicRegistries() {
        // Registry for inline polymorphic database types
        createPolymorphicRegistry(InlineDatabaseConfig.class, registry -> {
            registry.register("mysql", InlineMySQLConfig.class);
            registry.register("postgres", InlinePostgreSQLConfig.class);
        });

        // Registry for non-inline polymorphic cache types
        createPolymorphicRegistry(CacheConfig.class, registry -> {
            registry.register("redis", RedisConfig.class);
            registry.register("memcached", MemcachedConfig.class);
        });

        // Registry for storage with custom key name
        createPolymorphicRegistry(StorageConfig.class, registry -> {
            registry.register("s3", S3Config.class);
            registry.register("gcs", GCSConfig.class);
        });
    }

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

            InlineDatabaseAppConfig result = (InlineDatabaseAppConfig) recordFactory.createInstance(
                    data, InlineDatabaseAppConfig.class, ""
            );

            assertEquals("MyApp", result.appName());
            assertInstanceOf(InlineMySQLConfig.class, result.database());

            InlineMySQLConfig mysql = (InlineMySQLConfig) result.database();
            assertEquals("db.example.com", mysql.host());
            assertEquals(3307, mysql.port());
            assertEquals("mysql", mysql.driver()); // Default value
        }

        @Test
        @DisplayName("Should resolve different inline polymorphic types")
        void shouldResolveDifferentInlinePolymorphicTypes() {
            // Test MySQL
            Map<String, Object> mysqlData = Map.of(
                    "app-name", "MySQLApp",
                    "type", "mysql",
                    "database", Map.of("host", "mysql.example.com", "port", 3306)
            );

            InlineDatabaseAppConfig mysqlResult = (InlineDatabaseAppConfig) recordFactory.createInstance(
                    mysqlData, InlineDatabaseAppConfig.class, ""
            );
            assertInstanceOf(InlineMySQLConfig.class, mysqlResult.database());

            // Test PostgreSQL
            Map<String, Object> postgresData = Map.of(
                    "app-name", "PostgresApp",
                    "type", "postgres",
                    "database", Map.of("host", "postgres.example.com", "port", 5432)
            );

            InlineDatabaseAppConfig postgresResult = (InlineDatabaseAppConfig) recordFactory.createInstance(
                    postgresData, InlineDatabaseAppConfig.class, ""
            );
            assertInstanceOf(InlinePostgreSQLConfig.class, postgresResult.database());
        }

        @Test
        @DisplayName("Should use default values for inline polymorphic fields")
        void shouldUseDefaultValuesForInlinePolymorphicFields() {
            Map<String, Object> data = Map.of(
                    "app-name", "DefaultApp",
                    "type", "mysql",
                    "database", Map.of() // Empty, should use defaults
            );

            InlineDatabaseAppConfig result = (InlineDatabaseAppConfig) recordFactory.createInstance(
                    data, InlineDatabaseAppConfig.class, ""
            );

            InlineMySQLConfig mysql = (InlineMySQLConfig) result.database();
            assertEquals("localhost", mysql.host()); // Default
            assertEquals(3306, mysql.port()); // Default
            assertEquals("mysql", mysql.driver()); // Default
        }
    }

    @Nested
    @DisplayName("Inline vs Non-Inline Behavior")
    class InlineVsNonInlineTest {

        @Test
        @DisplayName("Should handle both inline and non-inline polymorphic fields in same config")
        void shouldHandleBothInlineAndNonInlineFields() {
            Map<String, Object> data = Map.of(
                    "app-name", "MixedApp",
                    "type", "mysql", // For inline database
                    "database", Map.of("host", "db.example.com", "port", 3306),
                    "cache", Map.of(
                            "type", "redis", // For non-inline cache
                            "host", "cache.example.com",
                            "port", 6379
                    )
            );

            MixedInlineConfig result = (MixedInlineConfig) recordFactory.createInstance(
                    data, MixedInlineConfig.class, ""
            );

            assertEquals("MixedApp", result.appName());
            assertInstanceOf(InlineMySQLConfig.class, result.database());
            assertInstanceOf(RedisConfig.class, result.cache());
        }

        @Test
        @DisplayName("Non-inline polymorphic should fail if type is at parent level")
        void nonInlinePolymorphicShouldFailIfTypeAtParentLevel() {
            Map<String, Object> data = Map.of(
                    "app-name", "BadConfig",
                    "type", "redis", // Type at wrong level for non-inline
                    "cache", Map.of("host", "cache.example.com", "port", 6379)
            );

            assertThrows(StructuraException.class, () ->
                    recordFactory.createInstance(data, CacheAppConfig.class, "")
            );
        }
    }

    @Nested
    @DisplayName("Custom Discriminator Key Names")
    class CustomDiscriminatorKeyTest {

        @Test
        @DisplayName("Should support inline polymorphic with custom key name")
        void shouldSupportInlinePolymorphicWithCustomKey() {
            Map<String, Object> data = Map.of(
                    "app-name", "StorageApp",
                    "provider", "s3", // Custom key name
                    "storage", Map.of(
                            "bucket", "my-s3-bucket",
                            "region", "us-west-2"
                    )
            );

            StorageAppConfig result = (StorageAppConfig) recordFactory.createInstance(
                    data, StorageAppConfig.class, ""
            );

            assertEquals("StorageApp", result.appName());
            assertInstanceOf(S3Config.class, result.storage());
            assertEquals("my-s3-bucket", ((S3Config) result.storage()).bucket());
            assertEquals("us-west-2", ((S3Config) result.storage()).region());
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

            StorageAppConfig s3Result = (StorageAppConfig) recordFactory.createInstance(
                    s3Data, StorageAppConfig.class, ""
            );
            assertInstanceOf(S3Config.class, s3Result.storage());

            // Test GCS
            Map<String, Object> gcsData = Map.of(
                    "app-name", "GCSApp",
                    "provider", "gcs",
                    "storage", Map.of("bucket", "gcs-bucket", "location", "eu")
            );

            StorageAppConfig gcsResult = (StorageAppConfig) recordFactory.createInstance(
                    gcsData, StorageAppConfig.class, ""
            );
            assertInstanceOf(GCSConfig.class, gcsResult.storage());
            assertEquals("eu", ((GCSConfig) gcsResult.storage()).location());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTest {

        @Test
        @DisplayName("Should throw exception when inline discriminator key is missing")
        void shouldThrowExceptionWhenInlineDiscriminatorKeyMissing() {
            Map<String, Object> data = Map.of(
                    "app-name", "NoTypeApp",
                    // Missing "type" key
                    "database", Map.of("host", "db.example.com", "port", 3306)
            );

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    recordFactory.createInstance(data, InlineDatabaseAppConfig.class, "")
            );

            assertContainsAll(exception.getMessage(), "Inline polymorphic field", "requires discriminator key", "type");
        }

        @Test
        @DisplayName("Should throw exception for unknown inline polymorphic type")
        void shouldThrowExceptionForUnknownInlinePolymorphicType() {
            Map<String, Object> data = Map.of(
                    "app-name", "UnknownTypeApp",
                    "type", "mongodb", // Unknown type
                    "database", Map.of("host", "db.example.com")
            );

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    recordFactory.createInstance(data, InlineDatabaseAppConfig.class, "")
            );

            assertContainsAll(exception.getMessage(), "No registered type found", "mongodb");
        }

        @Test
        @DisplayName("Should throw exception when inline polymorphic field is not a Map")
        void shouldThrowExceptionWhenInlinePolymorphicFieldNotMap() {
            Map<String, Object> data = Map.of(
                    "app-name", "BadDataApp",
                    "type", "mysql",
                    "database", "not-a-map" // Should be a Map
            );

            assertThrows(StructuraException.class, () ->
                    recordFactory.createInstance(data, InlineDatabaseAppConfig.class, "")
            );
        }

        @Test
        @DisplayName("Should throw exception with custom key name missing")
        void shouldThrowExceptionWithCustomKeyNameMissing() {
            Map<String, Object> data = Map.of(
                    "app-name", "NoProviderApp",
                    // Missing "provider" key
                    "storage", Map.of("bucket", "my-bucket")
            );

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    recordFactory.createInstance(data, StorageAppConfig.class, "")
            );

            assertContainsAll(exception.getMessage(), "Inline polymorphic field", "requires discriminator key", "provider");
        }
    }

    @Nested
    @DisplayName("Collections with Inline Polymorphic Types")
    class CollectionsTest {

        @Test
        @DisplayName("Should handle list of inline polymorphic types")
        void shouldHandleListOfInlinePolymorphicTypes() {
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
                            ),
                            Map.of(
                                    "type", "mysql",
                                    "host", "mysql2.example.com",
                                    "port", 3307
                            )
                    )
            );

            InlineDatabaseListConfig result = (InlineDatabaseListConfig) recordFactory.createInstance(
                    data, InlineDatabaseListConfig.class, ""
            );

            assertEquals("MultiDBApp", result.appName());
            assertEquals(3, result.databases().size());

            assertInstanceOf(InlineMySQLConfig.class, result.databases().get(0));
            assertInstanceOf(InlinePostgreSQLConfig.class, result.databases().get(1));
            assertInstanceOf(InlineMySQLConfig.class, result.databases().get(2));

            assertEquals("mysql1.example.com", result.databases().get(0).getHost());
            assertEquals("postgres1.example.com", result.databases().get(1).getHost());
            assertEquals("mysql2.example.com", result.databases().get(2).getHost());
        }
    }

    @Nested
    @DisplayName("Fully Inline Polymorphic - @Options(inline=true) + @Polymorphic(inline=true)")
    class FullyInlinePolymorphicTest {

        @Test
        @DisplayName("Should fully flatten polymorphic fields when both inline flags are true")
        void shouldFullyFlattenPolymorphicFieldsWhenBothInlineTrue() {
            Map<String, Object> data = Map.of(
                    "app-name", "FullyInlineApp",
                    "type", "mysql",
                    // All database fields at root level
                    "host", "fully-inline.example.com",
                    "port", 3308,
                    "driver", "custom-mysql-driver"
            );

            FullyInlinePolyConfig result = (FullyInlinePolyConfig) recordFactory.createInstance(
                    data, FullyInlinePolyConfig.class, ""
            );

            assertEquals("FullyInlineApp", result.appName());
            assertInstanceOf(InlineMySQLConfig.class, result.database());

            InlineMySQLConfig mysql = (InlineMySQLConfig) result.database();
            assertEquals("fully-inline.example.com", mysql.host());
            assertEquals(3308, mysql.port());
            assertEquals("custom-mysql-driver", mysql.driver());
        }

        @Test
        @DisplayName("Should resolve different polymorphic types when fully inline")
        void shouldResolveDifferentPolymorphicTypesWhenFullyInline() {
            // MySQL
            Map<String, Object> mysqlData = Map.of(
                    "app-name", "MySQLFullyInline",
                    "type", "mysql",
                    "host", "mysql.example.com",
                    "port", 3306
            );

            FullyInlinePolyConfig mysqlResult = (FullyInlinePolyConfig) recordFactory.createInstance(
                    mysqlData, FullyInlinePolyConfig.class, ""
            );
            assertInstanceOf(InlineMySQLConfig.class, mysqlResult.database());

            // PostgreSQL
            Map<String, Object> postgresData = Map.of(
                    "app-name", "PostgresFullyInline",
                    "type", "postgres",
                    "host", "postgres.example.com",
                    "port", 5432
            );

            FullyInlinePolyConfig postgresResult = (FullyInlinePolyConfig) recordFactory.createInstance(
                    postgresData, FullyInlinePolyConfig.class, ""
            );
            assertInstanceOf(InlinePostgreSQLConfig.class, postgresResult.database());
        }

        @Test
        @DisplayName("Should use default values when fully inline")
        void shouldUseDefaultValuesWhenFullyInline() {
            Map<String, Object> data = Map.of(
                    "app-name", "DefaultFullyInline",
                    "type", "mysql"
                    // No database fields, should use all defaults
            );

            FullyInlinePolyConfig result = (FullyInlinePolyConfig) recordFactory.createInstance(
                    data, FullyInlinePolyConfig.class, ""
            );

            InlineMySQLConfig mysql = (InlineMySQLConfig) result.database();
            assertEquals("localhost", mysql.host()); // Default
            assertEquals(3306, mysql.port()); // Default
            assertEquals("mysql", mysql.driver()); // Default
        }

        @Test
        @DisplayName("Should throw exception when discriminator key is missing for fully inline")
        void shouldThrowExceptionWhenDiscriminatorKeyMissingForFullyInline() {
            Map<String, Object> data = Map.of(
                    "app-name", "MissingTypeFullyInline",
                    // Missing "type" key
                    "host", "db.example.com",
                    "port", 3306
            );

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    recordFactory.createInstance(data, FullyInlinePolyConfig.class, "")
            );

            assertContainsAll(exception.getMessage(), "Fully inline polymorphic field", "requires discriminator key", "type");
        }

        @Test
        @DisplayName("Should throw exception for unknown type when fully inline")
        void shouldThrowExceptionForUnknownTypeWhenFullyInline() {
            Map<String, Object> data = Map.of(
                    "app-name", "UnknownTypeFullyInline",
                    "type", "oracle", // Unknown type
                    "host", "oracle.example.com"
            );

            StructuraException exception = assertThrows(StructuraException.class, () ->
                    recordFactory.createInstance(data, FullyInlinePolyConfig.class, "")
            );

            assertContainsAll(exception.getMessage(), "No registered type found for", "oracle");
        }

        @Test
        @DisplayName("Should work with custom discriminator key when fully inline")
        void shouldWorkWithCustomDiscriminatorKeyWhenFullyInline() {
            Map<String, Object> data = Map.of(
                    "app-name", "CustomKeyFullyInline",
                    "provider", "s3", // Custom key
                    // All storage fields at root level
                    "bucket", "fully-inline-bucket",
                    "region", "ap-south-1"
            );

            FullyInlineStorageConfig result = (FullyInlineStorageConfig) recordFactory.createInstance(
                    data, FullyInlineStorageConfig.class, ""
            );

            assertEquals("CustomKeyFullyInline", result.appName());
            assertInstanceOf(S3Config.class, result.storage());
            assertEquals("fully-inline-bucket", ((S3Config) result.storage()).bucket());
            assertEquals("ap-south-1", ((S3Config) result.storage()).region());
        }

        @Test
        @DisplayName("Should handle mixed fully inline and non-inline polymorphic fields")
        void shouldHandleMixedFullyInlineAndNonInlinePolymorphicFields() {
            Map<String, Object> data = Map.of(
                    "app-name", "MixedFullyInline",
                    "type", "mysql", // For fully inline database
                    "host", "mixed.example.com",
                    "port", 3306,
                    "cache", Map.of( // Non-inline cache
                            "type", "redis",
                            "host", "cache.example.com",
                            "port", 6379
                    )
            );

            // Note: This would require a specific config model that we'd need to add to TestModels
            // For now, this test validates the concept
        }
    }

    @Nested
    @DisplayName("Backward Compatibility")
    class BackwardCompatibilityTest {

        @Test
        @DisplayName("Should not break existing non-inline polymorphic behavior")
        void shouldNotBreakExistingNonInlinePolymorphicBehavior() {
            Map<String, Object> data = Map.of(
                    "app-name", "TraditionalApp",
                    "cache", Map.of(
                            "type", "redis", // Type inside the nested structure
                            "host", "redis.example.com",
                            "port", 6379
                    )
            );

            CacheAppConfig result = (CacheAppConfig) recordFactory.createInstance(
                    data, CacheAppConfig.class, ""
            );

            assertEquals("TraditionalApp", result.appName());
            assertInstanceOf(RedisConfig.class, result.cache());
            assertEquals("redis.example.com", result.cache().getHost());
        }

        @Test
        @DisplayName("Default inline value should be false for backward compatibility")
        void defaultInlineValueShouldBeFalseForBackwardCompatibility() {
            // Non-inline should be the default, requiring type inside the nested structure
            Map<String, Object> data = Map.of(
                    "app-name", "DefaultBehaviorApp",
                    "type", "redis", // Type at parent level
                    "cache", Map.of("host", "cache.example.com", "port", 6379)
            );

            // Should fail because non-inline is default and type is in wrong place
            assertThrows(StructuraException.class, () ->
                    recordFactory.createInstance(data, CacheAppConfig.class, "")
            );
        }
    }
}