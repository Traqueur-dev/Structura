# Structura 🏗️

> A modern, type-safe YAML configuration library for Java that leverages records and annotations for seamless configuration management.

## ✨ Features

- 🎯 **Type-safe**: Compile-time safety with Java records
- 🔧 **Annotation-driven**: Flexible configuration with `@Options` and default value annotations
- 🔑 **Key-based mapping**: Flexible YAML structures with `@Options(isKey = true)` for both simple and complex object flattening
- 🏗️ **Nested configurations**: Support for complex, hierarchical settings
- 📋 **Collections support**: Lists, Sets, and Maps with generic type safety
- 🔄 **Enum integration**: Special support for configuration enums
- 🎭 **Polymorphic interfaces**: Automatic type resolution based on YAML keys for plugin systems
- 🔀 **Automatic type conversion**: Smart conversion between compatible types
- 🎨 **Kebab-case mapping**: Automatic camelCase ↔ kebab-case field name conversion
- ⚡ **Zero dependencies**: Only optional SnakeYAML for YAML parsing

## 🚀 Quick Start

### Installation

Add Structura to your project:

```gradle
repository {
    maven { url = "https://jitpack.io" } // JitPack repository for Structura
}

dependencies {
    implementation("com.github.Traqueur-dev:Structura:<VERSION>") // Replace <VERSION> with the latest release
    implementation("org.yaml:snakeyaml:2.4") // Required for YAML parsing
}
```

### Basic Usage

1. **Define your configuration record**:

```java
import fr.traqueur.structura.api.Loadable;

public record AppConfig(
        @DefaultString("MyApp") String appName,
        @DefaultInt(8080) int port,
        @DefaultBool(false) boolean enableSsl,
        DatabaseConfig database
) implements Loadable {
}

public record DatabaseConfig(
        String host,
        @DefaultInt(5432) int port,
        String database,
        @DefaultString("postgres") String username
) implements Loadable {
}
```

2. **Create your YAML configuration**:

```yaml
# config.yml
app-name: "Production App"
port: 9000
enable-ssl: true
database:
  host: "db.production.com"
  port: 5432
  database: "myapp_prod"
  username: "app_user"
```

3. **Load and use your configuration**:

```java
import fr.traqueur.structura.api.Structura;

// Load from file
AppConfig config = Structura.load(Path.of("config.yml"), AppConfig.class);

// Load from resources
AppConfig config = Structura.loadFromResource("/config.yml", AppConfig.class);

// Parse from string
String yamlContent = "app-name: Test\nport: 3000";
AppConfig config = Structura.parse(yamlContent, AppConfig.class);

// Use your configuration
System.out.println("Starting " + config.appName() + " on port " + config.port());
```

## 📚 Advanced Features

### Default Values

Use annotation-based default values for optional configuration:

```java
public record ServerConfig(
    @DefaultString("localhost") String host,
    @DefaultInt(8080) int port,
    @DefaultBool(false) boolean ssl,
    @DefaultLong(30000L) long timeout,
    @DefaultDouble(1.5) double retryMultiplier
) implements Loadable {}
```

### Custom Field Names

Override automatic kebab-case conversion:

```java
public record CustomConfig(
    @Options(name = "app-name") String applicationName,
    @Options(name = "db-config") DatabaseConfig databaseConfiguration
) implements Loadable {}
```

### Key-based Mapping

Use `@Options(isKey = true)` to create flexible YAML structures where keys become field values or where complex objects can be flattened.

#### Simple Key Mapping

For simple types (String, int, etc.), the YAML key becomes the field value:

```java
public record DatabaseConnection(
    @Options(isKey = true) String name,
    String host,
    @DefaultInt(5432) int port
) implements Loadable {}
```

```yaml
# The key "production" becomes the value of the "name" field
production:
  host: "prod.db.example.com"
  port: 5432
```

```java
DatabaseConnection config = Structura.parse(yaml, DatabaseConnection.class);
// config.name() returns "production"
// config.host() returns "prod.db.example.com"
// config.port() returns 5432
```

#### Complex Object Flattening

