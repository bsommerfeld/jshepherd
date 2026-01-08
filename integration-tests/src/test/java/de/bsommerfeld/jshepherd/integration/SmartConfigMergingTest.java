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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-format integration tests for Smart Config Merging.
 * Tests that all format implementations correctly handle:
 * - Obsolete keys being removed on save
 * - New fields getting default values
 * - User-modified values being preserved
 */
class SmartConfigMergingTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Each test gets a fresh temp directory
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml"})
    @DisplayName("Obsolete keys are removed when saving")
    void obsoleteKeysRemovedOnSave(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);

        // Create config file with obsolete keys
        String content = createConfigWithObsoleteKey(format);
        Files.writeString(configPath, content);

        // Load and save - obsolete keys should be dropped
        TestConfig config = ConfigurationLoader.from(configPath)
            .withoutComments()
            .load(TestConfig::new);

        assertEquals("user value", config.stringValue, "User value should be loaded");
        assertEquals(42, config.intValue, "User int should be loaded");

        config.save();

        // Verify obsolete key is gone
        String savedContent = Files.readString(configPath);
        assertFalse(savedContent.contains("obsolete"),
            "Obsolete key should be removed after save for " + format);
        assertTrue(savedContent.contains("user value") || savedContent.contains("user-value"),
            "User value should still exist for " + format);
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml"})
    @DisplayName("New fields get default values when loading partial config")
    void newFieldsGetDefaultValues(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);

        // Create config file with only some fields
        String content = createPartialConfig(format);
        Files.writeString(configPath, content);

        // Load - missing fields should have defaults
        TestConfig config = ConfigurationLoader.from(configPath)
            .withoutComments()
            .load(TestConfig::new);

        assertEquals("existing", config.stringValue, "Existing value should be loaded");
        // boolValue was not in file, should be default (false)
        assertFalse(config.boolValue, "Missing field should have default value for " + format);
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml"})
    @DisplayName("User-modified values are preserved on reload")
    void userModifiedValuesPreservedOnReload(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);

        // Create initial config
        TestConfig config = ConfigurationLoader.from(configPath)
            .withoutComments()
            .load(TestConfig::new);
        config.stringValue = "initial";
        config.save();

        // Simulate user editing the file
        String content = Files.readString(configPath);
        content = content.replace("initial", "USER MODIFIED");
        Files.writeString(configPath, content);

        // Reload - should pick up user modification
        config.reload();

        assertEquals("USER MODIFIED", config.stringValue,
            "User modification should be loaded for " + format);
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml"})
    @DisplayName("Full cycle: create, modify, reload, add field, save")
    void fullCycleIntegration(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);

        // Step 1: Create initial config with defaults
        TestConfig config = ConfigurationLoader.from(configPath)
            .withoutComments()
            .load(TestConfig::new);
        config.stringValue = "original";
        config.intValue = 100;
        config.save();

        // Step 2: Simulate user modifying the file
        String content = Files.readString(configPath);
        content = content.replace("original", "user-changed");
        Files.writeString(configPath, content);

        // Step 3: Reload - user change should be preserved
        config.reload();
        assertEquals("user-changed", config.stringValue,
            "User change should be preserved after reload for " + format);

        // Step 4: App modifies another value and saves
        config.doubleValue = 42.5;
        config.save();

        // Step 5: Verify both user change AND app change exist
        String finalContent = Files.readString(configPath);
        assertTrue(finalContent.contains("user-changed"),
            "User-modified value should still be in file for " + format);
        assertTrue(finalContent.contains("42.5"),
            "App-modified value should be in file for " + format);
    }

    // ==================== HELPER METHODS ====================

    private String createConfigWithObsoleteKey(String format) {
        return switch (format) {
            case "yaml" -> """
                string-value: user value
                int-value: 42
                obsolete-key: should disappear
                bool-value: true
                """;
            case "json" -> """
                {
                  "string-value": "user value",
                  "int-value": 42,
                  "obsolete-key": "should disappear",
                  "bool-value": true
                }
                """;
            case "toml" -> """
                string-value = "user value"
                int-value = 42
                obsolete-key = "should disappear"
                bool-value = true
                """;
            default -> throw new IllegalArgumentException("Unknown format: " + format);
        };
    }

    private String createPartialConfig(String format) {
        return switch (format) {
            case "yaml" -> """
                string-value: existing
                int-value: 99
                """;
            case "json" -> """
                {
                  "string-value": "existing",
                  "int-value": 99
                }
                """;
            case "toml" -> """
                string-value = "existing"
                int-value = 99
                """;
            default -> throw new IllegalArgumentException("Unknown format: " + format);
        };
    }

    // ==================== TEST CONFIG ====================

    @Comment("Test configuration for integration tests")
    public static class TestConfig extends ConfigurablePojo<TestConfig> {
        @Key("string-value")
        @Comment("A string value")
        public String stringValue = "default";

        @Key("int-value")
        @Comment("An integer value")
        public int intValue = 0;

        @Key("bool-value")
        @Comment("A boolean value")
        public boolean boolValue = false;

        @Key("double-value")
        @Comment("A double value")
        public double doubleValue = 0.0;
    }
}
