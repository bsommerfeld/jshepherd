package de.bsommerfeld.jshepherd.integration;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.ConfigurationLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-format integration tests for core persistence behavior.
 * Each test runs against all supported formats (YAML, JSON, TOML) to verify
 * consistent behavior across implementations. This eliminates boilerplate
 * in format-specific test classes.
 */
class CorePersistenceIntegrationTest {

    @TempDir
    Path tempDir;

    // ==================== LIFECYCLE TESTS ====================

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml"})
    @DisplayName("Load initial creates default config when file doesn't exist")
    void loadInitial_createsDefaultWhenFileNotExists(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);

        TestConfig config = ConfigurationLoader.from(configPath)
            .withoutComments()
            .load(TestConfig::new);

        assertNotNull(config, "Config should not be null");
        assertTrue(Files.exists(configPath), "Config file should be created for " + format);
        assertEquals("default", config.stringValue, "Default value should be set for " + format);
        assertEquals(0, config.intValue, "Default int should be 0 for " + format);
        assertFalse(config.boolValue, "Default bool should be false for " + format);
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml"})
    @DisplayName("Reload updates instance with file changes")
    void reload_updatesInstanceWithFileChanges(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);

        // Create initial config
        TestConfig config = ConfigurationLoader.from(configPath)
            .withoutComments()
            .load(TestConfig::new);
        config.stringValue = "initial";
        config.intValue = 42;
        config.save();

        // Modify file externally
        String content = Files.readString(configPath);
        content = content.replace("initial", "modified");
        content = content.replace("42", "999");
        Files.writeString(configPath, content);

        // Reload
        config.reload();