For complex types (records implementing Loadable), fields are flattened to the same level:

```java
public record ServerInfo(
    String host,
    @DefaultInt(8080) int port,
    @DefaultString("http") String protocol
) implements Loadable {}

public record AppConfig(
    @Options(isKey = true) ServerInfo server,  // Complex object as key
    String appName,
    @DefaultBool(false) boolean debugMode
) implements Loadable {}
```

```yaml
# Flattened structure - server fields at root level
host: "api.example.com"
port: 9000
protocol: "https"
app-name: "MyApp"
debug-mode: true
```

```java
AppConfig config = Structura.parse(yaml, AppConfig.class);
// config.server().host() returns "api.example.com"
// config.server().port() returns 9000
// config.server().protocol() returns "https"
// config.appName() returns "MyApp"
// config.debugMode() returns true
```

### Polymorphic Interfaces 🎭

**NEW!** Structura supports polymorphic configuration through interfaces and registries, perfect for plugin systems, database drivers, or any scenario where you need different implementations based on YAML content.

#### Setting Up Polymorphic Interfaces

1. **Define your polymorphic interface**:

```java
@Polymorphic(key = "type")  // Field name that determines the implementation
public interface DatabaseConfig extends Loadable {
    String getHost();
    int getPort();
}
```

2. **Create concrete implementations**:

```java
public record MySQLConfig(
    @DefaultString("localhost") String host,
    @DefaultInt(3306) int port,
    @DefaultString("mysql") String driver,
    @DefaultBool(true) boolean useSSL
) implements DatabaseConfig {
    @Override public String getHost() { return host; }
    @Override public int getPort() { return port; }
}

public record PostgreSQLConfig(
    @DefaultString("localhost") String host,
    @DefaultInt(5432) int port,
    @DefaultString("postgresql") String driver,
    @DefaultBool(false) boolean ssl
) implements DatabaseConfig {
    @Override public String getHost() { return host; }
    @Override public int getPort() { return port; }
}

public record MongoConfig(
    @DefaultString("localhost") String host,
    @DefaultInt(27017) int port,
    @DefaultString("admin") String authDatabase
) implements DatabaseConfig {
    @Override public String getHost() { return host; }
    @Override public int getPort() { return port; }
}
```

3. **Register implementations**:

```java
// One-time setup (typically in a static block or startup code)
PolymorphicRegistry.create(DatabaseConfig.class, registry -> {
    registry.register("mysql", MySQLConfig.class);
    registry.register("postgresql", PostgreSQLConfig.class);
    registry.register("mongodb", MongoConfig.class);
});
```

4. **Use in your configuration**:

```java
public record AppConfig(
    String appName,
    DatabaseConfig database,              // Polymorphic interface
    List<DatabaseConfig> backupDatabases  // Collections work too!
) implements Loadable {}
```

#### YAML Configuration

```yaml
app-name: "MyApp"
database:
  type: "mysql"              # This determines the implementation
  host: "prod.mysql.com"
  port: 3306
  use-ssl: true
  driver: "com.mysql.cj.jdbc.Driver"

backup-databases:
  - type: "postgresql"       # Different implementation
    host: "backup.postgres.com"
    port: 5432
    ssl: true
  - type: "mongodb"          # Another implementation
    host: "backup.mongo.com"
    port: 27017
    auth-database: "backup"
```

#### Automatic Type Resolution

```java
AppConfig config = Structura.parse(yaml, AppConfig.class);

// config.database() is automatically a MySQLConfig instance
MySQLConfig mysql = (MySQLConfig) config.database();
System.out.println("MySQL driver: " + mysql.driver());

// config.backupDatabases() contains PostgreSQLConfig and MongoConfig instances
PostgreSQLConfig postgres = (PostgreSQLConfig) config.backupDatabases().get(0);
MongoConfig mongo = (MongoConfig) config.backupDatabases().get(1);
```

#### Advanced Polymorphic Features

