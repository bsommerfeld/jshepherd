package de.metaphoriker.jshepherd;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import de.metaphoriker.jshepherd.annotation.Comment;
import de.metaphoriker.jshepherd.annotation.CommentSection;
import de.metaphoriker.jshepherd.annotation.Key;
import de.metaphoriker.jshepherd.annotation.Configuration;
import de.metaphoriker.jshepherd.utils.ClassUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/** BaseConfiguration is a class that manages configuration options and saves them to a file. */
public abstract class BaseConfiguration {

  private static final Gson GSON = new Gson();

  private final Map<String, ConfigurationOption<?>> configOptions = new LinkedHashMap<>();
  private final Properties properties = new Properties();

  private PrintWriter fileWriter;
  public Path file;

  /** Constructor for BaseConfiguration, uses the file name from the @Configuration annotation. */
  protected BaseConfiguration() {
    file = createFile();
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
  public void setDirectory(Path directory) {
    if (directory == null) {
      throw new IllegalArgumentException("The directory must not be null");
    }

    if (!Files.isDirectory(directory)) {
      throw new IllegalArgumentException("The provided path must be a directory");
    }

    file = directory.resolve(file);
  }

  /** Creates a {@link Path} object based on the data of the {@link Configuration} annotation. */
  private Path createFile() {
    Configuration configuration = retrieveConfigurationAnnotation();

    String fileName = configuration.fileName();
    String extension = configuration.type().getExtension();

    return Paths.get(fileName + "." + extension);
  }

  /** Retrieves the @Configuration annotation from the class. */
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
    try (PrintWriter writer = new PrintWriter(Files.newOutputStream(file))) {

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
      throw new IllegalStateException("Could not save configuration file: " + file.getFileName(), e);
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

        if (keyAnnotation == null) {
          continue;
        }

        field.setAccessible(true);
        Object fieldValue = field.get(this);
        String[] comments = getComments(field);
        ConfigurationOption<?> option = new ConfigurationOption<>(fieldValue, comments);
        configOptions.put(keyAnnotation.value(), option);
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
    Comment comment = field.getAnnotation(Comment.class);
    CommentSection commentSection = field.getAnnotation(CommentSection.class);

    List<String> result = new ArrayList<>();

    if(commentSection != null) {
      Collections.addAll(result, commentSection.value());
    }

    result.add(" ");

    if(comment != null) {
      Collections.addAll(result, comment.value());
    }

    return result.toArray(new String[0]);
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

  /** Process an individual field that is annotated with @Key. */
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
    if (Files.notExists(file)) {
      save();
      return;
    }

    try (InputStream is = Files.newInputStream(file)) {
      properties.load(is);
    } catch (IOException e) {
      throw new IllegalStateException("Could not load configuration file: " + file, e);
    }
  }

  /**
   * Writes the header for the configuration file from the @Comment annotation if present. Otherwise,
   * writes a default header.
   */
  private void writeHeader() {
    Comment headerAnnotation = this.getClass().getAnnotation(Comment.class);

    if (headerAnnotation == null) {
      return;
    }

    for (String line : headerAnnotation.value()) {
      writeComment(line);
    }

    fileWriter.println();
  }

  /** Writes the comment for a given configuration option. */
  private void writeValue(String key, ConfigurationOption<?> option) {
    Object value = option.getValue();
    String serializedValue = GSON.toJson(value);
    String delimiter = getDelimiter();
    fileWriter.printf("%s" + delimiter + " %s%n", key, serializedValue);
  }

  /**Determines the appropriate delimiter based on the type of configuration. */
  private String getDelimiter() {
    Configuration configuration = retrieveConfigurationAnnotation();
    return configuration.type().getDelimiter();
  }

  /** Writes the comment for a given configuration option. */
  private void writeComment(ConfigurationOption<?> option) {
      Arrays.stream(option.getComments()).forEach(this::writeComment);
  }

  /** Writes a comment to the configuration file. */
  private void writeComment(String comment) {
    //separate the section's comment from the opener field's comment
    if(!comment.equals(" "))
      comment = "# " + comment;

    fileWriter.println(comment);
  }

  /** Assigns a new value to a configuration option based on the properties file. */
  private <T> void assignNewValue(Field field, String newValue) throws IllegalAccessException {
    Class<?> type = field.getType();
    try {
      Object value = GSON.fromJson(newValue, type);
      field.set(this, value);
    } catch (JsonSyntaxException e) {
      throw new IllegalArgumentException("Unable to parse the configuration value for field: " + field.getName(), e);
    }
  }
}