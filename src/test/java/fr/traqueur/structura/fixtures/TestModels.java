package fr.traqueur.structura.fixtures;

import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.annotations.defaults.*;
import fr.traqueur.structura.api.Loadable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Common test models used across multiple test classes.
 * This centralized location eliminates duplication and makes tests more maintainable.
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

    public record ConfigWithEnums(
        LogLevel logLevel,
        Environment environment
    ) implements Loadable {}

    public record EnumListConfig(
        List<DatabaseType> supportedDatabases
    ) implements Loadable {}

    // ==================== Edge Cases ====================

    public record EmptyConfig() implements Loadable {}

    public record SingleFieldConfig(String value) implements Loadable {}

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
}
