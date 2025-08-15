package de.bsommerfeld.jshepherd.toml;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.CommentSection;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.core.AbstractPersistenceDelegate;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.utils.ClassUtils;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
        // Second pass for TOML-specific section mapping (nested POJOs)
        applyTomlSectionValues(instance, tomlResult);
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
      writeAnnotatedSectionFields(writer, pojoInstance);
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
      writeAnnotatedSectionFieldsWithComments(writer, pojoInstance);
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

      // Skip fields explicitly marked as TOML tables (handled separately)
      if (field.getAnnotation(TomlSection.class) != null) continue;

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

      // Skip fields explicitly marked as TOML tables (handled separately)
      if (field.getAnnotation(TomlSection.class) != null) continue;

      try {
        field.setAccessible(true);
        Object value = field.get(pojoInstance);

        // Only process Map fields here (non-Map fields are handled separately)
        if (value instanceof Map) {
          if (firstTable) {
            writer.println(); // Blank line before first table
            firstTable = false;
          } else {
            writer.println(); // Blank line between subsequent tables
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

    // Process each field
    for (int fieldIdx = 0; fieldIdx < fields.size(); fieldIdx++) {
      Field field = fields.get(fieldIdx);
      // Skip static and transient fields
      if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
        continue;
      }

      Key keyAnnotation = field.getAnnotation(Key.class);
      if (keyAnnotation == null) continue;

      // Skip fields explicitly marked as TOML tables (handled separately)
      if (field.getAnnotation(TomlSection.class) != null) continue;

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

          // Add blank line only if another simple field will be written next (not a table/section)
          boolean addBlankLine = false;
          for (int k = fieldIdx + 1; k < fields.size(); k++) {
            Field nextField = fields.get(k);
            if (Modifier.isStatic(nextField.getModifiers()) || Modifier.isTransient(nextField.getModifiers())) continue;
            if (nextField.getAnnotation(Key.class) == null) continue;
            // If the next keyed field is a TOML section, do not add blank due to simple field
            if (nextField.getAnnotation(TomlSection.class) != null) {
              addBlankLine = false;
              break;
            }
            try {
              nextField.setAccessible(true);
              Object nextVal = nextField.get(pojoInstance);
              if (nextVal != null && !(nextVal instanceof Map)) {
                // Another simple field will be written by this method
                addBlankLine = true;
                break;
              }
              // If it's a map or null, keep scanning for a subsequent simple field
            } catch (IllegalAccessException ignored) {
              // Be conservative: avoid adding extra spacing on access issues
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

      // Skip fields explicitly marked as TOML sections (handled separately)
      if (field.getAnnotation(TomlSection.class) != null) continue;

      try {
        field.setAccessible(true);
        Object value = field.get(pojoInstance);

        // Only process Map fields here (non-Map fields are handled separately)
        if (value instanceof Map) {
          if (firstTable) {
            writer.println(); // Blank line before first table section
            firstTable = false;
          } else {
            writer.println(); // Blank line between subsequent tables
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

  private void writeAnnotatedSectionFields(PrintWriter writer, T pojoInstance) throws IOException {
    List<Field> fields = ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
    boolean firstTable = true;
    for (Field field : fields) {
      if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) continue;
      Key keyAnnotation = field.getAnnotation(Key.class);
      TomlSection section = field.getAnnotation(TomlSection.class);
      if (keyAnnotation == null || section == null) continue;
      try {
        field.setAccessible(true);
        Object value = field.get(pojoInstance);
        String tableName = !section.value().isEmpty() ? section.value() : (keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value());
        if (value instanceof Map<?, ?> map) {
          if (firstTable) { writer.println(); firstTable = false; } else { writer.println(); }
          writeTomlTable(writer, tableName, map);
        } else {
          if (firstTable) { writer.println(); firstTable = false; } else { writer.println(); }
          writeTomlSectionFromPojo(writer, tableName, value, null, false);
        }
      } catch (IllegalAccessException e) {
        System.err.println(LOG_PREFIX + "ERROR: Could not access field " + field.getName() + " during section save: " + e.getMessage());
      }
    }
  }

  private void writeAnnotatedSectionFieldsWithComments(PrintWriter writer, T pojoInstance) throws IOException {
    List<Field> fields = ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
    boolean firstTable = true;
    for (Field field : fields) {
      if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) continue;
      Key keyAnnotation = field.getAnnotation(Key.class);
      TomlSection section = field.getAnnotation(TomlSection.class);
      if (keyAnnotation == null || section == null) continue;
      try {
        field.setAccessible(true);
        Object value = field.get(pojoInstance);
        String tableName = !section.value().isEmpty() ? section.value() : (keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value());
        if (firstTable) { writer.println(); firstTable = false; } else { writer.println(); }
        Comment fieldComment = field.getAnnotation(Comment.class);
        if (fieldComment != null) {
          for (String commentLine : fieldComment.value()) writer.println("# " + commentLine);
        }
        if (value instanceof Map<?, ?> map) {
          writeTomlTable(writer, tableName, map);
        } else {
          writeTomlSectionFromPojo(writer, tableName, value, field, true);
        }
      } catch (IllegalAccessException e) {
        System.err.println(LOG_PREFIX + "ERROR: Could not access field " + field.getName() + " during section save with comments: " + e.getMessage());
      }
    }
  }

  private void writeTomlSectionFromPojo(PrintWriter writer, String tableName, Object nestedPojo, Field originalField, boolean withComments) {
    writer.println("[" + tableName + "]");
    if (nestedPojo == null) {
      return;
    }
    List<Field> nestedFields = ClassUtils.getAllFieldsInHierarchy(nestedPojo.getClass(), ConfigurablePojo.class);
    for (Field nf : nestedFields) {
      if (Modifier.isStatic(nf.getModifiers()) || Modifier.isTransient(nf.getModifiers())) continue;
      Key k = nf.getAnnotation(Key.class);
      if (k == null) continue;
      if (withComments) {
        Comment innerComment = nf.getAnnotation(Comment.class);
        if (innerComment != null) {
          for (String commentLine : innerComment.value()) writer.println("# " + commentLine);
        }
      }
      try {
        nf.setAccessible(true);
        Object v = nf.get(nestedPojo);
        if (v == null) {
          continue;
        }
        String key = k.value().isEmpty() ? nf.getName() : k.value();
        writeTomlValue(writer, key, v);
      } catch (IllegalAccessException e) {
        System.err.println(LOG_PREFIX + "ERROR: Could not access nested field " + nf.getName() + ": " + e.getMessage());
      }
    }
  }

  private void applyTomlSectionValues(T instance, TomlParseResult tomlResult) {
    List<Field> fields = ClassUtils.getAllFieldsInHierarchy(instance.getClass(), ConfigurablePojo.class);
    for (Field field : fields) {
      if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) continue;
      Key keyAnnotation = field.getAnnotation(Key.class);
      TomlSection section = field.getAnnotation(TomlSection.class);
      if (keyAnnotation == null || section == null) continue;
      String tableKey = !section.value().isEmpty() ? section.value() : (keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value());
      if (!tomlResult.contains(tableKey)) continue;
      Object raw = tomlResult.get(tableKey);
      if (!(raw instanceof TomlTable tbl)) continue;
      try {
        field.setAccessible(true);
        Class<?> fieldType = field.getType();
        if (Map.class.isAssignableFrom(fieldType)) {
          Map<String, Object> javaMap = new LinkedHashMap<>();
          for (String k : tbl.keySet()) {
            Object rv = tbl.get(k);
            if (rv instanceof TomlArray ta) {
              List<Object> list = new ArrayList<>();
              for (int i = 0; i < ta.size(); i++) list.add(ta.get(i));
              rv = list;
            } else if (rv instanceof TomlTable tt) {
              Map<String, Object> m = new LinkedHashMap<>();
              for (String kk : tt.keySet()) m.put(kk, tt.get(kk));
              rv = m;
            }
            javaMap.put(k, rv);
          }
          field.set(instance, javaMap);
        } else {
          Object target = field.get(instance);
          if (target == null) {
            try {
              Constructor<?> ctor = fieldType.getDeclaredConstructor();
              ctor.setAccessible(true);
              target = ctor.newInstance();
            } catch (Exception e) {
              System.err.println(LOG_PREFIX + "WARNING: Could not instantiate nested section for field '" + field.getName() + "': " + e.getMessage());
              continue;
            }
          }
          List<Field> nestedFields = ClassUtils.getAllFieldsInHierarchy(fieldType, ConfigurablePojo.class);
          for (Field nf : nestedFields) {
            if (Modifier.isStatic(nf.getModifiers()) || Modifier.isTransient(nf.getModifiers())) continue;
            Key innerKey = nf.getAnnotation(Key.class);
            if (innerKey == null) continue;
            String innerName = innerKey.value().isEmpty() ? nf.getName() : innerKey.value();
            if (!tbl.contains(innerName)) continue;
            Object rv = tbl.get(innerName);
            Class<?> targetType = nf.getType();
            if (rv instanceof TomlArray ta && List.class.isAssignableFrom(targetType)) {
              List<Object> list = new ArrayList<>();
              for (int i = 0; i < ta.size(); i++) list.add(ta.get(i));
              // Attempt to convert numeric element types to match generic parameter (e.g., Integer)
              Type gType = nf.getGenericType();
              if (gType instanceof ParameterizedType pt) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length == 1 && args[0] instanceof Class<?> elemClass) {
                  if (Number.class.isAssignableFrom(elemClass) || elemClass.isPrimitive()) {
                    List<Object> convertedList = new ArrayList<>(list.size());
                    for (Object o : list) {
                      convertedList.add(convertNumericIfNeeded(o, elemClass));
                    }
                    list = convertedList;
                  }
                }
              }
              rv = list;
            } else if (rv instanceof TomlTable tt && Map.class.isAssignableFrom(targetType)) {
              Map<String, Object> m = new LinkedHashMap<>();
              for (String kk : tt.keySet()) m.put(kk, tt.get(kk));
              rv = m;
            }
            Object converted = convertNumericIfNeeded(rv, targetType);
            try {
              nf.setAccessible(true);
              nf.set(target, converted);
            } catch (IllegalAccessException e) {
              System.err.println(LOG_PREFIX + "WARNING: Could not set nested field '" + nf.getName() + "': " + e.getMessage());
            }
          }
          field.set(instance, target);
        }
      } catch (IllegalAccessException e) {
        System.err.println(LOG_PREFIX + "WARNING: Could not set field '" + field.getName() + "' from section: " + e.getMessage());
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
