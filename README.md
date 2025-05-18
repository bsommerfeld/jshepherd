# jShepherd Configuration

jShepherd Configuration is a lightweight, annotation-driven Java library for managing application settings in YAML files. It provides a simple, POJO-centric API, allowing users to interact directly with their configuration objects for getting, setting, saving, and reloading properties, while keeping the configuration classes clean and focused on data definition.

The core philosophy is to let annotations "do the work," minimizing boilerplate and making configuration management intuitive.

## Features

* **POJO-Centric API:** Interact directly with your configuration objects (`config.getProperty()`, `config.setProprety()`, `config.save()`, `config.reload()`).
* **Annotation-Driven:** Define YAML keys, comments, and comment sections using simple annotations on your POJO fields.
* **YAML Backend:** Uses SnakeYAML for robust YAML parsing and emission.
* **Clean Data Objects:** Your configuration POJOs extend a very lightweight abstract base class and remain focused on data, free from complex I/O or serialization logic.
* **Comment Preservation (Optional):** Supports a save mode that attempts to write comments and section headers from your annotations into the YAML file for better human readability.
* **Atomic Saves:** File saving operations are designed to be atomic (write to temp then move) to prevent data corruption.
* **In-Place Reload:** The `reload()` method updates the fields of your existing configuration POJO instance.
* **Default Value Handling:** Easy definition of default values in your POJO and automatic creation/saving of configuration files with these defaults if the file doesn't exist or is empty.

## Requirements

* Java 11 or higher (due to usage of `java.nio.file` and stream APIs like `String.lines()`).

## How to Integrate

### Maven
Add the following dependency to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.bsommerfeld</groupId>
    <artifactId>jshepherd</artifactId>
    <version>Tag</version>
</dependency>
```

### Gradle
Add the following to your `build.gradle`:

```groovy
    dependencyResolutionManagement {
        repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
        repositories {
            mavenCentral()
            maven { url 'https://jitpack.io' }
        }
    }

dependencies {
    implementation 'com.github.bsommerfeld:jshepherd:Tag'
}
```

## Core Concepts

* **Configuration POJO:** A plain Java class you define that extends `de.bsommerfeld.jshepherd.core.ConfigurablePojo`. This class holds your configuration fields and is annotated to map to YAML.
* **`ConfigurablePojo`:** A lightweight abstract base class that your POJOs extend. It provides the `save()` and `reload()` methods by delegating to an internal persistence mechanism.
* **`ConfigurationLoader`:** A static factory class (`de.bsommerfeld.jshepherd.core.ConfigurationLoader`) used to load your configuration POJO from a file. This is your main entry point to get an initialized configuration object.
* **Annotations:**
    * `@Key("yaml-key-name")`: Maps a Java field to a specific key name in the YAML file.
    * `@Comment({"Line 1", "Line 2"})`: Adds descriptive comments above a field in the YAML file, or at the class level for a file header.
    * `@CommentSection({"Section Title", "Description"})`: Defines a block of comments to group related fields, typically placed on the first field of a new section.

## Usage

### 1. Define Your Configuration POJO

Create a class that extends `ConfigurablePojo<YourPojoClass>` (using a self-referential generic type for type safety). Add your configuration fields and annotate them.

**Example: `AppSettings.java`**
```java
package com.example.config; // Your package

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.CommentSection;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Comment({
    "My Application Settings",
    "-----------------------",
    "All critical settings are defined here."
})
public class AppSettings extends ConfigurablePojo<AppSettings> {

    @CommentSection({
        "Database Configuration",
        "Connection details for the primary application database."
    })
    @Key("database.url")
    @Comment("The full JDBC URL (e.g., jdbc:postgresql://host:port/database)")
    private String databaseUrl = "jdbc:default:mydb";

    @Key("database.username")
    @Comment("Username for database connection.")
    private String dbUsername = "app_user";

    @CommentSection("Server Settings")
    @Key("server.host")
    @Comment("Hostname or IP address the server will bind to.")
    private String serverHost = "localhost";
    
    @Key("server.port")
    @Comment("Network port for the server.")
    private int serverPort = 8080;

    @Key("features.enable-beta")
    @Comment("Set to true to enable experimental beta features.")
    private boolean betaFeaturesEnabled = false;

    @Key("misc.default-items")
    private List<String> defaultItems = Arrays.asList("alpha", "omega");

    @Key("misc.thresholds")
    private Map<String, Double> thresholds;

