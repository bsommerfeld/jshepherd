package de.bsommerfeld.jshepherd.core;

import java.nio.file.Path;
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
        private final Path filePath;
        private boolean useComments = true;

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
         * Loads the configuration using the specified default POJO supplier.
         * If the file exists, values are loaded from it. Otherwise, a new file
         * is created with default values from the supplier.
         *
         * @param defaultPojoSupplier Supplier for creating default POJO instances
         * @param <T>                 The configuration POJO type
         * @return The loaded (or newly created) configuration instance
         */
        public <T extends ConfigurablePojo<T>> T load(Supplier<T> defaultPojoSupplier) {
            return ConfigurationLoader.load(filePath, defaultPojoSupplier, useComments);
        }
    }

    // ==================== INTERNAL HELPERS ====================

    private static <T extends ConfigurablePojo<T>> PersistenceDelegate<T> determinePersistenceDelegate(
            Path filePath, boolean useComplexSaveWithComments) {
        String fileName = filePath.getFileName().toString();
        String fileExtension = fileName.substring(fileName.lastIndexOf('.') + 1);

        PersistenceDelegateFactory factory = PersistenceDelegateFactoryRegistry.getFactory(fileExtension);
        return factory.create(filePath, useComplexSaveWithComments);
    }
}