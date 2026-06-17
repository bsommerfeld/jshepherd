package de.bsommerfeld.jshepherd.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Watches a configuration file for external changes and reloads the bound POJO
 * when the file is modified.
 *
 * <p>
 * Implementation note: this polls last-modified time and file size on a daemon
 * thread instead of using {@link java.nio.file.WatchService}. The JDK's
 * WatchService falls back to coarse polling on some platforms (notably macOS)
 * with latencies of several seconds that cannot be configured, while a direct
 * poll keeps the detection latency predictable and user-controlled on every
 * platform.
 * </p>
 */
final class ConfigurationWatcher {

    private static final Logger LOGGER = Logger.getLogger(ConfigurationWatcher.class.getName());

    private final Path filePath;
    private final long pollIntervalMillis;
    private final Runnable onChange;
    private final Thread thread;

    private volatile boolean running = true;
    private volatile FileTime lastModified;
    private volatile long lastSize = -1;

    /**
     * @param onChange invoked on the watcher thread after an external change is
     *                 detected; responsible for reloading and notifying listeners
     */
    ConfigurationWatcher(Path filePath, Duration pollInterval, Runnable onChange) {
        this.filePath = filePath;
        this.pollIntervalMillis = Math.max(1, pollInterval.toMillis());
        this.onChange = onChange;
        refreshSnapshot();
        this.thread = new Thread(this::run, "jshepherd-watcher-" + filePath.getFileName());
        this.thread.setDaemon(true);
    }

    void start() {
        thread.start();
    }

    void stop() {
        running = false;
        thread.interrupt();
    }

    /**
     * Re-records the file's current state so the next poll does not treat it as
     * an external change. Called after the POJO itself saves.
     */
    void refreshSnapshot() {
        try {
            if (Files.exists(filePath)) {
                lastModified = Files.getLastModifiedTime(filePath);
                lastSize = Files.size(filePath);
            } else {
                lastModified = null;
                lastSize = -1;
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Could not snapshot config file state for " + filePath, e);
        }
    }

    private void run() {
        while (running) {
            try {
                Thread.sleep(pollIntervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!running) {
                return;
            }
            try {
                if (hasChanged()) {
                    refreshSnapshot();
                    onChange.run();
                    LOGGER.log(Level.FINE, () -> "Auto-reloaded configuration from " + filePath);
                }
            } catch (Exception e) {
                // Keep watching: a half-written or temporarily invalid file should
                // not kill the watcher thread.
                LOGGER.log(Level.WARNING, "Auto-reload of '" + filePath + "' failed. Will keep watching.", e);
            }
        }
    }

    private boolean hasChanged() throws IOException {
        if (!Files.exists(filePath)) {
            return false;
        }
        FileTime currentModified = Files.getLastModifiedTime(filePath);
        long currentSize = Files.size(filePath);
        return !currentModified.equals(lastModified) || currentSize != lastSize;
    }
}
