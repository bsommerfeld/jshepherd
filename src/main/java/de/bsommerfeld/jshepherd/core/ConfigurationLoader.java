package de.bsommerfeld.jshepherd.core;

import de.bsommerfeld.jshepherd.core.json.JsonPersistenceDelegateFactory;
import de.bsommerfeld.jshepherd.core.properties.PropertiesPersistenceDelegateFactory;
import de.bsommerfeld.jshepherd.core.toml.TomlPersistenceDelegateFactory;
import de.bsommerfeld.jshepherd.core.yaml.YamlPersistenceDelegateFactory;

import java.nio.file.Path;
import java.util.function.Supplier;

public class ConfigurationLoader {

    /**
     * Loads a configuration POJO from the specified file path. If the file doesn't exist or is invalid, a default POJO
     * is created (using the supplier) and then saved to the path. The returned POJO instance will have save() and
     * reload() capabilities.
     *
     * @param filePath                   Path to the configuration file.
     * @param defaultPojoSupplier        A supplier for creating a default instance of the POJO (e.g.,
     *                                   {@code MyConfig::new}).
     * @param useComplexSaveWithComments If true, attempts to write comments from annotations during save. If false, a
     *                                   simpler YAML dump is performed.
     * @param <T>                        The type of the configuration POJO.
     *
     * @return An initialized instance of the configuration POJO.
     *
     * @throws ConfigurationException if initial loading or saving of defaults fails.
     */
    public static <T extends ConfigurablePojo<T>> T load(
            Path filePath,
            Supplier<T> defaultPojoSupplier,
            boolean useComplexSaveWithComments) {

        // Determine the delegate by the file extension
        // Will throw an exception if the extension is not supported
        PersistenceDelegate<T> delegate = determinePersistenceDelegate(filePath, useComplexSaveWithComments);

        T pojoInstance = delegate.loadInitial(defaultPojoSupplier);

        // This _setPersistenceDelegate now expects PersistenceDelegate<SELF> (which is T here)
        // No, ConfigurablePojo._setPersistenceDelegate expects PersistenceDelegate<?> because of the generic method,
        // but it internally casts to PersistenceDelegate<SELF>. So this is fine.
        pojoInstance._setPersistenceDelegate(delegate);

        // Invoke all with @PostInject annotated methods
        pojoInstance._invokePostInjectMethods();

        return pojoInstance;
    }

    /**
     * Loads a configuration POJO using a simple save strategy (no complex comment processing).
     *
     * @see #load(Path, Supplier, boolean)
     */
    public static <T extends ConfigurablePojo<T>> T load(
            Path filePath, Supplier<T> defaultPojoSupplier) {
        return load(filePath, defaultPojoSupplier, true); // Default to complex save
    }

  /**
   * Determines the appropriate persistence delegate for managing the persistence of configuration
   * data associated with the specified file path.
   *
   * @param filePath the path to the configuration file for which the persistence delegate should be
   *     determined
   */
  private static <T extends ConfigurablePojo<T>>
      PersistenceDelegate<T> determinePersistenceDelegate(
          Path filePath, boolean useComplexSaveWithComments) {
        String fileName = filePath.getFileName().toString();
        String fileExtension = fileName.substring(fileName.lastIndexOf('.') + 1);

        PersistenceDelegate<T> delegate;
        switch (fileExtension) {
            case "json":
                delegate = JsonPersistenceDelegateFactory.create(filePath, useComplexSaveWithComments);
                break;
            case "toml":
                delegate = TomlPersistenceDelegateFactory.create(filePath, useComplexSaveWithComments);
                break;
            case "yaml":
            case "yml":
                delegate = YamlPersistenceDelegateFactory.create(filePath, useComplexSaveWithComments);
                break;
            case "properties":
                delegate = PropertiesPersistenceDelegateFactory.create(filePath, useComplexSaveWithComments);
                break;
            default:
                throw new ConfigurationException("Unsupported file extension: " + fileExtension);
        }

        return delegate;
    }

}
