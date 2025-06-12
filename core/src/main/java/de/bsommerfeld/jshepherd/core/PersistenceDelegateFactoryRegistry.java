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
            throw new ConfigurationException("Unsupported file extension: " + fileExtension);
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