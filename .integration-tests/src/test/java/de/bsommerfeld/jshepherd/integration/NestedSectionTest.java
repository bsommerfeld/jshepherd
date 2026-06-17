package de.bsommerfeld.jshepherd.integration;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.annotation.Section;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.ConfigurationLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies recursive {@code @Section} nesting ("sections in sections") across
 * all formats, in both comment and no-comment save modes. Uses three levels of
 * nesting to prove arbitrary depth.
 */
class NestedSectionTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "Format: {0}, comments: {1}")
    @CsvSource({
            "yaml,true", "yaml,false",
            "json,true", "json,false",
            "toml,true", "toml,false",
            "properties,true", "properties,false",
    })
    @DisplayName("Three-level nested sections round-trip in all formats")
    void nestedSections_roundTripThreeLevelsDeep(String format, boolean withComments) throws Exception {
        Path configPath = tempDir.resolve("nested." + format);

        var builder = ConfigurationLoader.from(configPath);
        NestedConfig config = (withComments ? builder.withComments() : builder.withoutComments())
                .load(NestedConfig::new);

        // Mutate values at every nesting level
        config.appName = "changed-app";
        config.database.url = "jdbc:custom://somewhere";
        config.database.pool.maxSize = 99;
        config.database.pool.timeout.connectMillis = 1234;
        config.save();

        String content = Files.readString(configPath);
        assertTrue(content.contains("1234"),
                "Level-3 value should be in the file for " + format + ":\n" + content);

        var reloadBuilder = ConfigurationLoader.from(configPath);
        NestedConfig reloaded = (withComments ? reloadBuilder.withComments() : reloadBuilder.withoutComments())
                .load(NestedConfig::new);

        assertEquals("changed-app", reloaded.appName, "Root value for " + format);
        assertEquals("jdbc:custom://somewhere", reloaded.database.url, "Level 1 value for " + format);
        assertEquals(99, reloaded.database.pool.maxSize, "Level 2 value for " + format);
        assertEquals(1234, reloaded.database.pool.timeout.connectMillis, "Level 3 value for " + format);
        // Untouched defaults survive at every level
        assertEquals(10, reloaded.database.pool.minSize, "Level 2 default for " + format);
        assertEquals(500, reloaded.database.pool.timeout.readMillis, "Level 3 default for " + format);
    }

    // ==================== TEST CONFIG (3 nesting levels) ====================

    @Comment("Nested section test configuration")
    public static class NestedConfig extends ConfigurablePojo<NestedConfig> {
        @Key("app-name")
        @Comment("Application name")
        public String appName = "default-app";

        @Comment("Database settings")
        @Section("database")
        public Database database = new Database();
    }

    public static class Database {
        @Key("url")
        @Comment("JDBC URL")
        public String url = "jdbc:default";

        @Comment("Connection pool tuning")
        @Section("pool")
        public Pool pool = new Pool();
    }

    public static class Pool {
        @Key("min-size")
        public int minSize = 10;

        @Key("max-size")
        @Comment("Maximum pool size")
        public int maxSize = 20;

        @Comment("Timeout settings")
        @Section("timeout")
        public Timeout timeout = new Timeout();
    }

    public static class Timeout {
        @Key("connect-millis")
        public int connectMillis = 250;

        @Key("read-millis")
        public int readMillis = 500;
    }
}
