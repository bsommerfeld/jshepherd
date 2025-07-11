package de.bsommerfeld.jshepherd.yaml;

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
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Implementation of PersistenceDelegate for YAML format using SnakeYAML.
 * This class handles loading, saving, and reloading of configuration objects in YAML format.
 * 
 * <p>This implementation supports comments in the YAML file and provides special handling
 * for various data types including lists and maps.
 */
class YamlPersistenceDelegate<T extends ConfigurablePojo<T>>
    extends AbstractPersistenceDelegate<T> {
  private static final String LOG_PREFIX = "[YAML] ";

  // Lazy initialization - will be set on first use
  private Yaml yaml; // For loading the whole POJO and for simple dump
  private Yaml valueDumper; // For dumping individual field values in complex save
  private String lastCommentSectionHash; // Track the last comment section for formatting

  YamlPersistenceDelegate(Path filePath, boolean useComplexSaveWithComments) {
    super(filePath, useComplexSaveWithComments);
  }

  /**
   * Initializes the YAML instances if they haven't been initialized yet.
   * This method uses lazy initialization to avoid creating YAML instances until they're needed.
   * 
   * @param pojoClass The class of the POJO to be serialized/deserialized
   */
  private void initializeYamlIfNeeded(Class<T> pojoClass) {
    if (this.yaml != null) {
      return; // Already initialized
    }

    // Main Yaml instance configuration
    DumperOptions mainDumperOptions = new DumperOptions();
    mainDumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    mainDumperOptions.setPrettyFlow(true);
    mainDumperOptions.setIndent(2);
    mainDumperOptions.setIndicatorIndent(1);
    mainDumperOptions.setSplitLines(false);
    mainDumperOptions.setAllowUnicode(true);
    mainDumperOptions.setExplicitStart(false);
    mainDumperOptions.setExplicitEnd(false);

    Representer representer = new Representer(mainDumperOptions);
    representer.getPropertyUtils().setSkipMissingProperties(true);

    LoaderOptions loaderOptions = new LoaderOptions();
    this.yaml = new Yaml(new Constructor(pojoClass, loaderOptions), representer, mainDumperOptions);

    // Yaml instance for dumping individual values
    DumperOptions valueDumperOptions = new DumperOptions();
    valueDumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    valueDumperOptions.setIndent(2);
    valueDumperOptions.setIndicatorIndent(1);
    valueDumperOptions.setSplitLines(false);
    valueDumperOptions.setAllowUnicode(true);
    valueDumperOptions.setExplicitStart(false);
    valueDumperOptions.setExplicitEnd(false);
    this.valueDumper = new Yaml(new Representer(valueDumperOptions), valueDumperOptions);
  }

  /**
   * Attempts to load configuration data from the YAML file into the provided instance.
   * 
   * @param instance The instance to load data into
   * @return true if data was successfully loaded, false otherwise
   * @throws Exception if an error occurs during loading
   */
  @Override
  protected boolean tryLoadFromFile(T instance) throws Exception {
    // Initialize YAML with the actual POJO class
    initializeYamlIfNeeded((Class<T>) instance.getClass());

    try (Reader reader = Files.newBufferedReader(filePath)) {
      Yaml simpleYaml = new Yaml();
      Object yamlData = simpleYaml.load(reader);

      if (yamlData != null) {
        applyDataToInstance(instance, new YamlDataExtractor(yamlData));
        return true;
      }
    } catch (IOException e) {
      System.err.println(LOG_PREFIX + "ERROR: Failed to load YAML file: " + e.getMessage());
      throw e;
    }
    return false;
  }

  /**
   * Saves the configuration data to a YAML file with basic comments.
   * This method includes class-level comments but uses SnakeYAML's built-in dumping for the data.
   * 
   * @param pojoInstance The instance to save
   * @param targetPath The path to save the file to
   * @throws IOException if an error occurs during saving
   */
  @Override
  protected void saveSimple(T pojoInstance, Path targetPath) throws IOException {
    // Ensure YAML is initialized before saving
    initializeYamlIfNeeded((Class<T>) pojoInstance.getClass());

    try (Writer writer =
        Files.newBufferedWriter(
            targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
      // Add class-level comments if present
      Comment classComment = pojoInstance.getClass().getAnnotation(Comment.class);
      if (classComment != null && classComment.value().length > 0) {
        for (String line : classComment.value()) {
          writer.write("# " + line + System.lineSeparator());
        }
        writer.write(System.lineSeparator());
      }
      yaml.dump(pojoInstance, writer);
    } catch (IOException e) {
      System.err.println(LOG_PREFIX + "ERROR: Failed to save YAML file: " + e.getMessage());
      throw e;
    }
  }

  /**
   * Saves the configuration data to a YAML file with comprehensive comments.
   * This method includes class-level comments, section comments, and field-level comments.
   * It manually formats the YAML output to ensure proper comment placement.
   * 
   * @param pojoInstance The instance to save
   * @param targetPath The path to save the file to
   * @throws IOException if an error occurs during saving
   */
  @Override
  protected void saveWithComments(T pojoInstance, Path targetPath) throws IOException {
    // Ensure YAML is initialized before saving
    initializeYamlIfNeeded((Class<T>) pojoInstance.getClass());

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

        String yamlKey = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

        // Handle section comments
        CommentSection sectionAnnotation = field.getAnnotation(CommentSection.class);
        if (sectionAnnotation != null && sectionAnnotation.value().length > 0) {
          String currentSectionHash = String.join("|", sectionAnnotation.value());
          if (!currentSectionHash.equals(this.lastCommentSectionHash)) {
            if (this.lastCommentSectionHash != null || writer.checkError()) writer.println();
            for (String commentLine : sectionAnnotation.value()) writer.println("# " + commentLine);
            this.lastCommentSectionHash = currentSectionHash;
          }
        }

        // Handle field comments
        Comment fieldComment = field.getAnnotation(Comment.class);
        if (fieldComment != null) {
          for (String commentLine : fieldComment.value()) writer.println("# " + commentLine);
        }

        writer.print(yamlKey + ":");
        Object value;
        try {
          value = field.get(pojoInstance);
        } catch (IllegalAccessException e) {
          System.err.println(LOG_PREFIX + "ERROR: Could not access field " + field.getName() + " during save: " + e.getMessage());
          continue;
        }

        if (value == null) {
          writer.println(" null");
        } else {
          String valueAsYaml = this.valueDumper.dump(value);

          // Remove trailing newline if present
          if (valueAsYaml.endsWith(System.lineSeparator())) {
            valueAsYaml =
                valueAsYaml.substring(0, valueAsYaml.length() - System.lineSeparator().length());
          }

          // Determine if the value is a scalar or empty collection that can be written on a single line
          boolean isScalarOrFlowCollection =
              !(value instanceof List || value instanceof Map)
                  && !valueAsYaml.contains(System.lineSeparator());
          if (value instanceof List && ((List<?>) value).isEmpty()) isScalarOrFlowCollection = true;
          if (value instanceof Map && ((Map<?, ?>) value).isEmpty())
            isScalarOrFlowCollection = true;

          // Format the value appropriately
          if (isScalarOrFlowCollection) {
            writer.println(" " + valueAsYaml.trim());
          } else {
            writer.println();
            valueAsYaml
                .lines()
                .forEach(
                    line -> {
                      writer.println("  " + line);
                    });
          }
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
      System.err.println(LOG_PREFIX + "ERROR: Failed to save YAML file with comments: " + e.getMessage());
      throw e;
    }
  }

  /**
   * Implementation of DataExtractor for YAML data.
   * This class extracts values from a YAML map for applying to a POJO instance.
   */
  private record YamlDataExtractor(Object yamlData) implements DataExtractor {

    /**
     * Checks if the YAML data contains a value for the given key.
     * 
     * @param key The key to check
     * @return true if the key exists in the data, false otherwise
     */
    @Override
    public boolean hasValue(String key) {
      if (!(yamlData instanceof Map<?, ?> yamlMap)) return false;
      return yamlMap.containsKey(key);
    }

    /**
     * Gets the value for the given key from the YAML data.
     * 
     * @param key The key to get the value for
     * @param targetType The expected type of the value
     * @return The value from the YAML data, or null if not found
     */
    @Override
    public Object getValue(String key, Class<?> targetType) {
      if (!(yamlData instanceof Map<?, ?> yamlMap)) return null;
      return yamlMap.get(key);
    }
  }
}
