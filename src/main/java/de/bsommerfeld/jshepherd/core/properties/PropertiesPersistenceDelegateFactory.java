package de.bsommerfeld.jshepherd.core.properties;

import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.PersistenceDelegate;

import java.nio.file.Path;

/**
 * Factory class responsible for creating instances of {@link PropertiesPersistenceDelegate}. This factory encapsulates the
 * instantiation logic of the Properties-specific persistence delegate, allowing the
 * {@link de.bsommerfeld.jshepherd.core.ConfigurationLoader} to remain decoupled from the concrete delegate
 * implementation details.
 *
 * <p>This class is typically used internally by the JShepherd configuration framework.</p>
 */
public class PropertiesPersistenceDelegateFactory {

    /**
     * Creates a new instance of {@link PropertiesPersistenceDelegate} configured for the specified POJO type and file path.
     *
     * @param filePath                   Path to the Properties configuration file that the delegate will manage.
     * @param useComplexSaveWithComments A boolean flag indicating the desired save strategy: {@code true} to enable
     *                                   detailed, annotation-driven comment generation in the Properties output;
     *                                   {@code false} for a simpler, faster Properties dump without field-level comments.
     * @param <T>                        The specific type of the configuration POJO, which must extend
     *                                   {@link ConfigurablePojo}.
     *
     * @return A new, configured instance of {@code PersistenceDelegate<T>}.
     */
    public static <T extends ConfigurablePojo<T>> PersistenceDelegate<T> create(
            Path filePath, boolean useComplexSaveWithComments) {
        return new PropertiesPersistenceDelegate<>(filePath, useComplexSaveWithComments);
    }
}