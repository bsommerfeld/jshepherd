# JShepherd

JShepherd is an automatic configuration management library for Java.
With JShepherd, you can easily define, load, save, and manage your application's configuration files in YAML format, using a clean, POJO-centric approach driven by annotations.

## Installation

### Maven

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
    <version>VERSION</version> 
</dependency>
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
    implementation 'com.github.bsommerfeld:jshepherd:VERSION'
}
```

Replace `VERSION` with the latest version of JShepherd.

## Configuration Example
Here is a simple test configuration. You define your configuration structure as a POJO (Plain Old Java Object) extending `ConfigurablePojo<SelfType>`. Annotations guide how it's persisted.

If a YAML configuration file exists, values are loaded from it. Otherwise, the default values defined in your POJO are used, and a new configuration file is generated on the first load.

```java
// Class-level comment becomes the file header
@Comment({
    "This is a JShepherd Test Configuration File.",
    "All settings for the test application are here."
})
public class TestConfig extends ConfigurablePojo<TestConfig> { // Note the self-referential generic

    @CommentSection({
        "Basic Settings",
        "Fundamental string and integer values."
    })
    @Key("test.string.value")
    @Comment("A sample string configuration value.")
    private String testString = "defaultValue";

    @Key("test.integer.value")
    @Comment("A sample integer configuration value.")
    private int testInt = 123;

    @CommentSection("Numeric Precision Types")
    @Key("test.double.value")
    @Comment({"A sample double configuration value.", "Allows for multi-line comments!"})
    private double testDouble = 123.456;

    @Key("test.long.value")
    @Comment("A sample long configuration value.")
    private long testLong = 1234567890L;

    @Key("test.float.value")
    @Comment("A sample float configuration value.")
    private float testFloat = 123.456f;

    @CommentSection("Collections and Other Types")
    @Key("test.list.value")
    @Comment("A sample list configuration.")
    private List<String> testList = Arrays.asList("item1", "item2", "item3");

    @Key("test.boolean.value")
    @Comment("A sample boolean configuration value.")
    private boolean testBoolean = true;

    @Key("test.map.value")
    @Comment("A sample map configuration.")
    private Map<String, String> testMap;

    // Default constructor is used by the default supplier
    public TestConfig() {
        // Initialize defaults, especially for complex types or those not inline
        testMap = new LinkedHashMap<>();
        testMap.put("mapKey1", "mapValue1");
        testMap.put("mapKey2", "mapValue2");
    }

    // Standard Getters and Setters
    // ...
}
```

## Usage
You load and interact with your configuration POJO using the `ConfigurationLoader`. The loaded object itself acts as the "shepherd" of its configuration state.

```java
public class App {
    public static void main(String[] args) {
        Path configFile = Paths.get("test-application-config.yaml");

        // Ensure parent directory exists (optional, good practice)
        try {
            if (configFile.getParent() != null) {
                Files.createDirectories(configFile.getParent());
            }
        } catch (IOException e) {
            System.err.println("Could not create config directory: " + e.getMessage());
        }

        // Load the configuration.
        // This will read the file, or create it with defaults if it doesn't exist.
        // The 'true' flag enables saving with comments from annotations.
        TestConfig config = ConfigurationLoader.load(
            configFile,
            TestConfig.class,
            TestConfig::new, // Supplier for default instance
            true             // Use detailed, comment-driven save
        );

        System.out.println("Initial String Value: " + config.getTestString());
        System.out.println("Initial Port: " + config.getTestInt()); // Assuming testInt was a port for example

        // Modify configuration values
        config.setTestString("A new value set at runtime!");
        config.setTestInt(config.getTestInt() + 100); // Increment int value
        config.getTestList().add("newItemFromApp");   // Modify a list

        // Save the changes back to the YAML file
        config.save();
        System.out.println("Configuration saved.");

        // If the file is modified externally, you can reload it
        // config.reload();
        // System.out.println("String value after potential reload: " + config.getTestString());
    }
}
```

This will result in a `test-application-config.yaml` file (if useComplexSaveWithComments was true):

```yaml
# This is a JShepherd Test Configuration File.
# All settings for the test application are here.

# Basic Settings
# Fundamental string and integer values.
# A sample string configuration value.
test.string.value: A new value set at runtime!

# A sample integer configuration value.
test.integer.value: 223

# Numeric Precision Types
# A sample double configuration value.
# Allows for multi-line comments!
test.double.value: 123.456

# A sample long configuration value.
test.long.value: 1234567890

# A sample float configuration value.
test.float.value: 123.456

# Collections and Other Types
# A sample list configuration.
test.list.value:
  - item1
  - item2
  - item3
  - newItemFromApp

# A sample boolean configuration value.
test.boolean.value: true

# A sample map configuration.
test.map.value:
  mapKey1: mapValue1
  mapKey2: mapValue2
```

(Note: The exact formatting of the YAML, especially for collections and comments, is determined by the `saveWithAnnotationDrivenComments` logic and SnakeYAML's DumperOptions. The example above reflects a typical block style with comments.)

## Key Features Summary
* **Define configuration easily:** Extend `ConfigurablePojo` and use annotations.
* **Load Transparently:** `ConfigurationLoader.load()` handles file creation, default values, and parsing.
* **Interact Directly:** Use your POJO instance for all get, set, save, and reload operations.
* **Annotation-Driven Output:** `@Key`, `@Comment`, and `@CommentSection` control the YAML structure and documentation when `useComplexSaveWithComments` is enabled.

For more details on specific annotations or advanced usage, please refer to the library's documentation or source code.