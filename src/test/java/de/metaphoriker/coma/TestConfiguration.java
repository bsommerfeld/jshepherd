package de.metaphoriker.coma;

import de.metaphoriker.coma.annotation.Comment;
import de.metaphoriker.coma.annotation.Configuration;
import de.metaphoriker.coma.annotation.Key;
import java.util.Arrays;
import java.util.List;

// A concrete class for testing the abstract BaseConfiguration class
@Comment("Test configuration class comment")
@Configuration(fileName = "test-config", type = ConfigurationType.YAML)
class TestConfiguration extends BaseConfiguration {

  @Key("test-string")
  @Comment({"Test string configuration comment 1", "Test string configuration comment 2"})
  private String testString = "defaultValue";

  @Key("test-int")
  @Comment("Test int configuration comment")
  private int testInt = 123;

  @Key("test-double")
  @Comment("Test double configuration comment")
  private double testDouble = 123.456;

  @Key("test-long")
  @Comment("Test long configuration comment")
  private long testLong = 1234567890L;

  @Key("test-float")
  @Comment("Test float configuration comment")
  private float testFloat = 123.456f;

  @Key("test-list")
  @Comment("Test list configuration comment")
  private List<String> testList = Arrays.asList("item1", "item2", "item3");

  @Key("test-boolean")
  @Comment("Test boolean configuration comment")
  private boolean testBoolean = true;
}
