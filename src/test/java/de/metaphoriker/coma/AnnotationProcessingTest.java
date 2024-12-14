package de.metaphoriker.coma;

import static org.junit.jupiter.api.Assertions.*;

import de.metaphoriker.coma.annotation.Comment;
import de.metaphoriker.coma.annotation.Key;
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
        stringField.isAnnotationPresent(Key.class),
        "testString field should have @Key annotation.");

    Key key = stringField.getAnnotation(Key.class);
    assertEquals("test-string", key.value(), "testString field should have correct key name.");

    assertTrue(
        stringField.isAnnotationPresent(Comment.class),
        "testString field should have @Comment annotation.");

    Comment comment = stringField.getAnnotation(Comment.class);
    assertEquals(
        "Test string configuration comment 1",
        comment.value()[0],
        "testString field should have correct first comment line.");
    assertEquals(
        "Test string configuration comment 2",
        comment.value()[1],
        "testString field should have correct second comment line.");

    stringField.setAccessible(true);
    assertEquals(
        "defaultValue",
        stringField.get(config),
        "testString should have the correct default value.");
  }
}
