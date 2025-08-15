package de.bsommerfeld.jshepherd.yaml;

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
 * This test generates a YAML configuration file on disk for manual inspection.
 * Additionally, it verifies that values can be loaded from modified YAML and that changes
 * in the POJO are correctly written back to the YAML file.
 * The output is written to the yaml module's target directory.
 */
class GenerateYamlConfigExampleTest {

    @Test
    @DisplayName("Generate example YAML config and verify read/write roundtrips")
    void generateExampleConfig() throws Exception {
        // Define output path inside the yaml module's target directory
        Path out = Paths.get("target", "generated-configs", "example-config.yaml");
        Files.createDirectories(out.getParent());

        // Build sample config (flat @Key mapping)
        ExampleConfig cfg = new ExampleConfig();
        cfg.appName = "JShepherd Demo";
        cfg.debug = true;
        cfg.maxConnections = 25;
        cfg.tags = Arrays.asList("alpha", "beta", "release-candidate");
        cfg.thresholds = new LinkedHashMap<>();
        cfg.thresholds.put("low", 10);
        cfg.thresholds.put("medium", 50);
        cfg.thresholds.put("high", 100);

        // Save with comments enabled
        YamlPersistenceDelegate<ExampleConfig> delegate = new YamlPersistenceDelegate<>(out, true);
        delegate.save(cfg);

        // Verify the file exists
        assertTrue(Files.exists(out), "Config file should have been generated: " + out);
        System.out.println("[TEST OUTPUT] Generated YAML config at: " + out.toAbsolutePath());

        // 1) Load back and verify values are read correctly
        ExampleConfig loaded1 = new ExampleConfig();
        boolean loadedOk = delegate.tryLoadFromFile(loaded1);
        assertTrue(loadedOk, "Should be able to load previously saved YAML config");
        assertEquals("JShepherd Demo", loaded1.appName);
        assertTrue(loaded1.debug);
        assertEquals(25, loaded1.maxConnections);
        assertEquals(Arrays.asList("alpha", "beta", "release-candidate"), loaded1.tags);
        assertEquals(10, ((Number) loaded1.thresholds.get("low")).intValue());
        assertEquals(50, ((Number) loaded1.thresholds.get("medium")).intValue());
        assertEquals(100, ((Number) loaded1.thresholds.get("high")).intValue());

        // 2) Modify the YAML on disk and verify POJO picks up changes on load
        String modifiedYaml = "" +
                "app-name: \"Changed App\"\n" +
                "debug: false\n" +
                "max-connections: 99\n" +
                "tags: [x, y]\n" +
                "thresholds:\n" +
                "  low: 5\n" +
                "  medium: 60\n" +
                "  high: 101\n";
        Files.writeString(out, modifiedYaml);

        ExampleConfig loaded2 = new ExampleConfig();
        boolean loaded2Ok = delegate.tryLoadFromFile(loaded2);
        assertTrue(loaded2Ok, "Should load modified YAML");
        assertEquals("Changed App", loaded2.appName);
        assertFalse(loaded2.debug);
        assertEquals(99, loaded2.maxConnections);
        assertEquals(Arrays.asList("x", "y"), loaded2.tags);
        assertEquals(5, ((Number) loaded2.thresholds.get("low")).intValue());
        assertEquals(60, ((Number) loaded2.thresholds.get("medium")).intValue());
        assertEquals(101, ((Number) loaded2.thresholds.get("high")).intValue());

        // 3) Modify the POJO and save again, then assert the file contains the updated values
        loaded2.appName = "Saved Back";
        loaded2.debug = true;
        loaded2.maxConnections = 7;
        loaded2.tags = Arrays.asList("zeta");
        loaded2.thresholds = new LinkedHashMap<>();
        loaded2.thresholds.put("low", 1);
        loaded2.thresholds.put("medium", 2);
        loaded2.thresholds.put("high", 3);

        delegate.save(loaded2);
        String finalContent = Files.readString(out);
        assertTrue(finalContent.contains("app-name:"));
        assertTrue(finalContent.contains("Saved Back"));
        assertTrue(finalContent.contains("debug:"));
        assertTrue(finalContent.contains("max-connections:"));
        assertTrue(finalContent.contains("tags:"));
        assertTrue(finalContent.contains("thresholds:"));
        assertTrue(finalContent.contains("low:"));
        assertTrue(finalContent.contains("medium:"));
        assertTrue(finalContent.contains("high:"));
    }

    // =====================================================
    // Sample configuration classes for demonstration
    // =====================================================

    @Comment({
            "Example configuration for JShepherd YAML module.",
            "This file demonstrates comments, simple keys, a list, and a map.",
            "Edit the generated file in target/generated-configs/example-config.yaml to try out loading."
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
        @Comment("Threshold map with named levels")
        Map<String, Integer> thresholds = new LinkedHashMap<>();
    }
}
