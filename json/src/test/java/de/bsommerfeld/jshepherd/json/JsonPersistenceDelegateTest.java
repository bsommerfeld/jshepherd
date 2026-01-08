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

    // Note: Tests for collections, loadInitial, special chars, empty/null,
    // very long strings, and reload are now centralized in .integration-tests
    // module

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
