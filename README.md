# Structura 🏗️

> A modern, type-safe YAML configuration library for Java that leverages records and annotations for seamless configuration management.

## ✨ Features

- 🎯 **Type-safe**: Compile-time safety with Java records
- 🔧 **Annotation-driven**: Flexible configuration with `@Options` and default value annotations
- 🔑 **Key-based mapping**: Flexible YAML structures with `@Options(isKey = true)` for both simple and complex object flattening
- 🏗️ **Nested configurations**: Support for complex, hierarchical settings
- 📋 **Collections support**: Lists, Sets, and Maps with generic type safety
- 🔄 **Enum integration**: Special support for configuration enums
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
) implements Settings {
}

public record DatabaseConfig(
        String host,
        @DefaultInt(5432) int port,
        String database,
        @DefaultString("postgres") String username
) implements Settings {
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
) implements Settings {}
```

### Custom Field Names

Override automatic kebab-case conversion:

```java
public record CustomConfig(
    @Options(name = "app-name") String applicationName,
    @Options(name = "db-config") DatabaseConfig databaseConfiguration
) implements Settings {}
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

#### Recursive Key Mapping

Key mapping works at any nesting level:

```yaml
app-config:
  database:
    postgres-prod:           # Simple key mapping
      host: "db.prod.com"
      port: 5432
  server:
    host: "api.prod.com"     # Complex object flattening
    port: 8443
    service-name: "UserAPI"
```

```java
public record DatabaseConfig(
    @Options(isKey = true) String dbName,  // Simple key
    String host,
    @DefaultInt(5432) int port
) implements Loadable {}

public record ServerInfo(
    String host,
    @DefaultInt(8080) int port
) implements Loadable {}

public record ServiceConfig(
    @Options(isKey = true) ServerInfo server,  // Complex key
    String serviceName
) implements Loadable {}

public record AppConfig(
    DatabaseConfig database,
    ServiceConfig server
) implements Loadable {}
```

#### Rules and Behavior

- **Simple types with `isKey`**: Requires exactly one key at that level; the key becomes the field value
- **Complex types with `isKey`**: Fields of the complex type are extracted from the same level as other fields
- **Works recursively**: Each nesting level can have its own key mappings
- **Compatible with defaults**: Key fields can use default value annotations
- **Type safety**: Automatic conversion between YAML keys and field types

### Optional Fields

Mark fields as optional to avoid exceptions when missing:

```java
public record FlexibleConfig(
    String required,
    @Options(optional = true) String optional,
    @Options(optional = true) @DefaultString("fallback") String optionalWithDefault
) implements Settings {}
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
) implements Settings {}
```

### Configuration Enums

Create enums that can be populated with configuration data:

```java
public enum DatabaseType implements Settings {
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
    DatabaseConfig database,
    @Options(optional = true) List<String> features,
    @Options(optional = true) Map<String, String> customProperties
) implements Settings {}

public record ServerConfig(
    @DefaultString("localhost") String host,
    @DefaultInt(8080) int port,
    @DefaultBool(false) boolean enableSsl,
    @DefaultString("/") String contextPath
) implements Settings {}

public record DatabaseConfig(
    String url,
    String username,
    @Options(optional = true) String password,
    @DefaultInt(10) int maxConnections,
    @DefaultLong(5000L) long connectionTimeout
) implements Settings {}
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
  url: "jdbc:postgresql://db.example.com:5432/myapp"
  username: "app_user"
  password: "secure_password"
  max-connections: 20
  connection-timeout: 10000
features:
  - "feature-a"
  - "feature-b"
  - "experimental-feature"
custom-properties:
  cache.ttl: "3600"
  logging.level: "INFO"
```

## 🎮 API Reference

### Main API Class: `Structura`

```java
// Parse from string
<T extends Settings> T parse(String yamlContent, Class<T> configClass)

// Load from file path
<T extends Settings> T load(Path filePath, Class<T> configClass)

// Load from File object
<T extends Settings> T load(File file, Class<T> configClass)

// Load from resources
<T extends Settings> T loadFromResource(String resourcePath, Class<T> configClass)

// Parse enum configuration
<E extends Enum<E> & Settings> void parseEnum(String yamlContent, Class<E> enumClass)

// Load enum from file
<E extends Enum<E> & Settings> void loadEnum(Path filePath, Class<E> enumClass)

// Load enum from resources
<E extends Enum<E> & Settings> void loadEnumFromResource(String resourcePath, Class<E> enumClass)
```

### Annotations

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