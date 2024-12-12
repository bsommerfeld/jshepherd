package de.metaphoriker.coma;

import de.metaphoriker.coma.annotation.ConfigurationValue;
import de.metaphoriker.coma.annotation.Configuration;
import java.util.Arrays;
import java.util.List;

// A concrete class for testing the abstract BaseConfiguration class
@Configuration(fileName = "test-config", type = ConfigurationType.YAML)
class TestConfiguration extends BaseConfiguration {

  @ConfigurationValue(name = "test-string", description = "Test string configuration")
  private String testString = "defaultValue";

  @ConfigurationValue(name = "test-int", description = "Test integer configuration")
  private int testInt = 123;

  @ConfigurationValue(name = "test-double", description = "Test double configuration")
  private double testDouble = 123.456;

  @ConfigurationValue(name = "test-long", description = "Test long configuration")
  private long testLong = 1234567890L;

  @ConfigurationValue(name = "test-float", description = "Test float configuration")
  private float testFloat = 123.456f;

  @ConfigurationValue(name = "test-list", description = "Test list configuration")
  private List<String> testList = Arrays.asList("item1", "item2", "item3");

  @ConfigurationValue(name = "test-boolean", description = "Test boolean configuration")
  private boolean testBoolean = true;
}
