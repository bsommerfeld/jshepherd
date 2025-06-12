package de.bsommerfeld.jshepherd.core;

import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.annotation.PostInject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class AbstractPersistenceDelegateTest {

    @TempDir
    Path tempDir;

    private Path configPath;
    private TestPersistenceDelegate delegate;
    private TestConfig testConfig;

    @BeforeEach
    void setUp() {
        configPath = tempDir.resolve("test-config.json");
        delegate = new TestPersistenceDelegate(configPath, true);
        testConfig = new TestConfig();
    }

    @Test
    void loadInitial_shouldCreateDefaultWhenFileDoesNotExist() {
        // Arrange
        Supplier<TestConfig> supplier = TestConfig::new;

        // Act
        TestConfig result = delegate.loadInitial(supplier);

        // Assert
        assertNotNull(result);
        assertTrue(Files.exists(configPath), "Config file should be created");
        assertTrue(delegate.saveCalled, "Save should be called");
    }

    @Test
    void loadInitial_shouldLoadExistingFile() throws IOException {
        // Arrange
        Files.writeString(configPath, "{}"); // Create empty file
        Supplier<TestConfig> supplier = TestConfig::new;
        delegate.dataToLoad.put("stringValue", "loaded value");
        delegate.dataToLoad.put("intValue", 42);

        // Act
        TestConfig result = delegate.loadInitial(supplier);

        // Assert
        assertNotNull(result);
        assertEquals("loaded value", result.stringValue);
        assertEquals(42, result.intValue);
    }

    @Test
    void save_shouldCreateParentDirectories() throws IOException {
        // Arrange
        Path nestedPath = tempDir.resolve("nested/deeply/config.json");
        TestPersistenceDelegate nestedDelegate = new TestPersistenceDelegate(nestedPath, true);

        // Act
        nestedDelegate.save(testConfig);

        // Assert
        assertTrue(Files.exists(nestedPath.getParent()), "Parent directories should be created");
    }

    @Test
    void reload_shouldUpdateInstanceFields() throws IOException {
        // Arrange
        Files.writeString(configPath, "{}"); // Create empty file
        delegate.dataToLoad.put("stringValue", "updated value");
        delegate.dataToLoad.put("intValue", 99);

        // Act
        delegate.reload(testConfig);

        // Assert
        assertEquals("updated value", testConfig.stringValue);
        assertEquals(99, testConfig.intValue);
    }

    @Test
    void convertNumericIfNeeded_shouldConvertBetweenTypes() {
        // Arrange & Act
        Object intFromLong = delegate.convertNumericIfNeeded(100L, Integer.class);
        Object doubleFromInt = delegate.convertNumericIfNeeded(200, Double.class);
        Object floatFromDouble = delegate.convertNumericIfNeeded(300.5, Float.class);

        // Assert
        assertTrue(intFromLong instanceof Integer);
        assertEquals(100, intFromLong);

        assertTrue(doubleFromInt instanceof Double);
        assertEquals(200.0, doubleFromInt);

        assertTrue(floatFromDouble instanceof Float);
        assertEquals(300.5f, floatFromDouble);
    }

    // Test implementation of ConfigurablePojo
    private static class TestConfig extends ConfigurablePojo<TestConfig> {
        @Key
        private String stringValue = "default";

        @Key
        private int intValue = 0;

        boolean postInjectCalled = false;

        @PostInject
        private void onPostInject() {
            postInjectCalled = true;
        }
    }

    // Test implementation of AbstractPersistenceDelegate
    private static class TestPersistenceDelegate extends AbstractPersistenceDelegate<TestConfig> {
        boolean saveCalled = false;
        Map<String, Object> dataToLoad = new HashMap<>();

        TestPersistenceDelegate(Path filePath, boolean useComplexSaveWithComments) {
            super(filePath, useComplexSaveWithComments);
        }

        @Override
        protected boolean tryLoadFromFile(TestConfig instance) {
            if (dataToLoad.isEmpty()) {
                return false;
            }

            applyDataToInstance(instance, new TestDataExtractor(dataToLoad));
            return true;
        }

        @Override
        protected void saveSimple(TestConfig pojoInstance, Path targetPath) throws IOException {
            saveCalled = true;
            Files.writeString(targetPath, "{}"); // Write empty JSON
        }

        @Override
        protected void saveWithComments(TestConfig pojoInstance, Path targetPath) throws IOException {
            saveCalled = true;
            Files.writeString(targetPath, "{}"); // Write empty JSON
        }

        // Use the protected method for testing
        public Object testConvertNumericIfNeeded(Object value, Class<?> targetType) {
            return convertNumericIfNeeded(value, targetType);
        }

        private static class TestDataExtractor implements DataExtractor {
            private final Map<String, Object> data;

            TestDataExtractor(Map<String, Object> data) {
                this.data = data;
            }

            @Override
            public boolean hasValue(String key) {
                return data.containsKey(key);
            }

            @Override
            public Object getValue(String key, Class<?> targetType) {
                return data.get(key);
            }
        }
    }
}
