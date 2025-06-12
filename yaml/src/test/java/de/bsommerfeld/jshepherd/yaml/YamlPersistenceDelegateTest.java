package de.bsommerfeld.jshepherd.yaml;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class YamlPersistenceDelegateTest {

    @TempDir
    Path tempDir;

    private Path configPath;
    private YamlPersistenceDelegate<TestConfig> delegate;
    private TestConfig testConfig;

    @BeforeEach
    void setUp() {
        configPath = tempDir.resolve("test-config.yaml");
        delegate = new YamlPersistenceDelegate<>(configPath, true);
        testConfig = new TestConfig();
    }

    @Test
    void saveSimple_shouldCreateValidYamlFile() throws IOException {
        // Arrange
        testConfig.stringValue = "test value";
        testConfig.intValue = 42;
        testConfig.boolValue = true;

        // Act
        delegate.saveSimple(testConfig, configPath);

        // Assert
        assertTrue(Files.exists(configPath), "Config file should be created");
        String content = Files.readString(configPath);

        // Debug output
        System.out.println("[DEBUG] YAML content:");
        System.out.println(content);

        // Update assertions to match actual YAML format
        assertTrue(content.contains("stringValue: test value"), "YAML should contain string value");
        assertTrue(content.contains("intValue: 42"), "YAML should contain int value");
        assertTrue(content.contains("boolValue: true"), "YAML should contain boolean value");
    }

    @Test
    void saveWithComments_shouldCreateYamlFileWithComments() throws IOException {
        // Arrange
        testConfig.stringValue = "test value";
        testConfig.intValue = 42;
        testConfig.boolValue = true;

        // Act
        delegate.saveWithComments(testConfig, configPath);

        // Assert
        assertTrue(Files.exists(configPath), "Config file should be created");
        String content = Files.readString(configPath);

        // Debug output
        System.out.println("[DEBUG] YAML content with comments:");
        System.out.println(content);

        // Check for comments in YAML
        assertTrue(content.contains("# String value description"), "YAML should contain string comment");
        assertTrue(content.contains("# Integer value description"), "YAML should contain int comment");
        assertTrue(content.contains("# Boolean value description"), "YAML should contain boolean comment");

        // Check for values - update to match actual YAML format
        // The saveWithComments method in YamlPersistenceDelegate uses the @Key annotation values
        assertTrue(content.contains("string-value:"), "YAML should contain string value key");
        assertTrue(content.contains("int-value:"), "YAML should contain int value key");
        assertTrue(content.contains("bool-value:"), "YAML should contain bool value key");
        assertTrue(content.contains("test value"), "YAML should contain string value");
        assertTrue(content.contains("42"), "YAML should contain int value");
        assertTrue(content.contains("true"), "YAML should contain boolean value");
    }

    @Test
    void loadInitial_shouldCreateDefaultWhenFileDoesNotExist() {
        // Arrange
        Supplier<TestConfig> supplier = TestConfig::new;

        // Act
        TestConfig result = delegate.loadInitial(supplier);

        // Assert
        assertNotNull(result);
        assertTrue(Files.exists(configPath), "Config file should be created");
        assertEquals("default", result.stringValue);
        assertEquals(0, result.intValue);
        assertFalse(result.boolValue);
    }

    @Test
    void tryLoadFromFile_shouldLoadExistingFile() throws Exception {
        // Arrange
        String yaml = "string-value: loaded value\n" +
                "int-value: 99\n" +
                "bool-value: true\n";
        Files.writeString(configPath, yaml);

        // Act
        boolean result = delegate.tryLoadFromFile(testConfig);

        // Assert
        assertTrue(result, "Should return true for successful load");
        assertEquals("loaded value", testConfig.stringValue);
        assertEquals(99, testConfig.intValue);
        assertTrue(testConfig.boolValue);
    }

    @Test
    void reload_shouldUpdateInstanceFields() throws IOException {
        // Arrange
        String yaml = "string-value: updated value\n" +
                "int-value: 123\n" +
                "bool-value: true\n";
        Files.writeString(configPath, yaml);

        // Act
        delegate.reload(testConfig);

        // Assert
        assertEquals("updated value", testConfig.stringValue);
        assertEquals(123, testConfig.intValue);
        assertTrue(testConfig.boolValue);
    }

    // Test implementation of ConfigurablePojo
    private static class TestConfig extends ConfigurablePojo<TestConfig> {
        @Key("string-value")
        @Comment("String value description")
        private String stringValue = "default";

        @Key("int-value")
        @Comment("Integer value description")
        private int intValue = 0;

        @Key("bool-value")
        @Comment("Boolean value description")
        private boolean boolValue = false;

        // Getters and setters for JavaBean compatibility
        public String getStringValue() {
            return stringValue;
        }

        public void setStringValue(String stringValue) {
            this.stringValue = stringValue;
        }

        public int getIntValue() {
            return intValue;
        }

        public void setIntValue(int intValue) {
            this.intValue = intValue;
        }

        public boolean isBoolValue() {
            return boolValue;
        }

        public void setBoolValue(boolean boolValue) {
            this.boolValue = boolValue;
        }
    }
}
