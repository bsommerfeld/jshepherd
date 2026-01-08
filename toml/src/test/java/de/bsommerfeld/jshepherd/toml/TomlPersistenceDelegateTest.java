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

    // Note: Tests for loadInitial, reload, collections, special chars, empty/null,
    // and very long strings are now centralized in .integration-tests module

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
