package de.bsommerfeld.jshepherd.integration;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.Configuration;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.annotation.PostInject;
import de.bsommerfeld.jshepherd.annotation.Section;
import de.bsommerfeld.jshepherd.core.Config;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.ConfigurationException;
import de.bsommerfeld.jshepherd.core.ConfigurationLoader;
import de.bsommerfeld.jshepherd.core.FieldVisibility;
import de.bsommerfeld.jshepherd.core.LoadIssue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-format tests for {@code show(...)}/{@code hide(...)}: hidden fields and
 * sections stay out of the file until the configuration shows them — typically
 * from a {@code @PostInject} method reacting to a value the user changed.
 */
class FieldVisibilityTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "Format: {0}, comments: {1}")
    @CsvSource({"yaml,true", "yaml,false", "json,true", "json,false",
            "toml,true", "toml,false", "properties,true", "properties,false"})
    @DisplayName("Fields hidden in the constructor are absent from the default file; show()/hide() apply on save")
    void visibilityAppliesOnSave(String format, boolean comments) throws IOException {
        Path configPath = tempDir.resolve("config." + format);
        DebugConfig config = load(configPath, comments);

        assertDebugOptionsHidden(configPath);
        assertFalse(Files.readString(configPath).contains("cured-gate"));

        config.show("log-changes", "debug-options", "database.trace-sql");
        config.save();
        assertDebugOptionsShown(configPath);
        assertFalse(Files.readString(configPath).contains("cured-gate"), "Only the shown keys appear");

        config.hide("log-changes", "debug-options", "database.trace-sql");
        config.save();
        assertDebugOptionsHidden(configPath);
    }

    @Test
    @DisplayName("JSON: hidden fields are left out of the generated documentation too")
    void jsonDocumentationHonorsVisibility() throws IOException {
        Path configPath = tempDir.resolve("config.json");
        Path docPath = tempDir.resolve("config-config-documentation.md");
        DebugConfig config = load(configPath, true);

        String hiddenDoc = Files.readString(docPath);
        assertFalse(hiddenDoc.contains("log-changes"), hiddenDoc);
        assertFalse(hiddenDoc.contains("debug-options"), hiddenDoc);
        assertFalse(hiddenDoc.contains("trace-sql"), hiddenDoc);

        config.show("log-changes", "debug-options", "database.trace-sql");
        config.save();

        String shownDoc = Files.readString(docPath);
        assertTrue(shownDoc.contains("log-changes"), shownDoc);
        assertTrue(shownDoc.contains("debug-options.show-referrals"), shownDoc);
        assertTrue(shownDoc.contains("database.trace-sql"), shownDoc);
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml", "properties"})
    @DisplayName("reload() rewrites the file when @PostInject reacts to a flag the user flipped")
    void reloadShowsAndHidesWithoutExplicitSave(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);
        DebugConfig config = load(configPath, true);

        setValueInFile(configPath, "debug", "false", "true");
        config.reload();

        assertTrue(config.debug);
        assertDebugOptionsShown(configPath);

        setValueInFile(configPath, "debug", "true", "false");
        config.reload();

        assertFalse(config.debug);
        assertDebugOptionsHidden(configPath);
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml", "properties"})
    @DisplayName("Visibility can depend on any condition, e.g. a numeric threshold")
    void visibilityFollowsArbitraryConditions(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);
        DebugConfig config = load(configPath, true);

        setValueInFile(configPath, "max-count", "0.5", "0.7");
        config.reload();

        String content = Files.readString(configPath);
        assertTrue(content.contains("cured-gate"), "Threshold reached, field should appear:\n" + content);
        assertDebugOptionsHidden(configPath);
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml", "properties"})
    @DisplayName("A flag flipped while the application was down takes effect on the initial load")
    void initialLoadShowsFieldsForFlagSetInFile(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);
        load(configPath, true);
        setValueInFile(configPath, "debug", "false", "true");

        DebugConfig config = load(configPath, true);

        assertTrue(config.debug);
        assertDebugOptionsShown(configPath);
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml", "properties"})
    @DisplayName("Shown values round-trip, and loading an up-to-date file does not touch it")
    void shownValuesRoundTripWithoutRewritingTheFile(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);
        DebugConfig config = load(configPath, true);
        setValueInFile(configPath, "debug", "false", "true");
        config.reload();
        config.logChanges = true;
        config.debugOptions.showReferrals = false;
        config.database.traceSql = true;
        config.save();

        FileTime longAgo = FileTime.from(Instant.parse("2020-01-01T00:00:00Z"));
        Files.setLastModifiedTime(configPath, longAgo);

        DebugConfig reloaded = load(configPath, true);

        assertTrue(reloaded.logChanges);
        assertFalse(reloaded.debugOptions.showReferrals);
        assertTrue(reloaded.database.traceSql);
        assertEquals(longAgo, Files.getLastModifiedTime(configPath),
                "A file that already shows the right fields must not be rewritten for " + format);
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml", "properties"})
    @DisplayName("Hidden fields present in the file are still read, and dropped on the next save")
    void hiddenFieldsInFileAreStillRead(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);
        DebugConfig writer = load(configPath, true);
        writer.show("log-changes");
        writer.logChanges = true;
        writer.save();

        DebugConfig config = load(configPath, true);

        assertFalse(config.debug);
        assertTrue(config.logChanges, "Value of a hidden field should still be loaded for " + format);
        assertTrue(Files.readString(configPath).contains("log-changes"), "Loading alone does not drop it");

        config.save();
        assertDebugOptionsHidden(configPath);
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml", "properties"})
    @DisplayName("Auto-reload shows the fields as soon as the user flips the flag")
    void autoReloadShowsFields(String format) throws Exception {
        Path configPath = tempDir.resolve("config." + format);
        DebugConfig config = ConfigurationLoader.from(configPath)
                .withComments()
                .withAutoReload(Duration.ofMillis(50))
                .load(DebugConfig::new);

        CountDownLatch reloaded = new CountDownLatch(1);
        config.setOnAutoReload(reloaded::countDown);

        try {
            setValueInFile(configPath, "debug", "false", "true");

            assertTrue(reloaded.await(5, TimeUnit.SECONDS), "Auto-reload should fire for " + format);
            assertTrue(config.debug);
            assertDebugOptionsShown(configPath);
        } finally {
            config.stopAutoReload();
        }
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml", "properties"})
    @DisplayName("Plain @Configuration POJOs control visibility via a FieldVisibility parameter and the handle")
    void plainPojoControlsVisibility(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);
        Config<PlainDebugConfig> config = ConfigurationLoader.from(configPath).loadPlain(PlainDebugConfig::new);

        assertFalse(Files.readString(configPath).contains("log-changes"),
                "@PostInject hides the field right after the default file was created");
        assertNotNull(config.get().receivedIssues, "List<LoadIssue> and FieldVisibility can be combined");

        setValueInFile(configPath, "debug", "false", "true");
        config.reload();

        assertTrue(config.get().debug);
        assertTrue(Files.readString(configPath).contains("log-changes"));

        config.hide("log-changes");
        config.save();
        assertFalse(Files.readString(configPath).contains("log-changes"));
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml", "properties"})
    @DisplayName("bindVisibility(): fields follow their condition on load and reload, without @PostInject")
    void boundFieldsFollowTheirConditionOnLoadAndReload(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);
        loadBound(configPath);
        assertDebugOptionsHidden(configPath);
        assertFalse(Files.readString(configPath).contains("cured-gate"));

        // Flipped while the application was down
        setValueInFile(configPath, "debug", "false", "true");
        BoundDebugConfig config = loadBound(configPath);
        assertDebugOptionsShown(configPath);
        assertFalse(Files.readString(configPath).contains("cured-gate"), "Only satisfied conditions show");

        setValueInFile(configPath, "debug", "true", "false");
        setValueInFile(configPath, "max-count", "0.5", "0.7");
        config.reload();
        assertDebugOptionsHidden(configPath);
        assertTrue(Files.readString(configPath).contains("cured-gate"));
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml", "properties"})
    @DisplayName("bindVisibility(): conditions are re-evaluated on save() for values changed in code")
    void boundFieldsFollowValuesChangedInCode(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);
        BoundDebugConfig config = loadBound(configPath);

        config.debug = true;
        config.save();
        assertDebugOptionsShown(configPath);

        config.debug = false;
        config.save();
        assertDebugOptionsHidden(configPath);
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml", "properties"})
    @DisplayName("bindVisibility(): loading a file that already shows the right fields does not touch it")
    void boundFieldsDoNotRewriteAnUpToDateFile(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);
        BoundDebugConfig config = loadBound(configPath);
        config.debug = true;
        config.debugOptions.showReferrals = false;
        config.save();

        FileTime longAgo = FileTime.from(Instant.parse("2020-01-01T00:00:00Z"));
        Files.setLastModifiedTime(configPath, longAgo);

        BoundDebugConfig reloaded = loadBound(configPath);

        assertFalse(reloaded.debugOptions.showReferrals);
        assertEquals(longAgo, Files.getLastModifiedTime(configPath),
                "A file that already shows the right fields must not be rewritten for " + format);
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml", "properties"})
    @DisplayName("bindVisibility(): auto-reload shows the fields as soon as the user flips the flag")
    void autoReloadShowsBoundFields(String format) throws Exception {
        Path configPath = tempDir.resolve("config." + format);
        BoundDebugConfig config = ConfigurationLoader.from(configPath)
                .withAutoReload(Duration.ofMillis(50))
                .load(BoundDebugConfig::new);

        CountDownLatch reloaded = new CountDownLatch(1);
        config.setOnAutoReload(reloaded::countDown);

        try {
            setValueInFile(configPath, "debug", "false", "true");

            assertTrue(reloaded.await(5, TimeUnit.SECONDS), "Auto-reload should fire for " + format);
            assertDebugOptionsShown(configPath);
        } finally {
            config.stopAutoReload();
        }
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml", "properties"})
    @DisplayName("bindVisibility(): plain POJOs bind via the FieldVisibility parameter or the handle")
    void plainPojoBindsVisibility(String format) throws IOException {
        Path configPath = tempDir.resolve("config." + format);
        Config<PlainBoundConfig> config = ConfigurationLoader.from(configPath).loadPlain(PlainBoundConfig::new);

        assertFalse(Files.readString(configPath).contains("log-changes"),
                "Bound in @PostInject, so hidden right after the default file was created");
        assertTrue(Files.readString(configPath).contains("trace-sql"));

        setValueInFile(configPath, "debug", "false", "true");
        config.reload();
        assertTrue(Files.readString(configPath).contains("log-changes"));

        config.bindVisibility("trace-sql", plain -> plain.debug && plain.logChanges);
        assertTrue(Files.readString(configPath).contains("trace-sql"), "Bound via the handle: applies on save/reload");
        config.save();
        assertFalse(Files.readString(configPath).contains("trace-sql"));

        config.get().logChanges = true;
        config.save();
        assertTrue(Files.readString(configPath).contains("trace-sql"));
    }

    @Test
    @DisplayName("Hiding an unknown key fails fast")
    void unknownKeyFails() {
        Path configPath = tempDir.resolve("config.yaml");

        assertThrows(ConfigurationException.class,
                () -> ConfigurationLoader.from(configPath).load(BrokenConfig::new));
        assertFalse(Files.exists(configPath));
    }

    // ==================== HELPERS ====================

    private DebugConfig load(Path configPath, boolean comments) {
        ConfigurationLoader.Builder builder = ConfigurationLoader.from(configPath);
        return (comments ? builder.withComments() : builder.withoutComments()).load(DebugConfig::new);
    }

    private BoundDebugConfig loadBound(Path configPath) {
        return ConfigurationLoader.from(configPath).withComments().load(BoundDebugConfig::new);
    }

    /**
     * Simulates the user editing a root-level value by hand. Matches the key/value
     * syntax of all formats ({@code debug: false}, {@code "debug" : false},
     * {@code debug = false}, {@code debug=false}).
     */
    private void setValueInFile(Path configPath, String key, String oldValue, String newValue) throws IOException {
        String content = Files.readString(configPath);
        String edited = content.replaceFirst("(?m)^(\\s*\"?" + key + "\"?\\s*[:=]\\s*)" + oldValue, "$1" + newValue);
        assertNotEquals(content, edited, "'" + key + "' line not found in:\n" + content);
        Files.writeString(configPath, edited);
    }

    private void assertDebugOptionsHidden(Path configPath) throws IOException {
        String content = Files.readString(configPath);
        assertFalse(content.contains("log-changes"), "Hidden field should be absent:\n" + content);
        assertFalse(content.contains("debug-options"), "Hidden section should be absent:\n" + content);
        assertFalse(content.contains("show-referrals"), "Hidden section should be absent:\n" + content);
        assertFalse(content.contains("trace-sql"), "Hidden field inside a section should be absent:\n" + content);
        assertFalse(content.contains("Logs all changes"), "Comment of a hidden field should be absent:\n" + content);
        assertTrue(content.contains("pool-size"), "Other fields are unaffected:\n" + content);
    }

    private void assertDebugOptionsShown(Path configPath) throws IOException {
        String content = Files.readString(configPath);
        assertTrue(content.contains("log-changes"), "Shown field should be present:\n" + content);
        assertTrue(content.contains("debug-options"), "Shown section should be present:\n" + content);
        assertTrue(content.contains("show-referrals"), "Shown section should be present:\n" + content);
        assertTrue(content.contains("trace-sql"), "Shown field inside a section should be present:\n" + content);
    }

    // ==================== TEST CONFIGS ====================

    @Comment("Debug test configuration")
    public static class DebugConfig extends ConfigurablePojo<DebugConfig> {

        private static final String CURED_GATE = "cured-gate";
        private static final String[] DEBUG_OPTIONS = {"log-changes", "debug-options", "database.trace-sql"};

        @Key("host")
        @Comment("Server hostname")
        public String host = "localhost";

        @Key("debug")
        @Comment("Whether the debug mode is enabled or not")
        public boolean debug = false;

        @Key("max-count")
        @Comment("From 0.6 on, the cured gate can be configured")
        public double maxCount = 0.5;

        @Key("log-changes")
        @Comment("Logs all changes to the console for debug purposes")
        public boolean logChanges = false;

        @Key(CURED_GATE)
        @Comment("Whether the cured gate is active")
        public boolean curedGate = false;

        @Section("database")
        @Comment("Database settings")
        public DatabaseSettings database = new DatabaseSettings();

        @Section("debug-options")
        @Comment("Debug options, enabled through `debug`")
        public DebugOptions debugOptions = new DebugOptions();

        public DebugConfig() {
            hide(DEBUG_OPTIONS);
            hide(CURED_GATE);
        }

        @PostInject
        private void updateVisibility() {
            if (debug) {
                show(DEBUG_OPTIONS);
            } else {
                hide(DEBUG_OPTIONS);
            }

            if (maxCount >= 0.6) {
                show(CURED_GATE);
            } else {
                hide(CURED_GATE);
            }
        }
    }

    /** Same layout as {@link DebugConfig}, but bound once instead of toggled in {@code @PostInject}. */
    @Comment("Debug test configuration")
    public static class BoundDebugConfig extends ConfigurablePojo<BoundDebugConfig> {

        @Key("host")
        @Comment("Server hostname")
        public String host = "localhost";

        @Key("debug")
        @Comment("Whether the debug mode is enabled or not")
        public boolean debug = false;

        @Key("max-count")
        @Comment("From 0.6 on, the cured gate can be configured")
        public double maxCount = 0.5;

        @Key("log-changes")
        @Comment("Logs all changes to the console for debug purposes")
        public boolean logChanges = false;

        @Key("cured-gate")
        @Comment("Whether the cured gate is active")
        public boolean curedGate = false;

        @Section("database")
        @Comment("Database settings")
        public DatabaseSettings database = new DatabaseSettings();

        @Section("debug-options")
        @Comment("Debug options, enabled through `debug`")
        public DebugOptions debugOptions = new DebugOptions();

        public BoundDebugConfig() {
            bindVisibility("log-changes", () -> debug);
            bindVisibility("debug-options", () -> debug);
            bindVisibility("database.trace-sql", () -> debug);
            bindVisibility("cured-gate", () -> maxCount >= 0.6);
        }
    }

    public static class DatabaseSettings {
        @Key("pool-size")
        @Comment("Connection pool size")
        public int poolSize = 10;

        @Key("trace-sql")
        @Comment("Prints every SQL statement")
        public boolean traceSql = false;
    }

    public static class DebugOptions {
        @Key("show-referrals")
        @Comment("Shows the referrals")
        public boolean showReferrals = true;
    }

    @Configuration
    public static class PlainDebugConfig {
        @Key("debug")
        public boolean debug = false;

        @Key("log-changes")
        public boolean logChanges = false;

        public transient List<LoadIssue> receivedIssues;

        @PostInject
        private void updateVisibility(FieldVisibility fields, List<LoadIssue> issues) {
            receivedIssues = issues;
            if (debug) {
                fields.show("log-changes");
            } else {
                fields.hide("log-changes");
            }
        }
    }

    @Configuration
    public static class PlainBoundConfig {
        @Key("debug")
        public boolean debug = false;

        @Key("log-changes")
        public boolean logChanges = false;

        @Key("trace-sql")
        public boolean traceSql = false;

        @PostInject
        private void bindVisibility(FieldVisibility fields) {
            fields.bindVisibility("log-changes", () -> debug);
        }
    }

    public static class BrokenConfig extends ConfigurablePojo<BrokenConfig> {
        @Key("value")
        public String value = "value";

        public BrokenConfig() {
            hide("does-not-exist");
        }
    }
}
