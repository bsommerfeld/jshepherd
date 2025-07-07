package de.bsommerfeld.jshepherd.toml;

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
import java.util.*;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Implementation of PersistenceDelegate for TOML format using TOMLJ. This class handles loading,
 * saving, and reloading of configuration objects in TOML format.
 */
class TomlPersistenceDelegate<T extends ConfigurablePojo<T>>
    extends AbstractPersistenceDelegate<T> {

  TomlPersistenceDelegate(Path filePath, boolean useComplexSaveWithComments) {
    super(filePath, useComplexSaveWithComments);
  }

  @Override
  protected boolean tryLoadFromFile(T instance) throws Exception {
    TomlParseResult tomlResult = Toml.parse(filePath);

    if (!tomlResult.isEmpty()) {
      applyDataToInstance(instance, new TomlDataExtractor(tomlResult));
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

      // Write simple fields first, then tables
      writeSimpleFields(writer, pojoInstance);
      writeTableFields(writer, pojoInstance);
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

      // Write simple fields first, then tables (with comments)
      writeSimpleFieldsWithComments(writer, pojoInstance);
      writeTableFieldsWithComments(writer, pojoInstance);
    }
  }

  private void writeSimpleFields(PrintWriter writer, T pojoInstance) throws IOException {
    List<Field> fields =
        ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);

    for (Field field : fields) {
      if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
        continue;
      }

      Key keyAnnotation = field.getAnnotation(Key.class);
      if (keyAnnotation == null) continue;

      try {
        field.setAccessible(true);
        Object value = field.get(pojoInstance);

        if (value != null && !(value instanceof Map)) {
          String tomlKey =
              keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();
          writeTomlValue(writer, tomlKey, value);
        }
      } catch (IllegalAccessException e) {
        System.err.println("ERROR: Could not access field " + field.getName() + " during save.");
      }
    }
  }

  private void writeTableFields(PrintWriter writer, T pojoInstance) throws IOException {
    List<Field> fields =
        ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
    boolean firstTable = true;

    for (Field field : fields) {
      if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
        continue;
      }

      Key keyAnnotation = field.getAnnotation(Key.class);
      if (keyAnnotation == null) continue;

      try {
        field.setAccessible(true);
        Object value = field.get(pojoInstance);

        if (value instanceof Map) {
          if (firstTable) {
            writer.println(); // Blank line before first table
            firstTable = false;
          }

          String tomlKey =
              keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();
          writeTomlTable(writer, tomlKey, (Map<?, ?>) value);
        }
      } catch (IllegalAccessException e) {
        System.err.println("ERROR: Could not access field " + field.getName() + " during save.");
      }
    }
  }

  private void writeSimpleFieldsWithComments(PrintWriter writer, T pojoInstance)
      throws IOException {
    List<Field> fields =
        ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
    boolean hasWrittenField = false;

    for (int fieldIdx = 0; fieldIdx < fields.size(); fieldIdx++) {
      Field field = fields.get(fieldIdx);
      if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
        continue;
      }

      Key keyAnnotation = field.getAnnotation(Key.class);
      if (keyAnnotation == null) continue;

      try {
        field.setAccessible(true);
        Object value = field.get(pojoInstance);

        if (value != null && !(value instanceof Map)) {
          field.setAccessible(true);
          String tomlKey =
              keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

          CommentSection sectionAnnotation = field.getAnnotation(CommentSection.class);
          if (sectionAnnotation != null && sectionAnnotation.value().length > 0) {
            String currentSectionHash = String.join("|", sectionAnnotation.value());
            if (!currentSectionHash.equals(this.lastCommentSectionHash)) {
              if (this.lastCommentSectionHash != null) writer.println();
              for (String commentLine : sectionAnnotation.value())
                writer.println("# " + commentLine);
              this.lastCommentSectionHash = currentSectionHash;
            }
          }

          Comment fieldComment = field.getAnnotation(Comment.class);
          if (fieldComment != null) {
            for (String commentLine : fieldComment.value()) writer.println("# " + commentLine);
          }

          writeTomlValue(writer, tomlKey, value);
          hasWrittenField = true;

          // Add blank line after simple fields if more fields follow
          boolean addBlankLine = false;
          for (int k = fieldIdx + 1; k < fields.size(); k++) {
            Field nextField = fields.get(k);
            if (Modifier.isStatic(nextField.getModifiers())
                || Modifier.isTransient(nextField.getModifiers())) continue;
            if (nextField.getAnnotation(Key.class) != null) {
              addBlankLine = true;
              break;
            }
          }
          if (addBlankLine) {
            writer.println();
          }
        }
      } catch (IllegalAccessException e) {
        System.err.println("ERROR: Could not access field " + field.getName() + " during save.");
      }
    }
  }

  private void writeTableFieldsWithComments(PrintWriter writer, T pojoInstance) throws IOException {
    List<Field> fields =
        ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
    boolean firstTable = true;

    for (Field field : fields) {
      if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
        continue;
      }

      Key keyAnnotation = field.getAnnotation(Key.class);
      if (keyAnnotation == null) continue;

      try {
        field.setAccessible(true);
        Object value = field.get(pojoInstance);

        if (value instanceof Map) {
          if (firstTable) {
            writer.println(); // Blank line before first table section
            firstTable = false;
          }

          String tomlKey =
              keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

          Comment fieldComment = field.getAnnotation(Comment.class);
          if (fieldComment != null) {
            for (String commentLine : fieldComment.value()) writer.println("# " + commentLine);
          }

          writeTomlTable(writer, tomlKey, (Map<?, ?>) value);
        }
      } catch (IllegalAccessException e) {
        System.err.println("ERROR: Could not access field " + field.getName() + " during save.");
      }
    }
  }

  private void writeTomlValue(PrintWriter writer, String key, Object value) {
    if (value instanceof String) {
      writer.println(key + " = \"" + escapeString((String) value) + "\"");
    } else if (value instanceof Number || value instanceof Boolean) {
      writer.println(key + " = " + value);
    } else if (value instanceof LocalDate) {
      writer.println(key + " = " + value);
    } else if (value instanceof LocalDateTime) {
      writer.println(key + " = " + value);
    } else if (value instanceof List) {
      // TOML array syntax
      List<?> list = (List<?>) value;
      if (list.isEmpty()) {
        writer.println(key + " = []");
      } else {
        writer.print(key + " = [");
        for (int i = 0; i < list.size(); i++) {
          if (i > 0) writer.print(", ");
          Object item = list.get(i);
          if (item instanceof String) {
            writer.print("\"" + escapeString(item.toString()) + "\"");
          } else {
            writer.print(item.toString());
          }
        }
        writer.println("]");
      }
    } else {
      // Fallback: convert to string
      writer.println(key + " = \"" + escapeString(value.toString()) + "\"");
    }
  }

  private void writeTomlTable(PrintWriter writer, String tableName, Map<?, ?> map) {
    if (map.isEmpty()) {
      writer.println("[" + tableName + "]");
      return;
    }

    writer.println("[" + tableName + "]");
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String entryKey = entry.getKey().toString();
      Object entryValue = entry.getValue();

      if (entryValue instanceof String) {
        writer.println(entryKey + " = \"" + escapeString(entryValue.toString()) + "\"");
      } else if (entryValue instanceof Number || entryValue instanceof Boolean) {
        writer.println(entryKey + " = " + entryValue);
      } else if (entryValue instanceof List) {
        // Handle nested arrays in tables
        List<?> list = (List<?>) entryValue;
        if (list.isEmpty()) {
          writer.println(entryKey + " = []");
        } else {
          writer.print(entryKey + " = [");
          for (int i = 0; i < list.size(); i++) {
            if (i > 0) writer.print(", ");
            Object item = list.get(i);
            if (item instanceof String) {
              writer.print("\"" + escapeString(item.toString()) + "\"");
            } else {
              writer.print(item.toString());
            }
          }
          writer.println("]");
        }
      } else {
        writer.println(entryKey + " = \"" + escapeString(entryValue.toString()) + "\"");
      }
    }
  }

  private String escapeString(String str) {
    return str.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  // DataExtractor implementation for TOML
  private static class TomlDataExtractor implements DataExtractor {
    private final TomlParseResult tomlData;

    TomlDataExtractor(TomlParseResult tomlData) {
      this.tomlData = tomlData;
    }

    @Override
    public boolean hasValue(String key) {
      return tomlData.contains(key);
    }

    @Override
    public Object getValue(String key, Class<?> targetType) {
      Object rawValue = tomlData.get(key);

      // Convert TOML-specific types to Java collections
      if (rawValue instanceof TomlArray) {
        TomlArray tomlArray = (TomlArray) rawValue;
        if (List.class.isAssignableFrom(targetType)) {
          List<Object> javaList = new ArrayList<>();
          for (int i = 0; i < tomlArray.size(); i++) {
            javaList.add(tomlArray.get(i));
          }
          return javaList;
        }
      } else if (rawValue instanceof TomlTable tomlTable) {
        if (Map.class.isAssignableFrom(targetType)) {
          Map<String, Object> javaMap = new LinkedHashMap<>();
          for (String tableKey : tomlTable.keySet()) {
            javaMap.put(tableKey, tomlTable.get(tableKey));
          }
          return javaMap;
        }
      }

      return rawValue;
    }
  }
}
