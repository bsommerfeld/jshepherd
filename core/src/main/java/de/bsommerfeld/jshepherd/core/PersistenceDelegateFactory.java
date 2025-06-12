package de.bsommerfeld.jshepherd.core;

import java.nio.file.Path;

/**
 * Base interface for all persistence delegate factories.
 * Implementations are responsible for creating format-specific persistence delegates.
 */
public interface PersistenceDelegateFactory {

    /**
     * Returns the file extensions this factory supports (e.g., "json", "yaml", "yml").
     */
    String[] getSupportedExtensions();

    /**
     * Creates a persistence delegate for the specified file path.
     *
     * @param filePath Path to the configuration file
     * @param useComplexSaveWithComments Whether to use complex save with comments
     * @param <T> The configuration POJO type
     * @return A configured persistence delegate
     */
    <T extends ConfigurablePojo<T>> PersistenceDelegate<T> create(
            Path filePath, boolean useComplexSaveWithComments);
}