- **Custom key names**: Use `@Polymorphic(key = "provider")` for different field names
- **Auto-naming**: `registry.register(MySQLConfig.class)` uses lowercased class name
- **Type safety**: Compile-time guarantees that implementations match the interface
- **Error handling**: Clear messages showing available types when resolution fails
- **Default values**: All `@Default*` annotations work in polymorphic implementations

#### Use Cases

- **Database drivers**: Switch between MySQL, PostgreSQL, MongoDB based on configuration
- **Payment providers**: Different implementations for Stripe, PayPal, etc.
- **Logging backends**: File, console, remote logging with different configurations
- **Plugin systems**: Load different plugin implementations based on type
- **Cloud providers**: AWS, Azure, GCP with provider-specific settings

### Optional Fields

Mark fields as optional to avoid exceptions when missing:

```java
public record FlexibleConfig(
    String required,
    @Options(optional = true) String optional,
    @Options(optional = true) @DefaultString("fallback") String optionalWithDefault
) implements Loadable {}
```

### Collections

Structura supports all common collection types:

```yaml
# collections.yml
servers:
  - "server1.example.com"
  - "server2.example.com"
ports:
  - 8080
  - 8443
  - 9000
environment-vars:
  JAVA_HOME: "/usr/lib/jvm/java-21"
  PATH: "/usr/local/bin:/usr/bin"
database-configs:
  - host: "primary.db"
    port: 5432
  - host: "secondary.db"
    port: 5432
```

```java
public record ClusterConfig(
    List<String> servers,
    Set<Integer> ports,
    Map<String, String> environmentVars,
    List<DatabaseConfig> databaseConfigs
) implements Loadable {}
```

### Configuration Enums

Create enums that can be populated with configuration data:

```java
public enum DatabaseType implements Loadable {
    MYSQL, POSTGRESQL, MONGODB;
    
    @Options(optional = true) public String driver;
    @DefaultInt(0) public int defaultPort;
    @Options(optional = true) public Map<String, String> properties;
}
```

```yaml
# database-types.yml
mysql:
  driver: "com.mysql.cj.jdbc.Driver"
  default-port: 3306
  properties:
    useSSL: "true"
    serverTimezone: "UTC"
postgresql:
  driver: "org.postgresql.Driver"
  default-port: 5432
  properties:
    ssl: "false"
```

```java
// Populate enum constants
Structura.parseEnum(yamlContent, DatabaseType.class);

// Use populated enum
String mysqlDriver = DatabaseType.MYSQL.driver;
int postgresPort = DatabaseType.POSTGRESQL.defaultPort;
```

## 🎯 Field Name Mapping

Structura automatically converts between Java camelCase and YAML kebab-case:

| Java Field | YAML Key |
|------------|----------|
| `appName` | `app-name` |
| `databaseUrl` | `database-url` |
| `enableHttps` | `enable-https` |
| `maxRetryCount` | `max-retry-count` |

Override this behavior with `@Options(name = "custom-name")`.

## 🔧 Configuration Examples

### Complete Application Configuration

```java
public record ApplicationConfig(
    @DefaultString("MyApplication") String appName,
    @DefaultString("1.0.0") String version,
    ServerConfig server,
    DatabaseConfig database,      // Polymorphic interface
    @Options(optional = true) List<String> features,
    @Options(optional = true) Map<String, String> customProperties
) implements Loadable {}

public record ServerConfig(
    @DefaultString("localhost") String host,
    @DefaultInt(8080) int port,
    @DefaultBool(false) boolean enableSsl,
    @DefaultString("/") String contextPath
) implements Loadable {}

// DatabaseConfig is the polymorphic interface from examples above
```

```yaml
# application.yml
app-name: "Production Service"
version: "2.1.0"
server:
  host: "0.0.0.0"
  port: 9090
  enable-ssl: true
  context-path: "/api/v1"
database:
  type: "postgresql"  # Polymorphic resolution
  host: "db.example.com"
  port: 5432
  database: "myapp"
  username: "app_user"
  password: "secure_password"
features:
  - "feature-a"
  - "feature-b"
  - "experimental-feature"
custom-properties:
  cache.ttl: "3600"
  logging.level: "INFO"
```

