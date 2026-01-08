package de.bsommerfeld.jshepherd.toml;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.annotation.Section;
import de.bsommerfeld.jshepherd.core.AbstractPersistenceDelegate;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.utils.ClassUtils;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Implementation of PersistenceDelegate for TOML format using TOMLJ.
 */
class TomlPersistenceDelegate<T extends ConfigurablePojo<T>> extends AbstractPersistenceDelegate<T> {

  private static final Logger LOGGER = Logger.getLogger(TomlPersistenceDelegate.class.getName());

  TomlPersistenceDelegate(Path filePath, boolean useComplexSaveWithComments) {
    super(filePath, useComplexSaveWithComments);
  }

  /**
   * Checks if a field is a section (annotated with @Section).
   */
  private boolean isSectionField(Field field) {
    return field.getAnnotation(Section.class) != null;
  }

  /**
   * Gets the section/table name from @Section annotation.
   * Falls back to @Key value or field name.
   */
  private String getSectionTableName(Field field) {
    Section section = field.getAnnotation(Section.class);
    if (section != null && !section.value().isEmpty()) {
      return section.value();
    }
    return resolveKey(field);
  }

  @Override
  protected boolean tryLoadFromFile(T instance) throws Exception {
    try {
      TomlParseResult tomlResult = Toml.parse(filePath);

      if (tomlResult.hasErrors()) {
        String errors = tomlResult.errors().stream()
            .map(e -> e.getMessage())
            .collect(Collectors.joining(", "));
        throw new IOException("TOML parsing errors: " + errors);
      }

      if (!tomlResult.isEmpty()) {
        applyDataToInstance(instance, new TomlDataExtractor(tomlResult));
        applyTomlSectionValues(instance, tomlResult);
        return true;
      }
      return false;
    } catch (Exception e) {
      String msg = "Failed to load TOML file: " + e.getMessage();
      if (!(e instanceof IOException)) {
        LOGGER.log(Level.WARNING, msg, e);
      }
      throw e;
    }
  }

