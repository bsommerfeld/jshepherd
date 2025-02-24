package de.metaphoriker.jshepherd;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import de.metaphoriker.jshepherd.annotation.Comment;
import de.metaphoriker.jshepherd.annotation.Key;
import de.metaphoriker.jshepherd.annotation.Configuration;
import de.metaphoriker.jshepherd.utils.ClassUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.*;

/** BaseConfiguration is a class that manages configuration options and saves them to a file. */
public abstract class BaseConfiguration {

  private static final String COMMENT_PREFIX = "#";
  private static final String YAML_EXTENSION = ".yml";
  private static final String YAML_DELIMITER = ":";
  private static final String PROPERTIES_EXTENSION = ".properties";
  private static final String PROPERTIES_DELIMITER = "=";

  private static final Gson GSON = new Gson();

  private final Map<String, ConfigurationOption<?>> configOptions = new LinkedHashMap<>();
  private final Properties properties = new Properties();

  private PrintWriter fileWriter;

  private File file;

  /** Constructor for BaseConfiguration, uses the file name from the @Configuration annotation. */
  protected BaseConfiguration() {
    Configuration configAnnotation = retrieveConfigurationAnnotation();
    createFile(configAnnotation);
    createDirectoryIfNotExists(file.getParentFile());
  }

  /**
   * Loads the configuration file and updates internal options. Creates the configuration file if it
   * does not exist.
   */
  public void initialize() {
    reload();
    save();
  }

  /**
   * Set the directory where the configuration file should be saved.
   *
   * @param directory The directory path as a String or File
   */
  public void setDirectory(File directory) {
    if (directory == null) {
      throw new IllegalArgumentException("The directory must not be null");
    }

    Configuration configAnnotation = retrieveConfigurationAnnotation();
    createFile(configAnnotation, directory.getPath());
    createDirectoryIfNotExists(directory);
  }

  /**
   * Creates a file with the appropriate extension based on the configuration type.
   *
   * @param configuration the configuration object that provides the file name and type of the
   *     configuration
   * @param directory Optional parameter that specifies the directory where the file should be
   *     created. If null, the file will be created in the current directory.
   */
  private void createFile(Configuration configuration, String... directory) {
    String filePath = getFilePath(configuration, directory);
    switch (configuration.type()) {
      case YAML:
        this.file = new File(filePath + YAML_EXTENSION);
        break;
      case PROPERTIES:
        this.file = new File(filePath + PROPERTIES_EXTENSION);
        break;
      default:
        throw new IllegalArgumentException("Configuration type not supported");
    }
  }

  /**
   * Retrieves the file path based on a given configuration and optional directory.
   *
   * @param configuration The configuration object that provides the file name.
   * @param directory An array of strings representing the directories. The first element is used if
   *     present.
   * @return The full file path as a string. If the directory array is not empty, the path is
   *     constructed using the first directory and the file name from the configuration. Otherwise ,
   *     the file name from the configuration is returned.
   */
  private String getFilePath(Configuration configuration, String[] directory) {
    return directory.length > 0
            ? new File(directory[0], configuration.fileName()).getPath()
            : configuration.fileName();
  }

  /**
   * Retrieves the @Configuration annotation from the class.
   *
   * @return The Configuration annotation.
   */
  private Configuration retrieveConfigurationAnnotation() {
    Class<? extends BaseConfiguration> clazz = this.getClass();
    Configuration configAnnotation = clazz.getAnnotation(Configuration.class);
    if (Modifier.isAbstract(clazz.getModifiers())) {
      throw new IllegalStateException("Abstract classes cannot have @Configuration annotations.");
    }
    if (configAnnotation == null || configAnnotation.fileName().isEmpty()) {
      throw new IllegalStateException("Missing or empty @Configuration annotation with fileName.");
    }
    return configAnnotation;
  }

  /** Reloads the configuration from the file and updates internal options. */
  public void reload() {
    loadFileIfExists();
    loadValues();
  }

  /** Saves the current configuration options to the file with comments. */
  public void save() {
    try (PrintWriter writer = new PrintWriter(Files.newOutputStream(file.toPath()))) {
      fileWriter = writer;
      writeHeader();
      syncWithOptions();
      for (Map.Entry<String, ConfigurationOption<?>> entry : configOptions.entrySet()) {
        String key = entry.getKey();
        ConfigurationOption<?> option = entry.getValue();
        writeComment(option);
        writeValue(key, option);
        writer.println();
      }

    } catch (IOException | IllegalAccessException e) {
      throw new IllegalStateException("Could not save configuration file: " + file.getName(), e);
    }
  }

  /**
   * Synchronizes the current field values with the configuration options.
   *
   * <p>This method iterates over the fields of the current class and its superclasses. For each
   * field annotated with {@link Key}, the current field value is retrieved using reflection
   * and the corresponding entry in the {@code configOptions} map is updated with this value.
   *
   * @throws IllegalAccessException if the field values cannot be accessed via reflection.
   */
  private void syncWithOptions() throws IllegalAccessException {
    List<Class<?>> classHierarchy = ClassUtils.getHierarchy(getClass());
    for (Class<?> clazz : classHierarchy) {
      for (Field field : clazz.getDeclaredFields()) {
        Key keyAnnotation = field.getAnnotation(Key.class);
        if (keyAnnotation != null) {
          field.setAccessible(true);
          Object fieldValue = field.get(this);
          String[] comments = getComments(field);
          ConfigurationOption<?> option = new ConfigurationOption<>(fieldValue, comments);
          configOptions.put(keyAnnotation.value(), option);
        }
      }
    }
  }

