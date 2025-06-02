package de.bsommerfeld.jshepherd.core.toml;

import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.PersistenceDelegate;

import java.nio.file.Path;

/**
 * Factory class responsible for creating instances of {@link TomlPersistenceDelegate}. This factory encapsulates the
 * instantiation logic of the TOML-specific persistence delegate, allowing the
 * {@link de.bsommerfeld.jshepherd.core.ConfigurationLoader} to remain decoupled from the concrete delegate
 * implementation details.
 *
 * <p>This class is typically used internally by the JShepherd configuration framework.</p>
 */
public class TomlPersistenceDelegateFactory {

    /**
     * Creates a new instance of {@link TomlPersistenceDelegate} configured for the specified POJO type and file path.
     *
     * @param filePath                   Path to the TOML configuration file that the delegate will manage.
     * @param useComplexSaveWithComments A boolean flag indicating the desired save strategy: {@code true} to enable
     *                                   detailed, annotation-driven comment generation in the TOML output;
     *                                   {@code false} for a simpler, faster TOML dump without field-level comments.
     * @param <T>                        The specific type of the configuration POJO, which must extend
     *                                   {@link ConfigurablePojo}.
     *
     * @return A new, configured instance of {@code PersistenceDelegate<T>}.
     */
    public static <T extends ConfigurablePojo<T>> PersistenceDelegate<T> create(
            Path filePath, boolean useComplexSaveWithComments) {
        return new TomlPersistenceDelegate<>(filePath, useComplexSaveWithComments);
    }
}