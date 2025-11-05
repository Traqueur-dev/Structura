package fr.traqueur.structura.fixtures;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.annotations.defaults.*;
import fr.traqueur.structura.annotations.validation.*;
import fr.traqueur.structura.api.Loadable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Common test models used across multiple test classes.
 * This centralized location removes duplication and makes tests more maintainable.
 */
public final class TestModels {

    private TestModels() {} // Prevent instantiation

    // ==================== Simple Records ====================

    public record SimpleConfig(
        String name,
        int port,
        boolean enabled
    ) implements Loadable {}

    public record MinimalConfig(
        String name
    ) implements Loadable {}

    // ==================== Records with Defaults ====================

    public record ConfigWithDefaults(
        @DefaultString("default-app") String appName,
        @DefaultInt(8080) int serverPort,
        @DefaultBool(true) boolean debugMode,
        @DefaultLong(30000L) long timeout,
        @DefaultDouble(1.5) double multiplier
    ) implements Loadable {}

    public record ServerConfig(
        String host,
        @DefaultInt(8080) int port,
        @DefaultBool(false) boolean ssl
    ) implements Loadable {}

    public record DatabaseConfig(
        String host,
        @DefaultInt(5432) int port,
        String database,
        @DefaultString("postgres") String username,
        @Options(optional = true) String password
    ) implements Loadable {}

    // ==================== Nested Records ====================

    public record NestedConfig(
        String appName,
        DatabaseConfig database,
        ServerConfig server
    ) implements Loadable {}

    public record DeepNestedConfig(
        String name,
        NestedConfig config
    ) implements Loadable {}

    // ==================== Collections ====================

    public record CollectionsConfig(
        List<String> hosts,
        Set<Integer> ports,
        Map<String, String> properties
    ) implements Loadable {}

    public record RecordListConfig(
        List<DatabaseConfig> databases
    ) implements Loadable {}

    public record RecordMapConfig(
        Map<String, ServerConfig> servers
    ) implements Loadable {}

    // ==================== Custom Options ====================

    public record ConfigWithCustomNames(
        @Options(name = "app-name") String applicationName,
        @Options(name = "server-config") ServerConfig serverConfig
    ) implements Loadable {}

    public record ConfigWithOptionalFields(
        String required,
        @Options(optional = true) String optional,
        @Options(optional = true) @DefaultString("fallback") String optionalWithDefault
    ) implements Loadable {}

    // ==================== Key Mapping ====================

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

    public record ServerInfoWithDefaults(
        @DefaultString("localhost") String host,
        @DefaultInt(8080) int port,
        @DefaultString("http") String protocol
    ) implements Loadable {}

    public record ComplexKeyConfig(
        @Options(isKey = true) NestedKeyRecord server,
        String appName,
        @DefaultBool(false) boolean debugMode
    ) implements Loadable {}

    // ==================== Inline Fields ====================

    public record ConnectionInfo(
        String host,
        @DefaultInt(8080) int port
    ) implements Loadable {}

    public record InlineConfig(
        String appName,
        @Options(inline = true) ConnectionInfo connection
    ) implements Loadable {}

    public record MultipleInlineConfig(
        @Options(inline = true) ConnectionInfo primary,
        @Options(inline = true) ServerConfig secondary
    ) implements Loadable {}

    // More inline field test models
    public record AuthInfo(
        @DefaultString("user") String username,
        @DefaultString("password") String password
    ) implements Loadable {}

    public record AppConfigWithInline(
        String appName,
        @Options(inline = true) ServerInfoWithDefaults server,
        @DefaultBool(false) boolean debugMode
    ) implements Loadable {}

    public record AppConfigWithMultipleInline(
        String appName,
        @Options(inline = true) ServerInfoWithDefaults server,
        @Options(inline = true) DatabaseConfigWithDefaults database
    ) implements Loadable {}

    public record AppConfigMixed(
        String appName,
        @Options(inline = true) ServerInfoWithDefaults server,
        DatabaseConfig database  // Not inline
    ) implements Loadable {}

    public record DatabaseConfigWithDefaults(
        @DefaultString("localhost") String host,
        @DefaultInt(5432) int port,
        String database,
        @DefaultString("postgres") String username,
        @Options(optional = true) String password
    ) implements Loadable {}

    public record NestedInlineConfig(
        String appName,
        @Options(inline = true) AppConfigWithInline nested
    ) implements Loadable {}

    // Edge case inline models
    public record InlineNestedConfig(
        String name,
        @Options(inline = true) AuthInfo auth
    ) implements Loadable {}

    public record OuterInlineConfig(
        @Options(inline = true) InlineNestedConfig nested,
        @DefaultString("outer") String level
    ) implements Loadable {}

