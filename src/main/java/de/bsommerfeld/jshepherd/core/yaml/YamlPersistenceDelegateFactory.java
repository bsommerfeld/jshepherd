package de.bsommerfeld.jshepherd.core.yaml;

import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.PersistenceDelegate;

import java.nio.file.Path;

/**
 * Factory class responsible for creating instances of {@link YamlPersistenceDelegate}. This factory encapsulates the
 * instantiation logic of the YAML-specific persistence delegate, allowing the
 * {@link de.bsommerfeld.jshepherd.core.ConfigurationLoader} to remain decoupled from the concrete delegate
 * implementation details.
 *
 * <p>This class is typically used internally by the JShepherd configuration framework.</p>
 */
public class YamlPersistenceDelegateFactory {

    /**
     * Creates a new instance of {@link YamlPersistenceDelegate} configured for the specified POJO type and file path.
     *
     * @param filePath                   Path to the YAML configuration file that the delegate will manage.
     * @param pojoClass                  The class of the configuration POJO (must extend {@link ConfigurablePojo}).
     *                                   This is used by the delegate for loading and type-specific operations.
     * @param useComplexSaveWithComments A boolean flag indicating the desired save strategy: {@code true} to enable
     *                                   detailed, annotation-driven comment generation in the YAML output;
     *                                   {@code false} for a simpler, faster YAML dump without field-level comments.
     * @param <T>                        The specific type of the configuration POJO, which must extend
     *                                   {@link ConfigurablePojo}.
     *
     * @return A new, configured instance of {@code PersistenceDelegate<T>}.
     */
    public static <T extends ConfigurablePojo<T>> PersistenceDelegate<T> create(
            Path filePath, Class<T> pojoClass, boolean useComplexSaveWithComments) {
        return new YamlPersistenceDelegate<>(filePath, pojoClass, useComplexSaveWithComments);
    }
}