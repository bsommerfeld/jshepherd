package de.bsommerfeld.jshepherd.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurablePojoTest {

    @Mock
    private PersistenceDelegate<TestConfig> mockDelegate;

    private TestConfig config;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        config = new TestConfig();
        config._setPersistenceDelegate(mockDelegate);
    }

    @Test
    void save_shouldCallDelegateSave() {
        // Act
        config.save();

        // Assert
        verify(mockDelegate).save(config);
    }

    @Test
    void reload_shouldCallDelegateReloadAndInvokePostInject() {
        // Arrange
        config.postInjectCalled = false; // Reset flag

        // Act
        config.reload();

        // Assert
        verify(mockDelegate).reload(config);
        assertTrue(config.postInjectCalled, "PostInject method should be called during reload");
    }

    @Test
    void save_shouldThrowException_whenDelegateIsNull() {
        // Arrange
        TestConfig configWithoutDelegate = new TestConfig();

        // Act & Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                configWithoutDelegate::save
        );
        assertTrue(exception.getMessage().contains("not properly initialized"));
    }

    @Test
    void reload_shouldThrowException_whenDelegateIsNull() {
        // Arrange
        TestConfig configWithoutDelegate = new TestConfig();

        // Act & Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                configWithoutDelegate::reload
        );
        assertTrue(exception.getMessage().contains("not properly initialized"));
    }

    // Test implementation of ConfigurablePojo
    static class TestConfig extends ConfigurablePojo<TestConfig> {
        boolean postInjectCalled = false;

        @de.bsommerfeld.jshepherd.annotation.PostInject
        private void onPostInject() {
            postInjectCalled = true;
        }
    }
}
