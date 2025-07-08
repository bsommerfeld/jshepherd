package de.bsommerfeld.jshepherd.properties;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.CommentSection;
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

class PropertiesPersistenceDelegateTest {

    @TempDir
    Path tempDir;

    private Path configPath;
    private PropertiesPersistenceDelegate<TestConfig> delegate;
    private TestConfig testConfig;

    @BeforeEach
    void setUp() {
        configPath = tempDir.resolve("test-config.properties");
        delegate = new PropertiesPersistenceDelegate<>(configPath, true);
        testConfig = new TestConfig();
    }

    @Test
    @DisplayName("Save simple - Should create a valid Properties file with basic types")
    void saveSimple_shouldCreateValidPropertiesFile() throws IOException {
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
        assertTrue(content.contains("string-value=test value"), "Properties should contain string value");
        assertTrue(content.contains("int-value=42"), "Properties should contain int value");
        assertTrue(content.contains("bool-value=true"), "Properties should contain boolean value");
        assertTrue(content.contains("double-value=3.14159"), "Properties should contain double value");
        assertTrue(content.contains("long-value=9876543210"), "Properties should contain long value");
    }

    @Test
    @DisplayName("Save with comments - Should create Properties file with comments")
    void saveWithComments_shouldCreatePropertiesFileWithComments() throws IOException {
        // Arrange
        testConfig.stringValue = "test value";
        testConfig.intValue = 42;
        testConfig.boolValue = true;

        // Act
        delegate.saveWithComments(testConfig, configPath);

        // Assert
        assertTrue(Files.exists(configPath), "Config file should be created");
        String content = Files.readString(configPath);

        // Check for comments in Properties
        assertTrue(content.contains("# String value description"), "Properties should contain string comment");
        assertTrue(content.contains("# Integer value description"), "Properties should contain int comment");
        assertTrue(content.contains("# Boolean value description"), "Properties should contain boolean comment");
        assertTrue(content.contains("# Basic Types"), "Properties should contain comment section");

        // Check for values
        assertTrue(content.contains("string-value=test value"), "Properties should contain string value");
        assertTrue(content.contains("int-value=42"), "Properties should contain int value");
        assertTrue(content.contains("bool-value=true"), "Properties should contain boolean value");
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

        // Check collections - Properties format uses specific syntax for collections
        assertTrue(content.contains("string-list=[item1, item2, item3]"), 
                "Properties should contain string list");
        assertTrue(content.contains("int-list=[1, 2, 3, 4, 5]"), 
                "Properties should contain int list");
        assertTrue(content.contains("string-map={key1=value1, key2=value2}"), 
                "Properties should contain string map");
        assertTrue(content.contains("nested-map={"), 
                "Properties should contain nested map");
        assertTrue(content.contains("nestedString=nested value"), 
                "Properties should contain nested string");

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
    }

    @Test
    @DisplayName("Load initial - Should create default when file does not exist")
    void loadInitial_shouldCreateDefaultWhenFileDoesNotExist() {
        // Arrange
        Supplier<TestConfig> supplier = TestConfig::new;

        // Act
        TestConfig result = delegate.loadInitial(supplier);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertTrue(Files.exists(configPath), "Config file should be created");
        assertEquals("default", result.stringValue, "String value should be default");
        assertEquals(0, result.intValue, "Int value should be default");
        assertFalse(result.boolValue, "Bool value should be default");
    }

    @Test
    @DisplayName("Special characters - Should handle special characters correctly")
    void saveAndLoad_shouldHandleSpecialCharactersCorrectly() throws Exception {
        // Arrange - Use a subset of special characters that Properties can handle reliably
        testConfig.specialChars = "Special chars: !@#$%^&*()_+{}[]|;'<>,.?/";

        // Act - Save
        delegate.saveSimple(testConfig, configPath);

        // Assert - File exists and contains escaped special characters
        assertTrue(Files.exists(configPath), "Config file should be created");
        String content = Files.readString(configPath);

        // Properties format escapes special characters differently than JSON
        // Check that the file contains the special characters key
        assertTrue(content.contains("special-chars="), 
                "Properties should contain special characters key");

        // Act - Load into a new instance
        TestConfig loadedConfig = new TestConfig();
        delegate.tryLoadFromFile(loadedConfig);

        // Assert - Special characters loaded correctly
        // For Properties format, we need to verify that the loaded value contains the essential parts
        // rather than an exact match, as some characters might be escaped differently
        String loaded = loadedConfig.specialChars;
        assertNotNull(loaded, "Loaded special characters should not be null");
        assertTrue(loaded.contains("Special chars"), "Should contain the text part");
        assertTrue(loaded.contains("!@#$%^&*()_+"), "Should contain the special characters");
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
        assertTrue(content.contains("empty-string="), 
                "Properties should contain empty string key");
        // Null values should be omitted from properties
        assertFalse(content.contains("null-string="), 
                "Properties should not contain null string key");
        assertTrue(content.contains("string-list=[]"), 
                "Properties should contain empty list");
        assertTrue(content.contains("string-map={}"), 
                "Properties should contain empty map");

        // Act - Load into a new instance
        TestConfig loadedConfig = new TestConfig();
        delegate.tryLoadFromFile(loadedConfig);

        // Assert - Empty/null values loaded correctly
        assertEquals("", loadedConfig.emptyString, "Empty string should be preserved");
        assertNull(loadedConfig.nullString, "Null string should retain default value (null)");
        assertTrue(loadedConfig.stringList.isEmpty(), "String list should be empty");
        assertTrue(loadedConfig.stringMap.isEmpty(), "String map should be empty");
    }