    public record AllDefaultsInlineConfig(
        @DefaultString("DefaultApp") String appName,
        @Options(inline = true) ServerInfoWithDefaults server
    ) implements Loadable {}

    public record StrictServerInfo(
        String host,  // No default - required
        @DefaultInt(8080) int port
    ) implements Loadable {}

    public record StrictInlineConfig(
        String appName,
        @Options(inline = true) StrictServerInfo server
    ) implements Loadable {}

    public record InvalidInlineConfig(
        String appName,
        @Options(inline = true) String value  // String is not a record
    ) implements Loadable {}

    public record TraditionalNestedConfig(
        String appName,
        NestedKeyRecord server  // No @Options(inline = true)
    ) implements Loadable {}

    public record NoOptionsNestedConfig(
        String appName,
        NestedKeyRecord server  // No @Options
    ) implements Loadable {}

    // ==================== Polymorphic Interfaces ====================

    @Polymorphic(key = "type")
    public interface PaymentMethod extends Loadable {}

    public record CreditCard(
        String cardNumber,
        String cvv
    ) implements PaymentMethod {}

    public record PayPal(
        String email
    ) implements PaymentMethod {}

    public record BankTransfer(
        String iban,
        String bic
    ) implements PaymentMethod {}

    @Polymorphic(key = "type", inline = true)
    public interface InlinePaymentMethod extends Loadable {}

    public record InlineCreditCard(
        String cardNumber
    ) implements InlinePaymentMethod {}

    public record InlinePayPal(
        String email
    ) implements InlinePaymentMethod {}

    // Inline polymorphic database configs
    @Polymorphic(key = "type", inline = true)
    public interface InlineDatabaseConfig extends Loadable {
        String getHost();
        int getPort();
    }

    public record InlineMySQLConfig(
        @DefaultString("localhost") String host,
        @DefaultInt(3306) int port,
        @DefaultString("mysql") String driver
    ) implements InlineDatabaseConfig {
        @Override public String getHost() { return host; }
        @Override public int getPort() { return port; }
    }

    public record InlinePostgreSQLConfig(
        @DefaultString("localhost") String host,
        @DefaultInt(5432) int port,
        @DefaultString("postgresql") String driver
    ) implements InlineDatabaseConfig {
        @Override public String getHost() { return host; }
        @Override public int getPort() { return port; }
    }

    // Non-inline polymorphic cache configs
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

    // Inline polymorphic storage with custom key
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

    @Polymorphic(key = "db-type", useKey = true)
    public interface KeyAsDiscriminatorDB extends Loadable {}

    public record MySQL(
        String host,
        @DefaultInt(3306) int port
    ) implements KeyAsDiscriminatorDB {}

    public record PostgreSQL(
        String host,
        @DefaultInt(5432) int port
    ) implements KeyAsDiscriminatorDB {}

    // ==================== Polymorphic Configurations ====================

    public record PaymentConfig(
        PaymentMethod method
    ) implements Loadable {}

    public record InlinePaymentConfig(
        @Options(inline = true) InlinePaymentMethod method
    ) implements Loadable {}

    public record PolymorphicListConfig(
        List<PaymentMethod> methods
    ) implements Loadable {}

    public record PolymorphicMapConfig(
        Map<String, PaymentMethod> methodsByName
    ) implements Loadable {}

    public record KeyDiscriminatorConfig(
        KeyAsDiscriminatorDB database
    ) implements Loadable {}

    public record KeyDiscriminatorListConfig(
        List<KeyAsDiscriminatorDB> databases
    ) implements Loadable {}

    public record KeyDiscriminatorMapConfig(
        Map<String, KeyAsDiscriminatorDB> databases
    ) implements Loadable {}

    // Inline polymorphic configurations
    public record InlineDatabaseAppConfig(
        String appName,
        InlineDatabaseConfig database
    ) implements Loadable {}

    public record ComplexInlineConfig(
        String appName,
        InlineDatabaseConfig database,
        CacheConfig cache,
        StorageConfig storage
    ) implements Loadable {}

    public record InlineDatabaseListConfig(
        String appName,
        List<InlineDatabaseConfig> databases
    ) implements Loadable {}

    public record CacheAppConfig(
        String appName,
        CacheConfig cache
    ) implements Loadable {}

    public record StorageAppConfig(
        String appName,
        StorageConfig storage
    ) implements Loadable {}

    public record MixedInlineConfig(
        String appName,
        InlineDatabaseConfig database,
        CacheConfig cache
    ) implements Loadable {}

    public record FullyInlinePolyConfig(
        String appName,
        @Options(inline = true) InlineDatabaseConfig database
    ) implements Loadable {}

