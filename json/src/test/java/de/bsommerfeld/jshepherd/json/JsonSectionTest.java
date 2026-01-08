package de.bsommerfeld.jshepherd.json;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.annotation.Section;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.ConfigurationLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for @Section annotation in JSON format.
 */
@DisplayName("JSON @Section Tests")
class JsonSectionTest {

    @TempDir
    Path tempDir;

    // ==================== TEST CONFIGS ====================

    @Comment("Server Configuration")
    static class ServerConfig extends ConfigurablePojo<ServerConfig> {
        @Key("name")
        private String name = "TestServer";

        @Key("port")
        private int port = 8080;

        @Section("database")
        private DatabaseSettings database = new DatabaseSettings();

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

    @Test
    @DisplayName("Section creates nested JSON structure")
    void sectionCreatesNestedJson() throws IOException {
        Path configPath = tempDir.resolve("server.json");

        ServerConfig config = ConfigurationLoader.from(configPath)
                .withComments()
                .load(ServerConfig::new);

        String content = Files.readString(configPath);

        // Root fields
        assertTrue(content.contains("\"name\" : \"TestServer\""), "Root name should be present");
        assertTrue(content.contains("\"port\" : 8080"), "Root port should be present");

        // Nested structure - JSON creates nested objects for @Section fields
        assertTrue(content.contains("\"database\""), "Database section should be present");
        assertTrue(content.contains("\"cache\""), "Cache section should be present");
        assertTrue(content.contains("\"host\" : \"localhost\""), "Nested host should be present");
        assertTrue(content.contains("\"max-size\" : 1000"), "Nested max-size should be present");
    }

    @Test
    @DisplayName("Section values load correctly")
    void sectionValuesLoadCorrectly() throws IOException {
        Path configPath = tempDir.resolve("load-test.json");
        String json = """
                {
                  "name": "loaded-server",
                  "port": 9999,
                  "database": {
                    "host": "db.example.com",
                    "port": 3306
                  },
                  "cache": {
                    "max-size": 5000
                  }
                }
                """;
        Files.writeString(configPath, json);

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
        Path configPath = tempDir.resolve("roundtrip.json");

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

    @Test
    @DisplayName("Documentation file is generated with section info")
    void documentationFileGenerated() throws IOException {
        Path configPath = tempDir.resolve("doc-test.json");

        ServerConfig config = ConfigurationLoader.from(configPath)
                .withComments()
                .load(ServerConfig::new);

        Path docPath = tempDir.resolve("doc-test-config-documentation.md");
        assertTrue(Files.exists(docPath), "Documentation file should be created");

        String docContent = Files.readString(docPath);
        assertTrue(docContent.contains("Server Configuration"), "Class comment should be in docs");
    }
}