  @Override
  protected void saveSimple(T pojoInstance, Path targetPath) throws IOException {
    try (PrintWriter writer = new PrintWriter(
        Files.newBufferedWriter(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
      writeClassComments(writer, pojoInstance);
      writeSimpleFields(writer, pojoInstance);
      writeTableFields(writer, pojoInstance);
      writeAnnotatedSectionFields(writer, pojoInstance);
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Failed to save TOML file", e);
      throw e;
    }
  }

  @Override
  protected void saveWithComments(T pojoInstance, Path targetPath) throws IOException {
    try (PrintWriter writer = new PrintWriter(
        Files.newBufferedWriter(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
      writeClassComments(writer, pojoInstance);
      writeSimpleFieldsWithComments(writer, pojoInstance);
      writeTableFieldsWithComments(writer, pojoInstance);
      writeAnnotatedSectionFieldsWithComments(writer, pojoInstance);
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Failed to save TOML file with comments", e);
      throw e;
    }
  }

  private void writeSimpleFields(PrintWriter writer, T pojoInstance) {
    List<Field> fields = ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
    for (Field field : fields) {
      if (shouldSkipField(field))
        continue;
      Key keyAnnotation = field.getAnnotation(Key.class);
      if (keyAnnotation == null || isSectionField(field))
        continue;

      try {
        field.setAccessible(true);
        Object value = field.get(pojoInstance);
        // Map fields handled by writeTableFields
        if (value != null && !(value instanceof Map)) {
          String tomlKey = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();
          writeTomlValue(writer, tomlKey, value);
        }
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Error accessing field " + field.getName(), e);
      }
    }
  }

  private void writeTableFields(PrintWriter writer, T pojoInstance) {
    List<Field> fields = ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
    boolean firstTable = true;
    for (Field field : fields) {
      if (shouldSkipField(field))
        continue;
      Key keyAnnotation = field.getAnnotation(Key.class);
      if (keyAnnotation == null || isSectionField(field))
        continue;

      try {
        field.setAccessible(true);
        Object value = field.get(pojoInstance);
        if (value instanceof Map) {
          if (firstTable) {
            writer.println();
            firstTable = false;
          } else {
            writer.println();
          }
          String tomlKey = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();
          writeTomlTable(writer, tomlKey, (Map<?, ?>) value);
        }
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Error accessing field " + field.getName(), e);
      }
    }
  }

  private void writeSimpleFieldsWithComments(PrintWriter writer, T pojoInstance) {
    List<Field> fields = ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
    for (int i = 0; i < fields.size(); i++) {
      Field field = fields.get(i);
      if (shouldSkipField(field))
        continue;
      Key keyAnnotation = field.getAnnotation(Key.class);
      if (keyAnnotation == null || isSectionField(field))
        continue;

      try {
        field.setAccessible(true);
        Object value = field.get(pojoInstance);
        if (value != null && !(value instanceof Map)) {
          String tomlKey = resolveKey(field);

          writeFieldComments(writer, field);
          writeTomlValue(writer, tomlKey, value);

          // Add blank line logic
          boolean addBlankLine = false;
          for (int k = i + 1; k < fields.size(); k++) {
            Field next = fields.get(k);
            if (shouldSkipField(next) || next.getAnnotation(Key.class) == null)
              continue;
            if (isSectionField(next))
              break;
            try {
              next.setAccessible(true);
              Object nVal = next.get(pojoInstance);
              if (nVal != null && !(nVal instanceof Map)) {
                addBlankLine = true;
                break;
              }
            } catch (Exception ignored) {
              break;
            }
          }
          if (addBlankLine)
            writer.println();
        }
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Error accessing field " + field.getName(), e);
      }
    }
  }

  private void writeTableFieldsWithComments(PrintWriter writer, T pojoInstance) {
    List<Field> fields = ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
    boolean firstTable = true;
    for (Field field : fields) {
      if (shouldSkipField(field))
        continue;
      Key keyAnnotation = field.getAnnotation(Key.class);
      if (keyAnnotation == null || isSectionField(field))
        continue;

      try {
        field.setAccessible(true);
        Object value = field.get(pojoInstance);
        if (value instanceof Map) {
          if (firstTable) {
            writer.println();
            firstTable = false;
          } else {
            writer.println();
          }
          String tomlKey = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();
          writeFieldComments(writer, field);
          writeTomlTable(writer, tomlKey, (Map<?, ?>) value);
        }
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Error accessing field " + field.getName(), e);
      }
    }
  }

  private void writeAnnotatedSectionFields(PrintWriter writer, T pojoInstance) {
    List<Field> fields = ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
    boolean firstTable = true;
    for (Field field : fields) {
      if (shouldSkipField(field))
        continue;
      if (!isSectionField(field))
        continue;

      try {
        field.setAccessible(true);
        Object value = field.get(pojoInstance);
        String tableName = getSectionTableName(field);
        if (firstTable) {
          writer.println();
          firstTable = false;
        } else {
          writer.println();
        }

        if (value instanceof Map<?, ?> map) {
          writeTomlTable(writer, tableName, map);
        } else {
          writeTomlSectionFromPojo(writer, tableName, value, null, false);
        }
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Error section field " + field.getName(), e);
      }
    }
  }

  private void writeAnnotatedSectionFieldsWithComments(PrintWriter writer, T pojoInstance) {
    List<Field> fields = ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
    boolean firstTable = true;
    for (Field field : fields) {
      if (shouldSkipField(field))
        continue;
      if (!isSectionField(field))
        continue;

      try {
        field.setAccessible(true);
        Object value = field.get(pojoInstance);
        String tableName = getSectionTableName(field);
        if (firstTable) {
          writer.println();
          firstTable = false;
        } else {
          writer.println();
        }

        writeFieldComments(writer, field);

        if (value instanceof Map<?, ?> map) {
          writeTomlTable(writer, tableName, map);
        } else {
          writeTomlSectionFromPojo(writer, tableName, value, field, true);
        }
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Error section field " + field.getName(), e);
      }
    }
  }

  // ... Writing Core Logic ...

  private void writeTomlValue(PrintWriter writer, String key, Object value) {
    writer.print(key + " = ");
    if (value instanceof String) {
      writer.println("\"" + escapeString((String) value) + "\"");
    } else if (value instanceof Number || value instanceof Boolean || value instanceof LocalDate
        || value instanceof LocalDateTime) {
      writer.println(value);
    } else if (value instanceof List) {
      writeInlineArray(writer, (List<?>) value);
      writer.println();
    } else {
      // POJO or Unknown -> Inline Table
      writeInlineTable(writer, value);
      writer.println();
    }
  }

  private void writeInlineArray(PrintWriter writer, List<?> list) {
    if (list.isEmpty()) {
      writer.print("[]");
      return;
    }
    writer.print("[");
    for (int i = 0; i < list.size(); i++) {
      if (i > 0)
        writer.print(", ");
      Object item = list.get(i);
      if (item instanceof String) {
        writer.print("\"" + escapeString((String) item) + "\"");
      } else if (item instanceof List) {
        writeInlineArray(writer, (List<?>) item);
      } else if (isPrimitiveOrDate(item)) {
        writer.print(item);
      } else {
        writeInlineTable(writer, item);
      }
    }
    writer.print("]");
  }

  private void writeInlineTable(PrintWriter writer, Object pojoOrMap) {
    if (pojoOrMap == null) {
      writer.print("{}"); // Or error? TOML doesn't support nulls well.
      return;
    }
    Map<String, Object> map = new LinkedHashMap<>();
    if (pojoOrMap instanceof Map) {
      map.putAll((Map) pojoOrMap);
    } else {
      // Reflect POJO
      List<Field> fields = ClassUtils.getAllFieldsInHierarchy(pojoOrMap.getClass(), ConfigurablePojo.class); // Or just
                                                                                                             // Object.class
                                                                                                             // if not
                                                                                                             // ConfigurablePojo
      // If it's just a random POJO, maybe GetAllFieldsInHierarchy(..., Object.class)
      // But ClassUtils might require ConfigurablePojo. Using what we have.
      // If ClassUtils is restrictive, we might miss fields.
      // Assuming POJOs used here are config-like.
      for (Field f : fields) {
        if (shouldSkipField(f))
          continue;
        Key k = f.getAnnotation(Key.class);
        if (k == null)
          continue;
        try {
          f.setAccessible(true);
          Object v = f.get(pojoOrMap);
          if (v != null) {
            String name = k.value().isEmpty() ? f.getName() : k.value();
            map.put(name, v);
          }
        } catch (Exception ignored) {
        }
      }
    }

    writer.print("{ ");
    int count = 0;
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      if (count > 0)
        writer.print(", ");
      writer.print(entry.getKey() + " = ");
      Object v = entry.getValue();
      if (v instanceof String)
        writer.print("\"" + escapeString((String) v) + "\"");
      else if (v instanceof List)
        writeInlineArray(writer, (List<?>) v);
      else if (isPrimitiveOrDate(v))
        writer.print(v);
      else
        writeInlineTable(writer, v); // Recurse
      count++;
    }
    writer.print(" }");
  }

  private boolean isPrimitiveOrDate(Object o) {
    return o instanceof Number || o instanceof Boolean || o instanceof LocalDate || o instanceof LocalDateTime;
  }

  private void writeTomlTable(PrintWriter writer, String tableName, Map<?, ?> map) {
    writer.println("[" + tableName + "]");
    if (map.isEmpty())
      return;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String entryKey = entry.getKey().toString();
      Object entryValue = entry.getValue();
      // Reuse logic? writeTomlValue writes 'key = value\n'.
      writeTomlValue(writer, entryKey, entryValue);
    }
  }

  private void writeTomlSectionFromPojo(PrintWriter writer, String tableName, Object nestedPojo, Field originalField,
      boolean withComments) {
    writer.println("[" + tableName + "]");
    if (nestedPojo == null)
      return;

    List<Field> nestedFields = ClassUtils.getAllFieldsInHierarchy(nestedPojo.getClass(), ConfigurablePojo.class);
    for (Field nf : nestedFields) {
      if (shouldSkipField(nf))
        continue;
      Key k = nf.getAnnotation(Key.class);
      if (k == null)
        continue;
      if (withComments) {
        Comment innerComment = nf.getAnnotation(Comment.class);
        if (innerComment != null) {
          for (String c : innerComment.value())
            writer.println("# " + c);
        }
      }
      try {
        nf.setAccessible(true);
        Object v = nf.get(nestedPojo);
        if (v == null)
          continue;
        String key = k.value().isEmpty() ? nf.getName() : k.value();
        writeTomlValue(writer, key, v);
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Error extracting nested field " + nf.getName(), e);
      }
    }
  }

  private String escapeString(String str) {
    return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t",
        "\\t");
  }

  private void applyTomlSectionValues(T instance, TomlParseResult tomlResult) {
    List<Field> fields = ClassUtils.getAllFieldsInHierarchy(instance.getClass(), ConfigurablePojo.class);
    for (Field field : fields) {
      if (shouldSkipField(field))
        continue;
      if (!isSectionField(field))
        continue;

      String tableKey = getSectionTableName(field);
      if (!tomlResult.contains(tableKey))
        continue;

      Object raw = tomlResult.get(tableKey);
      if (!(raw instanceof TomlTable tbl))
        continue;

      try {
        field.setAccessible(true);
        Class<?> fieldType = field.getType();
        if (Map.class.isAssignableFrom(fieldType)) {
          Map<String, Object> javaMap = convertTomlTableToMap(tbl);
          field.set(instance, javaMap);
        } else {
          // Instantiating Nested POJO
          Object target = field.get(instance);
          if (target == null) {
            try {
              Constructor<?> ctor = fieldType.getDeclaredConstructor();
              ctor.setAccessible(true);
              target = ctor.newInstance();
            } catch (Exception e) {
              LOGGER.log(Level.WARNING, "Could not instantiate nested section " + fieldType.getSimpleName(), e);
              continue;
            }
          }
          populatePojoFromTable(target, tbl);
          field.set(instance, target);
        }
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Error applying section " + tableKey, e);
      }
    }
  }

  // ... Helpers for loading ...
  private static Map<String, Object> convertTomlTableToMap(TomlTable tbl) {
    Map<String, Object> javaMap = new LinkedHashMap<>();
    for (String k : tbl.keySet()) {
      Object rv = tbl.get(k);
      if (rv instanceof TomlArray) { // Fixed: Cast check
        TomlArray ta = (TomlArray) rv;
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < ta.size(); i++)
          list.add(ta.get(i));
        rv = list;
      } else if (rv instanceof TomlTable) { // Fixed: Cast check
        rv = convertTomlTableToMap((TomlTable) rv);
      }
      javaMap.put(k, rv);
    }
    return javaMap;
  }

