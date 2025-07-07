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
 */
class PropertiesPersistenceDelegate<T extends ConfigurablePojo<T>>
    extends AbstractPersistenceDelegate<T> {
  // Date formatters for Properties serialization
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
  private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_TIME;

  PropertiesPersistenceDelegate(Path filePath, boolean useComplexSaveWithComments) {
    super(filePath, useComplexSaveWithComments);
  }

  @Override
  protected boolean tryLoadFromFile(T instance) throws Exception {
    Properties properties = new Properties();
    try (InputStream inputStream = Files.newInputStream(filePath)) {
      properties.load(inputStream);
    }

    if (!properties.isEmpty()) {
      applyDataToInstance(instance, new PropertiesDataExtractor(properties));
      return true;
    }
    return false;
  }

  @Override
  protected void saveSimple(T pojoInstance, Path targetPath) throws IOException {
    try (PrintWriter writer =
        new PrintWriter(
            Files.newBufferedWriter(
                targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
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
    }
  }

  @Override
  protected void saveWithComments(T pojoInstance, Path targetPath) throws IOException {
    try (PrintWriter writer =
        new PrintWriter(
            Files.newBufferedWriter(
                targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
      this.lastCommentSectionHash = null;

      Comment classComment = pojoInstance.getClass().getAnnotation(Comment.class);
      if (classComment != null && classComment.value().length > 0) {
        for (String line : classComment.value()) writer.println("# " + line);
        writer.println();
      }

      List<Field> fields =
          ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
      for (int fieldIdx = 0; fieldIdx < fields.size(); fieldIdx++) {
        Field field = fields.get(fieldIdx);
        if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
          continue;
        }
        field.setAccessible(true);
        Key keyAnnotation = field.getAnnotation(Key.class);
        if (keyAnnotation == null) continue;

        String propKey = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

        CommentSection sectionAnnotation = field.getAnnotation(CommentSection.class);
        if (sectionAnnotation != null && sectionAnnotation.value().length > 0) {
          String currentSectionHash = String.join("|", sectionAnnotation.value());
          if (!currentSectionHash.equals(this.lastCommentSectionHash)) {
            if (this.lastCommentSectionHash != null) writer.println();
            for (String commentLine : sectionAnnotation.value()) writer.println("# " + commentLine);
            this.lastCommentSectionHash = currentSectionHash;
          }
        }

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
          System.err.println("ERROR: Could not access field " + field.getName() + " during save.");
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
    }
  }

  private Properties createPropertiesFromPojo(T pojoInstance) {
    Properties properties = new Properties();

    List<Field> fields =
        ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
    for (Field field : fields) {
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
        System.err.println(
            "ERROR: Could not access field " + field.getName() + " during properties creation.");
      }
    }

    return properties;
  }

  private String convertFieldValueToString(Object value) {
    if (value == null) {
      return "";
    }

    if (value instanceof String) {
      return (String) value;
    } else if (value instanceof LocalDate) {
      return ((LocalDate) value).format(DATE_FORMATTER);
    } else if (value instanceof LocalDateTime) {
      return ((LocalDateTime) value).format(DATETIME_FORMATTER);
    } else if (value instanceof LocalTime) {
      return ((LocalTime) value).format(TIME_FORMATTER);
    } else if (value instanceof List<?> list) {
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
      return value.toString();
    }
  }

  private String escapePropertyKey(String key) {
    return key.replace(" ", "\\ ")
        .replace(":", "\\:")
        .replace("=", "\\=")
        .replace("#", "\\#")
        .replace("!", "\\!");
  }

  private String escapePropertyValue(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
        .replace("\f", "\\f");
  }

  // DataExtractor implementation for Properties
  private record PropertiesDataExtractor(Properties properties) implements DataExtractor {

    @Override
    public boolean hasValue(String key) {
      return properties.containsKey(key);
    }

    @Override
    public Object getValue(String key, Class<?> targetType) {
      String propValue = properties.getProperty(key);
      if (propValue != null && !propValue.trim().isEmpty()) {
        return convertStringToFieldType(propValue, targetType);
      }
      return null;
    }

    private Object convertStringToFieldType(String value, Class<?> targetType) {
      if (value == null || value.trim().isEmpty()) {
        return null;
      }

      value = value.trim();

      try {
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
        System.err.println(
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
