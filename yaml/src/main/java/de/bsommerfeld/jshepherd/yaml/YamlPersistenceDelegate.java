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

class YamlPersistenceDelegate<T extends ConfigurablePojo<T>>
    extends AbstractPersistenceDelegate<T> {
  // Lazy initialization - will be set on first use
  private Yaml yaml; // For loading the whole POJO and for simple dump
  private Yaml valueDumper; // For dumping individual field values in complex save

  YamlPersistenceDelegate(Path filePath, boolean useComplexSaveWithComments) {
    super(filePath, useComplexSaveWithComments);
  }

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
    }
    return false;
  }

  @Override
  protected void saveSimple(T pojoInstance, Path targetPath) throws IOException {
    // Ensure YAML is initialized before saving
    initializeYamlIfNeeded((Class<T>) pojoInstance.getClass());

    try (Writer writer =
        Files.newBufferedWriter(
            targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
      Comment classComment = pojoInstance.getClass().getAnnotation(Comment.class);
      if (classComment != null && classComment.value().length > 0) {
        for (String line : classComment.value()) {
          writer.write("# " + line + System.lineSeparator());
        }
        writer.write(System.lineSeparator());
      }
      yaml.dump(pojoInstance, writer);
    }
  }

  @Override
  protected void saveWithComments(T pojoInstance, Path targetPath) throws IOException {
    // Ensure YAML is initialized before saving
    initializeYamlIfNeeded((Class<T>) pojoInstance.getClass());

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

        String yamlKey = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

        CommentSection sectionAnnotation = field.getAnnotation(CommentSection.class);
        if (sectionAnnotation != null && sectionAnnotation.value().length > 0) {
          String currentSectionHash = String.join("|", sectionAnnotation.value());
          if (!currentSectionHash.equals(this.lastCommentSectionHash)) {
            if (this.lastCommentSectionHash != null || writer.checkError()) writer.println();
            for (String commentLine : sectionAnnotation.value()) writer.println("# " + commentLine);
            this.lastCommentSectionHash = currentSectionHash;
          }
        }

        Comment fieldComment = field.getAnnotation(Comment.class);
        if (fieldComment != null && fieldComment.value().length > 0) {
          for (String commentLine : fieldComment.value()) writer.println("# " + commentLine);
        }

        writer.print(yamlKey + ":");
        Object value;
        try {
          value = field.get(pojoInstance);
        } catch (IllegalAccessException e) {
          System.err.println("ERROR: Could not access field " + field.getName() + " during save.");
          continue;
        }

        if (value == null) {
          writer.println(" null");
        } else {
          String valueAsYaml = this.valueDumper.dump(value);

          if (valueAsYaml.endsWith(System.lineSeparator())) {
            valueAsYaml =
                valueAsYaml.substring(0, valueAsYaml.length() - System.lineSeparator().length());
          }

          boolean isScalarOrFlowCollection =
              !(value instanceof List || value instanceof Map)
                  && !valueAsYaml.contains(System.lineSeparator());
          if (value instanceof List && ((List<?>) value).isEmpty()) isScalarOrFlowCollection = true;
          if (value instanceof Map && ((Map<?, ?>) value).isEmpty())
            isScalarOrFlowCollection = true;

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
    }
  }

  // DataExtractor implementation for YAML
  private static class YamlDataExtractor implements DataExtractor {
    private final Object yamlData;

    YamlDataExtractor(Object yamlData) {
      this.yamlData = yamlData;
    }

    @Override
    public boolean hasValue(String key) {
      if (!(yamlData instanceof Map)) return false;
      Map<?, ?> yamlMap = (Map<?, ?>) yamlData;
      return yamlMap.containsKey(key);
    }

    @Override
    public Object getValue(String key, Class<?> targetType) {
      if (!(yamlData instanceof Map)) return null;
      Map<?, ?> yamlMap = (Map<?, ?>) yamlData;
      return yamlMap.get(key);
    }
  }
}
