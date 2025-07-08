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
 * 
 * <p>This implementation supports comments in the TOML file and provides special handling
 * for various data types including dates, times, lists, and maps.
 */
class TomlPersistenceDelegate<T extends ConfigurablePojo<T>>
    extends AbstractPersistenceDelegate<T> {
  private static final String LOG_PREFIX = "[TOML] ";

  // Track the last comment section for formatting
  private String lastCommentSectionHash;

  TomlPersistenceDelegate(Path filePath, boolean useComplexSaveWithComments) {
    super(filePath, useComplexSaveWithComments);
  }

  /**
   * Attempts to load configuration data from the TOML file into the provided instance.
   * 
   * @param instance The instance to load data into
   * @return true if data was successfully loaded, false otherwise
   * @throws Exception if an error occurs during loading
   */
  @Override
  protected boolean tryLoadFromFile(T instance) throws Exception {
    try {
      TomlParseResult tomlResult = Toml.parse(filePath);

      // Check for parsing errors
      if (tomlResult.hasErrors()) {
        StringBuilder errorMsg = new StringBuilder(LOG_PREFIX + "ERROR: TOML parsing errors:");
        tomlResult.errors().forEach(error -> 
            errorMsg.append("\n  - ").append(error.getMessage()));
        System.err.println(errorMsg);
        throw new IOException("Failed to parse TOML file: " + tomlResult.errors().get(0).getMessage());
      }

      if (!tomlResult.isEmpty()) {
        applyDataToInstance(instance, new TomlDataExtractor(tomlResult));
        return true;
      }
      return false;
    } catch (Exception e) {
      if (!(e instanceof IOException)) {
        System.err.println(LOG_PREFIX + "ERROR: Failed to load TOML file: " + e.getMessage());
      }
      throw e;
    }
  }

  /**
   * Saves the configuration data to a TOML file with basic comments.
   * This method includes class-level comments but not field-level comments.
   * Simple fields are written first, followed by table fields.
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

      // Write simple fields first, then tables
      writeSimpleFields(writer, pojoInstance);
      writeTableFields(writer, pojoInstance);
    } catch (IOException e) {
      System.err.println(LOG_PREFIX + "ERROR: Failed to save TOML file: " + e.getMessage());
      throw e;
    }
  }

  /**
   * Saves the configuration data to a TOML file with comprehensive comments.
   * This method includes class-level comments, section comments, and field-level comments.
   * Simple fields are written first, followed by table fields.
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

      // Write simple fields first, then tables (with comments)
      writeSimpleFieldsWithComments(writer, pojoInstance);
      writeTableFieldsWithComments(writer, pojoInstance);
    } catch (IOException e) {
      System.err.println(LOG_PREFIX + "ERROR: Failed to save TOML file with comments: " + e.getMessage());
      throw e;
    }
  }

  /**
   * Writes simple fields (non-Map fields) to the TOML file.
   * 
   * @param writer The PrintWriter to write to
   * @param pojoInstance The instance to extract data from
   * @throws IOException if an error occurs during writing
   */
  private void writeSimpleFields(PrintWriter writer, T pojoInstance) throws IOException {
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

      try {
        field.setAccessible(true);
        Object value = field.get(pojoInstance);

        // Only process non-Map fields here (Map fields are handled as tables)
        if (value != null && !(value instanceof Map)) {
          String tomlKey =
              keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();
          writeTomlValue(writer, tomlKey, value);
        }
      } catch (IllegalAccessException e) {
        System.err.println(LOG_PREFIX + "ERROR: Could not access field " + field.getName() + 
                " during save: " + e.getMessage());
      }
    }
  }

  /**
   * Writes table fields (Map fields) to the TOML file.
   * 
   * @param writer The PrintWriter to write to
   * @param pojoInstance The instance to extract data from
   * @throws IOException if an error occurs during writing
   */
  private void writeTableFields(PrintWriter writer, T pojoInstance) throws IOException {
    // Get all fields from the class hierarchy
    List<Field> fields =
        ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
    boolean firstTable = true;

    // Process each field
    for (Field field : fields) {
      // Skip static and transient fields
      if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
        continue;
      }

      Key keyAnnotation = field.getAnnotation(Key.class);
      if (keyAnnotation == null) continue;

      try {
        field.setAccessible(true);
        Object value = field.get(pojoInstance);

        // Only process Map fields here (non-Map fields are handled separately)
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
        System.err.println(LOG_PREFIX + "ERROR: Could not access field " + field.getName() + 
                " during save: " + e.getMessage());
      }
    }
  }

  /**
   * Writes simple fields (non-Map fields) to the TOML file with comments.
   * This method includes section comments and field-level comments.
   * 
   * @param writer The PrintWriter to write to
   * @param pojoInstance The instance to extract data from
   * @throws IOException if an error occurs during writing
   */
  private void writeSimpleFieldsWithComments(PrintWriter writer, T pojoInstance)
      throws IOException {
    // Get all fields from the class hierarchy
    List<Field> fields =
        ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
    boolean hasWrittenField = false;

    // Process each field
    for (int fieldIdx = 0; fieldIdx < fields.size(); fieldIdx++) {
      Field field = fields.get(fieldIdx);
      // Skip static and transient fields
      if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
        continue;
      }

      Key keyAnnotation = field.getAnnotation(Key.class);
      if (keyAnnotation == null) continue;

      try {
        field.setAccessible(true);
        Object value = field.get(pojoInstance);

        // Only process non-Map fields here (Map fields are handled as tables)
        if (value != null && !(value instanceof Map)) {
          field.setAccessible(true);
          String tomlKey =
              keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

          // Handle section comments
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

          // Handle field comments
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
        System.err.println(LOG_PREFIX + "ERROR: Could not access field " + field.getName() + 
                " during save with comments: " + e.getMessage());
      }
    }
  }

  /**
   * Writes table fields (Map fields) to the TOML file with comments.
   * This method includes field-level comments for tables.
   * 
   * @param writer The PrintWriter to write to
   * @param pojoInstance The instance to extract data from
   * @throws IOException if an error occurs during writing
   */
  private void writeTableFieldsWithComments(PrintWriter writer, T pojoInstance) throws IOException {
    // Get all fields from the class hierarchy
    List<Field> fields =
        ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
    boolean firstTable = true;

    // Process each field
    for (Field field : fields) {
      // Skip static and transient fields
      if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
        continue;
      }

      Key keyAnnotation = field.getAnnotation(Key.class);
      if (keyAnnotation == null) continue;

      try {
        field.setAccessible(true);
        Object value = field.get(pojoInstance);

        // Only process Map fields here (non-Map fields are handled separately)
        if (value instanceof Map) {
          if (firstTable) {
            writer.println(); // Blank line before first table section
            firstTable = false;
          }

          String tomlKey =
              keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

          // Handle field comments
          Comment fieldComment = field.getAnnotation(Comment.class);
          if (fieldComment != null) {
            for (String commentLine : fieldComment.value()) writer.println("# " + commentLine);
          }

          writeTomlTable(writer, tomlKey, (Map<?, ?>) value);
        }
      } catch (IllegalAccessException e) {
        System.err.println(LOG_PREFIX + "ERROR: Could not access field " + field.getName() + 
                " during table save with comments: " + e.getMessage());
      }
    }
  }

  /**
   * Writes a TOML key-value pair to the output.
   * This method handles different data types with appropriate TOML syntax.
   * 
   * @param writer The PrintWriter to write to
   * @param key The TOML key
   * @param value The value to write
   */
  private void writeTomlValue(PrintWriter writer, String key, Object value) {
    if (value instanceof String) {
      // String values need quotes and escaping
      writer.println(key + " = \"" + escapeString((String) value) + "\"");
    } else if (value instanceof Number || value instanceof Boolean) {
      // Numbers and booleans can be written directly
      writer.println(key + " = " + value);
    } else if (value instanceof LocalDate) {
      // Dates are formatted according to TOML spec
      writer.println(key + " = " + value);
    } else if (value instanceof LocalDateTime) {
      // DateTimes are formatted according to TOML spec
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

  /**
   * Writes a TOML table to the output.
   * This method handles the table header and all key-value pairs within the table.
   * 
   * @param writer The PrintWriter to write to
   * @param tableName The name of the TOML table
   * @param map The map containing the table data
   */
  private void writeTomlTable(PrintWriter writer, String tableName, Map<?, ?> map) {
    // Write table header
    writer.println("[" + tableName + "]");

    // Return early if the map is empty
    if (map.isEmpty()) {
      return;
    }

    // Write each key-value pair in the table
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String entryKey = entry.getKey().toString();
      Object entryValue = entry.getValue();

      if (entryValue instanceof String) {
        // String values need quotes and escaping
        writer.println(entryKey + " = \"" + escapeString(entryValue.toString()) + "\"");
      } else if (entryValue instanceof Number || entryValue instanceof Boolean) {
        // Numbers and booleans can be written directly
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
        // Fallback: convert to string
        writer.println(entryKey + " = \"" + escapeString(entryValue.toString()) + "\"");
      }
    }
  }

  /**
   * Escapes special characters in a string according to the TOML format specification.
   * 
   * @param str The string to escape
   * @return The escaped string
   */
  private String escapeString(String str) {
    return str.replace("\\", "\\\\")  // Backslash must be escaped first
        .replace("\"", "\\\"")        // Double quotes
        .replace("\n", "\\n")         // Newlines
        .replace("\r", "\\r")         // Carriage returns
        .replace("\t", "\\t");        // Tabs
  }

  /**
   * Implementation of DataExtractor for TOML format.
   * This class extracts values from a TomlParseResult and converts TOML-specific types to Java types.
   */
  private static class TomlDataExtractor implements DataExtractor {
    private final TomlParseResult tomlData;

    /**
     * Creates a new TomlDataExtractor with the given TOML parse result.
     * 
     * @param tomlData The TOML parse result
     */
    TomlDataExtractor(TomlParseResult tomlData) {
      this.tomlData = tomlData;
    }

    /**
     * Checks if the TOML data contains a value for the given key.
     * 
     * @param key The key to check
     * @return true if the key exists in the data, false otherwise
     */
    @Override
    public boolean hasValue(String key) {
      return tomlData.contains(key);
    }

    /**
     * Gets the value for the given key from the TOML data and converts it to the target type.
     * This method handles conversion from TOML-specific types to standard Java collections.
     * 
     * @param key The key to get the value for
     * @param targetType The expected type of the value
     * @return The converted value, or null if not found
     */
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
