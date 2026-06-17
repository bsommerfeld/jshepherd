# JShepherd

[![Maven Central](https://img.shields.io/maven-central/v/de.bsommerfeld.jshepherd/core?label=Maven%20Central)](https://central.sonatype.com/artifact/de.bsommerfeld.jshepherd/core)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/Java-21%2B-blue)](https://openjdk.org/projects/jdk/21/)
[![Build Status](https://github.com/bsommerfeld/jshepherd/actions/workflows/maven-test.yml/badge.svg)](https://github.com/bsommerfeld/jshepherd/actions/workflows/maven-test.yml)

JShepherd is an annotation-based configuration management library for Java that supports YAML, JSON, TOML and Java Properties with automatic format detection based on file extensions. It intelligently merges configuration changes — adding new fields and removing obsolete ones without overwriting user-modified values.

## Key Features

* **🎯 Automatic Format Detection** — File extension (`.yaml`, `.json`, `.toml`, `.properties`) determines the persistence format
* **📝 Annotation-Driven** — Declarative configuration with `@Key`, `@Comment`, `@Section`
* **🔄 Smart Config Merging** — Automatically adds new keys and removes obsolete ones without losing user-modified values
* **✅ Post-Load Validation** — `@PostInject` methods are called after loading, enabling validation or derived field computation
* **💾 Live Reload & Persistence** — Call `config.reload()` or `config.save()` at any time
* **👀 Auto-Reload** — Opt-in file watching: the POJO updates itself when the file changes on disk
* **📚 Documentation Generation** — Auto-generated `.md` docs for formats without comment support (JSON)
* **🔧 Type-Safe API** — Compile-time checked `save()` and `reload()` via self-referential generics (`ConfigurablePojo<T>`)
* **🪶 Plain POJO Option** — Don't want to extend anything? Annotate with `@Configuration` and manage via a `Config<T>` handle
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
        <version>4.0.4</version>
    </dependency>

    <!-- Format-specific modules (include only what you need) -->
    <dependency>
        <groupId>de.bsommerfeld.jshepherd</groupId>
        <artifactId>yaml</artifactId>
        <version>4.0.4</version>
    </dependency>
    <dependency>
        <groupId>de.bsommerfeld.jshepherd</groupId>
        <artifactId>json</artifactId>
        <version>4.0.4</version>
    </dependency>
    <dependency>
        <groupId>de.bsommerfeld.jshepherd</groupId>
        <artifactId>toml</artifactId>
        <version>4.0.4</version>
    </dependency>
    <dependency>
        <groupId>de.bsommerfeld.jshepherd</groupId>
        <artifactId>properties</artifactId>
        <version>4.0.4</version>
    </dependency>
</dependencies>
```

### Gradle

```groovy
dependencies {
    // Core module (required)
    implementation 'de.bsommerfeld.jshepherd:core:4.0.4'

    // Format-specific modules (include only what you need)
    implementation 'de.bsommerfeld.jshepherd:yaml:4.0.4'
    implementation 'de.bsommerfeld.jshepherd:json:4.0.4'
    implementation 'de.bsommerfeld.jshepherd:toml:4.0.4'
    implementation 'de.bsommerfeld.jshepherd:properties:4.0.4'
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
Path configFile = Paths.get("config.yaml");  // or .json, .toml, .properties

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
| **Properties** | `.properties` | ✅ Inline comments | Flat format — nested data via dotted keys (`database.url`), lists comma-separated, UTF-8 |

## Annotations

| Annotation          | Target       | Purpose                                                     |
|---------------------|--------------|-------------------------------------------------------------|
| `@Key("name")`      | Field        | Custom key name in config file                              |
| `@Comment("text")`  | Type, Field  | Adds comments (header or inline)                            |
| `@Section("name")`  | Field        | Nested POJO as config section — recursive (all formats)     |
| `@PostInject`       | Method       | Invoked after loading — use for validation or derived state |

**Notes:**

* Only fields annotated with `@Key` (or `@Section`) are persisted. `static` and `transient` fields are always ignored.
* `@Key` without a value falls back to the field name.
* `@PostInject` methods must be parameterless. Methods declared in superclasses are invoked as well; execution order is not guaranteed.

## Supported Field Types

| Category      | Types                                                                   |
|---------------|-------------------------------------------------------------------------|
| Primitives    | `int`, `long`, `short`, `byte`, `float`, `double`, `boolean` (+ boxed)  |
| Text          | `String`                                                                |
| Enums         | Any `enum` — stored as the constant name in all formats                 |
| Collections   | `List<...>` of the types above                                          |
| Maps          | `Map<String, ...>` — rendered as nested mapping / TOML table            |
| Date/Time     | `LocalDate`, `LocalDateTime` — native in TOML, ISO-8601 strings elsewhere |
| Nested POJOs  | Via `@Section` — recursive, sections can contain sections               |

> **Type coercion:** values are converted to the field's type on load — including quoted numbers and booleans (`port: "8080"` into an `int` field works). `String` fields are never coerced. This is also what makes the `.properties` format fully typed, even though every raw value there is a string.

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

## Plain POJOs — No Extends Required

If you prefer your config classes completely plain, annotate them with `@Configuration` and load them with `loadPlain(...)`. The lifecycle then lives on a `Config<T>` handle instead of the POJO:

```java
@Configuration
@Comment("Application settings")
public class AppConfig {
    @Key("host")
    private String host = "localhost";

    @Key("port")
    private int port = 8080;

    @Section("database")
    private DatabaseSettings database = new DatabaseSettings();

    // Optional: receive load issues directly as a parameter
    @PostInject
    private void validate(List<LoadIssue> issues) {
        if (!issues.isEmpty()) {
            throw new IllegalStateException("Invalid config: " + issues);
        }
    }
}

Config<AppConfig> config = ConfigurationLoader.from(path).loadPlain(AppConfig::new);

AppConfig app = config.get();
app.setPort(9090);
config.save();
config.reload();
config.getLastLoadIssues();
```

Everything works identically to the extends-based API: all annotations, all formats, nested sections, smart merging, auto-reload (`withAutoReload()` before `loadPlain`, listener/stop via the handle).

> `@PostInject` methods may take either no parameters or a single `List<LoadIssue>` parameter — the latter is handy for plain POJOs, which have no `getLastLoadIssues()` method of their own. This works in both API styles.

## Auto-Reload

Opt in to file watching and the configuration keeps itself up to date when the file is edited externally:

```java
ServerConfig config = ConfigurationLoader.from(Paths.get("config.yaml"))
    .withAutoReload()                        // polls once per second (daemon thread)
    // .withAutoReload(Duration.ofMillis(250)) // or pick your own interval
    .load(ServerConfig::new);

// Optional: get notified after each automatic reload
config.setOnAutoReload(() -> log.info("Configuration changed on disk"));

// Stop watching at any time — save()/reload() keep working
config.stopAutoReload();
```

Details:

* The watcher is a **daemon thread**, so it never prevents JVM shutdown.
* `@PostInject` methods run after every automatic reload, exactly like a manual `reload()`.
* The POJO's own `save()` calls do **not** trigger a reload.
* The listener runs on the watcher thread — keep it short and thread-safe.
* A temporarily unparseable file (e.g. mid-edit) logs a warning and is retried on the next change; the watcher never dies.

## Error Handling & Logging

* All configuration errors are wrapped in an unchecked `ConfigurationException` (failed saves, parse errors on `reload()`, unsupported file extensions).
* **Per-key load issues** (TOML, Properties): a value that cannot be converted (e.g. `port = "abc"` for an `int` field) leaves the field at its default, logs a warning, and is recorded on the POJO. Inspect it via `getLastLoadIssues()` — also inside `@PostInject`, so you can fail fast yourself:

  ```java
  @PostInject
  private void validate() {
      if (!getLastLoadIssues().isEmpty()) {
          throw new IllegalStateException("Invalid config: " + getLastLoadIssues());
      }
  }
  ```

  YAML and JSON bind the whole document at once — a type mismatch there fails the entire load loudly (on initial load: backup + defaults; on `reload()`: `ConfigurationException`).
* If the config file exists but **cannot be parsed on initial load**, JShepherd falls back to defaults — but first copies the broken file to `<filename>.bak` so user edits are never silently lost. A warning is logged.
* Saves are **atomic**: the file is written to a temp file first, then moved into place, so a crash mid-save can't corrupt your config.
* JShepherd logs through `java.util.logging` (logger names match the package names). Routine operations log at `FINE`; problems at `WARNING`.

## JPMS / Module Path

All artifacts ship with proper module descriptors and can be used on the module path:

```java
module my.app {
    requires de.bsommerfeld.jshepherd.core;
    // Format modules register themselves via ServiceLoader
    requires de.bsommerfeld.jshepherd.yaml;

    // Allow reflective access to your config POJOs.
    // Both the core module and the format module (which bundles its
    // serializer) reflect into your POJO classes.
    opens my.app.config;
}
```

## License

JShepherd is released under the [MIT License](https://opensource.org/licenses/MIT).
