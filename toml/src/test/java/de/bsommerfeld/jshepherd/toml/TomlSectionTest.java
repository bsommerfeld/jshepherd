package de.bsommerfeld.jshepherd.toml;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.annotation.Section;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.ConfigurationLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for unified @Section annotation in TOML format.
 */
@DisplayName("TOML @Section Tests")
class TomlSectionTest {

    @TempDir
    Path tempDir;

    // ==================== TEST CONFIGS ====================

    @Comment("Server Configuration")
    static class ServerConfig extends ConfigurablePojo<ServerConfig> {
        @Key("name")
        @Comment("Server name")
        private String name = "TestServer";

        @Key("port")
        private int port = 8080;

        @Comment("Database settings")
        @Section("database")
        private DatabaseSettings database = new DatabaseSettings();

        @Comment("Cache settings")
        @Section("cache")
        private CacheSettings cache = new CacheSettings();

        ServerConfig() {
        }

        String getName() {
            return name;
        }

        void setName(String name) {
            this.name = name;
        }

        int getPort() {
            return port;
        }

        DatabaseSettings getDatabase() {
            return database;
        }

        CacheSettings getCache() {
            return cache;
        }
    }

    static class DatabaseSettings {
        @Key("host")
        @Comment("Database hostname")
        private String host = "localhost";

        @Key("port")
        private int port = 5432;

        public DatabaseSettings() {
        }

        String getHost() {
            return host;
        }

        void setHost(String host) {
            this.host = host;
        }

        int getPort() {
            return port;
        }

        void setPort(int port) {
            this.port = port;
        }
    }

    static class CacheSettings {
        @Key("max-size")
        private int maxSize = 1000;

        public CacheSettings() {
        }

        int getMaxSize() {
            return maxSize;
        }
    }

    // ==================== TESTS ====================

    @Nested
    @DisplayName("Section Output Generation")
    class SectionOutputTests {

        @Test
        @DisplayName("Section creates TOML table structure with comments")
        void sectionCreatesTomlTable() throws IOException {
            Path configPath = tempDir.resolve("server.toml");

            ServerConfig config = ConfigurationLoader.from(configPath)
                    .withComments()
                    .load(ServerConfig::new);

            String content = Files.readString(configPath);

            // Root fields at top level
            assertTrue(content.contains("name = \"TestServer\""), "Root field should be present");
            assertTrue(content.contains("port = 8080"), "Root port should be present");

            // Section tables
            assertTrue(content.contains("[database]"), "Database section should be present");
            assertTrue(content.contains("[cache]"), "Cache section should be present");

            // Fields inside section
            assertTrue(content.contains("host = \"localhost\""), "Nested host should be present");
            assertTrue(content.contains("max-size = 1000"), "Nested max-size should be present");

            // Comments
            assertTrue(content.contains("# Database settings"), "Section comment should be present");
            assertTrue(content.contains("# Database hostname"), "Nested field comment should be present");
        }

        @Test
        @DisplayName("Section values load correctly")
        void sectionValuesLoadCorrectly() throws IOException {
            Path configPath = tempDir.resolve("load-test.toml");
            String toml = """
                    name = "loaded-server"
                    port = 9999

                    [database]
                    host = "db.example.com"
                    port = 3306

                    [cache]
                    max-size = 5000
                    """;
            Files.writeString(configPath, toml);

            ServerConfig config = ConfigurationLoader.load(configPath, ServerConfig::new);

            assertEquals("loaded-server", config.getName());
            assertEquals(9999, config.getPort());
            assertEquals("db.example.com", config.getDatabase().getHost());
            assertEquals(3306, config.getDatabase().getPort());
            assertEquals(5000, config.getCache().getMaxSize());
        }

        @Test
        @DisplayName("Round-trip preserves section values")
        void roundTripPreservesSectionValues() throws IOException {
            Path configPath = tempDir.resolve("roundtrip.toml");

            ServerConfig config = ConfigurationLoader.from(configPath)
                    .withComments()
                    .load(ServerConfig::new);

            // Modify
            config.setName("modified");
            config.getDatabase().setHost("new-host.com");
            config.getDatabase().setPort(1234);

            // Save
            config.save();

            // Reload
            ServerConfig reloaded = ConfigurationLoader.load(configPath, ServerConfig::new);

            assertEquals("modified", reloaded.getName());
            assertEquals("new-host.com", reloaded.getDatabase().getHost());
            assertEquals(1234, reloaded.getDatabase().getPort());
        }
    }
}
