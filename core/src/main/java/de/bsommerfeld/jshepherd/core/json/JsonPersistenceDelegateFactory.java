package de.bsommerfeld.jshepherd.core.json;

import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.PersistenceDelegate;

import java.nio.file.Path;

/**
 * Factory class responsible for creating instances of {@link JsonPersistenceDelegate}. This factory encapsulates the
 * instantiation logic of the JSON-specific persistence delegate, allowing the
 * {@link de.bsommerfeld.jshepherd.core.ConfigurationLoader} to remain decoupled from the concrete delegate
 * implementation details.
 *
 * <p>This class is typically used internally by the JShepherd configuration framework.</p>
 */
public class JsonPersistenceDelegateFactory {

    /**
     * Creates a new instance of {@link JsonPersistenceDelegate} configured for the specified POJO type and file path.
     *
     * @param filePath                   Path to the JSON configuration file that the delegate will manage.
     * @param useComplexSaveWithComments A boolean flag indicating the desired save strategy: {@code true} to enable
     *                                   detailed, annotation-driven comment generation in the JSON output;
     *                                   {@code false} for a simpler, faster JSON dump without field-level comments.
     * @param <T>                        The specific type of the configuration POJO, which must extend
     *                                   {@link ConfigurablePojo}.
     *
     * @return A new, configured instance of {@code PersistenceDelegate<T>}.
     */
    public static <T extends ConfigurablePojo<T>> PersistenceDelegate<T> create(
            Path filePath, boolean useComplexSaveWithComments) {
        return new JsonPersistenceDelegate<>(filePath, useComplexSaveWithComments);
    }
}