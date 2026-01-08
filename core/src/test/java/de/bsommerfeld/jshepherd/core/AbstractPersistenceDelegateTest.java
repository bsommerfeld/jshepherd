package de.bsommerfeld.jshepherd.core;

import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.annotation.PostInject;
import de.bsommerfeld.jshepherd.annotation.Section;
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

    @Test
    void isSection_shouldIdentifySectionFields() throws NoSuchFieldException {
        // Arrange
        java.lang.reflect.Field sectionField = ConfigWithSection.class.getDeclaredField("nested");
        java.lang.reflect.Field regularField = ConfigWithSection.class.getDeclaredField("name");

        // Act & Assert
        assertTrue(delegate.testIsSection(sectionField), "@Section field should be identified");
        assertFalse(delegate.testIsSection(regularField), "Regular @Key field should not be a section");
    }

    @Test
    void resolveSectionName_shouldReturnExplicitNameOrFallback() throws NoSuchFieldException {
        // Arrange
        java.lang.reflect.Field explicitSection = ConfigWithSection.class.getDeclaredField("nested");
        java.lang.reflect.Field keyFallback = ConfigWithSection.class.getDeclaredField("defaultNameSection");

        // Act & Assert
        assertEquals("explicit-name", delegate.testResolveSectionName(explicitSection));
        assertEquals("default-name-section", delegate.testResolveSectionName(keyFallback),
                "Should fall back to @Key value");
    }

    @Test
    void getNonSectionFields_shouldReturnOnlyKeyFields() {
        // Act
        var fields = delegate.testGetNonSectionFields(ConfigWithSection.class, ConfigurablePojo.class);

        // Assert
        assertEquals(1, fields.size());
        assertEquals("name", fields.get(0).getName());
    }

    @Test
    void getSectionFields_shouldReturnOnlySectionFields() {
        // Act
        var fields = delegate.testGetSectionFields(ConfigWithSection.class, ConfigurablePojo.class);

        // Assert
        assertEquals(2, fields.size());
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

    // Config with @Section fields for testing
    private static class ConfigWithSection extends ConfigurablePojo<ConfigWithSection> {
        @Key("name")
        private String name = "test";

        @Section("explicit-name")
        private NestedSettings nested = new NestedSettings();

        @Key("default-name-section")
        @Section
        private NestedSettings defaultNameSection = new NestedSettings();
    }

    private static class NestedSettings {
        @Key("value")
        private String value = "nested";
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

        // Section utility test helpers
        public boolean testIsSection(java.lang.reflect.Field field) {
            return isSection(field);
        }

        public String testResolveSectionName(java.lang.reflect.Field field) {
            return resolveSectionName(field);
        }

        public java.util.List<java.lang.reflect.Field> testGetNonSectionFields(Class<?> clazz, Class<?> stopClass) {
            return getNonSectionFields(clazz, stopClass);
        }

        public java.util.List<java.lang.reflect.Field> testGetSectionFields(Class<?> clazz, Class<?> stopClass) {
            return getSectionFields(clazz, stopClass);
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