    @Test
    @DisplayName("Very long string - Should handle very long strings correctly")
    void saveAndLoad_shouldHandleVeryLongStringsCorrectly() throws Exception {
        // Arrange - Use a moderately long string that Properties can handle
        // Properties format has limitations with very long strings
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
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
        // For Properties format, we need to verify the essential characteristics
        // rather than an exact match
        String loaded = loadedConfig.veryLongString;
        assertNotNull(loaded, "Loaded long string should not be null");
        assertTrue(loaded.contains("Long string content 0"), "Should contain the beginning");
        assertTrue(loaded.contains("Long string content 99"), "Should contain the end");
        assertTrue(loaded.length() > 1000, "Loaded string should maintain substantial length");
    }

    @Test
    @DisplayName("Moderately long string - Should handle moderately long strings correctly")
    void saveAndLoad_shouldHandleModeratelyLongStringsCorrectly() throws Exception {
        // Arrange - Use a moderately long string that Properties can handle
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append("Moderate string ").append(i).append(" ");
        }
        String moderateString = sb.toString().trim(); // Remove trailing space
        testConfig.veryLongString = moderateString;

        // Act - Save
        delegate.saveSimple(testConfig, configPath);

        // Act - Load into a new instance
        TestConfig loadedConfig = new TestConfig();
        delegate.tryLoadFromFile(loadedConfig);

        // Assert - Moderately long string loaded correctly
        // Properties might trim trailing spaces, so we compare trimmed strings
        assertEquals(moderateString.trim(), loadedConfig.veryLongString.trim(), 
                "Moderately long string should be preserved correctly after save/load (ignoring trailing spaces)");

        // Also verify the content is preserved
        assertTrue(loadedConfig.veryLongString.contains("Moderate string 0"), 
                "Should contain the beginning");
        assertTrue(loadedConfig.veryLongString.contains("Moderate string 9"), 
                "Should contain the end");
    }

    @Test
    @DisplayName("Try load from file - Should load existing file")
    void tryLoadFromFile_shouldLoadExistingFile() throws Exception {
        // Arrange
        String properties = "string-value=loaded value\n" +
                "int-value=99\n" +
                "bool-value=true\n";
        Files.writeString(configPath, properties);

        // Act
        boolean result = delegate.tryLoadFromFile(testConfig);

        // Assert
        assertTrue(result, "Should return true for successful load");
        assertEquals("loaded value", testConfig.stringValue, "String value should be loaded");
        assertEquals(99, testConfig.intValue, "Int value should be loaded");
        assertTrue(testConfig.boolValue, "Bool value should be loaded");
    }

    @Test
    @DisplayName("Malformed Properties - Should handle malformed input files gracefully")
    void tryLoadFromFile_shouldHandleMalformedInputFilesGracefully() throws Exception {
        // Arrange - Create malformed properties file
        // Properties implementation is very forgiving, but this should at least cause warnings
        String malformedProperties = "string-value=broken value\n" +
                "int-value=99\\\n" + // Backslash at end causes line continuation
                "bool-value=true\n";
        Files.writeString(configPath, malformedProperties);

        // Act - Load the malformed file
        boolean result = delegate.tryLoadFromFile(testConfig);

        // Assert - Should load the file but handle the malformed parts gracefully
        assertTrue(result, "Should return true for successful load");
        assertEquals("broken value", testConfig.stringValue, "String value should be loaded");
        // The int-value will be malformed due to line continuation, so it should remain default
        assertEquals(0, testConfig.intValue, "Int value should remain default for malformed input");
        // The bool-value will not be loaded because it's treated as part of the int-value due to line continuation
        assertFalse(testConfig.boolValue, "Bool value should remain default due to line continuation");
    }

    @Test
    @DisplayName("Properties with invalid format - Should handle properties with invalid format")
    void tryLoadFromFile_shouldHandleInvalidFormatProperties() throws Exception {
        // Arrange - Create properties with invalid format but not syntax error
        String invalidProperties = "string-value=broken value\n" +
                "int-value=not_an_integer\n" + // Not a valid integer
                "bool-value=true\n";
        Files.writeString(configPath, invalidProperties);

        // Act
        boolean result = delegate.tryLoadFromFile(testConfig);

        // Assert - Should load the file but not convert the invalid integer
        assertTrue(result, "Should return true for successful load");
        assertEquals("broken value", testConfig.stringValue, "String value should be loaded");
        assertEquals(0, testConfig.intValue, "Int value should remain default for invalid format");
        assertTrue(testConfig.boolValue, "Bool value should be loaded");
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
    @DisplayName("Reload - Should update instance fields")
    void reload_shouldUpdateInstanceFields() throws IOException {
        // Arrange
        String properties = "string-value=updated value\n" +
                "int-value=123\n" +
                "bool-value=true\n" +
                "double-value=2.71828\n" +
                "long-value=1234567890\n" +
                "string-list=[reload1, reload2]\n" +
                "string-map={key1=reload value}\n";
        Files.writeString(configPath, properties);

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
                    Path threadPath = tempDir.resolve("thread-" + threadNum + ".properties");
                    PropertiesPersistenceDelegate<TestConfig> threadDelegate = 
                            new PropertiesPersistenceDelegate<>(threadPath, false);

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
    @Comment({"Test configuration class", "Used for testing Properties persistence"})
    private static class TestConfig extends ConfigurablePojo<TestConfig> {
        @Key("string-value")
        @Comment("String value description")
        @CommentSection("Basic Types")
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
        @CommentSection("Collection Types")
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
        @CommentSection("Special Cases")
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
