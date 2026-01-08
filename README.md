# JShepherd

[![Maven Central](https://img.shields.io/maven-central/v/de.bsommerfeld/core?label=Maven%20Central)](https://central.sonatype.com/artifact/de.bsommerfeld/core)

JShepherd is an annotation-based configuration management library for Java that supports modern hierarchical formats (YAML, JSON, TOML) with automatic format detection based on file extensions. It intelligently merges configuration changes — adding new fields and removing obsolete ones without overwriting user-modified values.

## Installation

JShepherd is available on **Maven Central**. Check the badge above for the latest version.

### Maven

```xml
<dependencies>
    <!-- Core module (required) -->
    <dependency>
        <groupId>de.bsommerfeld</groupId>
        <artifactId>core</artifactId>
        <version>4.0.0</version>
    </dependency>

    <!-- Format-specific modules (include only what you need) -->
    <dependency>
        <groupId>de.bsommerfeld</groupId>
        <artifactId>yaml</artifactId>
        <version>4.0.0</version>
    </dependency>
    <dependency>
        <groupId>de.bsommerfeld</groupId>
        <artifactId>json</artifactId>
        <version>4.0.0</version>
    </dependency>
    <dependency>
        <groupId>de.bsommerfeld</groupId>
        <artifactId>toml</artifactId>
        <version>4.0.0</version>
    </dependency>
</dependencies>
```

### Gradle

```groovy
dependencies {
    // Core module (required)
    implementation 'de.bsommerfeld:core:4.0.0'

    // Format-specific modules (include only what you need)
    implementation 'de.bsommerfeld:yaml:4.0.0'
    implementation 'de.bsommerfeld:json:4.0.0'
    implementation 'de.bsommerfeld:toml:4.0.0'
}
```

## Quick Start

### 1. Define Your Configuration

Extend `ConfigurablePojo<YourClassName>` — the self-reference enables type-safe `save()` and `reload()`:

```java
@Comment("Application Configuration")
public class AppConfig extends ConfigurablePojo<AppConfig> {

    @Key("app-name")
    @Comment("The application name")
    private String appName = "MyApp";

    @Key("server-port")
    @Comment("Server port number")
    private int serverPort = 8080;

    @Key("debug-mode")
    @Comment("Enable debug logging")
    private boolean debugMode = false;

    public AppConfig() {}

    @PostInject
    private void validate() {
        if (serverPort < 0) throw new IllegalArgumentException("Port cannot be negative");
    }

    // Getters and setters...
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public int getServerPort() { return serverPort; }
    public void setServerPort(int serverPort) { this.serverPort = serverPort; }
}
```

### 2. Load and Use

```java
Path configFile = Paths.get("config.yaml");  // or .json, .toml

// Fluent Builder API (recommended)
AppConfig config = ConfigurationLoader.from(configFile)
    .withComments()
    .load(AppConfig::new);

// Or use the static factory method
AppConfig config = ConfigurationLoader.load(configFile, AppConfig::new);

System.out.println("App: " + config.getAppName());

// Modify and persist
config.setServerPort(9090);
config.save();

// Reload from file
config.reload();
```

## Supported Formats

| Format   | Extensions      | Comments Support     | Notes                          |
|----------|-----------------|----------------------|--------------------------------|
| **YAML** | `.yaml`, `.yml` | ✅ Inline comments   | Full native support            |
| **TOML** | `.toml`         | ✅ Inline comments   | Full native support + sections |
| **JSON** | `.json`         | ❌ No native support | Generates `.md` documentation  |

## Annotations

### Core Annotations

| Annotation                   | Target       | Purpose                           |
|------------------------------|--------------|-----------------------------------|
| `@Key("name")`               | Field        | Custom key name in config file    |
| `@Comment("text")`           | Type, Field  | Adds comments (header or inline)  |
| `@PostInject`                | Method       | Called after configuration loaded |
| `@Section("name")`           | Field        | Nested POJO as section (all formats) |

### Nested Sections with `@Section`

The `@Section` annotation works across all formats (YAML, TOML, JSON) to create nested configuration structures:

```java
@Comment("Server Configuration")
public class ServerConfig extends ConfigurablePojo<ServerConfig> {

    @Key("host")
    private String host = "localhost";

    @Comment("Database connection settings")
    @Section("database")
    private DatabaseSettings database = new DatabaseSettings();

    @Comment("Cache tuning")
    @Section("cache")
    private CacheSettings cache = new CacheSettings();
}

// Section POJOs don't extend ConfigurablePojo
public class DatabaseSettings {
    @Key("url")
    @Comment("JDBC connection URL")
    private String url = "jdbc:postgresql://localhost/mydb";
    
    @Key("pool-size")
    private int poolSize = 10;
}
```

**Generated YAML:**

```yaml
# Server Configuration

host: localhost

# Database connection settings
database:
  # JDBC connection URL
  url: jdbc:postgresql://localhost/mydb
  pool-size: 10

# Cache tuning
cache:
  max-entries: 1000
  ttl-seconds: 300
```

**Generated TOML:**

```toml
# Server Configuration

host = "localhost"

# Database connection settings
[database]
# JDBC connection URL
url = "jdbc:postgresql://localhost/mydb"
pool-size = 10

# Cache tuning
[cache]
max-entries = 1000
ttl-seconds = 300
```

**Generated JSON:**

```json
{
  "host": "localhost",
  "database": {
    "url": "jdbc:postgresql://localhost/mydb",
    "pool-size": 10
  },
  "cache": {
    "max-entries": 1000,
    "ttl-seconds": 300
  }
}
```

> **Note:** `@Key` fields declared after `@Section` fields do NOT belong to the section — they remain at root level.

## API Reference

### Loading Configuration

```java
// Fluent Builder API
AppConfig config = ConfigurationLoader.from(Paths.get("config.yaml"))
    .withComments()      // Enable comment generation (default)
    .load(AppConfig::new);

AppConfig config = ConfigurationLoader.from(Paths.get("config.yaml"))
    .withoutComments()   // Faster, simpler output
    .load(AppConfig::new);

// Static Factory Methods
AppConfig config = ConfigurationLoader.load(path, AppConfig::new);
AppConfig config = ConfigurationLoader.load(path, AppConfig::new, withComments);
```

### Instance Methods

```java
config.save();    // Persist current state to file
config.reload();  // Reload values from file
```

## Key Features

* **🎯 Automatic Format Detection** — File extension determines persistence format
* **📝 Annotation-Driven** — Declarative configuration with `@Key`, `@Comment`, `@Section`
* **🔄 Live Reload** — Call `config.reload()` to sync with external file changes
* **💾 Simple Persistence** — Call `config.save()` to write changes
* **� Smart Config Merging** — Automatically adds new keys and removes obsolete ones without losing user-modified values
* **�📚 Documentation Generation** — Auto-generated docs for formats without comment support
* **🔧 Type Safety** — Compile-time checking with self-referential generics
* **⚡ Zero Configuration** — Sensible defaults out of the box
* **🧩 Modular** — Include only the format modules you need

## Example Output

**YAML** (`config.yaml`):

```yaml
# Application Configuration

# The application name
app-name: MyApp

# Server port number  
server-port: 8080

# Enable debug logging
debug-mode: false
```

**TOML** (`config.toml`):

```toml
# Application Configuration

# The application name
app-name = "MyApp"

# Server port number  
server-port = 8080

# Enable debug logging
debug-mode = false
```

**JSON** (`config.json`):

```json
{
  "app-name": "MyApp",
  "server-port": 8080,
  "debug-mode": false
}
```

> JSON does not support comments. When `withComments()` is enabled, a `config-documentation.md` file is generated alongside the JSON file.

---