### Plugin System Example

```java
@Polymorphic(key = "provider")
public interface PaymentProvider extends Loadable {
    String getName();
    boolean processPayment(BigDecimal amount);
}

public record StripeProvider(
    @DefaultString("Stripe") String name,
    String apiKey,
    @DefaultBool(false) boolean testMode
) implements PaymentProvider {
    @Override public String getName() { return name; }
    @Override public boolean processPayment(BigDecimal amount) { /* implementation */ }
}

public record PayPalProvider(
    @DefaultString("PayPal") String name,
    String clientId,
    String clientSecret
) implements PaymentProvider {
    @Override public String getName() { return name; }
    @Override public boolean processPayment(BigDecimal amount) { /* implementation */ }
}

// Setup
PolymorphicRegistry.create(PaymentProvider.class, registry -> {
    registry.register("stripe", StripeProvider.class);
    registry.register("paypal", PayPalProvider.class);
});

// Configuration
public record PaymentConfig(
    PaymentProvider primaryProvider,
    List<PaymentProvider> fallbackProviders
) implements Loadable {}
```

```yaml
# payment.yml
primary-provider:
  provider: "stripe"
  api-key: "sk_live_..."
  test-mode: false

fallback-providers:
  - provider: "paypal"
    client-id: "paypal_client_..."
    client-secret: "paypal_secret_..."
  - provider: "stripe"
    api-key: "sk_test_..."
    test-mode: true
```

## 🎮 API Reference

### Main API Class: `Structura`

```java
// Parse from string
<T extends Loadable> T parse(String yamlContent, Class<T> configClass)

// Load from file path
<T extends Loadable> T load(Path filePath, Class<T> configClass)

// Load from File object
<T extends Loadable> T load(File file, Class<T> configClass)

// Load from resources
<T extends Loadable> T loadFromResource(String resourcePath, Class<T> configClass)

// Parse enum configuration
<E extends Enum<E> & Loadable> void parseEnum(String yamlContent, Class<E> enumClass)

// Load enum from file
<E extends Enum<E> & Loadable> void loadEnum(Path filePath, Class<E> enumClass)

// Load enum from resources
<E extends Enum<E> & Loadable> void loadEnumFromResource(String resourcePath, Class<E> enumClass)
```

### Polymorphic Registry API

```java
// Create and configure a registry
PolymorphicRegistry.create(InterfaceClass.class, registry -> {
    registry.register("key", ImplementationClass.class);
    registry.register(AutoNamedClass.class); // Uses lowercased class name
});

// Retrieve an existing registry
PolymorphicRegistry<InterfaceClass> registry = PolymorphicRegistry.get(InterfaceClass.class);

// Check available implementations
Set<String> availableTypes = registry.availableNames();
Optional<Class<? extends InterfaceClass>> impl = registry.get("key");
```

### Annotations

#### `@Polymorphic`
```java
@Polymorphic(key = "type")  // Default: "type"
```

#### `@Options`
```java
@Options(
        name = "custom-field-name",    // Override field name
        optional = true,               // Mark as optional
        isKey = true                   // Use for key-based mapping
)
```

#### Default Value Annotations
```java
@DefaultString("default value")
@DefaultInt(42)
@DefaultLong(1000L)
@DefaultDouble(3.14)
@DefaultBool(true)
```

## 🚨 Error Handling

Structura provides clear error messages for common configuration issues:

```java
try {
    AppConfig config = Structura.load(configPath, AppConfig.class);
} catch (StructuraException e) {
    // Handle configuration errors
    logger.error("Configuration error: " + e.getMessage());
}
```

Common error scenarios:
- Missing required fields
- Type conversion failures
- Invalid YAML syntax
- File not found
- Invalid enum values
- **Unknown polymorphic types with available options listed**
- **Missing polymorphic type keys**

## 🧪 Testing

Structura is extensively tested with JUnit 5. Run tests with:

```bash
./gradlew test
```

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📋 Requirements

- Java 21 or higher
- Gradle 8.10 or higher
- SnakeYAML 2.4 (for YAML parsing)