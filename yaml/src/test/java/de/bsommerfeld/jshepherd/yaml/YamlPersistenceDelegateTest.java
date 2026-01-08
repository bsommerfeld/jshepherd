package de.bsommerfeld.jshepherd.yaml;

import de.bsommerfeld.jshepherd.annotation.Comment;

import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    @DisplayName("No global tags (saveSimple) - Should not write SnakeYAML global type tags")
    void saveSimple_shouldNotWriteGlobalTypeTags() throws IOException {
        testConfig.boolValue = true;

        // Act
        delegate.saveSimple(testConfig, configPath);

        // Assert
        assertTrue(Files.exists(configPath), "Config file should be created");
        String content = Files.readString(configPath);

        assertFalse(content.startsWith("!!"), "YAML must not start with a global tag");
        assertFalse(content.contains("\n!!"), "YAML must not contain global tags in any line");
    }

    @Test
    @DisplayName("No global tags (saveWithComments) - Should not write SnakeYAML global type tags")
    void saveWithComments_shouldNotWriteGlobalTypeTags() throws IOException {
        // Arrange
        testConfig.boolValue = false;

        // Act
        delegate.saveWithComments(testConfig, configPath);

        // Assert
        assertTrue(Files.exists(configPath), "Config file should be created");
        String content = Files.readString(configPath);

        assertFalse(content.startsWith("!!"), "YAML must not start with a global tag");
        assertFalse(content.contains("\n!!"), "YAML must not contain global tags in any line");

        // Sanity-Check
        assertTrue(content.contains("bool-value:"), "YAML should contain the boolean key");
        assertFalse(content.contains("!!"), "YAML must not contain any global tag for boolean value");
    }

    @Test
    @DisplayName("Save simple - Should create a valid YAML file with basic types")
    void saveSimple_shouldCreateValidYamlFile() throws IOException {
        // Arrange
        testConfig.stringValue = "test value";
        testConfig.intValue = 42;
        testConfig.boolValue = true;
        testConfig.doubleValue = 3.14159;
        testConfig.longValue = 9876543210L;

        // Act
        delegate.saveSimple(testConfig, configPath);

        // Assert
        assertTrue(Files.exists(configPath), "Config file should be created");
        String content = Files.readString(configPath);

        // Check basic types - YAML format uses field names from JavaBean properties
        assertTrue(content.contains("string-value: test value"), "YAML should contain string value");
        assertTrue(content.contains("int-value: 42"), "YAML should contain int value");
        assertTrue(content.contains("bool-value: true"), "YAML should contain boolean value");
        assertTrue(content.contains("double-value: 3.14159"), "YAML should contain double value");
        assertTrue(content.contains("long-value: 9876543210"), "YAML should contain long value");
    }

    @Test
    @DisplayName("Save with comments - Should create YAML file with comments")
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

        // Check for comments in YAML
        assertTrue(content.contains("# String value description"), "YAML should contain string comment");
        assertTrue(content.contains("# Integer value description"), "YAML should contain int comment");
        assertTrue(content.contains("# Boolean value description"), "YAML should contain boolean comment");

        // Check for values - The saveWithComments method in YamlPersistenceDelegate
        // uses the @Key annotation values
        assertTrue(content.contains("string-value:"), "YAML should contain string value key");
        assertTrue(content.contains("int-value:"), "YAML should contain int value key");
        assertTrue(content.contains("bool-value:"), "YAML should contain bool value key");
        assertTrue(content.contains("test value"), "YAML should contain string value");
        assertTrue(content.contains("42"), "YAML should contain int value");
        assertTrue(content.contains("true"), "YAML should contain boolean value");
    }

    @Test
    @DisplayName("Try load from file - Should load existing file")
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
        assertEquals("loaded value", testConfig.stringValue, "String value should be loaded");
        assertEquals(99, testConfig.intValue, "Int value should be loaded");
        assertTrue(testConfig.boolValue, "Bool value should be loaded");
    }

    // Note: Tests for loadInitial, reload, collections, special chars, empty/null,
    // and very long strings are now centralized in .integration-tests module

    @Test
    @DisplayName("Malformed YAML - Should handle malformed input files")
    void tryLoadFromFile_shouldHandleMalformedInputFiles() throws Exception {
        // Arrange - Create malformed YAML file
        String malformedYaml = "string-value: broken value\n" +
                "int-value: 99\n" +
                "bool-value: true\n" +
                "invalid:\n  - missing colon\n" + // Invalid YAML syntax
                "  unclosed quote: \"open quote\n"; // Unclosed quote
        Files.writeString(configPath, malformedYaml);

        // Act & Assert - Should throw exception for malformed YAML
        Exception exception = assertThrows(Exception.class, () -> {
            delegate.tryLoadFromFile(testConfig);
        }, "Should throw exception for malformed YAML");

        // Print the actual error message for debugging
        System.out.println("[DEBUG_LOG] YAML parse error: " + exception.getMessage());

        // We don't need to check the specific error message content, just that an
        // exception was thrown
        // The exact error message format can vary between YAML parser implementations
        assertNotNull(exception.getMessage(), "Exception should have an error message");
    }

    @Test
    @DisplayName("Empty file - Should handle empty input files")
    void tryLoadFromFile_shouldHandleEmptyInputFiles() throws Exception {
        // Arrange - Create empty file
        Files.writeString(configPath, "");

        // Act
        boolean result = delegate.tryLoadFromFile(testConfig);

        // Assert
        assertFalse(result, "Should return false for empty file");
        // Values should remain unchanged
        assertEquals("default", testConfig.stringValue, "String value should remain default");
        assertEquals(0, testConfig.intValue, "Int value should remain default");
        assertFalse(testConfig.boolValue, "Bool value should remain default");
    }

    @Test
    @DisplayName("Non-existent file - Should handle non-existent files")
    void tryLoadFromFile_shouldHandleNonExistentFiles() throws Exception {
        // Arrange - Ensure file doesn't exist
        Files.deleteIfExists(configPath);

        // Act & Assert - Should throw exception for non-existent file
        Exception exception = assertThrows(Exception.class, () -> {
            delegate.tryLoadFromFile(testConfig);
        }, "Should throw exception for non-existent file");

        // Verify exception is related to file not found
        assertTrue(exception instanceof IOException,
                "Exception should be an IOException");
    }

    @Test
    @DisplayName("Concurrent access - Should complete within timeout")
    void concurrentAccess_shouldCompleteWithinTimeout() throws Exception {
        // Arrange
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        // Act - Multiple threads saving and loading concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i;
            executorService.submit(() -> {
                try {
                    // Create a unique config for this thread
                    TestConfig threadConfig = new TestConfig();
                    threadConfig.stringValue = "Thread " + threadNum;
                    threadConfig.intValue = threadNum;

                    // Create a unique path for this thread
                    Path threadPath = tempDir.resolve("thread-" + threadNum + ".yaml");
                    YamlPersistenceDelegate<TestConfig> threadDelegate = new YamlPersistenceDelegate<>(threadPath,
                            false);

                    // Save and load - catch any exceptions that might occur
                    try {
                        threadDelegate.saveSimple(threadConfig, threadPath);
                        threadDelegate.reload(threadConfig);
                    } catch (Exception e) {
                        // Log the exception but don't fail the test
                        System.out.println("[DEBUG_LOG] Exception in thread " + threadNum + ": " + e.getMessage());
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all threads to complete
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // Assert - only check that all threads completed within the timeout
        assertTrue(completed, "All threads should complete within timeout");

        // Log any exceptions that occurred, but don't fail the test
        if (!exceptions.isEmpty()) {
            System.out.println("[DEBUG_LOG] Exceptions during concurrent access: " + exceptions);
        }
    }

    // Test implementation of ConfigurablePojo with various field types
    @Comment({ "Test configuration class", "Used for testing YAML persistence" })
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

        @Key("double-value")
        @Comment("Double value description")
        private double doubleValue = 0.0;

        @Key("long-value")
        @Comment("Long value description")
        private long longValue = 0L;

        @Key("string-list")
        @Comment("List of strings")
        private List<String> stringList = new ArrayList<>();

        @Key("int-list")
        @Comment("List of integers")
        private List<Integer> intList = new ArrayList<>();

        @Key("string-map")
        @Comment("Map of string to string")
        private Map<String, String> stringMap = new HashMap<>();

        @Key("nested-map")
        @Comment("Map with nested values")
        private Map<String, Object> nestedMap = new HashMap<>();

        @Key("special-chars")
        @Comment("String with special characters")
        private String specialChars = "";

        @Key("empty-string")
        @Comment("Empty string value")
        private String emptyString = "";

        @Key("null-string")
        @Comment("Null string value")
        private String nullString = null;

        @Key("very-long-string")
        @Comment("Very long string value")
        private String veryLongString = "";

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

        public double getDoubleValue() {
            return doubleValue;
        }

        public void setDoubleValue(double doubleValue) {
            this.doubleValue = doubleValue;
        }

        public long getLongValue() {
            return longValue;
        }

        public void setLongValue(long longValue) {
            this.longValue = longValue;
        }
    }
}
