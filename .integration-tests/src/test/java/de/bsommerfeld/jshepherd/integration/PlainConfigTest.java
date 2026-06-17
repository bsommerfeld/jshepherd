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
import de.bsommerfeld.jshepherd.core.LoadIssue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the plain-POJO API: {@code @Configuration} classes that do NOT
 * extend ConfigurablePojo, loaded via loadPlain() and managed through the
 * {@code Config<T>} handle.
 */
class PlainConfigTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml", "properties"})
    @DisplayName("Plain @Configuration POJO round-trips via Config handle in all formats")
    void plainPojo_roundTripsViaHandle(String format) throws Exception {
        Path configPath = tempDir.resolve("plain." + format);

        Config<PlainConfig> config = ConfigurationLoader.from(configPath)
                .withComments()
                .loadPlain(PlainConfig::new);

        assertTrue(Files.exists(configPath), "File should be created with defaults for " + format);
        assertTrue(config.get().postInjectRan, "@PostInject should run on initial load for " + format);

        // Mutate (including a nested section value) and save via the handle
        config.get().host = "example.org";
        config.get().database.poolSize = 77;
        config.save();

        Config<PlainConfig> reloaded = ConfigurationLoader.from(configPath)
                .withComments()
                .loadPlain(PlainConfig::new);

        assertEquals("example.org", reloaded.get().host, "Root value for " + format);
        assertEquals(77, reloaded.get().database.poolSize, "Section value for " + format);
        assertTrue(reloaded.getLastLoadIssues().isEmpty(), "Clean load expected for " + format);
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml", "properties"})
    @DisplayName("Handle reload picks up external file changes")
    void handleReload_picksUpExternalChanges(String format) throws Exception {
        Path configPath = tempDir.resolve("reload." + format);

        Config<PlainConfig> config = ConfigurationLoader.from(configPath)
                .withoutComments()
                .loadPlain(PlainConfig::new);

        String content = Files.readString(configPath);
        Files.writeString(configPath, content.replace("localhost", "changed-host"));

        config.reload();
        assertEquals("changed-host", config.get().host, "Reload should apply external edit for " + format);
    }

    @Test
    @DisplayName("@PostInject can receive List<LoadIssue> as parameter")
    void postInject_receivesLoadIssuesParameter() throws Exception {
        Path configPath = tempDir.resolve("issues.toml");
        Files.writeString(configPath, "port = \"abc\"\n");

        Config<PlainConfig> config = ConfigurationLoader.from(configPath)
                .withoutComments()
                .loadPlain(PlainConfig::new);

        assertEquals(8080, config.get().port, "Bad value should leave default untouched");
        assertEquals(1, config.getLastLoadIssues().size());
        assertNotNull(config.get().issuesSeenInPostInject,
                "@PostInject(List<LoadIssue>) parameter should be injected");
        assertEquals(1, config.get().issuesSeenInPostInject.size());
        assertEquals("port", config.get().issuesSeenInPostInject.get(0).key());
    }

    @Test
    @DisplayName("loadPlain rejects classes without @Configuration")
    void loadPlain_rejectsUnannotatedClass() {
        Path configPath = tempDir.resolve("bad.yaml");
        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> ConfigurationLoader.from(configPath).loadPlain(NotAnnotated::new));
        assertTrue(e.getMessage().contains("@Configuration"), "Got: " + e.getMessage());
    }

    @Test
    @DisplayName("loadPlain rejects ConfigurablePojo subclasses")
    void loadPlain_rejectsConfigurablePojoSubclass() {
        Path configPath = tempDir.resolve("bad2.yaml");
        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> ConfigurationLoader.from(configPath).loadPlain(ExtendsBased::new));
        assertTrue(e.getMessage().contains("load(...)"), "Got: " + e.getMessage());
    }

    // ==================== TEST CONFIGS ====================

    @Configuration
    @Comment("Plain configuration - no extends!")
    public static class PlainConfig {
        @Key("host")
        @Comment("The hostname")
        public String host = "localhost";

        @Key("port")
        public int port = 8080;

        @Comment("Database settings")
        @Section("database")
        public Database database = new Database();

        public transient boolean postInjectRan;
        public transient List<LoadIssue> issuesSeenInPostInject;

        @PostInject
        private void markRan() {
            postInjectRan = true;
        }

        @PostInject
        private void captureIssues(List<LoadIssue> issues) {
            issuesSeenInPostInject = issues;
        }
    }

    public static class Database {
        @Key("pool-size")
        public int poolSize = 10;
    }

    public static class NotAnnotated {
        @Key("value")
        public String value = "x";
    }

    public static class ExtendsBased extends ConfigurablePojo<ExtendsBased> {
        @Key("value")
        public String value = "x";
    }
}