    public AppSettings() {
        // Initialize default values, especially for complex types
        thresholds = new LinkedHashMap<>();
        thresholds.put("low", 0.1);
        thresholds.put("medium", 0.5);
        thresholds.put("high", 0.9);
    }

    // Standard Getters and Setters for all fields...
    public String getDatabaseUrl() { return databaseUrl; }
    public void setDatabaseUrl(String databaseUrl) { this.databaseUrl = databaseUrl; }
    public String getDbUsername() { return dbUsername; }
    public void setDbUsername(String dbUsername) { this.dbUsername = dbUsername; }
    public String getServerHost() { return serverHost; }
    public void setServerHost(String serverHost) { this.serverHost = serverHost; }
    public int getServerPort() { return serverPort; }
    public void setServerPort(int serverPort) { this.serverPort = serverPort; }
    public boolean isBetaFeaturesEnabled() { return betaFeaturesEnabled; } // Note 'is' prefix for boolean getter
    public void setBetaFeaturesEnabled(boolean betaFeaturesEnabled) { this.betaFeaturesEnabled = betaFeaturesEnabled; }
    public List<String> getDefaultItems() { return defaultItems; }
    public void setDefaultItems(List<String> defaultItems) { this.defaultItems = defaultItems; }
    public Map<String, Double> getThresholds() { return thresholds; }
    public void setThresholds(Map<String, Double> thresholds) { this.thresholds = thresholds; }
}
```

### 2. Load Your Configuration
Use the `ConfigurationLoader` to obtain an instance of your POJO. This instance will be "live," meaning it's connected to the file and can be saved or reloaded.

```java
import de.bsommerfeld.jshepherd.core.ConfigurationLoader;
import com.example.config.AppSettings; // Your POJO
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException; // For createDirectories
import java.nio.file.Files;  // For createDirectories

public class MainApp {
    public static void main(String[] args) {
        Path configFile = Paths.get("config", "app-settings.yaml");

        // Optional: Ensure parent directory exists. The library handles atomic save to this path.
        try {
            if (configFile.getParent() != null) {
                 Files.createDirectories(configFile.getParent());
            }
        } catch (IOException e) {
            System.err.println("Could not create config directory: " + e.getMessage());
            // Depending on requirements, you might want to handle this more gracefully or re-throw
        }

        AppSettings appSettings = ConfigurationLoader.load(
            configFile,
            AppSettings.class,
            AppSettings::new, // Supplier for creating a default instance
            true              // true = save with annotation-driven comments
                              // false = simple YAML dump without detailed comments
        );

        // Now you can use appSettings directly
        System.out.println("Loaded server port: " + appSettings.getServerPort());
    }
}
```

The `ConfigurationLoader.load()` method will:

- Read the YAML file if it exists.
- If the file doesn't exist, is empty, or is invalid, it will use the `defaultPojoSupplier` to create a default instance.
- This default instance will then be saved to the specified `filePath` (creating the file).
- The returned POJO instance is ready for use.

### 3. Access and Modify Data
Interact with the POJO using its standard getters and setters.

```java
// ... continuing from above
System.out.println("Current DB URL: " + appSettings.getDatabaseUrl());

appSettings.setDatabaseUrl("jdbc:mysql://localhost:3306/production");
appSettings.setServerPort(9000);
appSettings.getDefaultItems().add("gamma"); // Modifying a mutable collection
```

### 4. Save Changes
Call the `save()` method directly on your POJO instance to persist any modifications to the YAML file.

```java
appSettings.save();
System.out.println("Configuration updated and saved.");
```

The save operation is atomic (writes to a temporary file first, then moves) to help prevent data corruption during the write process.

### 5. Reload Configuration
If the YAML file might have been changed externally (e.g., by another process or manual edit), call the `reload()` method on your POJO instance. This will re-read the file and update the fields of your existing POJO instance in place.

```java
// Imagine app-settings.yaml was changed by another process or manually
appSettings.reload();
System.out.println("Configuration reloaded. New server port: " + appSettings.getServerPort());
```

## Annotation Reference

jShepherd Configuration uses a few key annotations to define how your POJO fields map to the YAML file and how comments should be generated.

* **`@Key("your-yaml-key-name")`**
    * **Target:** `ElementType.FIELD`
    * **Purpose:** Specifies the exact key name to be used in the YAML file for the annotated field.
    * **Attribute:**
        * `value()`: (String, mandatory) The name of the key in the YAML file. If left empty (e.g., `@Key("")`), the Java field name itself might be used as a fallback by some save mechanisms, but providing an explicit key is strongly recommended for clarity and consistency, especially for the comment-driven save mode.
    * **Note:** Only fields explicitly annotated with `@Key` are processed by the `saveWithAnnotationDrivenComments` mode. Fields without `@Key` might be serialized by the `saveSimpleDump` mode if they are standard bean properties and SnakeYAML is configured to do so, but they won't have associated comments from field-level annotations.

* **`@Comment({"Comment line 1", "Another comment line"})`**
    * **Target:** `ElementType.FIELD` or `ElementType.TYPE` (Class)
    * **Purpose:** Adds descriptive comments to the generated YAML file.
    * **Attribute:**
        * `value()`: (String array, mandatory) Each string in the array becomes a new comment line, prefixed with `# ` in the YAML output.
    * **Usage:**
        * When placed on a **field**, the comments appear directly above that field's key-value pair in the YAML file (if using the comment-driven save mode).
        * When placed on a **class** (your POJO class), the comments appear at the very beginning of the YAML file, serving as a file header. This is respected by both simple and comment-driven save modes.

