package de.bsommerfeld.jshepherd.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationLoaderTest {

    @TempDir
    Path tempDir;

    @Mock
    private PersistenceDelegateFactory mockFactory;

    @Mock
    @SuppressWarnings("rawtypes")
    private PersistenceDelegate mockDelegate;

    private Path configPath;
    private TestConfig testConfig;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        MockitoAnnotations.openMocks(this);
        configPath = tempDir.resolve("config.json");
        testConfig = new TestConfig();

        // Setup mock delegate
        when(mockDelegate.loadInitial(any())).thenReturn(testConfig);

        // Setup mock factory - using raw types with suppressed warnings
        when(mockFactory.create(eq(configPath), anyBoolean())).thenReturn(mockDelegate);
        when(mockFactory.getSupportedExtensions()).thenReturn(new String[]{"json"});

        // Register the mock factory
        PersistenceDelegateFactoryRegistry.registerFactory(mockFactory);
    }

    @Test
    void load_shouldReturnConfigWithDelegate() {
        // Arrange
        Supplier<TestConfig> supplier = TestConfig::new;

        // Act
        TestConfig result = ConfigurationLoader.load(configPath, supplier);

        // Assert
        assertNotNull(result, "Result should not be null");
        verify(mockDelegate).loadInitial(supplier);
        verify(mockFactory).create(eq(configPath), eq(true));
    }

    @Test
    void load_withCommentsDisabled_shouldPassCorrectFlag() {
        // Arrange
        Supplier<TestConfig> supplier = TestConfig::new;

        // Act
        TestConfig result = ConfigurationLoader.load(configPath, supplier, false);

        // Assert
        assertNotNull(result, "Result should not be null");
        verify(mockFactory).create(eq(configPath), eq(false));
    }

    // Test implementation of ConfigurablePojo
    private static class TestConfig extends ConfigurablePojo<TestConfig> {
        // Empty implementation for testing
    }
}