    public record FullyInlineStorageConfig(
        String appName,
        @Options(inline = true) StorageConfig storage
    ) implements Loadable {}

    // ==================== Enums ====================

    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    public enum Environment {
        DEVELOPMENT, STAGING, PRODUCTION
    }

    public enum DatabaseType implements Loadable {
        MYSQL, POSTGRESQL, MONGO_DB_SOURCE;

        @Options(optional = true)
        public String driver;

        @DefaultInt(0)
        public int defaultPort;

        @Options(optional = true)
        public Map<String, String> properties;
    }

    // Enum for integration tests (has fields for loading)
    public enum LoadableLogLevel implements Loadable {
        DEBUG, INFO, WARN, ERROR;

        @DefaultString("unknown")
        public String description;

        @DefaultInt(0)
        public int priority;
    }

    public record ConfigWithEnums(
        LogLevel logLevel,
        Environment environment
    ) implements Loadable {}

    // ==================== Edge Cases ====================

    public record EmptyConfig() implements Loadable {}

    public record AllTypesConfig(
        String stringField,
        int intField,
        long longField,
        double doubleField,
        float floatField,
        boolean booleanField,
        byte byteField,
        short shortField,
        char charField
    ) implements Loadable {}

    // Simple record for converter tests
    public record SimpleRecord(String name, int value) implements Loadable {}

    // Simple test enum
    public enum TestEnum { VALUE1, VALUE2, VALUE3 }

    // ==================== Validation Test Models ====================

    public record ValidatedRecord(
        @Min(value = 0, message = "Age must be non-negative") int age,
        @Max(value = 100, message = "Age must not exceed 100") int maxAge,
        @Size(min = 2, max = 20, message = "Name must be between 2 and 20 characters") String name,
        @NotEmpty(message = "Description cannot be empty") String description,
        @Pattern(value = "^[A-Z]{2,3}$", message = "Code must be 2-3 uppercase letters") String code
    ) implements Loadable {}

    public record NestedValidatedRecord(
        @NotEmpty String parentName,
        ValidatedRecord child
    ) implements Loadable {}

    public record CollectionSizeRecord(
        @Size(min = 1, max = 3) List<String> items,
        @Size(min = 2, max = 5) Map<String, String> properties
    ) implements Loadable {}

    public record CollectionNotEmptyRecord(
        @NotEmpty List<String> items,
        @NotEmpty Map<String, String> properties
    ) implements Loadable {}

    public record OptionalValidationRecord(
        @Min(0) Integer optionalValue,
        @NotEmpty String requiredValue
    ) implements Loadable {}

    public record ArrayValidationRecord(
        @Size(min = 2, max = 5) String[] items
    ) implements Loadable {}

    public record DefaultMessageRecord(
        @Min(5) int value
    ) implements Loadable {}

    public record NonNumericValidationRecord(
        @Min(5) String value
    ) implements Loadable {}

    public record UnsupportedSizeValidationRecord(
        @Size(min = 1) Object unsupported
    ) implements Loadable {}

    public enum ValidatedEnum implements Loadable {
        TEST_VALUE;

        @Min(10) public int minValue;
        @Max(100) public int maxValue;
        @NotEmpty public String name;
    }

    // Key discriminator interface (useKey without inline)
    @Polymorphic(useKey = true)
    public interface ItemMetadata extends Loadable {}

    public record FoodMetadata(int nutrition, double saturation) implements ItemMetadata {}

    public record PotionMetadata(String color, String basePotionType) implements ItemMetadata {}

    public record TrimMetadata(String material, String pattern) implements ItemMetadata {}

    public record LeatherArmorMetadata(String color) implements ItemMetadata {}

    // Key discriminator configs
    public record SimpleFieldConfig(ItemMetadata trim) implements Loadable {}

    public record ConfigWithFood(ItemMetadata food) implements Loadable {}

    public record ListFieldConfig(List<ItemMetadata> metadata) implements Loadable {}

    public record MapFieldConfig(Map<String, ItemMetadata> metadata) implements Loadable {}

    public record ComplexKeyDiscriminatorConfig(
        ItemMetadata trim,
        List<ItemMetadata> items,
        Map<String, ItemMetadata> namedItems
    ) implements Loadable {}

    // Key discriminator WITH inline (for validation error testing)
    @Polymorphic(useKey = true, inline = true)
    public interface ItemMetadataInline extends Loadable {}

    public record FoodMetadataInline(int nutrition, double saturation) implements ItemMetadataInline {}

    public record SimpleFieldConfigInline(ItemMetadataInline trim) implements Loadable {}

    // ==================== ValueConverter Test Models ====================

