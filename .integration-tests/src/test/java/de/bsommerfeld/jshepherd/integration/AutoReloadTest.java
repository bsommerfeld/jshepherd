package de.bsommerfeld.jshepherd.integration;

import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.annotation.PostInject;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.ConfigurationLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end auto-reload tests: enable {@code withAutoReload()} on a real file
 * in each format, edit the file externally, and confirm the POJO updates, the
 * listener fires, {@code @PostInject} re-runs, and {@code stopAutoReload()}
 * halts further updates.
 */
class AutoReloadTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"yaml", "json", "toml", "properties"})
    @DisplayName("Auto-reload applies external file changes and notifies the listener")
    void autoReload_appliesExternalChange(String format) throws Exception {
        Path configPath = tempDir.resolve("autoreload." + format);

        WatchConfig config = ConfigurationLoader.from(configPath)
                .withoutComments()
                .withAutoReload(Duration.ofMillis(50))
                .load(WatchConfig::new);

        assertTrue(config.isAutoReloadActive(), "Watcher should be active for " + format);
        assertEquals(1, config.postInjectCount, "@PostInject runs once on initial load for " + format);

        CountDownLatch reloaded = new CountDownLatch(1);
        config.setOnAutoReload(reloaded::countDown);

        // Simulate an external process editing the file. The replacement value
        // has a different length, so the change is detected even if the file
        // system's modification-time granularity is coarse.
        String content = Files.readString(configPath);
        Files.writeString(configPath, content.replace("localhost", "changed-by-another-process"));

        try {
            assertTrue(reloaded.await(5, TimeUnit.SECONDS),
                    "Auto-reload listener should fire within timeout for " + format);
            assertEquals("changed-by-another-process", config.host,
                    "POJO should reflect the external change for " + format);
            assertTrue(config.postInjectCount >= 2,
                    "@PostInject should re-run after auto-reload for " + format);
        } finally {
            config.stopAutoReload();
        }
        assertFalse(config.isAutoReloadActive(), "Watcher should be stopped for " + format);
    }

    @Test
    @DisplayName("stopAutoReload halts further reloads")
    void stopAutoReload_haltsReloads() throws Exception {
        Path configPath = tempDir.resolve("stop.yaml");

        WatchConfig config = ConfigurationLoader.from(configPath)
                .withoutComments()
                .withAutoReload(Duration.ofMillis(50))
                .load(WatchConfig::new);

        config.stopAutoReload();
        assertFalse(config.isAutoReloadActive());

        String content = Files.readString(configPath);
        Files.writeString(configPath, content.replace("localhost", "should-be-ignored"));

        // Give a stopped watcher several poll intervals to (not) react.
        Thread.sleep(300);
        assertEquals("localhost", config.host, "A stopped watcher must not reload the POJO");
    }

    @Test
    @DisplayName("save() through an active watcher does not trigger a self-reload")
    void save_doesNotTriggerSelfReload() throws Exception {
        Path configPath = tempDir.resolve("selfsave.yaml");

        WatchConfig config = ConfigurationLoader.from(configPath)
                .withoutComments()
                .withAutoReload(Duration.ofMillis(50))
                .load(WatchConfig::new);

        try {
            int countAfterLoad = config.postInjectCount;
            config.host = "saved-by-app";
            config.save();

            // The watcher must not see our own write as an external change.
            Thread.sleep(300);
            assertEquals(countAfterLoad, config.postInjectCount,
                    "save() must not cause the watcher to reload (no extra @PostInject)");
            assertEquals("saved-by-app", config.host);
        } finally {
            config.stopAutoReload();
        }
    }

    public static class WatchConfig extends ConfigurablePojo<WatchConfig> {
        @Key("host")
        public String host = "localhost";

        public transient int postInjectCount = 0;

        @PostInject
        private void countLoads() {
            postInjectCount++;
        }
    }
}
