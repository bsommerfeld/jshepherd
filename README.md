# JShepherd

JShepherd is an annotation based automatic configuration management library for Java that supports multiple formats (
YAML, JSON, TOML, Properties) with automatic format detection based on file extensions.

## Installation

JShepherd is now modular. You need to include the core module and any format-specific modules you want to use.

### Maven

```xml

<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
<!-- Core module (required) -->
<dependency>
    <groupId>com.github.bsommerfeld.jshepherd</groupId>
    <artifactId>core</artifactId>
    <version>VERSION</version>
</dependency>

<!-- Format-specific modules (include only what you need) -->
<dependency>
    <groupId>com.github.bsommerfeld.jshepherd</groupId>
    <artifactId>json</artifactId>
    <version>VERSION</version>
</dependency>
<dependency>
    <groupId>com.github.bsommerfeld.jshepherd</groupId>
    <artifactId>yaml</artifactId>
    <version>VERSION</version>
</dependency>
<dependency>
    <groupId>com.github.bsommerfeld.jshepherd</groupId>
    <artifactId>toml</artifactId>
    <version>VERSION</version>
</dependency>
<dependency>
    <groupId>com.github.bsommerfeld.jshepherd</groupId>
    <artifactId>properties</artifactId>
    <version>VERSION</version>
</dependency>
</dependencies>
```

### Gradle

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}

dependencies {
    // Core module (required)
    implementation 'com.github.bsommerfeld.jshepherd:core:VERSION'

    // Format-specific modules (include only what you need)
    implementation 'com.github.bsommerfeld.jshepherd:json:VERSION'
    implementation 'com.github.bsommerfeld.jshepherd:yaml:VERSION'
    implementation 'com.github.bsommerfeld.jshepherd:toml:VERSION'
    implementation 'com.github.bsommerfeld.jshepherd:properties:VERSION'
}
```

Replace `VERSION` with the latest version of JShepherd (current: 3.2.0).

## Quick Start

Define your configuration as a POJO extending `ConfigurablePojo<SelfType>`:

```java

@Comment("My Application Configuration")
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

    // Constructor and getters/setters...
    public AppConfig() {
    }

    @PostInject
    private void validateConfigValues() {
        if (serverPort < 0)
            throw new IllegalArgumentException("Port cannot be negative.");
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }
    // ... other getters/setters
}
```

Load and use your configuration:

```java
public class App {
    public static void main(String[] args) {
        // File extension determines format automatically
        Path configFile = Paths.get("config.yaml");  // or .json, .toml, .properties

        AppConfig config = ConfigurationLoader.load(configFile, AppConfig::new);

        System.out.println("App: " + config.getAppName());
        System.out.println("Port: " + config.getServerPort());

        // Modify and save
        config.setServerPort(9090);
        config.save();

        // Reload from file
        config.reload();
    }
}
```

## Supported Formats

| Format         | Extensions      | Comments Support    | Documentation       |
|----------------|-----------------|---------------------|---------------------|
| **YAML**       | `.yaml`, `.yml` | ✅ Inline comments   | Native support      |
| **JSON**       | `.json`         | ❌ No native support | Separate `.md` file |
| **TOML**       | `.toml`         | ✅ Inline comments   | Native support      |
| **Properties** | `.properties`   | ✅ Inline comments   | Native support      |

### JSON Documentation Files

When using JSON format with comments enabled, JShepherd automatically creates a companion documentation file:

```java
// config.json + config-documentation.md will be created
AppConfig config = ConfigurationLoader.load(
                Paths.get("config.json"),
                AppConfig::new,
                true  // Enable documentation generation
        );
```

## Key Features

* **🎯 Automatic Format Detection**: File extension determines the persistence format
* **📝 Annotation-Driven**: Use `@Key`, `@Comment`, and `@CommentSection` for structure and documentation
* **🔄 Live Reload**: Call `config.reload()` to update from external file changes
* **💾 Simple Persistence**: Call `config.save()` to write changes back to file
* **📚 Documentation Generation**: Automatic documentation files for formats without native comment support
* **🔧 Type Safety**: Full compile-time type checking with self-referential generics
* **⚡ Zero Configuration**: Works out of the box with sensible defaults
* **🧩 Modular Structure**: Include only the format modules you need

## Annotations

| Annotation                   | Purpose                          | Example                            |
|------------------------------|----------------------------------|------------------------------------|
| `@Key("custom-name")`        | Custom field name in config file | Maps `serverPort` → `server-port`  |
| `@Comment("Description")`    | Field documentation              | Adds comments above the field      |
| `@CommentSection("Section")` | Group related fields             | Creates section headers            |
| `@PostInject`                | Post-load initialization         | Method called after config loading |

## Example Output

**YAML** (`config.yaml`):

```yaml
# My Application Configuration

# The application name
app-name: MyApp

# Server port number  
server-port: 9090

# Enable debug logging
debug-mode: false
```

**JSON** (`config.json` + `config-documentation.md`):

```json
{
  "app-name": "MyApp",
  "server-port": 9090,
  "debug-mode": false
}
```

For detailed technical documentation, see: [Technical Documentation (German)](.docs/TECHNISCHE_DOKUMENTATION_de_V3.md)

*AI tools were used to assist in the development of this library.*
