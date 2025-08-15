package de.bsommerfeld.jshepherd.toml;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This test generates a TOML configuration file on disk so you can inspect it manually.
 * Additionally, it verifies that values can be loaded from modified TOML and that changes
 * in the POJO are correctly written back to the TOML file.
 * The output is written to the toml module's target directory.
 */
class GenerateTomlConfigExampleTest {

    @Test
    @DisplayName("Generate example TOML config and verify read/write roundtrips")
    void generateExampleConfig() throws Exception {
        // Define output path inside the toml module's target directory
        Path out = Paths.get("target", "generated-configs", "example-config.toml");
        Files.createDirectories(out.getParent());

        // Build sample config with nested TOML section and comments
        ExampleConfig cfg = new ExampleConfig();
        cfg.appName = "JShepherd Demo";
        cfg.debug = true;
        cfg.maxConnections = 25;
        cfg.tags = Arrays.asList("alpha", "beta", "release-candidate");
        cfg.thresholds = new LinkedHashMap<>();
        cfg.thresholds.put("low", 10);
        cfg.thresholds.put("medium", 50);
        cfg.thresholds.put("high", 100);

        cfg.database = new DatabaseSection();
        cfg.database.host = "localhost";
        cfg.database.port = 5432;
        cfg.database.username = "demo";
        cfg.database.enabled = true;

        // Save with comments enabled
        TomlPersistenceDelegate<ExampleConfig> delegate = new TomlPersistenceDelegate<>(out, true);
        delegate.save(cfg);

        // Verify the file exists
        assertTrue(Files.exists(out), "Config file should have been generated: " + out);
        System.out.println("[TEST OUTPUT] Generated TOML config at: " + out.toAbsolutePath());

        // 1) Load back and verify values are read correctly
        ExampleConfig loaded1 = new ExampleConfig();
        boolean loadedOk = delegate.tryLoadFromFile(loaded1);
        assertTrue(loadedOk, "Should be able to load previously saved TOML config");
        assertEquals("JShepherd Demo", loaded1.appName);
        assertTrue(loaded1.debug);
        assertEquals(25, loaded1.maxConnections);
        assertEquals(Arrays.asList("alpha", "beta", "release-candidate"), loaded1.tags);
        assertEquals(10, ((Number) loaded1.thresholds.get("low")).intValue());
        assertEquals(50, ((Number) loaded1.thresholds.get("medium")).intValue());
        assertEquals(100, ((Number) loaded1.thresholds.get("high")).intValue());
        assertNotNull(loaded1.database);
        assertEquals("localhost", loaded1.database.host);
        assertEquals(5432, loaded1.database.port);
        assertEquals("demo", loaded1.database.username);
        assertTrue(loaded1.database.enabled);

        // 2) Modify the TOML on disk and verify POJO picks up changes on load
        String modifiedToml = "" +
                "app-name = \"Changed App\"\n" +
                "debug = false\n" +
                "max-connections = 99\n" +
                "tags = [\"x\", \"y\"]\n\n" +
                "[thresholds]\n" +
                "low = 5\n" +
                "medium = 60\n" +
                "high = 101\n\n" +
                "[database]\n" +
                "host = \"db\"\n" +
                "port = 6543\n" +
                "username = \"root\"\n" +
                "enabled = false\n";
        Files.writeString(out, modifiedToml);

        ExampleConfig loaded2 = new ExampleConfig();
        boolean loaded2Ok = delegate.tryLoadFromFile(loaded2);
        assertTrue(loaded2Ok, "Should load modified TOML");
        assertEquals("Changed App", loaded2.appName);
        assertFalse(loaded2.debug);
        assertEquals(99, loaded2.maxConnections);
        assertEquals(Arrays.asList("x", "y"), loaded2.tags);
        assertEquals(5, ((Number) loaded2.thresholds.get("low")).intValue());
        assertEquals(60, ((Number) loaded2.thresholds.get("medium")).intValue());
        assertEquals(101, ((Number) loaded2.thresholds.get("high")).intValue());
        assertNotNull(loaded2.database);
        assertEquals("db", loaded2.database.host);
        assertEquals(6543, loaded2.database.port);
        assertEquals("root", loaded2.database.username);
        assertFalse(loaded2.database.enabled);

        // 3) Modify the POJO and save again, then assert the file contains the updated values
        loaded2.appName = "Saved Back";
        loaded2.debug = true;
        loaded2.maxConnections = 7;
        loaded2.tags = Arrays.asList("zeta");
        loaded2.thresholds = new LinkedHashMap<>();
        loaded2.thresholds.put("low", 1);
        loaded2.thresholds.put("medium", 2);
        loaded2.thresholds.put("high", 3);
        loaded2.database.host = "server";
        loaded2.database.port = 7777;
        loaded2.database.username = "svc";
        loaded2.database.enabled = true;

        delegate.save(loaded2);
        String finalContent = Files.readString(out);
        assertTrue(finalContent.contains("app-name = \"Saved Back\""));
        assertTrue(finalContent.contains("debug = true"));
        assertTrue(finalContent.contains("max-connections = 7"));
        assertTrue(finalContent.contains("tags = ["));
        assertTrue(finalContent.contains("\"zeta\""));
        assertTrue(finalContent.contains("[thresholds]"));
        assertTrue(finalContent.contains("low = 1"));
        assertTrue(finalContent.contains("medium = 2"));
        assertTrue(finalContent.contains("high = 3"));
        assertTrue(finalContent.contains("[database]"));
        assertTrue(finalContent.contains("host = \"server\""));
        assertTrue(finalContent.contains("port = 7777"));
        assertTrue(finalContent.contains("username = \"svc\""));
        assertTrue(finalContent.contains("enabled = true"));
    }

    // =====================================================
    // Sample configuration classes for demonstration
    // =====================================================

    @Comment({
            "Example configuration for JShepherd TOML module.",
            "This file demonstrates comments, simple keys, a list, a map-as-table, and a nested section.",
            "Edit the generated file in target/generated-configs/example-config.toml to try out loading."
    })
    static class ExampleConfig extends ConfigurablePojo<ExampleConfig> {
        @Key("app-name")
        @Comment("The display name of the application")
        String appName = "Application";

        @Key("debug")
        @Comment("Enable or disable debug mode")
        boolean debug = false;

        @Key("max-connections")
        @Comment("Maximum number of concurrent connections")
        int maxConnections = 10;

        @Key("tags")
        @Comment("List of arbitrary tags")
        List<String> tags = new ArrayList<>();

        @Key("thresholds")
        @TomlSection("thresholds")
        @Comment("Threshold table with named levels")
        Map<String, Integer> thresholds = new LinkedHashMap<>();

        @Key("database")
        @TomlSection
        @Comment("Database connection settings")
        DatabaseSection database = new DatabaseSection();
    }

    static class DatabaseSection extends ConfigurablePojo<DatabaseSection> {
        @Key("host")
        @Comment("Database host name")
        String host = "localhost";

        @Key("port")
        @Comment("Database port number")
        int port = 5432;

        @Key("username")
        @Comment("Database user name")
        String username = "user";

        @Key("enabled")
        @Comment("Enable or disable the database feature")
        boolean enabled = false;
    }
}
