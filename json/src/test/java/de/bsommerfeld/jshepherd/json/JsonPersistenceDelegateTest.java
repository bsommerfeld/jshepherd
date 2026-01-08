package de.bsommerfeld.jshepherd.json;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.ConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    @DisplayName("Save simple - Should create a valid JSON file with basic types")
    void saveSimple_shouldCreateValidJsonFile() throws IOException {
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

        // Check basic types
        assertTrue(content.contains("\"string-value\" : \"test value\""), "JSON should contain string value");
        assertTrue(content.contains("\"int-value\" : 42"), "JSON should contain int value");
        assertTrue(content.contains("\"bool-value\" : true"), "JSON should contain boolean value");
        assertTrue(content.contains("\"double-value\" : 3.14159"), "JSON should contain double value");
        assertTrue(content.contains("\"long-value\" : 9876543210"), "JSON should contain long value");
    }

    @Test
    @DisplayName("Save with comments - Should create JSON file and documentation")
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
    @DisplayName("Save and load collections - Should handle lists and maps correctly")
    void saveAndLoad_shouldHandleCollectionsCorrectly() throws Exception {
        // Arrange
        testConfig.stringList = Arrays.asList("item1", "item2", "item3");
        testConfig.intList = Arrays.asList(1, 2, 3, 4, 5);

        Map<String, String> stringMap = new HashMap<>();
        stringMap.put("key1", "value1");
        stringMap.put("key2", "value2");
        testConfig.stringMap = stringMap;

        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("nestedString", "nested value");
        nestedMap.put("nestedInt", 42);
        nestedMap.put("nestedList", Arrays.asList("a", "b", "c"));
        testConfig.nestedMap = nestedMap;

        // Act - Save
        delegate.saveSimple(testConfig, configPath);

        // Assert - File exists and contains collections
        assertTrue(Files.exists(configPath), "Config file should be created");
        String content = Files.readString(configPath);

        // Check collections
        assertTrue(content.contains("\"string-list\" : [ \"item1\", \"item2\", \"item3\" ]"),
                "JSON should contain string list");
        assertTrue(content.contains("\"int-list\" : [ 1, 2, 3, 4, 5 ]"),
                "JSON should contain int list");
        assertTrue(content.contains("\"string-map\""), "JSON should contain string map");
        assertTrue(content.contains("\"key1\" : \"value1\""), "JSON should contain map entries");
        assertTrue(content.contains("\"nested-map\""), "JSON should contain nested map");
        assertTrue(content.contains("\"nestedString\" : \"nested value\""), "JSON should contain nested string");
        assertTrue(content.contains("\"nestedInt\" : 42"), "JSON should contain nested int");

        // Act - Load into a new instance
        TestConfig loadedConfig = new TestConfig();
        delegate.tryLoadFromFile(loadedConfig);

        // Assert - Collections loaded correctly
        assertEquals(3, loadedConfig.stringList.size(), "String list should have 3 items");
        assertEquals("item1", loadedConfig.stringList.get(0), "First string list item should match");
        assertEquals(5, loadedConfig.intList.size(), "Int list should have 5 items");
        assertEquals(2, loadedConfig.stringMap.size(), "String map should have 2 entries");
        assertEquals("value1", loadedConfig.stringMap.get("key1"), "Map value should match");
        assertNotNull(loadedConfig.nestedMap, "Nested map should not be null");
        assertEquals("nested value", loadedConfig.nestedMap.get("nestedString"), "Nested string should match");
        assertEquals(42, ((Number) loadedConfig.nestedMap.get("nestedInt")).intValue(), "Nested int should match");
    }

    @Test
    @DisplayName("Load initial - Should create default when file does not exist")
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
    @DisplayName("Special characters - Should handle special characters correctly")
    void saveAndLoad_shouldHandleSpecialCharactersCorrectly() throws Exception {
        // Arrange
        testConfig.specialChars = "Special chars: !@#$%^&*()_+{}[]|\\:;\"'<>,.?/\n\t\r";

        // Act - Save
        delegate.saveSimple(testConfig, configPath);

        // Assert - File exists and contains escaped special characters
        assertTrue(Files.exists(configPath), "Config file should be created");
        String content = Files.readString(configPath);

        // Check special characters are properly escaped
        assertTrue(
                content.contains("\"special-chars\" : \"Special chars: !@#$%^&*()_+{}[]|\\\\:;\\\"'<>,.?/\\n\\t\\r\""),
                "JSON should contain properly escaped special characters");

        // Act - Load into a new instance
        TestConfig loadedConfig = new TestConfig();
        delegate.tryLoadFromFile(loadedConfig);

        // Assert - Special characters loaded correctly
        assertEquals(testConfig.specialChars, loadedConfig.specialChars,
                "Special characters should be preserved after save/load");
    }

    @Test
    @DisplayName("Empty and null values - Should handle empty and null values correctly")
    void saveAndLoad_shouldHandleEmptyAndNullValuesCorrectly() throws Exception {
        // Arrange
        testConfig.emptyString = "";
        testConfig.nullString = null;
        testConfig.stringList = new ArrayList<>(); // Empty list
        testConfig.stringMap = new HashMap<>(); // Empty map

        // Act - Save
        delegate.saveSimple(testConfig, configPath);

        // Assert - File exists and handles empty/null values
        assertTrue(Files.exists(configPath), "Config file should be created");
        String content = Files.readString(configPath);

        // Check empty values
        assertTrue(content.contains("\"empty-string\" : \"\""),
                "JSON should contain empty string");
        // Null values should be omitted from JSON
        assertFalse(content.contains("\"null-string\""),
                "JSON should not contain null string");
        assertTrue(content.contains("\"string-list\" : [ ]"),
                "JSON should contain empty list");
        assertTrue(content.contains("\"string-map\" : { }"),
                "JSON should contain empty map");

        // Act - Load into a new instance
        TestConfig loadedConfig = new TestConfig();
        // Note: We're not setting nullString here, as it should remain the default
        // value
        delegate.tryLoadFromFile(loadedConfig);

        // Assert - Empty/null values loaded correctly
        assertEquals("", loadedConfig.emptyString, "Empty string should be preserved");
        // Since null values are omitted from JSON, the field will retain its default
        // value (null)
        assertNull(loadedConfig.nullString, "Null string should retain default value (null)");
        assertTrue(loadedConfig.stringList.isEmpty(), "String list should be empty");
        assertTrue(loadedConfig.stringMap.isEmpty(), "String map should be empty");
    }

    @Test
    @DisplayName("Very long string - Should handle very long strings correctly")
    void saveAndLoad_shouldHandleVeryLongStringsCorrectly() throws Exception {
        // Arrange
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("Long string content ").append(i).append(" ");
        }
        testConfig.veryLongString = sb.toString();

        // Act - Save
        delegate.saveSimple(testConfig, configPath);

        // Assert - File exists and contains long string
        assertTrue(Files.exists(configPath), "Config file should be created");

        // Act - Load into a new instance
        TestConfig loadedConfig = new TestConfig();
        delegate.tryLoadFromFile(loadedConfig);

        // Assert - Long string loaded correctly
        assertEquals(testConfig.veryLongString, loadedConfig.veryLongString,
                "Very long string should be preserved after save/load");
        assertTrue(loadedConfig.veryLongString.length() > 9000,
                "Loaded string should maintain its length");
    }

    @Test
    @DisplayName("Try load from file - Should load existing file")
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
    @DisplayName("Malformed JSON - Should handle malformed input files")
    void tryLoadFromFile_shouldHandleMalformedInputFiles() throws Exception {
        // Arrange - Create malformed JSON file
        String malformedJson = "{\n" +
                "  \"string-value\" : \"broken value\",\n" +
                "  \"int-value\" : 99,\n" +
                "  \"bool-value\" : true\n" + // Missing closing brace
                "";
        Files.writeString(configPath, malformedJson);

        // Act & Assert - Should throw exception for malformed JSON
        Exception exception = assertThrows(Exception.class, () -> {
            delegate.tryLoadFromFile(testConfig);
        }, "Should throw exception for malformed JSON");

        // Verify exception message contains useful information
        String errorMessage = exception.getMessage();
        assertTrue(errorMessage != null &&
                (errorMessage.contains("JSON") || errorMessage.contains("parse") ||
                        errorMessage.contains("syntax") || errorMessage.contains("Unexpected end")),
                "Exception should mention parsing error: " + errorMessage);
    }

    @Test
    @DisplayName("Empty file - Should handle empty input files")
    void tryLoadFromFile_shouldHandleEmptyInputFiles() throws Exception {
        // Arrange - Create empty file
        Files.writeString(configPath, "");

        // Act & Assert - Should throw exception for empty file
        Exception exception = assertThrows(Exception.class, () -> {
            delegate.tryLoadFromFile(testConfig);
        }, "Should throw exception for empty file");

        // Verify exception message contains useful information
        String errorMessage = exception.getMessage();
        assertTrue(errorMessage != null &&
                (errorMessage.contains("end-of-input") || errorMessage.contains("empty")),
                "Exception should mention empty file or end-of-input: " + errorMessage);

        // Values should remain unchanged
        assertEquals("default", testConfig.stringValue);
        assertEquals(0, testConfig.intValue);
        assertFalse(testConfig.boolValue);
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
    @DisplayName("Reload - Should update instance fields")
    void reload_shouldUpdateInstanceFields() throws IOException {
        // Arrange
        String json = "{\n" +
                "  \"string-value\" : \"updated value\",\n" +
                "  \"int-value\" : 123,\n" +
                "  \"bool-value\" : true,\n" +
                "  \"double-value\" : 2.71828,\n" +
                "  \"long-value\" : 1234567890,\n" +
                "  \"string-list\" : [ \"reload1\", \"reload2\" ],\n" +
                "  \"string-map\" : { \"key1\" : \"reload value\" }\n" +
                "}";
        Files.writeString(configPath, json);

        // Act
        delegate.reload(testConfig);

        // Assert - Basic types
        assertEquals("updated value", testConfig.stringValue, "String value should be updated");
        assertEquals(123, testConfig.intValue, "Int value should be updated");
        assertTrue(testConfig.boolValue, "Boolean value should be updated");
        assertEquals(2.71828, testConfig.doubleValue, 0.00001, "Double value should be updated");
        assertEquals(1234567890, testConfig.longValue, "Long value should be updated");

        // Assert - Collections
        assertEquals(2, testConfig.stringList.size(), "String list should have 2 items");
        assertEquals("reload1", testConfig.stringList.get(0), "First string list item should match");
        assertEquals(1, testConfig.stringMap.size(), "String map should have 1 entry");
        assertEquals("reload value", testConfig.stringMap.get("key1"), "Map value should match");
    }

    @Test
    @DisplayName("Concurrent access - Should handle concurrent access safely")
    void concurrentAccess_shouldHandleConcurrentAccessSafely() throws Exception {
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
                    Path threadPath = tempDir.resolve("thread-" + threadNum + ".json");
                    JsonPersistenceDelegate<TestConfig> threadDelegate = new JsonPersistenceDelegate<>(threadPath,
                            false);

                    // Save and load
                    threadDelegate.saveSimple(threadConfig, threadPath);
                    threadDelegate.reload(threadConfig);

                    // Verify
                    if (!threadConfig.stringValue.equals("Thread " + threadNum)) {
                        throw new AssertionError("String value mismatch in thread " + threadNum);
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

        // Assert
        assertTrue(completed, "All threads should complete within timeout");
        assertTrue(exceptions.isEmpty(),
                "No exceptions should occur during concurrent access: " + exceptions);
    }

    // Test implementation of ConfigurablePojo with various field types
    @Comment({ "Test configuration class", "Used for testing JSON persistence" })
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
    }
}
