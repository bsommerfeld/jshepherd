package de.metaphoriker.coma;

import static org.junit.jupiter.api.Assertions.*;

import de.metaphoriker.coma.annotation.ConfigValue;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnnotationProcessingTest {

  private TestConfiguration config;

  @BeforeEach
  void setUp() {
    config = new TestConfiguration();
    config.initialize();
  }

  @Test
  void testAnnotationsAreProcessed() throws Exception {
    Field stringField = TestConfiguration.class.getDeclaredField("testString");
    assertTrue(
        stringField.isAnnotationPresent(ConfigValue.class),
        "testString field should have @ConfigValue annotation.");

    ConfigValue configValue = stringField.getAnnotation(ConfigValue.class);
    assertEquals(
        "test-string", configValue.name(), "testString field should have correct key name.");
    assertEquals(
        "Test string configuration",
        configValue.description()[0],
        "testString field should have correct description.");

    stringField.setAccessible(true);
    assertEquals(
        "defaultValue",
        stringField.get(config),
        "testString should have the correct default value.");
  }
}
