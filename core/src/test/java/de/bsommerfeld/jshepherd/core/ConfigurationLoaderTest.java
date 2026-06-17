package de.bsommerfeld.jshepherd.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

    @Test
    @SuppressWarnings("unchecked")
    void withAutoReload_shouldReloadAndNotifyListener_whenFileChangesOnDisk() throws Exception {
        // Arrange
        Path watchedPath = tempDir.resolve("watched.json");
        Files.writeString(watchedPath, "{}");
        when(mockFactory.create(eq(watchedPath), anyBoolean())).thenReturn(mockDelegate);

        TestConfig config = ConfigurationLoader.from(watchedPath)
                .withAutoReload(Duration.ofMillis(50))
                .load(TestConfig::new);
        assertTrue(config.isAutoReloadActive(), "Watcher should be running after load");

        CountDownLatch listenerCalled = new CountDownLatch(1);
        config.setOnAutoReload(listenerCalled::countDown);

        // Act - simulate an external edit
        Files.writeString(watchedPath, "{\"changed\":true}");

        // Assert
        verify(mockDelegate, timeout(5000)).reload(config);
        assertTrue(listenerCalled.await(5, TimeUnit.SECONDS), "Auto-reload listener should be notified");

        // Cleanup / stop semantics
        config.stopAutoReload();
        assertFalse(config.isAutoReloadActive(), "Watcher should be stopped");
    }

    @Test
    void withAutoReload_shouldRejectNonPositiveInterval() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfigurationLoader.from(configPath).withAutoReload(Duration.ZERO));
    }

    @Test
    void load_shouldThrowClearError_whenFileHasNoExtension() {
        // Arrange
        Path noExtensionPath = tempDir.resolve("config");

        // Act & Assert
        ConfigurationException exception = assertThrows(
                ConfigurationException.class,
                () -> ConfigurationLoader.load(noExtensionPath, TestConfig::new)
        );
        assertTrue(exception.getMessage().contains("no file extension"),
                "Error should explain that the extension is missing, got: " + exception.getMessage());
    }

    // Test implementation of ConfigurablePojo
    private static class TestConfig extends ConfigurablePojo<TestConfig> {
        // Empty implementation for testing
    }
}