* **`@CommentSection({"Section Title", "Optional longer description for the section."})`**
    * **Target:** `ElementType.FIELD`
    * **Purpose:** Marks the beginning of a new conceptual section in the configuration file when using the comment-driven save mode. The provided comments are written as a block before the field it annotates.
    * **Attribute:**
        * `value()`: (String array, mandatory) Each string forms a line in the section header comment block.
    * **Behavior:** A blank line is typically inserted before a new section header for better readability, unless it's the very first section appearing immediately after a file header. The library tracks changes in sections to print these headers appropriately.

## Save Styles

When you load your configuration using `ConfigurationLoader.load(...)`, the final boolean parameter, `useComplexSaveWithComments`, determines how the `save()` method on your POJO will behave:

* **`true` (Comment-Driven Save):**
    * The `save()` method will process `@Key`, `@Comment`, and `@CommentSection` annotations to generate a richly commented and structured YAML file.
    * This mode is ideal for configurations that are intended to be human-readable and directly editable, as the comments provide context.
    * The output order of fields generally follows their declaration order within the class and its superclass hierarchy (superclass fields first).
    * This mode involves more complex internal logic for YAML generation.

* **`false` (Simple Dump):**
    * The `save()` method performs a more straightforward dump of the POJO's current state using SnakeYAML's standard object-to-YAML capabilities.
    * Only a class-level `@Comment` (if present on your POJO class) will be written as a file header.
    * Field-level `@Comment` and `@CommentSection` annotations are **ignored** for output in this mode.
    * This mode is generally faster and results in a more compact YAML file if detailed comments within the output file are not a priority. The order of keys in the output will depend on SnakeYAML's default serialization order for maps (which is usually based on key insertion order for `LinkedHashMap` or alphabetical for `TreeMap`, but can vary for plain `HashMap`).

Choose the style that best suits your application's needs for configuration file readability and maintainability.

## Error Handling

The library may throw a `de.bsommerfeld.jshepherd.core.ConfigurationException` (which is a `RuntimeException`) for issues encountered during the configuration lifecycle:

* **During `ConfigurationLoader.load(...)`:**
    * If the specified configuration file cannot be read (e.g., due to permissions or I/O errors).
    * If the YAML content is malformed and cannot be parsed by SnakeYAML.
    * If the default POJO instance cannot be created via the provided supplier.
    * If saving the initial default configuration fails.
* **During `config.save()`:**
    * If the configuration file cannot be written to (e.g., I/O errors, permissions).
    * If there are issues during YAML serialization (less common for valid POJOs).
* **During `config.reload()`:**
    * If the configuration file cannot be re-read.
    * If the reloaded YAML content is malformed.
    * If there are issues updating the fields of the existing POJO instance (e.g., reflection errors, though unlikely with accessible fields).
* **General:**
    * Calling `save()` or `reload()` on a POJO instance that was not initialized through `ConfigurationLoader` (and thus lacks its internal persistence delegate) will result in an `IllegalStateException`.

It is recommended to be aware of these potential exceptions. While the library attempts to handle common scenarios gracefully (like creating a default file), critical file system issues might still propagate as `ConfigurationException`.

## Full Example

For a runnable demonstration showcasing most of these features, including POJO definition with various data types, annotations, loading, modification, saving with comments, and reloading, please refer to the example classes typically provided with this library:

* **POJO Definition:** `de.bsommerfeld.jshepherd.example.TestConfigurationPojo.java`
* **Main Application:** `de.bsommerfeld.jshepherd.example.AppMain.java`

These examples provide a practical look at how to integrate and use the jShepherd Configuration library in an application.

---