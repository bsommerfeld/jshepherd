<img src="https://github.com/user-attachments/assets/eea2b422-1ea0-44ac-b1b6-22070de6f363" alt="Transparent" width="100" height="100" align="right" />
<br><br>

# JShepherd

JShepherd is an automatic config management library for Java.
With JShepherd you can automatically create and migrate config files without having to manually manage them in your project.

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
    <groupId>com.github.metaphoriker</groupId>
    <artifactId>jshepherd</artifactId>
    <version>VERSION</version>
</dependency>
```

### Gradle

```groovy
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}

dependencies {
    implementation 'com.github.metaphoriker:jshepherd:VERSION'
}
```

### Configuration

Here is a simple test configuration. The values are automatically loaded from the configuration file if it exists,
otherwise the default values are used for first generation.

```java
@Configuration(fileName = "test-config", type = ConfigurationType.YAML)
@Comment("A test configuration")
class TestConfiguration extends BaseConfiguration {

    @Key("test-string")
    @Comment("Test string configuration")
    private String testString = "defaultValue";

    @Key("test-int")
    @Comment("Test integer configuration")
    private int testInt = 123;

    @Key("test-double")
    @Comment({"Test double configuration", "Multi-line comment!"})
    private double testDouble = 123.456;

    @Key("test-long")
    @Comment("Test long configuration")
    private long testLong = 1234567890L;

    @Key("test-float")
    @Comment("Test float configuration")
    private float testFloat = 123.456f;

    @Key("test-list")
    @Comment("Test list configuration")
    private List<String> testList = List.of("item1", "item2", "item3");

    @Key("test-boolean")
    @Comment("Test boolean configuration")
    private boolean testBoolean = true;
}
```

After that you can initialize the configuration and use it like this.

The instantiated object then acts like the shepherd of the configuration. If you change the value of a variable
and save the configuration, the value inside the configuration will be changed.

```java
public static void main(String[] args) {
    TestConfiguration config = new TestConfiguration();
    config.initialize(); // loads the configuration from file or creates a new one
}
```

Which results in this configuration file:

```yaml
# A test configuration

# Test string configuration
test-string: "defaultValue"

# Test integer configuration
test-int: 123

# Test double configuration
# Multi-line comment!
test-double: 123.456

# Test long configuration
test-long: 1234567890

# Test float configuration
test-float: 100.0

# Test list configuration
test-list: ["item1","item2","item3"]

# Test boolean configuration
test-boolean: true
```