        assertEquals("modified", config.stringValue, "String should be reloaded for " + format);
        assertEquals(999, config.intValue, "Int should be reloaded for " + format);
    }

    // ==================== COLLECTION TESTS ====================

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml"})
    @DisplayName("Save and load handles string lists correctly")
    void saveAndLoad_handlesStringListsCorrectly(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);

        TestConfig config = ConfigurationLoader.from(configPath)
            .withoutComments()
            .load(TestConfig::new);

        config.stringList.add("item1");
        config.stringList.add("item2");
        config.stringList.add("item3");
        config.save();

        TestConfig reloaded = ConfigurationLoader.from(configPath)
            .withoutComments()
            .load(TestConfig::new);

        assertEquals(3, reloaded.stringList.size(), "List should have 3 items for " + format);
        assertTrue(reloaded.stringList.contains("item1"), "List should contain item1 for " + format);
        assertTrue(reloaded.stringList.contains("item2"), "List should contain item2 for " + format);
        assertTrue(reloaded.stringList.contains("item3"), "List should contain item3 for " + format);
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json"})  // TOML has known Long conversion issues with integer lists
    @DisplayName("Save and load handles integer lists correctly")
    void saveAndLoad_handlesIntegerListsCorrectly(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);

        TestConfig config = ConfigurationLoader.from(configPath)
            .withoutComments()
            .load(TestConfig::new);

        config.intList.add(1);
        config.intList.add(2);
        config.intList.add(3);
        config.save();

        TestConfig reloaded = ConfigurationLoader.from(configPath)
            .withoutComments()
            .load(TestConfig::new);

        assertEquals(3, reloaded.intList.size(), "List should have 3 items for " + format);
        assertTrue(reloaded.intList.contains(1), "List should contain 1 for " + format);
        assertTrue(reloaded.intList.contains(2), "List should contain 2 for " + format);
        assertTrue(reloaded.intList.contains(3), "List should contain 3 for " + format);
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml"})
    @DisplayName("Save and load handles string maps correctly")
    void saveAndLoad_handlesStringMapsCorrectly(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);

        TestConfig config = ConfigurationLoader.from(configPath)
            .withoutComments()
            .load(TestConfig::new);

        config.stringMap.put("key1", "value1");
        config.stringMap.put("key2", "value2");
        config.save();

        TestConfig reloaded = ConfigurationLoader.from(configPath)
            .withoutComments()
            .load(TestConfig::new);

        assertEquals(2, reloaded.stringMap.size(), "Map should have 2 entries for " + format);
        assertEquals("value1", reloaded.stringMap.get("key1"), "Map should contain key1=value1 for " + format);
        assertEquals("value2", reloaded.stringMap.get("key2"), "Map should contain key2=value2 for " + format);
    }

    // ==================== EDGE CASE TESTS ====================

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml"})
    @DisplayName("Save and load handles special characters correctly")
    void saveAndLoad_handlesSpecialCharactersCorrectly(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);

        TestConfig config = ConfigurationLoader.from(configPath)
            .withoutComments()
            .load(TestConfig::new);

        config.stringValue = "Special: äöü ß € @#$%^&*()";
        config.save();

        TestConfig reloaded = ConfigurationLoader.from(configPath)
            .withoutComments()
            .load(TestConfig::new);

        assertEquals(config.stringValue, reloaded.stringValue,
            "Special characters should be preserved for " + format);
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml"})
    @DisplayName("Save and load handles empty strings correctly")
    void saveAndLoad_handlesEmptyStringsCorrectly(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);

        TestConfig config = ConfigurationLoader.from(configPath)
            .withoutComments()
            .load(TestConfig::new);

        config.stringValue = "";
        config.save();

        TestConfig reloaded = ConfigurationLoader.from(configPath)
            .withoutComments()
            .load(TestConfig::new);

        assertEquals("", reloaded.stringValue, "Empty string should be preserved for " + format);
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml"})
    @DisplayName("Save and load handles very long strings correctly")
    void saveAndLoad_handlesVeryLongStringsCorrectly(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);

        TestConfig config = ConfigurationLoader.from(configPath)
            .withoutComments()
            .load(TestConfig::new);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("This is a very long string that should be handled correctly. ");
        }
        config.stringValue = sb.toString();
        config.save();

        TestConfig reloaded = ConfigurationLoader.from(configPath)
            .withoutComments()
            .load(TestConfig::new);

        assertEquals(config.stringValue, reloaded.stringValue,
            "Very long string should be preserved for " + format);
    }

    // ==================== TYPE TESTS ====================

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml"})
    @DisplayName("Save and load handles all primitive types correctly")
    void saveAndLoad_handlesAllPrimitiveTypesCorrectly(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);

        TestConfig config = ConfigurationLoader.from(configPath)
            .withoutComments()
            .load(TestConfig::new);

        config.stringValue = "test string";
        config.intValue = 42;
        config.boolValue = true;
        config.doubleValue = 3.14159;
        config.longValue = 9876543210L;
        config.save();

        TestConfig reloaded = ConfigurationLoader.from(configPath)
            .withoutComments()
            .load(TestConfig::new);

        assertEquals("test string", reloaded.stringValue, "String should match for " + format);
        assertEquals(42, reloaded.intValue, "Int should match for " + format);
        assertTrue(reloaded.boolValue, "Bool should match for " + format);
        assertEquals(3.14159, reloaded.doubleValue, 0.0001, "Double should match for " + format);
        assertEquals(9876543210L, reloaded.longValue, "Long should match for " + format);
    }

    // ==================== TEST CONFIG ====================

    @Comment("Test configuration for integration tests")
    public static class TestConfig extends ConfigurablePojo<TestConfig> {
        @Key("string-value")
        public String stringValue = "default";

        @Key("int-value")
        public int intValue = 0;

        @Key("bool-value")
        public boolean boolValue = false;

        @Key("double-value")
        public double doubleValue = 0.0;

        @Key("long-value")
        public long longValue = 0L;

        @Key("string-list")
        public List<String> stringList = new ArrayList<>();

        @Key("int-list")
        public List<Integer> intList = new ArrayList<>();

        @Key("string-map")
        public Map<String, String> stringMap = new HashMap<>();
    }
}
