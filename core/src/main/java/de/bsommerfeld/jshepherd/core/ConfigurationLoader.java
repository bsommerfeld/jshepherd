package de.bsommerfeld.jshepherd.core;

import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Entry point for loading configuration files.
 * Supports both static factory methods and a fluent builder API.
 */
public class ConfigurationLoader {

    // ==================== STATIC FACTORY METHODS (Backward Compatible)
    // ====================

    /**
     * Loads configuration from the specified file path with comments enabled.
     */
    public static <T extends ConfigurablePojo<T>> T load(
            Path filePath, Supplier<T> defaultPojoSupplier) {
        return load(filePath, defaultPojoSupplier, true);
    }

    /**
     * Loads configuration from the specified file path.
     */
    public static <T extends ConfigurablePojo<T>> T load(
            Path filePath,
            Supplier<T> defaultPojoSupplier,
            boolean useComplexSaveWithComments) {

        PersistenceDelegate<T> delegate = determinePersistenceDelegate(filePath, useComplexSaveWithComments);

        T pojoInstance = delegate.loadInitial(defaultPojoSupplier);
        pojoInstance._setPersistenceDelegate(delegate);
        pojoInstance._invokePostInjectMethods();

        return pojoInstance;
    }

    // ==================== FLUENT BUILDER API ====================

    /**
     * Starts building a configuration loader for the specified file path.
     * The format is automatically detected based on the file extension.
     *
     * <p>
     * Example usage:
     * </p>
     * 
     * <pre>{@code
     * AppConfig config = ConfigurationLoader.from(Paths.get("config.yaml"))
     *         .withComments()
     *         .load(AppConfig::new);
     * }</pre>
     *
     * @param filePath Path to the configuration file
     * @return A builder for further configuration
     */
    public static Builder from(Path filePath) {
        return new Builder(filePath);
    }

    /**
     * Fluent builder for constructing and loading configuration.
     */
    public static final class Builder {
        private static final Duration DEFAULT_AUTO_RELOAD_INTERVAL = Duration.ofSeconds(1);

        private final Path filePath;
        private boolean useComments = true;
        private Duration autoReloadInterval;

        private Builder(Path filePath) {
            this.filePath = filePath;
        }

        /**
         * Enables comment preservation and generation.
         * This is the default behavior.
         */
        public Builder withComments() {
            this.useComments = true;
            return this;
        }

        /**
         * Disables comment handling for faster, simpler serialization.
         */
        public Builder withoutComments() {
            this.useComments = false;
            return this;
        }

        /**
         * Enables automatic reloading: the configuration file is watched on a
         * background daemon thread and the POJO is reloaded whenever the file
         * changes on disk. Polls once per second.
         *
         * <p>Use {@link ConfigurablePojo#setOnAutoReload(Runnable)} to get
         * notified after a reload and {@link ConfigurablePojo#stopAutoReload()}
         * to stop watching.</p>
         */
        public Builder withAutoReload() {
            return withAutoReload(DEFAULT_AUTO_RELOAD_INTERVAL);
        }

        /**
         * Enables automatic reloading with a custom poll interval.
         *
         * @param pollInterval how often to check the file for changes; must be positive
         */
        public Builder withAutoReload(Duration pollInterval) {
            if (pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()) {
                throw new IllegalArgumentException("Auto-reload poll interval must be positive");
            }
            this.autoReloadInterval = pollInterval;
            return this;
        }

        /**
         * Loads the configuration using the specified default POJO supplier.
         * If the file exists, values are loaded from it. Otherwise, a new file
         * is created with default values from the supplier.
         *
         * @param defaultPojoSupplier Supplier for creating default POJO instances
         * @param <T>                 The configuration POJO type
         * @return The loaded (or newly created) configuration instance
         */
        public <T extends ConfigurablePojo<T>> T load(Supplier<T> defaultPojoSupplier) {
            T pojoInstance = ConfigurationLoader.load(filePath, defaultPojoSupplier, useComments);

            if (autoReloadInterval != null) {
                ConfigurationWatcher watcher = new ConfigurationWatcher(filePath, autoReloadInterval, () -> {
                    pojoInstance.reload();
                    Runnable listener = pojoInstance.autoReloadListener;
                    if (listener != null) {
                        listener.run();
                    }
                });
                pojoInstance._setWatcher(watcher);
                watcher.start();
            }

            return pojoInstance;
        }

        /**
         * Loads a plain {@code @Configuration}-annotated POJO (no need to extend
         * {@code ConfigurablePojo}) and returns a {@link Config} handle that
         * carries the lifecycle operations:
         *
         * <pre>{@code
         * Config<AppConfig> config = ConfigurationLoader.from(path).loadPlain(AppConfig::new);
         * AppConfig app = config.get();
         * config.save();
         * }</pre>
         *
         * @param defaultPojoSupplier Supplier for creating default POJO instances
         * @param <T>                 The plain configuration POJO type
         * @return A handle wrapping the loaded (or newly created) configuration
         */
        public <T> Config<T> loadPlain(Supplier<T> defaultPojoSupplier) {
            T probe = defaultPojoSupplier.get();
            if (probe instanceof ConfigurablePojo<?>) {
                throw new ConfigurationException(probe.getClass().getSimpleName()
                        + " extends ConfigurablePojo — use load(...) instead of loadPlain(...)");
            }
            if (!probe.getClass().isAnnotationPresent(de.bsommerfeld.jshepherd.annotation.Configuration.class)) {
                throw new ConfigurationException(probe.getClass().getSimpleName()
                        + " must be annotated with @Configuration to be loaded as a plain config POJO");
            }

            PersistenceDelegate<T> delegate = determinePersistenceDelegate(filePath, useComments);
            T instance = delegate.loadInitial(defaultPojoSupplier);
            PostInjectInvoker.invoke(instance, null, delegate.getLastLoadIssues());

            ConfigHandle<T> handle = new ConfigHandle<>(instance, delegate);
            if (autoReloadInterval != null) {
                ConfigurationWatcher watcher = new ConfigurationWatcher(filePath, autoReloadInterval, () -> {
                    handle.reload();
                    handle.notifyAutoReloadListener();
                });
                handle._setWatcher(watcher);
                watcher.start();
            }
            return handle;
        }
    }

    // ==================== INTERNAL HELPERS ====================

    private static <T> PersistenceDelegate<T> determinePersistenceDelegate(
            Path filePath, boolean useComplexSaveWithComments) {
        String fileName = filePath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            throw new ConfigurationException(
                    "Cannot determine configuration format: file name '" + fileName
                            + "' has no file extension (expected e.g. .yaml, .json or .toml)");
        }
        String fileExtension = fileName.substring(dotIndex + 1);

        PersistenceDelegateFactory factory = PersistenceDelegateFactoryRegistry.getFactory(fileExtension);
        return factory.create(filePath, useComplexSaveWithComments);
    }
}