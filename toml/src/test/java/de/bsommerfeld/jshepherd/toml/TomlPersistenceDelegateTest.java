package de.bsommerfeld.jshepherd.toml;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.annotation.Section;
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

class TomlPersistenceDelegateTest {

    @TempDir
    Path tempDir;

    private Path configPath;
    private TomlPersistenceDelegate<TestConfig> delegate;
    private TestConfig testConfig;
    private TestConfigWithSection testConfigWithSection;

    @BeforeEach
    void setUp() {
        configPath = tempDir.resolve("test-config.toml");
        delegate = new TomlPersistenceDelegate<>(configPath, true);
        testConfig = new TestConfig();
    }

    @Test
    @DisplayName("Save simple - Should create a valid TOML file with basic types")
    void saveSimple_shouldCreateValidTomlFile() throws IOException {
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
        assertTrue(content.contains("string-value = \"test value\""), "TOML should contain string value");
        assertTrue(content.contains("int-value = 42"), "TOML should contain int value");
        assertTrue(content.contains("bool-value = true"), "TOML should contain boolean value");
        assertTrue(content.contains("double-value = 3.14159"), "TOML should contain double value");
        assertTrue(content.contains("long-value = 9876543210"), "TOML should contain long value");
    }

    @Test
    @DisplayName("Save with comments - Should create TOML file with comments")
    void saveWithComments_shouldCreateTomlFileWithComments() throws IOException {
        // Arrange
        testConfig.stringValue = "test value";
        testConfig.intValue = 42;
        testConfig.boolValue = true;

        // Act
        delegate.saveWithComments(testConfig, configPath);

        // Assert
        assertTrue(Files.exists(configPath), "Config file should be created");
        String content = Files.readString(configPath);

        // Check for comments in TOML
        assertTrue(content.contains("# String value description"), "TOML should contain string comment");
        assertTrue(content.contains("# Integer value description"), "TOML should contain int comment");
        // Check for values
        assertTrue(content.contains("string-value = \"test value\""), "TOML should contain string value");
        assertTrue(content.contains("int-value = 42"), "TOML should contain int value");
        assertTrue(content.contains("bool-value = true"), "TOML should contain boolean value");
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
    @DisplayName("Try load from file - Should load existing file")
    void tryLoadFromFile_shouldLoadExistingFile() throws Exception {
        // Arrange
        String toml = "string-value = \"loaded value\"\n" +
                "int-value = 99\n" +
                "bool-value = true\n";
        Files.writeString(configPath, toml);

        // Act
        boolean result = delegate.tryLoadFromFile(testConfig);

        // Assert
        assertTrue(result, "Should return true for successful load");
        assertEquals("loaded value", testConfig.stringValue, "String value should be loaded");
        assertEquals(99, testConfig.intValue, "Int value should be loaded");
        assertTrue(testConfig.boolValue, "Bool value should be loaded");
    }

    @Test
    @DisplayName("Reload - Should update instance fields")
    void reload_shouldUpdateInstanceFields() throws IOException {
        // Arrange
        String toml = "string-value = \"updated value\"\n" +
                "int-value = 123\n" +
                "bool-value = true\n" +
                "double-value = 2.71828\n" +
                "long-value = 1234567890\n" +
                "string-list = [\"reload1\", \"reload2\"]\n" +
                "string-map = { key1 = \"reload value\" }\n";
        Files.writeString(configPath, toml);

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

        // Check collections - TOML format uses specific syntax for collections
        assertTrue(content.contains("string-list = ["),
                "TOML should contain string list");
        assertTrue(content.contains("\"item1\""), "TOML should contain list item");
        assertTrue(content.contains("int-list = ["),
                "TOML should contain int list");
        assertTrue(content.contains("1, 2, 3"), "TOML should contain int list items");
        assertTrue(content.contains("[string-map]"),
                "TOML should contain string map table");
        assertTrue(content.contains("key1 = \"value1\""), "TOML should contain map entry");
        assertTrue(content.contains("[nested-map]"),
                "TOML should contain nested map table");
        assertTrue(content.contains("nestedString = \"nested value\""),
                "TOML should contain nested string");

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
    @DisplayName("Special characters - Should handle special characters correctly")
    void saveAndLoad_shouldHandleSpecialCharactersCorrectly() throws Exception {
        // Arrange - TOML has good support for special characters
        testConfig.specialChars = "Special chars: !@#$%^&*()_+{}[]|\\:;\"'<>,.?/\n\t\r";

        // Act - Save
        delegate.saveSimple(testConfig, configPath);

        // Assert - File exists and contains escaped special characters
        assertTrue(Files.exists(configPath), "Config file should be created");
        String content = Files.readString(configPath);

        // Check special characters are properly escaped in TOML format
        assertTrue(content.contains("special-chars = \"Special chars: !@#$%^&*()_+{}[]|\\\\:;\\\"'<>,.?/\\n\\t\\r\""),
                "TOML should contain properly escaped special characters");

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
        assertTrue(content.contains("empty-string = \"\""),
                "TOML should contain empty string");
        // Null values should be omitted from TOML
        assertFalse(content.contains("null-string"),
                "TOML should not contain null string");
        assertTrue(content.contains("string-list = ["),
                "TOML should contain empty list");
        assertTrue(content.contains("[string-map]"),
                "TOML should contain empty map table");

        // Act - Load into a new instance
        TestConfig loadedConfig = new TestConfig();
        delegate.tryLoadFromFile(loadedConfig);

        // Assert - Empty/null values loaded correctly
        assertEquals("", loadedConfig.emptyString, "Empty string should be preserved");
        // Since null values are omitted from TOML, the field will retain its default
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
    @DisplayName("Malformed TOML - Should handle malformed input files")
    void tryLoadFromFile_shouldHandleMalformedInputFiles() throws Exception {
        // Arrange - Create malformed TOML file
        String malformedToml = "string-value = \"broken value\"\n" +
                "int-value = 99\n" +
                "bool-value = true" + // Missing newline after value
                "[invalid-section"; // Invalid section declaration
        Files.writeString(configPath, malformedToml);

        // Act & Assert - Should throw exception for malformed TOML
        Exception exception = assertThrows(Exception.class, () -> {
            delegate.tryLoadFromFile(testConfig);
        }, "Should throw exception for malformed TOML");

        // Verify exception message contains useful information
        String errorMessage = exception.getMessage();
        assertTrue(errorMessage != null &&
                (errorMessage.contains("TOML") || errorMessage.contains("parse") ||
                        errorMessage.contains("syntax")),
                "Exception should mention parsing error: " + errorMessage);
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
                    Path threadPath = tempDir.resolve("thread-" + threadNum + ".toml");
                    TomlPersistenceDelegate<TestConfig> threadDelegate = new TomlPersistenceDelegate<>(threadPath,
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

    @Test
    @DisplayName("TOML Section - Save nested POJO as table")
    void tomlSection_saveNestedPojoAsTable() throws IOException {
        // Arrange
        Path sectionPath = tempDir.resolve("section-config.toml");
        TomlPersistenceDelegate<TestConfigWithSection> sectionDelegate = new TomlPersistenceDelegate<>(sectionPath,
                false);
        TestConfigWithSection cfg = new TestConfigWithSection();
        cfg.app = new InnerSection();
        cfg.app.name = "demo";
        cfg.app.enabled = true;
        cfg.app.ports = Arrays.asList(8080, 8081);

        // Act
        sectionDelegate.saveSimple(cfg, sectionPath);

        // Assert
        String content = Files.readString(sectionPath);
        assertTrue(content.contains("[app]"), "Should contain [app] table");
        assertTrue(content.contains("name = \"demo\""), "Should contain name inside table");
        assertTrue(content.contains("enabled = true"), "Should contain enabled inside table");
        assertTrue(content.contains("ports = ["), "Should contain ports array inside table");
    }

    @Test
    @DisplayName("TOML Section - Load nested POJO from table")
    void tomlSection_loadNestedPojoFromTable() throws Exception {
        // Arrange
        Path sectionPath = tempDir.resolve("section-config-load.toml");
        String toml = "[app]\n" +
                "name = \"loaded\"\n" +
                "enabled = true\n" +
                "ports = [8080, 8082]\n";
        Files.writeString(sectionPath, toml);
        TomlPersistenceDelegate<TestConfigWithSection> sectionDelegate = new TomlPersistenceDelegate<>(sectionPath,
                false);
        TestConfigWithSection cfg = new TestConfigWithSection();

        // Act
        boolean loaded = sectionDelegate.tryLoadFromFile(cfg);

        // Assert
        assertTrue(loaded, "Should load TOML with section");
        assertNotNull(cfg.app, "Nested section object should be instantiated");
        assertEquals("loaded", cfg.app.name);
        assertTrue(cfg.app.enabled);
        assertEquals(Arrays.asList(8080, 8082), cfg.app.ports);
    }

    // Config for TOML Section tests
    private static class TestConfigWithSection extends ConfigurablePojo<TestConfigWithSection> {
        @Key("app")
        @Section
        private InnerSection app;
    }

    private static class InnerSection extends ConfigurablePojo<InnerSection> {
        @Key("name")
        private String name;
        @Key("enabled")
        private boolean enabled;
        @Key("ports")
        private List<Integer> ports = new ArrayList<>();
    }

    // Test implementation of ConfigurablePojo with various field types
    @Comment({ "Test configuration class", "Used for testing TOML persistence" })
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
