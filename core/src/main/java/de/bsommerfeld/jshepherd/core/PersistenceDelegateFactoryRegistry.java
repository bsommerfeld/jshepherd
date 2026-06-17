package de.bsommerfeld.jshepherd.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for persistence delegate factories using the Service Provider Interface pattern.
 */
public class PersistenceDelegateFactoryRegistry {

    private static final Map<String, PersistenceDelegateFactory> factories = new ConcurrentHashMap<>();

    static {
        // Load all factories using ServiceLoader
        ServiceLoader<PersistenceDelegateFactory> loader =
                ServiceLoader.load(PersistenceDelegateFactory.class);

        for (PersistenceDelegateFactory factory : loader) {
            for (String extension : factory.getSupportedExtensions()) {
                factories.put(extension.toLowerCase(), factory);
            }
        }
    }

    /**
     * Gets the appropriate factory for the given file extension.
     */
    public static PersistenceDelegateFactory getFactory(String fileExtension) {
        PersistenceDelegateFactory factory = factories.get(fileExtension.toLowerCase());
        if (factory == null) {
            if (factories.isEmpty()) {
                throw new ConfigurationException(
                        "Unsupported file extension: '" + fileExtension
                                + "'. No format modules were found on the classpath/module path. "
                                + "Add a format module (e.g. de.bsommerfeld.jshepherd:yaml, :json or :toml).");
            }
            throw new ConfigurationException(
                    "Unsupported file extension: '" + fileExtension
                            + "'. Supported extensions: " + String.join(", ", new TreeSet<>(factories.keySet())));
        }
        return factory;
    }

    /**
     * Registers a factory manually (useful for testing or dynamic registration).
     */
    public static void registerFactory(PersistenceDelegateFactory factory) {
        for (String extension : factory.getSupportedExtensions()) {
            factories.put(extension.toLowerCase(), factory);
        }
    }
}