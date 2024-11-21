package de.metaphoriker.coma;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class ConfigurationOptionTest {

  @Test
  public void testEmptyOption() {
    ConfigurationOption<Object> emptyOption = ConfigurationOption.EMPTY_OPTION;
    assertEquals("", emptyOption.getValue());
    assertArrayEquals(new String[] {""}, emptyOption.getComments());
  }

  @Test
  public void testGetValueWithString() {
    ConfigurationOption<String> option =
        new ConfigurationOption<>("Test Value", new String[] {"Test Comment"});
    assertEquals("Test Value", option.getValue());
    assertEquals("Test Comment", option.getComments()[0]);
  }

  @Test
  public void testGetValueWithInteger() {
    ConfigurationOption<Integer> option =
        new ConfigurationOption<>(42, new String[] {"Integer Comment"});
    assertEquals(42, option.getValue());
    assertEquals("Integer Comment", option.getComments()[0]);
  }

  @Test
  void testNullValueThrowsException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ConfigurationOption<>(null, new String[] {"This should throw an exception"}));

    ConfigurationOption<String> option =
        new ConfigurationOption<>("value", new String[] {"Valid string option"});
    assertThrows(IllegalArgumentException.class, () -> option.withNewValue(null, String.class));
  }

  @Test
  public void testWithNewValueValidString() {
    ConfigurationOption<String> option =
        new ConfigurationOption<>("Old Value", new String[] {"Old Comment"});
    ConfigurationOption<String> newOption = option.withNewValue("New Value", String.class);
    assertEquals("New Value", newOption.getValue());
    assertEquals("Old Comment", newOption.getComments()[0]);
  }

  @Test
  public void testWithNewValueValidInteger() {
    ConfigurationOption<Integer> option =
        new ConfigurationOption<>(100, new String[] {"Initial Comment"});
    ConfigurationOption<Integer> newOption = option.withNewValue(200, Integer.class);
    assertEquals(200, newOption.getValue());
    assertEquals("Initial Comment", newOption.getComments()[0]);
  }

  @Test
  public void testWithNewValueInvalidType() {
    ConfigurationOption<String> option =
        new ConfigurationOption<>("Old Value", new String[] {"Old Comment"});
    assertThrows(IllegalArgumentException.class, () -> option.withNewValue(123, String.class));
  }

  @Test
  public void testWithNewValueValidTypeButNullValue() {
    ConfigurationOption<String> option =
        new ConfigurationOption<>("Non-null Value", new String[] {"Non-null Comment"});
    assertThrows(IllegalArgumentException.class, () -> option.withNewValue(null, String.class));
  }
}