  /**
   * Retrieves the comments for a given field from the {@link Comment} annotation.
   *
   * @param field The field to retrieve comments from.
   * @return An array of comments or an empty array if no comments are found.
   */
  private String[] getComments(Field field) {
    Comment commentAnnotation = field.getAnnotation(Comment.class);
    return commentAnnotation != null ? commentAnnotation.value() : new String[0];
  }

  /**
   * Loads configuration values from the properties file and updates internal options. Scans the
   * class for fields annotated with @Key and updates their values.
   */
  private void loadValues() {
    List<Class<?>> classHierarchy = ClassUtils.getHierarchy(getClass());
    for (Class<?> clazz : classHierarchy) {
      processFields(clazz);
    }
  }

  /** Processes all fields in a class that are annotated with @Key. */
  private void processFields(Class<?> clazz) {
    for (Field field : clazz.getDeclaredFields()) {
      Key keyAnnotation = field.getAnnotation(Key.class);
      if (keyAnnotation != null) {
        processField(field, keyAnnotation);
      }
    }
  }

  /**
   * Process an individual field that is annotated with @Key.
   *
   * @param field The field to process.
   * @param keyAnnotation The annotation instance for this field.
   */
  private void processField(Field field, Key keyAnnotation) {
    String key = keyAnnotation.value();
    field.setAccessible(true);
    try {
      if (properties.containsKey(key)) {
        processExistingProperty(field, key);
      } else {
        processDefaultValue(field, key);
      }
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Unable to access field: " + field.getName(), e);
    }
  }

  /**
   * Processes a field that has a corresponding key in the properties file. Assigns the property
   * value to the field and creates a ConfigurationOption.
   */
  private void processExistingProperty(Field field, String key) throws IllegalAccessException {
    String newValue = properties.getProperty(key);
    assignNewValue(field, newValue);
    String[] comments = getComments(field);
    ConfigurationOption<?> option = new ConfigurationOption<>(field.get(this), comments);
    configOptions.put(key, option);
  }

  /**
   * Processes a field that does not have a corresponding key in the properties file. Uses the
   * current field value or a default value to create a ConfigurationOption.
   */
  private void processDefaultValue(Field field, String key) throws IllegalAccessException {
    Object fieldValue = field.get(this);
    String[] comments = getComments(field);
    ConfigurationOption<?> option;
    if (fieldValue != null) {
      option = new ConfigurationOption<>(fieldValue, comments);
    } else {
      option = new ConfigurationOption<>("", comments);
    }
    configOptions.put(key, option);
  }

  /** Loads the configuration from the file if it exists. */
  private void loadFileIfExists() {
    if (file.exists()) {
      try (FileInputStream fis = new FileInputStream(file)) {
        properties.load(fis);
      } catch (IOException e) {
        throw new IllegalStateException("Could not load configuration file: " + file.getName(), e);
      }
    } else {
      save();
    }
  }

  /**
   * Writes the header for the configuration file from the @Comment annotation if present. Otherwise,
   * writes a default header.
   */
  private void writeHeader() {
    Comment headerAnnotation = this.getClass().getAnnotation(Comment.class);
    if (headerAnnotation != null) {
      String[] headerLines = headerAnnotation.value();
      for (String line : headerLines) {
        writeComment(line);
      }
    }
    fileWriter.println();
  }

  /**
   * Writes the comment for a given configuration option.
   *
   * @param key The configuration option.
   */
  private void writeValue(String key, ConfigurationOption<?> option) {
    Object value = option.getValue();
    String serializedValue = GSON.toJson(value);
    String delimiter = getDelimiter();
    fileWriter.printf("%s" + delimiter + " %s%n", key, serializedValue);
  }

  /**
   * Determines the appropriate delimiter based on the type of configuration.
   *
   * @return The delimiter string corresponding to the configuration type.
   * @throws IllegalArgumentException if the configuration type is not supported.
   */
  private String getDelimiter() {
    Configuration configuration = retrieveConfigurationAnnotation();
    switch (configuration.type()) {
      case YAML:
        return YAML_DELIMITER;
      case PROPERTIES:
        return PROPERTIES_DELIMITER;
      default:
        throw new IllegalArgumentException("Configuration type not supported");
    }
  }

  /**
   * Writes the comment for a given configuration option.
   *
   * @param option The configuration option.
   */
  private void writeComment(ConfigurationOption<?> option) {
      Arrays.stream(option.getComments()).forEach(this::writeComment);
  }

  /**
   * Writes a comment to the configuration file.
   *
   * @param comment The comment to write.
   */
  private void writeComment(String comment) {
    fileWriter.println(COMMENT_PREFIX + " " + comment);
  }

  /**
   * Assigns a new value to a configuration option based on the properties file.
   *
   * @param field The field representing the configuration option.
   * @param newValue The value from the properties file.
   */
  private <T> void assignNewValue(Field field, String newValue) throws IllegalAccessException {
    Class<?> type = field.getType();
    try {
      Object value = GSON.fromJson(newValue, type);
      field.set(this, value);
    } catch (JsonSyntaxException e) {
      throw new IllegalArgumentException(
              "Unable to parse the configuration value for field: " + field.getName(), e);
    }
  }

  /**
   * Ensures the parent directory exists; creates it if necessary.
   *
   * @param directory The directory to check or create.
   */
  private void createDirectoryIfNotExists(File directory) {
    if (directory != null && !directory.exists()) {
      directory.mkdirs();
    }
  }
}