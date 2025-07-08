package de.bsommerfeld.jshepherd.properties;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.CommentSection;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.core.AbstractPersistenceDelegate;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.utils.ClassUtils;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Implementation of PersistenceDelegate for Java Properties format. This class handles loading,
 * saving, and reloading of configuration objects in Properties format.
 * 
 * <p>This implementation supports comments in the properties file and provides special handling
 * for various data types including dates, times, lists, and maps.
 */
class PropertiesPersistenceDelegate<T extends ConfigurablePojo<T>>
    extends AbstractPersistenceDelegate<T> {
  private static final String LOG_PREFIX = "[PROPERTIES] ";

  // Date formatters for Properties serialization
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
  private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_TIME;

  // Track the last comment section for formatting
  private String lastCommentSectionHash;

  PropertiesPersistenceDelegate(Path filePath, boolean useComplexSaveWithComments) {
    super(filePath, useComplexSaveWithComments);
  }

  /**
   * Attempts to load configuration data from the Properties file into the provided instance.
   * 
   * @param instance The instance to load data into
   * @return true if data was successfully loaded, false otherwise
   * @throws Exception if an error occurs during loading
   */
  @Override
  protected boolean tryLoadFromFile(T instance) throws Exception {
    Properties properties = new Properties();
    try (InputStream inputStream = Files.newInputStream(filePath)) {
      properties.load(inputStream);
    } catch (IOException e) {
      System.err.println(LOG_PREFIX + "ERROR: Failed to load Properties file: " + e.getMessage());
      throw e;
    }

    if (!properties.isEmpty()) {
      applyDataToInstance(instance, new PropertiesDataExtractor(properties));
      return true;
    }
    return false;
  }

  /**
   * Saves the configuration data to a Properties file with basic comments.
   * This method includes class-level comments but not field-level comments.
   * 
   * @param pojoInstance The instance to save
   * @param targetPath The path to save the file to
   * @throws IOException if an error occurs during saving
   */
  @Override
  protected void saveSimple(T pojoInstance, Path targetPath) throws IOException {
    try (PrintWriter writer =
        new PrintWriter(
            Files.newBufferedWriter(
                targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
      // Add class-level comments if present
      Comment classComment = pojoInstance.getClass().getAnnotation(Comment.class);
      if (classComment != null && classComment.value().length > 0) {
        for (String line : classComment.value()) {
          writer.println("# " + line);
        }
        writer.println();
      }

      // Convert POJO to Properties format
      Properties properties = createPropertiesFromPojo(pojoInstance);

      // Write properties in sorted order for consistency
      List<String> sortedKeys = new ArrayList<>(properties.stringPropertyNames());
      Collections.sort(sortedKeys);

      for (String key : sortedKeys) {
        String value = properties.getProperty(key);
        writer.println(escapePropertyKey(key) + "=" + escapePropertyValue(value));
      }
    } catch (IOException e) {
      System.err.println(LOG_PREFIX + "ERROR: Failed to save Properties file: " + e.getMessage());
      throw e;
    }
  }

  /**
   * Saves the configuration data to a Properties file with comprehensive comments.
   * This method includes class-level comments, section comments, and field-level comments.
   * 
   * @param pojoInstance The instance to save
   * @param targetPath The path to save the file to
   * @throws IOException if an error occurs during saving
   */
  @Override
  protected void saveWithComments(T pojoInstance, Path targetPath) throws IOException {
    try (PrintWriter writer =
        new PrintWriter(
            Files.newBufferedWriter(
                targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
      this.lastCommentSectionHash = null;

      // Add class-level comments if present
      Comment classComment = pojoInstance.getClass().getAnnotation(Comment.class);
      if (classComment != null && classComment.value().length > 0) {
        for (String line : classComment.value()) writer.println("# " + line);
        writer.println();
      }

      // Get all fields from the class hierarchy
      List<Field> fields =
          ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);

      // Process each field
      for (int fieldIdx = 0; fieldIdx < fields.size(); fieldIdx++) {
        Field field = fields.get(fieldIdx);
        // Skip static and transient fields
        if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
          continue;
        }
        field.setAccessible(true);
        Key keyAnnotation = field.getAnnotation(Key.class);
        if (keyAnnotation == null) continue;

        String propKey = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

        // Handle section comments
        CommentSection sectionAnnotation = field.getAnnotation(CommentSection.class);
        if (sectionAnnotation != null && sectionAnnotation.value().length > 0) {
          String currentSectionHash = String.join("|", sectionAnnotation.value());
          if (!currentSectionHash.equals(this.lastCommentSectionHash)) {
            if (this.lastCommentSectionHash != null) writer.println();
            for (String commentLine : sectionAnnotation.value()) writer.println("# " + commentLine);
            this.lastCommentSectionHash = currentSectionHash;
          }
        }

        // Handle field comments
        Comment fieldComment = field.getAnnotation(Comment.class);
        if (fieldComment != null) {
          for (String commentLine : fieldComment.value()) writer.println("# " + commentLine);
        }

        try {
          Object value = field.get(pojoInstance);
          if (value != null) {
            String stringValue = convertFieldValueToString(value);
            writer.println(escapePropertyKey(propKey) + "=" + escapePropertyValue(stringValue));
          }
        } catch (IllegalAccessException e) {
          System.err.println(LOG_PREFIX + "ERROR: Could not access field " + field.getName() + 
                  " during save: " + e.getMessage());
          continue;
        }

        // Logic for blank line after entry
        boolean addBlankLine = false;
        if (fieldIdx < fields.size() - 1) {
          for (int k = fieldIdx + 1; k < fields.size(); k++) {
            Field nextField = fields.get(k);
            if (Modifier.isStatic(nextField.getModifiers())
                || Modifier.isTransient(nextField.getModifiers())) continue;
            if (nextField.getAnnotation(Key.class) != null) {
              addBlankLine = true;
              break;
            }
          }
        }
        if (addBlankLine) {
          writer.println();
        }
      }
    } catch (IOException e) {
      System.err.println(LOG_PREFIX + "ERROR: Failed to save Properties file with comments: " + e.getMessage());
      throw e;
    }
  }

  /**
   * Creates a Properties object from the POJO instance.
   * This method extracts all fields with @Key annotations and converts their values to strings.
   * 
   * @param pojoInstance The instance to extract data from
   * @return A Properties object containing the POJO's data
   */
  private Properties createPropertiesFromPojo(T pojoInstance) {
    Properties properties = new Properties();

    // Get all fields from the class hierarchy
    List<Field> fields =
        ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);

    // Process each field
    for (Field field : fields) {
      // Skip static and transient fields
      if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
        continue;
      }

      Key keyAnnotation = field.getAnnotation(Key.class);
      if (keyAnnotation == null) continue;

      String propKey = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

      try {
        field.setAccessible(true);
        Object value = field.get(pojoInstance);

        if (value != null) {
          String stringValue = convertFieldValueToString(value);
          properties.setProperty(propKey, stringValue);
        }
      } catch (IllegalAccessException e) {
        System.err.println(LOG_PREFIX + 
            "ERROR: Could not access field " + field.getName() + 
            " during properties creation: " + e.getMessage());
      }
    }

    return properties;
  }

  /**
   * Converts a field value to a string representation suitable for Properties format.
   * This method handles various data types including dates, times, lists, and maps.
   * 
   * @param value The value to convert
   * @return A string representation of the value
   */
  private String convertFieldValueToString(Object value) {
    if (value == null) {
      return "";
    }

    // Handle different types with appropriate formatting
    if (value instanceof String) {
      return (String) value;
    } else if (value instanceof LocalDate) {
      return ((LocalDate) value).format(DATE_FORMATTER);
    } else if (value instanceof LocalDateTime) {
      return ((LocalDateTime) value).format(DATETIME_FORMATTER);
    } else if (value instanceof LocalTime) {
      return ((LocalTime) value).format(TIME_FORMATTER);
    } else if (value instanceof List<?> list) {
      // Format lists as [item1, item2, ...]
      if (list.isEmpty()) {
        return "[]";
      }
      StringBuilder sb = new StringBuilder("[");
      for (int i = 0; i < list.size(); i++) {
        if (i > 0) sb.append(", ");
        sb.append(list.get(i).toString());
      }
      sb.append("]");
      return sb.toString();
    } else if (value instanceof Map<?, ?> map) {
      // Format maps as {key1=value1, key2=value2, ...}
      if (map.isEmpty()) {
        return "{}";
      }
      StringBuilder sb = new StringBuilder("{");
      boolean first = true;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!first) sb.append(", ");
        sb.append(entry.getKey()).append("=").append(entry.getValue());
        first = false;
      }
      sb.append("}");
      return sb.toString();
    } else {
      // Default to toString() for other types
      return value.toString();
    }
  }

  /**
   * Escapes special characters in property keys according to the Properties format specification.
   * 
   * @param key The property key to escape
   * @return The escaped property key
   */
  private String escapePropertyKey(String key) {
    return key.replace(" ", "\\ ")
        .replace(":", "\\:")
        .replace("=", "\\=")
        .replace("#", "\\#")
        .replace("!", "\\!");
  }

  /**
   * Escapes special characters in property values according to the Properties format specification.
   * 
   * @param value The property value to escape
   * @return The escaped property value
   */
  private String escapePropertyValue(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
        .replace("\f", "\\f");
  }

  /**
   * Implementation of DataExtractor for Properties format.
   * This class extracts values from a Properties object and converts them to appropriate types.
   */
  private record PropertiesDataExtractor(Properties properties) implements DataExtractor {

    /**
     * Checks if the Properties data contains a value for the given key.
     * 
     * @param key The key to check
     * @return true if the key exists in the data, false otherwise
     */
    @Override
    public boolean hasValue(String key) {
      return properties.containsKey(key);
    }

    /**
     * Gets the value for the given key from the Properties data and converts it to the target type.
     * 
     * @param key The key to get the value for
     * @param targetType The expected type of the value
     * @return The converted value, or null if not found or conversion fails
     */
    @Override
    public Object getValue(String key, Class<?> targetType) {
      String propValue = properties.getProperty(key);
      if (propValue != null && !propValue.trim().isEmpty()) {
        return convertStringToFieldType(propValue, targetType);
      }
      return null;
    }

    /**
     * Converts a string value from Properties to the appropriate field type.
     * This method handles various data types including primitives, dates, times, lists, and maps.
     * 
     * @param value The string value to convert
     * @param targetType The target type to convert to
     * @return The converted value, or null if conversion fails
     */
    private Object convertStringToFieldType(String value, Class<?> targetType) {
      if (value == null || value.trim().isEmpty()) {
        return null;
      }

      value = value.trim();

      try {
        // Handle different target types with appropriate conversion
        if (targetType == String.class) {
          return value;
        } else if (targetType == Integer.class || targetType == int.class) {
          return Integer.parseInt(value);
        } else if (targetType == Long.class || targetType == long.class) {
          return Long.parseLong(value);
        } else if (targetType == Double.class || targetType == double.class) {
          return Double.parseDouble(value);
        } else if (targetType == Float.class || targetType == float.class) {
          return Float.parseFloat(value);
        } else if (targetType == Boolean.class || targetType == boolean.class) {
          return Boolean.parseBoolean(value);
        } else if (targetType == Short.class || targetType == short.class) {
          return Short.parseShort(value);
        } else if (targetType == Byte.class || targetType == byte.class) {
          return Byte.parseByte(value);
        } else if (targetType == LocalDate.class) {
          return LocalDate.parse(value, DATE_FORMATTER);
        } else if (targetType == LocalDateTime.class) {
          return LocalDateTime.parse(value, DATETIME_FORMATTER);
        } else if (targetType == LocalTime.class) {
          return LocalTime.parse(value, TIME_FORMATTER);
        } else if (List.class.isAssignableFrom(targetType)) {
          // Parse comma-separated values as List
          if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
          }
          if (value.trim().isEmpty()) {
            return new ArrayList<>();
          }
          return Arrays.asList(value.split(",\\s*"));
        } else if (Map.class.isAssignableFrom(targetType)) {
          // Parse key=value pairs as Map
          Map<String, String> map = new HashMap<>();
          if (value.startsWith("{") && value.endsWith("}")) {
            value = value.substring(1, value.length() - 1);
          }
          if (!value.trim().isEmpty()) {
            String[] pairs = value.split(",\\s*");
            for (String pair : pairs) {
              String[] keyValue = pair.split("=", 2);
              if (keyValue.length == 2) {
                map.put(keyValue[0].trim(), keyValue[1].trim());
              }
            }
          }
          return map;
        } else {
          // For other types, return as string
          return value;
        }
      } catch (Exception e) {
        System.err.println(LOG_PREFIX +
            "WARNING: Failed to convert value '"
                + value
                + "' to type "
                + targetType.getSimpleName()
                + ": "
                + e.getMessage());
        return null;
      }
    }
  }
}
