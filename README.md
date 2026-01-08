# JShepherd

[![Maven Central](https://img.shields.io/maven-central/v/de.bsommerfeld.jshepherd/core?label=Maven%20Central)](https://central.sonatype.com/artifact/de.bsommerfeld.jshepherd/core)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/Java-21%2B-blue)](https://openjdk.org/projects/jdk/21/)
[![Build Status](https://github.com/bsommerfeld/jshepherd/actions/workflows/maven-test.yml/badge.svg)](https://github.com/bsommerfeld/jshepherd/actions/workflows/maven-test.yml)

JShepherd is an annotation-based configuration management library for Java that supports modern hierarchical formats (YAML, JSON, TOML) with automatic format detection based on file extensions. It intelligently merges configuration changes — adding new fields and removing obsolete ones without overwriting user-modified values.

## Key Features

* **🎯 Automatic Format Detection** — File extension (`.yaml`, `.json`, `.toml`) determines the persistence format
* **📝 Annotation-Driven** — Declarative configuration with `@Key`, `@Comment`, `@Section`
* **🔄 Smart Config Merging** — Automatically adds new keys and removes obsolete ones without losing user-modified values
* **✅ Post-Load Validation** — `@PostInject` methods are called after loading, enabling validation or derived field computation
* **💾 Live Reload & Persistence** — Call `config.reload()` or `config.save()` at any time
* **📚 Documentation Generation** — Auto-generated `.md` docs for formats without comment support (JSON)
* **🔧 Type-Safe API** — Compile-time checked `save()` and `reload()` via self-referential generics (`ConfigurablePojo<T>`)
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
@Comment("Server Configuration")
public class ServerConfig extends ConfigurablePojo<ServerConfig> {

    public enum Environment { DEV, STAGING, PROD }

    @Key("host")
    @Comment("Server hostname")
    private String host = "localhost";

    @Key("port")
    @Comment("Server port number")
    private int port = 8080;

    @Key("environment")
    @Comment("Deployment environment")
    private Environment environment = Environment.DEV;

    @Key("allowed-origins")
    @Comment("CORS allowed origins")
    private List<String> allowedOrigins = List.of("http://localhost:3000");

    @Key("feature-flags")
    @Comment("Feature toggles")
    private Map<String, Boolean> featureFlags = Map.of("darkMode", true, "betaFeatures", false);

    @Comment("Database connection settings")
    @Section("database")
    private DatabaseSettings database = new DatabaseSettings();

    @Comment("Cache tuning")
    @Section("cache")
    private CacheSettings cache = new CacheSettings();

    public ServerConfig() {}

    @PostInject
    private void validate() {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535");
        }
    }

    // Getters and setters...
}

// Section POJOs don't extend ConfigurablePojo
public class DatabaseSettings {
    @Key("url")
    @Comment("JDBC connection URL")
    private String url = "jdbc:postgresql://localhost/mydb";
    
    @Key("pool-size")
    @Comment("Connection pool size")
    private int poolSize = 10;
}

public class CacheSettings {
    @Key("max-entries")
    @Comment("Maximum cache entries")
    private int maxEntries = 1000;
    
    @Key("ttl-seconds")
    @Comment("Time-to-live in seconds")
    private int ttlSeconds = 300;
}
```

### 2. Load, Use, and Persist

```java
Path configFile = Paths.get("config.yaml");  // or .json, .toml

// Load configuration (creates file with defaults if it doesn't exist)
ServerConfig config = ConfigurationLoader.from(configFile)
    .withComments()
    .load(ServerConfig::new);

System.out.println("Host: " + config.getHost());

// Modify and persist
config.setPort(9090);
config.save();

// Pick up external file changes at runtime
config.reload();
```

### 3. Generated Output

**YAML** (`config.yaml`):

```yaml
# Server Configuration

# Server hostname
host: localhost

# Server port number
port: 8080

# Deployment environment
environment: DEV

# CORS allowed origins
allowed-origins:
  - http://localhost:3000

# Feature toggles
feature-flags:
  darkMode: true
  betaFeatures: false

# Database connection settings
database:
  # JDBC connection URL
  url: jdbc:postgresql://localhost/mydb
  # Connection pool size
  pool-size: 10

# Cache tuning
cache:
  # Maximum cache entries
  max-entries: 1000
  # Time-to-live in seconds
  ttl-seconds: 300
```

**TOML** (`config.toml`):

```toml
# Server Configuration

# Server hostname
host = "localhost"

# Server port number
port = 8080

# Deployment environment
environment = "DEV"

# CORS allowed origins
allowed-origins = ["http://localhost:3000"]

# Feature toggles
[feature-flags]
darkMode = true
betaFeatures = false

# Database connection settings
[database]
# JDBC connection URL
url = "jdbc:postgresql://localhost/mydb"
# Connection pool size
pool-size = 10

# Cache tuning
[cache]
# Maximum cache entries
max-entries = 1000
# Time-to-live in seconds
ttl-seconds = 300
```

**JSON** (`config.json`):

```json
{
  "host": "localhost",
  "port": 8080,
  "environment": "DEV",
  "allowed-origins": ["http://localhost:3000"],
  "feature-flags": {
    "darkMode": true,
    "betaFeatures": false
  },
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

> **Note:** JSON does not support comments. When `withComments()` is enabled, a `config-documentation.md` file is generated alongside the JSON file.

## Supported Formats

| Format   | Extensions      | Comments Support     | Notes                          |
|----------|-----------------|----------------------|--------------------------------|
| **YAML** | `.yaml`, `.yml` | ✅ Inline comments   | Full native support            |
| **TOML** | `.toml`         | ✅ Inline comments   | Full native support + sections |
| **JSON** | `.json`         | ❌ No native support | Generates `.md` documentation  |

## Annotations

| Annotation          | Target       | Purpose                                                     |
|---------------------|--------------|-------------------------------------------------------------|
| `@Key("name")`      | Field        | Custom key name in config file                              |
| `@Comment("text")`  | Type, Field  | Adds comments (header or inline)                            |
| `@Section("name")`  | Field        | Nested POJO as config section (all formats)                 |
| `@PostInject`       | Method       | Invoked after loading — use for validation or derived state |

## Smart Merging in Action

JShepherd's key differentiator is **intelligent configuration merging**. When your Java class evolves, the library automatically synchronizes the config file without losing user modifications.

### Scenario

Your application is deployed with **v1.0**. A user has customized `port` to `9090`. Now you release **v1.1** — removing `legacy-mode` and adding `max-connections`.

<table>
<tr>
<th>📂 Config File (User's v1.0)</th>
<th>☕ Java Class (v1.1)</th>
</tr>
<tr>
<td>

```yaml
# config.yaml
host: localhost
port: 9090         # User modified!
legacy-mode: true  # Obsolete in v1.1
```

</td>
<td>

```java
public class ServerConfig ... {
    @Key("host")
    String host = "localhost";

    @Key("port")
    int port = 8080;

    // legacy-mode removed in v1.1

    @Key("max-connections")  // NEW
    int maxConnections = 100;
}
```

</td>
</tr>
</table>

**After initial load + `config.save()`:**

```yaml
# config.yaml — automatically merged
host: localhost
port: 9090             # ✅ User value PRESERVED
max-connections: 100   # ✅ New field with default
# legacy-mode: gone    # ✅ Obsolete key REMOVED
```

| Behavior | Description |
|----------|-------------|
| **Preserve User Values** | `port: 9090` stays — the user's setting is never overwritten |
| **Add New Fields** | `max-connections` is injected with its Java default value |
| **Remove Obsolete Keys** | `legacy-mode` is dropped — no orphaned keys clutter the file |

> **How it works:** On `load()`, existing file values are merged into the Java object. On `save()`, only fields defined in the current Java class are written back — obsolete keys are simply not serialized.

---

