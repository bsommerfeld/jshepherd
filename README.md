# JShepherd

[![Maven Central](https://img.shields.io/maven-central/v/de.bsommerfeld.jshepherd/core?label=Maven%20Central)](https://central.sonatype.com/artifact/de.bsommerfeld.jshepherd/core)

JShepherd is an annotation-based configuration management library for Java that supports modern hierarchical formats (YAML, JSON, TOML) with automatic format detection based on file extensions. It intelligently merges configuration changes — adding new fields and removing obsolete ones without overwriting user-modified values.

## Key Features

* **🎯 Automatic Format Detection** — File extension determines persistence format
* **📝 Annotation-Driven** — Declarative configuration with `@Key`, `@Comment`, `@Section`
* **🔄 Smart Config Merging** — Automatically adds new keys and removes obsolete ones without losing user-modified values
* **💾 Live Reload & Persistence** — Call `config.reload()` or `config.save()` at any time
* **📚 Documentation Generation** — Auto-generated `.md` docs for formats without comment support (JSON)
* **🔧 Type Safety** — Compile-time checking with self-referential generics
* **⚡ Zero Configuration** — Sensible defaults out of the box
* **🧩 Modular** — Include only the format modules you need

## Installation

JShepherd is available on **Maven Central**. Check the badge above for the latest version.

### Maven

```xml
<dependencies>
    <!-- Core module (required) -->
    <dependency>
        <groupId>de.bsommerfeld.jshepherd</groupId>
        <artifactId>core</artifactId>
        <version>4.0.1</version>
    </dependency>

    <!-- Format-specific modules (include only what you need) -->
    <dependency>
        <groupId>de.bsommerfeld.jshepherd</groupId>
        <artifactId>yaml</artifactId>
        <version>4.0.1</version>
    </dependency>
    <dependency>
        <groupId>de.bsommerfeld.jshepherd</groupId>
        <artifactId>json</artifactId>
        <version>4.0.1</version>
    </dependency>
    <dependency>
        <groupId>de.bsommerfeld.jshepherd</groupId>
        <artifactId>toml</artifactId>
        <version>4.0.1</version>
    </dependency>
</dependencies>
```

### Gradle

```groovy
dependencies {
    // Core module (required)
    implementation 'de.bsommerfeld.jshepherd:core:4.0.1'

    // Format-specific modules (include only what you need)
    implementation 'de.bsommerfeld.jshepherd:yaml:4.0.1'
    implementation 'de.bsommerfeld.jshepherd:json:4.0.1'
    implementation 'de.bsommerfeld.jshepherd:toml:4.0.1'
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

### 2. Load, Use, and Persist

```java
Path configFile = Paths.get("config.yaml");  // or .json, .toml

// Fluent Builder API (recommended)
AppConfig config = ConfigurationLoader.from(configFile)
    .withComments()      // Enable comment generation (default)
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

| Annotation                   | Target       | Purpose                           |
|------------------------------|--------------|-----------------------------------|
| `@Key("name")`               | Field        | Custom key name in config file    |
| `@Comment("text")`           | Type, Field  | Adds comments (header or inline)  |
| `@PostInject`                | Method       | Called after configuration loaded |
| `@Section("name")`           | Field        | Nested POJO as section (all formats) |

## Nested Sections with `@Section`

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

**YAML Output:**

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

**TOML Output:**

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

**JSON Output:**

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

> **Note:** `@Key` fields declared after `@Section` fields do NOT belong to the section — they remain at root level. JSON does not support comments; when `withComments()` is enabled, a `config-documentation.md` file is generated alongside the JSON file.

---
