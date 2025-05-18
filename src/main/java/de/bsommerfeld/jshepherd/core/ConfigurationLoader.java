package de.bsommerfeld.jshepherd.core;

import de.bsommerfeld.jshepherd.core.yaml.YamlPersistenceDelegateFactory;

import java.nio.file.Path;
import java.util.function.Supplier;

public class ConfigurationLoader {

    /**
     * Loads a configuration POJO from the specified file path.
     * If the file doesn't exist or is invalid, a default POJO is created (using the supplier)
     * and then saved to the path. The returned POJO instance will have save() and reload()
     * capabilities.
     *
     * @param filePath Path to the configuration file.
     * @param pojoClass The class of the configuration POJO, must extend {@link ConfigurablePojo}.
     * @param defaultPojoSupplier A supplier for creating a default instance of the POJO (e.g., {@code MyConfig::new}).
     * @param useComplexSaveWithComments If true, attempts to write comments from annotations during save.
     * If false, a simpler YAML dump is performed.
     * @return An initialized instance of the configuration POJO.
     * @param <T> The type of the configuration POJO.
     * @throws ConfigurationException if initial loading or saving of defaults fails.
     */
    public static <T extends ConfigurablePojo<T>> T load(
            Path filePath,
            Class<T> pojoClass,
            Supplier<T> defaultPojoSupplier,
            boolean useComplexSaveWithComments) {

        // Use the factory to create the delegate
        PersistenceDelegate<T> delegate = YamlPersistenceDelegateFactory.create(
                filePath, pojoClass, useComplexSaveWithComments
        );

        T pojoInstance = delegate.loadInitial(defaultPojoSupplier);

        // This _setPersistenceDelegate now expects PersistenceDelegate<SELF> (which is T here)
        // No, ConfigurablePojo._setPersistenceDelegate expects PersistenceDelegate<?> because of the generic method,
        // but it internally casts to PersistenceDelegate<SELF>. So this is fine.
        pojoInstance._setPersistenceDelegate(delegate);

        return pojoInstance;
    }

    /**
     * Loads a configuration POJO using a simple save strategy (no complex comment processing).
     * @see #load(Path, Class, Supplier, boolean)
     */
    public static <T extends ConfigurablePojo<T>> T load(
            Path filePath, Class<T> pojoClass, Supplier<T> defaultPojoSupplier) {
        return load(filePath, pojoClass, defaultPojoSupplier, true); // Default to complex save
    }
}
