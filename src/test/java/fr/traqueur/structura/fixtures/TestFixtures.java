package fr.traqueur.structura.fixtures;

import java.util.Map;

/**
 * Common YAML fixtures and test data used across multiple test classes.
 * This centralized location eliminates duplication and makes tests more maintainable.
 */
public final class TestFixtures {

    private TestFixtures() {} // Prevent instantiation

    // ==================== Simple YAML Configurations ====================

    public static final String SIMPLE_CONFIG = """
        name: "MyApp"
        port: 8080
        enabled: true
        """;

    public static final String MINIMAL_CONFIG = """
        name: "TestApp"
        """;

    public static final String EMPTY_CONFIG = "{}";

    // ==================== Configurations with Defaults ====================

    public static final String PARTIAL_CONFIG_WITH_DEFAULTS = """
        app-name: "CustomApp"
        server-port: 9000
        """;

    // ==================== Nested Configurations ====================

    public static final String NESTED_CONFIG = """
        app-name: "MyApp"
        database:
          host: "db.example.com"
          port: 5432
          database: "mydb"
          username: "admin"
        server:
          host: "localhost"
          port: 8080
          ssl: true
        """;

    public static final String DEEP_NESTED_CONFIG = """
        name: "MainApp"
        config:
          app-name: "NestedApp"
          database:
            host: "db.local"
            port: 3306
            database: "testdb"
            username: "root"
          server:
            host: "server.local"
            port: 443
            ssl: true
        """;

    // ==================== Collections ====================

    public static final String COLLECTIONS_CONFIG = """
        hosts:
          - "host1.example.com"
          - "host2.example.com"
          - "host3.example.com"
        ports:
          - 8080
          - 8081
          - 8082
        properties:
          key1: "value1"
          key2: "value2"
          key3: "value3"
        """;

    public static final String RECORD_LIST_CONFIG = """
        databases:
          - host: "db1.example.com"
            port: 5432
            database: "db1"
            username: "user1"
          - host: "db2.example.com"
            port: 3306
            database: "db2"
            username: "user2"
        """;

    public static final String RECORD_MAP_CONFIG = """
        servers:
          primary:
            host: "primary.example.com"
            port: 443
            ssl: true
          secondary:
            host: "secondary.example.com"
            port: 8080
            ssl: false
        """;

    // ==================== Custom Names and Options ====================

    public static final String CUSTOM_NAMES_CONFIG = """
        app-name: "CustomApp"
        server-config:
          host: "localhost"
          port: 8080
        """;

    public static final String OPTIONAL_FIELDS_CONFIG = """
        required: "must-have"
        optional: "nice-to-have"
        """;

    public static final String OPTIONAL_FIELDS_MINIMAL = """
        required: "must-have"
        """;

    // ==================== Key Mapping ====================

    public static final String SIMPLE_KEY_RECORD = """
        my-key:
          value-int: 42
          value-double: 3.14
        """;

    public static final String COMPLEX_KEY_CONFIG = """
        host: "server.example.com"
        port: 9000
        protocol: "https"
        app-name: "KeyApp"
        debug-mode: true
        """;

    public static final String KEY_RECORD_LIST_CONFIG = """
        items:
          first:
            value-int: 100
            value-double: 10.5
          second:
            value-int: 200
            value-double: 20.5
          third:
            value-int: 300
            value-double: 30.5
        """;

    // ==================== Inline Fields ====================

    public static final String INLINE_CONFIG = """
        app-name: "InlineApp"
        host: "localhost"
        port: 8080
        """;

    public static final String MULTIPLE_INLINE_CONFIG = """
        host: "primary.local"
        port: 9000
        ssl: true
        """;

    // ==================== Polymorphic Configurations ====================

    public static final String PAYMENT_METHOD_CREDIT_CARD = """
        method:
          type: "credit-card"
          card-number: "1234-5678-9012-3456"
          cvv: "123"
        """;

    public static final String PAYMENT_METHOD_PAYPAL = """
        method:
          type: "pay-pal"
          email: "user@example.com"
        """;

    public static final String INLINE_PAYMENT_METHOD = """
        type: "inline-credit-card"
        card-number: "1234-5678-9012-3456"
        """;

    public static final String FULLY_INLINE_PAYMENT = """
        type: "inline-pay-pal"
        email: "user@example.com"
        """;

    public static final String POLYMORPHIC_LIST = """
        methods:
          - type: "credit-card"
            card-number: "1111-2222-3333-4444"
            cvv: "111"
          - type: "pay-pal"
            email: "user1@example.com"
          - type: "bank-transfer"
            iban: "DE89370400440532013000"
            bic: "COBADEFFXXX"
        """;

    public static final String KEY_AS_DISCRIMINATOR_LIST = """
        databases:
          my-sql:
            host: "mysql1.local"
            port: 3306
          postgre-sql:
            host: "postgres1.local"
            port: 5432
        """;

    public static final String KEY_AS_DISCRIMINATOR_MAP = """
        databases:
          my-sql:
            host: "mysql-primary.local"
          postgre-sql:
            host: "postgres-secondary.local"
        """;

    // ==================== Enum Configurations ====================

    public static final String ENUM_CONFIG = """
        log-level: INFO
        environment: PRODUCTION
        """;

    public static final String ENUM_DATA_FULL = """
        mysql:
          driver: "com.mysql.cj.jdbc.Driver"
          default-port: 3306
        postgresql:
          driver: "org.postgresql.Driver"
          default-port: 5432
        mongo-db-source:
          driver: "mongodb.jdbc.MongoDriver"
          default-port: 27017
        """;

    // ==================== Integration Test Enum Configurations ====================

    public static final String LOADABLE_ENUM_FULL = """
        debug:
          description: "Debug level logging"
          priority: 1
        info:
          description: "Info level logging"
          priority: 2
        warn:
          description: "Warning level logging"
          priority: 3
        error:
          description: "Error level logging"
          priority: 4
        """;

    public static final String LOADABLE_ENUM_PARTIAL = """
        debug:
          description: "Debug from file"
        info:
          priority: 42
        warn: {}
        error:
          description: "Error from file"
          priority: 99
        """;

    public static final String LOADABLE_ENUM_FILE_VARIANT = """
        debug:
          description: "File debug"
          priority: 10
        info:
          description: "File info"
          priority: 20
        warn:
          description: "File warn"
          priority: 30
        error:
          description: "File error"
          priority: 40
        """;

    // ==================== Edge Cases ====================

    public static final String INVALID_YAML = """
        this is not: valid: yaml: syntax
        [unclosed bracket
        """;

    public static final String ALL_TYPES_CONFIG = """
        string-field: "text"
        int-field: 42
        long-field: 9223372036854775807
        double-field: 3.14159
        float-field: 2.718
        boolean-field: true
        byte-field: 127
        short-field: 32767
        char-field: "A"
        """;

    // ==================== Helper Methods ====================

    /**
     * Creates a simple YAML configuration with custom values.
     */
    public static String createSimpleConfig(String name, int port, boolean enabled) {
        return String.format("""
            name: "%s"
            port: %d
            enabled: %b
            """, name, port, enabled);
    }
}