    @Polymorphic(key = "type")
    public interface TestDatabaseConfig extends Loadable {
        String getHost();
        int getPort();
    }

    @Polymorphic(key = "provider")
    public interface TestPaymentProvider extends Loadable {
        String getName();
        boolean isEnabled();
    }

    public record TestMySQLConfig(
            @DefaultString("localhost") String host,
            @DefaultInt(3306) int port,
            @DefaultString("mysql") String driver
    ) implements TestDatabaseConfig {
        @Override public String getHost() { return host; }
        @Override public int getPort() { return port; }
    }

    public record TestPostgreSQLConfig(
            @DefaultString("localhost") String host,
            @DefaultInt(5432) int port,
            @DefaultString("postgresql") String driver
    ) implements TestDatabaseConfig {
        @Override public String getHost() { return host; }
        @Override public int getPort() { return port; }
    }

    public record TestStripeProvider(
            @DefaultString("Stripe") String name,
            @DefaultBool(true) boolean enabled,
            @DefaultString("sk_test_") String apiKey
    ) implements TestPaymentProvider {
        @Override public String getName() { return name; }
        @Override public boolean isEnabled() { return enabled; }
    }

    public record TestPayPalProvider(
            @DefaultString("PayPal") String name,
            @DefaultBool(false) boolean enabled,
            @DefaultString("client_id_") String clientId
    ) implements TestPaymentProvider {
        @Override public String getName() { return name; }
        @Override public boolean isEnabled() { return enabled; }
    }

    public record TestConfigWithPolymorphic(
            String appName,
            TestDatabaseConfig database,
            List<TestDatabaseConfig> backupDatabases,
            TestPaymentProvider paymentProvider
    ) implements Loadable {}

    // ==================== CustomReaderIntegration Test Models ====================

    // Mock Adventure API classes
    public interface Component {
        String asPlainText();
    }

    public static class TextComponent implements Component {
        private final String text;
        private final String color;

        public TextComponent(String text, String color) {
            this.text = text;
            this.color = color;
        }

        @Override
        public String asPlainText() {
            return text;
        }

        public String getColor() {
            return color;
        }

        @Override
        public String toString() {
            return "TextComponent{text='" + text + "', color='" + color + "'}";
        }
    }

    public static class MiniMessage {
        private static final MiniMessage INSTANCE = new MiniMessage();

        public static MiniMessage miniMessage() {
            return INSTANCE;
        }

        public Component deserialize(String input) {
            if (input.contains("<green>")) {
                String text = input.replace("<green>", "").replace("</green>", "");
                return new TextComponent(text, "green");
            } else if (input.contains("<red>")) {
                String text = input.replace("<red>", "").replace("</red>", "");
                return new TextComponent(text, "red");
            } else if (input.contains("<blue>")) {
                String text = input.replace("<blue>", "").replace("</blue>", "");
                return new TextComponent(text, "blue");
            } else {
                return new TextComponent(input, "white");
            }
        }
    }

    public record MessageConfig(Component welcomeMessage, Component errorMessage) implements Loadable {}
    public record ChatConfig(Component prefix, Component suffix, String playerName) implements Loadable {}
    public record MessagesConfig(List<Component> announcements, Map<String, Component> customMessages) implements Loadable {}
    public record CustomReaderSimpleConfig(String message) implements Loadable {}
    public record ServerConfigWithComponents(String serverName, int maxPlayers, Component motd, Component shutdownMessage, List<Component> rules) implements Loadable {}
    public record ChatFormat(Component prefix, Component suffix) implements Loadable {}
    public record PlayerConfig(String playerName, ChatFormat chatFormat) implements Loadable {}

    // ==================== GenericTypeReader Test Models ====================

    public record Box<T>(T value) {
        @Override
        public String toString() {
            return "Box(" + value + ")";
        }
    }

    public record StringBox(String value) {}
    public record IntBox(int value) {}

    public interface Container<T> {
        T value();
    }

    public record StringContainer(String value) implements Container<String> {
        @Override
        public String toString() {
            return "StringContainer{value='" + value + "'}";
        }
    }

    public record GenericCollectionConfig(List<Container<String>> containers) implements Loadable {}

    // ==================== ConcreteTypeSection Test Models ====================

    public record FoodItem(int nutrition, double saturation) implements Loadable {}
    public record ServerConfigSimple(String host, int port) implements Loadable {}

    public record ConfigWithFoodList(List<FoodItem> items) implements Loadable {}
    public record ConfigWithServerList(List<ServerConfigSimple> servers) implements Loadable {}

    public record ConfigWithFoodMap(Map<String, FoodItem> items) implements Loadable {}
    public record ConfigWithServerMap(Map<String, ServerConfigSimple> servers) implements Loadable {}
}