  private void populatePojoFromTable(Object target, TomlTable tbl) {
    List<Field> nestedFields = ClassUtils.getAllFieldsInHierarchy(target.getClass(), ConfigurablePojo.class);
    for (Field nf : nestedFields) {
      if (shouldSkipField(nf))
        continue;
      Key innerKey = nf.getAnnotation(Key.class);
      if (innerKey == null)
        continue;
      String innerName = innerKey.value().isEmpty() ? nf.getName() : innerKey.value();
      if (!tbl.contains(innerName))
        continue;

      Object rv = tbl.get(innerName);
      try {
        nf.setAccessible(true);
        if (rv instanceof TomlArray && List.class.isAssignableFrom(nf.getType())) {
          TomlArray ta = (TomlArray) rv;
          List<Object> list = new ArrayList<>();

          // Detect generic type for numeric conversion
          Class<?> listContentType = getListGenericType(nf);

          for (int i = 0; i < ta.size(); i++) {
            Object item = ta.get(i);
            if (listContentType != null) {
              item = convertNumericIfNeeded(item, listContentType);
            }
            list.add(item);
          }
          nf.set(target, list);
        } else if (rv instanceof TomlTable && Map.class.isAssignableFrom(nf.getType())) {
          nf.set(target, convertTomlTableToMap((TomlTable) rv));
        } else {
          nf.set(target, convertNumericIfNeeded(rv, nf.getType()));
        }
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Failed to set nested field " + innerName, e);
      }
    }
  }

  private Class<?> getListGenericType(Field field) {
    if (field.getGenericType() instanceof ParameterizedType) {
      ParameterizedType pt = (ParameterizedType) field.getGenericType();
      if (pt.getActualTypeArguments().length > 0) {
        Type content = pt.getActualTypeArguments()[0];
        if (content instanceof Class<?>) {
          return (Class<?>) content;
        }
      }
    }
    return null;
  }

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
      if (rawValue instanceof TomlArray) {
        TomlArray ar = (TomlArray) rawValue;
        if (List.class.isAssignableFrom(targetType)) {
          List<Object> l = new ArrayList<>();
          for (int i = 0; i < ar.size(); i++)
            l.add(ar.get(i));
          return l;
        }
      } else if (rawValue instanceof TomlTable) {
        if (Map.class.isAssignableFrom(targetType)) {
          return convertTomlTableToMap((TomlTable) rawValue);
        }
        // Return null for non-Map tables (e.g. Sections) so AbstractPersistenceDelegate
        // skips simple setting.
        // These are handled by applyTomlSectionValues.
        return null;
      }
      return rawValue;
    }
  }
}
