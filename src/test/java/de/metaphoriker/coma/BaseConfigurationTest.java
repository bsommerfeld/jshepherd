package de.metaphoriker.coma;

import static org.junit.jupiter.api.Assertions.*;

import de.metaphoriker.coma.annotation.ConfigurationValue;
import de.metaphoriker.coma.annotation.Configuration;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BaseConfigurationTest {

  private TestConfiguration config;

  @BeforeEach
  void setUp() {
    config = new TestConfiguration();
  }

  @Test
  void testAbstractClassCannotHaveConfigurationAnnotation() {
    assertThrows(
        IllegalStateException.class,
        () -> {
          new AbstractConfigClass() {}.initialize();
        },
        "Abstract class should not be able to have the @Configuration annotation.");
  }

  @Test
  void testParentIntFieldLoadAndSave() throws Exception {
    ChildConfig childConfig = new ChildConfig();
    childConfig.initialize();

    Field parentField = ParentConfig.class.getDeclaredField("parentField");
    parentField.setAccessible(true);

    assertEquals(1, parentField.get(childConfig));

    parentField.set(childConfig, 100);
    childConfig.saveConfiguration();
    childConfig.reloadConfig();

    assertEquals(100, parentField.get(childConfig));
  }

  public abstract class ParentConfig extends BaseConfiguration {
    @ConfigurationValue(name = "parent-field", description = "Field from parent class")
    private int parentField = 1;

    protected int getParentField() {
      return parentField;
    }
  }

  @Configuration(fileName = "test-config", type = ConfigurationType.YAML)
  public class ChildConfig extends ParentConfig {
    @ConfigurationValue(name = "child-field", description = "Field from child class")
    private String childField = "childValue";
  }

  // Test abstract class with the @Configuration annotation
  @Configuration(fileName = "abstract-config", type = ConfigurationType.YAML)
  public abstract static class AbstractConfigClass extends BaseConfiguration {
    @ConfigurationValue(name = "abstract-field", description = "Field from abstract class")
    private String abstractField = "abstractValue";
  }

  @Test
  void testStringFieldLoadAndSave() throws Exception {
    Field stringField = TestConfiguration.class.getDeclaredField("testString");
    stringField.setAccessible(true);

    assertEquals("defaultValue", stringField.get(config));

    stringField.set(config, "newValue");
    config.saveConfiguration();

    config.reloadConfig();
    assertEquals("newValue", stringField.get(config));
  }

  @Test
  void testIntegerFieldLoadAndSave() throws Exception {
    Field intField = TestConfiguration.class.getDeclaredField("testInt");
    intField.setAccessible(true);

    assertEquals(123, intField.get(config));

    intField.set(config, 100);
    config.saveConfiguration();

    config.reloadConfig();
    assertEquals(100, intField.get(config));
  }

  @Test
  void testBooleanFieldLoadAndSave() throws Exception {
    Field booleanField = TestConfiguration.class.getDeclaredField("testBoolean");
    booleanField.setAccessible(true);

    assertTrue((boolean) booleanField.get(config));

    booleanField.set(config, false);
    config.saveConfiguration();

    config.reloadConfig();
    assertFalse((boolean) booleanField.get(config));
  }

  @Test
  void testListFieldLoadAndSave() throws Exception {
    Field listField = TestConfiguration.class.getDeclaredField("testList");
    listField.setAccessible(true);

    assertEquals(Arrays.asList("item1", "item2", "item3"), listField.get(config));

    listField.set(config, Arrays.asList("newItem1", "newItem2"));
    config.saveConfiguration();

    config.reloadConfig();
    assertEquals(Arrays.asList("newItem1", "newItem2"), listField.get(config));
  }

  @Test
  void testDoubleFieldLoadAndSave() throws Exception {
    Field doubleField = TestConfiguration.class.getDeclaredField("testDouble");
    doubleField.setAccessible(true);

    assertEquals(123.456, (double) doubleField.get(config));

    doubleField.set(config, 100.0);
    config.saveConfiguration();

    config.reloadConfig();
    assertEquals(100.0, (double) doubleField.get(config));
  }

  @Test
  void testLongFieldLoadAndSave() throws Exception {
    Field longField = TestConfiguration.class.getDeclaredField("testLong");
    longField.setAccessible(true);

    assertEquals(1234567890L, (long) longField.get(config));

    longField.set(config, 100L);
    config.saveConfiguration();

    config.reloadConfig();
    assertEquals(100L, (long) longField.get(config));
  }

  @Test
  void testFloatFieldLoadAndSave() throws Exception {
    Field floatField = TestConfiguration.class.getDeclaredField("testFloat");
    floatField.setAccessible(true);

    assertEquals(123.456f, (float) floatField.get(config));

    floatField.set(config, 100.0f);
    config.saveConfiguration();

    config.reloadConfig();
    assertEquals(100.0f, (float) floatField.get(config));
  }

  @Test
  void testFileCreationAndLoading() {
    File configFile = new File("test-config.yml");
    assertTrue(configFile.exists(), "Config file should be created initially.");

    assertTrue(configFile.delete(), "Config file should be deleted.");

    config.reloadConfig();

    assertTrue(configFile.exists(), "Config file should be recreated after reloadConfig().");
  }
}
