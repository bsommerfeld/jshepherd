package de.bsommerfeld.jshepherd.properties;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.annotation.Section;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    @DisplayName("Save with comments - Should write comments, keys and sections")
    void saveWithComments_shouldWriteCommentsAndDottedSections() throws IOException {
        delegate.saveWithComments(testConfig, configPath);

        String content = Files.readString(configPath);
        assertTrue(content.contains("# Test configuration"), "Class comment should be written");
        assertTrue(content.contains("# The hostname"), "Field comment should be written");
        assertTrue(content.contains("host=localhost"), "Scalar should be written as key=value");
        assertTrue(content.contains("database.url=jdbc:postgresql://localhost/db"),
                "Section field should use dotted key, got:\n" + content);
        assertTrue(content.contains("flags.darkMode=true"), "Map entries should use dotted keys");
    }

    @Test
    @DisplayName("Round-trip - All supported types should survive save and load")
    void saveAndLoad_shouldRoundTripAllTypes() throws Exception {
        testConfig.host = "example.org";
        testConfig.port = 9090;
        testConfig.ratio = 0.25;
        testConfig.enabled = true;
        testConfig.mode = Mode.FAST;
        testConfig.releaseDate = LocalDate.of(2026, 6, 12);
        testConfig.tags = List.of("a", "b", "c");
        testConfig.weights = List.of(1, 2, 3);
        testConfig.flags = new LinkedHashMap<>(Map.of("darkMode", true));
        testConfig.database.url = "jdbc:h2:mem";
        testConfig.database.poolSize = 42;

        delegate.saveWithComments(testConfig, configPath);

        TestConfig loaded = new TestConfig();
        assertTrue(delegate.tryLoadFromFile(loaded), "File should not be considered empty");

        assertEquals("example.org", loaded.host);
        assertEquals(9090, loaded.port);
        assertEquals(0.25, loaded.ratio);
        assertTrue(loaded.enabled);
        assertEquals(Mode.FAST, loaded.mode);
        assertEquals(LocalDate.of(2026, 6, 12), loaded.releaseDate);
        assertEquals(List.of("a", "b", "c"), loaded.tags);
        assertEquals(List.of(1, 2, 3), loaded.weights, "List elements should be converted to Integer");
        assertEquals(Boolean.TRUE, loaded.flags.get("darkMode"), "Map values should be converted via generic type");
        assertEquals("jdbc:h2:mem", loaded.database.url);
        assertEquals(42, loaded.database.poolSize);
    }

    @Test
    @DisplayName("Load - Missing keys should keep Java defaults")
    void load_shouldKeepDefaultsForMissingKeys() throws Exception {
        Files.writeString(configPath, "host=changed\n");

        TestConfig loaded = new TestConfig();
        assertTrue(delegate.tryLoadFromFile(loaded));

        assertEquals("changed", loaded.host);
        assertEquals(8080, loaded.port, "Absent key should keep default");
        assertEquals("jdbc:postgresql://localhost/db", loaded.database.url,
                "Absent section should keep defaults");
    }

    @Test
    @DisplayName("Load - Empty file should report false")
    void load_shouldReturnFalseForEmptyFile() throws Exception {
        Files.writeString(configPath, "");
        assertFalse(delegate.tryLoadFromFile(new TestConfig()));
    }

    @Test
    @DisplayName("Load - Empty list value should produce empty list")
    void load_shouldParseEmptyList() throws Exception {
        Files.writeString(configPath, "tags=\n");

        TestConfig loaded = new TestConfig();
        delegate.tryLoadFromFile(loaded);

        assertTrue(loaded.tags.isEmpty(), "Blank value should mean empty list");
    }

    @Test
    @DisplayName("Save simple - Should not write any comments")
    void saveSimple_shouldNotWriteComments() throws IOException {
        delegate.saveSimple(testConfig, configPath);

        String content = Files.readString(configPath);
        assertFalse(content.contains("#"), "Simple save should not contain comments, got:\n" + content);
    }

    enum Mode {SLOW, FAST}

    @Comment("Test configuration")
    private static class TestConfig extends ConfigurablePojo<TestConfig> {
        @Key("host")
        @Comment("The hostname")
        private String host = "localhost";

        @Key("port")
        private int port = 8080;

        @Key("ratio")
        private double ratio = 0.5;

        @Key("enabled")
        private boolean enabled = false;

        @Key("mode")
        private Mode mode = Mode.SLOW;

        @Key("release-date")
        private LocalDate releaseDate = LocalDate.of(2020, 1, 1);

        @Key("tags")
        private List<String> tags = new ArrayList<>(List.of("default"));

        @Key("weights")
        private List<Integer> weights = new ArrayList<>();

        @Key("flags")
        private Map<String, Boolean> flags = new LinkedHashMap<>(Map.of("darkMode", true));

        @Comment("Database settings")
        @Section("database")
        private DatabaseSettings database = new DatabaseSettings();
    }

    private static class DatabaseSettings {
        @Key("url")
        private String url = "jdbc:postgresql://localhost/db";

        @Key("pool-size")
        private int poolSize = 10;
    }
}
