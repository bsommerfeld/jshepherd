package de.bsommerfeld.jshepherd.integration;

import de.bsommerfeld.jshepherd.core.PersistenceDelegateFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that all format implementations are discoverable via ServiceLoader.
 * This validates that shading preserves META-INF/services registrations and
 * that module-info.class provides directives are correctly injected by moditect.
 */
class ServiceLoaderTest {

    @Test
    @DisplayName("ServiceLoader discovers exactly four format factories")
    void serviceLoader_discoversAllFourFactories() {
        List<PersistenceDelegateFactory> factories = ServiceLoader
                .load(PersistenceDelegateFactory.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toList());

        assertEquals(4, factories.size(),
                "Expected exactly 4 format factories (json, yaml, toml, properties), found: " +
                factories.stream()
                        .map(f -> f.getClass().getSimpleName())
                        .collect(Collectors.joining(", ")));
    }

    @Test
    @DisplayName("All expected file extensions are supported")
    void serviceLoader_allExpectedExtensionsSupported() {
        List<String> extensions = ServiceLoader
                .load(PersistenceDelegateFactory.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .flatMap(f -> Arrays.stream(f.getSupportedExtensions()))
                .collect(Collectors.toList());

        assertTrue(extensions.contains("json"), "json extension must be supported");
        assertTrue(extensions.contains("yaml"), "yaml extension must be supported");
        assertTrue(extensions.contains("toml"), "toml extension must be supported");
        assertTrue(extensions.contains("properties"), "properties extension must be supported");
    }
}
