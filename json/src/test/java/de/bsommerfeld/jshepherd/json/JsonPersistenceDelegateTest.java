package de.bsommerfeld.jshepherd.json;

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

class JsonPersistenceDelegateTest {

    @TempDir
    Path tempDir;

    private Path configPath;
    private JsonPersistenceDelegate<TestConfig> delegate;
    private TestConfig testConfig;

    @BeforeEach
    void setUp() {
        configPath = tempDir.resolve("test-config.json");
        delegate = new JsonPersistenceDelegate<>(configPath, true);
        testConfig = new TestConfig();
    }

    @Test
    void saveSimple_shouldCreateValidJsonFile() throws IOException {
        // Arrange
        testConfig.stringValue = "test value";
        testConfig.intValue = 42;
        testConfig.boolValue = true;

        // Act
        delegate.saveSimple(testConfig, configPath);

        // Assert
        assertTrue(Files.exists(configPath), "Config file should be created");
        String content = Files.readString(configPath);
        assertTrue(content.contains("\"string-value\" : \"test value\""), "JSON should contain string value");
        assertTrue(content.contains("\"int-value\" : 42"), "JSON should contain int value");
        assertTrue(content.contains("\"bool-value\" : true"), "JSON should contain boolean value");
    }

    @Test
    void saveWithComments_shouldCreateJsonFileAndDocumentation() throws IOException {
        // Arrange
        testConfig.stringValue = "test value";
        testConfig.intValue = 42;
        testConfig.boolValue = true;

        // Act
        delegate.saveWithComments(testConfig, configPath);

        // Assert
        assertTrue(Files.exists(configPath), "Config file should be created");
        
        // Check for documentation file
        Path docPath = configPath.getParent().resolve("test-config-config-documentation.md");
        assertTrue(Files.exists(docPath), "Documentation file should be created");
        
        String docContent = Files.readString(docPath);
        assertTrue(docContent.contains("# Configuration Documentation"), "Documentation should have title");
        assertTrue(docContent.contains("String value description"), "Documentation should contain field comment");
        assertTrue(docContent.contains("**Type:** `String`"), "Documentation should contain field type");
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
        String json = "{\n" +
                "  \"string-value\" : \"loaded value\",\n" +
                "  \"int-value\" : 99,\n" +
                "  \"bool-value\" : true\n" +
                "}";
        Files.writeString(configPath, json);

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
        String json = "{\n" +
                "  \"string-value\" : \"updated value\",\n" +
                "  \"int-value\" : 123,\n" +
                "  \"bool-value\" : true\n" +
                "}";
        Files.writeString(configPath, json);

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
    }
}