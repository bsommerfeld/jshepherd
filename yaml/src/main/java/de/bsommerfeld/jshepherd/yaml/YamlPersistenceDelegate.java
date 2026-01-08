package de.bsommerfeld.jshepherd.yaml;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.CommentSection;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.core.AbstractPersistenceDelegate;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.utils.ClassUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.FieldProperty;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

class YamlPersistenceDelegate<T extends ConfigurablePojo<T>> extends AbstractPersistenceDelegate<T> {

  private static final Logger LOGGER = Logger.getLogger(YamlPersistenceDelegate.class.getName());
  private static final BeanAccess BEAN_ACCESS = BeanAccess.FIELD;

  private Yaml yaml;
  private Yaml valueDumper;

  YamlPersistenceDelegate(Path filePath, boolean useComplexSaveWithComments) {
    super(filePath, useComplexSaveWithComments);
  }

  private void initializeYamlIfNeeded(Class<T> pojoClass) {
    if (this.yaml != null) {
      return;
    }

    JShepherdPropertyUtils propertyUtils = new JShepherdPropertyUtils();
    propertyUtils.setSkipMissingProperties(false);
    propertyUtils.setBeanAccess(BEAN_ACCESS);

    DumperOptions mainDumperOptions = createDumperOptions();
    Representer representer = new AlwaysMapRepresenter(mainDumperOptions);
    representer.addClassTag(pojoClass, Tag.MAP);
    representer.setPropertyUtils(propertyUtils);

    LoaderOptions loaderOptions = new LoaderOptions();
    Constructor constructor = new Constructor(pojoClass, loaderOptions);

    // Critical Fix: Explicitly register TypeDescription with correct PropertyUtils.
    TypeDescription rootDescription = new TypeDescription(pojoClass);
    rootDescription.setPropertyUtils(propertyUtils);
    constructor.addTypeDescription(rootDescription);

    constructor.setPropertyUtils(propertyUtils);

    this.yaml = new Yaml(constructor, representer, mainDumperOptions);

    DumperOptions valueDumperOptions = createDumperOptions();
    Representer valueDumperRepresenter = new AlwaysMapRepresenter(valueDumperOptions);
    valueDumperRepresenter.addClassTag(ArrayList.class, Tag.SEQ);
    valueDumperRepresenter.addClassTag(HashMap.class, Tag.MAP);
    valueDumperRepresenter.addClassTag(LinkedHashMap.class, Tag.MAP);
    valueDumperRepresenter.setPropertyUtils(propertyUtils);

    this.valueDumper = new Yaml(valueDumperRepresenter, valueDumperOptions);
  }

  private DumperOptions createDumperOptions() {
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setPrettyFlow(true);
    options.setIndent(2);
    options.setIndicatorIndent(1);
    options.setSplitLines(false);
    options.setAllowUnicode(true);
    options.setExplicitStart(false);
    options.setExplicitEnd(false);
    return options;
  }

  @Override
  protected boolean tryLoadFromFile(T instance) throws Exception {
    initializeYamlIfNeeded((Class<T>) instance.getClass());

    try (Reader reader = Files.newBufferedReader(filePath)) {
      T loaded = yaml.loadAs(reader, (Class<T>) instance.getClass());
      if (loaded != null) {
        copyProperties(loaded, instance);
        return true;
      }
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Failed to load YAML file", e);
      throw e;
    }
    return false;
  }

  private void copyProperties(T source, T target) {
    List<Field> fields = ClassUtils.getAllFieldsInHierarchy(source.getClass(), ConfigurablePojo.class);
    for (Field field : fields) {
      if (shouldSkipField(field)) {
        continue;
      }
      try {
        field.setAccessible(true);
        Object value = field.get(source);
        field.set(target, value);
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Failed to copy field " + field.getName(), e);
      }
    }
  }

  @Override
  protected void saveSimple(T pojoInstance, Path targetPath) throws IOException {
    initializeYamlIfNeeded((Class<T>) pojoInstance.getClass());
    try (Writer writer = Files.newBufferedWriter(targetPath, StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING)) {
      writeClassComments(writer, pojoInstance);
      yaml.dump(pojoInstance, writer);
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Failed to save YAML file", e);
      throw e;
    }
  }

  @Override
  protected void saveWithComments(T pojoInstance, Path targetPath) throws IOException {
    initializeYamlIfNeeded((Class<T>) pojoInstance.getClass());

    try (PrintWriter writer = new PrintWriter(
        Files.newBufferedWriter(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
      this.lastCommentSectionHash = null;

      writeClassComments(writer, pojoInstance);

      List<Field> fields = ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);

      for (int fieldIdx = 0; fieldIdx < fields.size(); fieldIdx++) {
        Field field = fields.get(fieldIdx);
        if (shouldSkipField(field)) {
          continue;
        }
        field.setAccessible(true);
        Key keyAnnotation = field.getAnnotation(Key.class);
        if (keyAnnotation == null)
          continue;

        String yamlKey = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

        writeSectionComments(writer, field);
        writeFieldComments(writer, field);

        writer.print(yamlKey + ":");
        Object value;
        try {
          value = field.get(pojoInstance);
        } catch (IllegalAccessException e) {
          LOGGER.log(Level.SEVERE, "Could not access field " + field.getName(), e);
          continue;
        }

        if (value == null) {
          writer.println(" null");
        } else {
          String valueAsYaml = this.valueDumper.dump(value);

          if (valueAsYaml.endsWith(System.lineSeparator())) {
            valueAsYaml = valueAsYaml.substring(0, valueAsYaml.length() - System.lineSeparator().length());
          }

          boolean isScalarOrFlowCollection = !(value instanceof List || value instanceof Map)
              && !valueAsYaml.contains(System.lineSeparator());
          if (value instanceof List && ((List<?>) value).isEmpty())
            isScalarOrFlowCollection = true;
          if (value instanceof Map && ((Map<?, ?>) value).isEmpty())
            isScalarOrFlowCollection = true;

          if (isScalarOrFlowCollection) {
            writer.println(" " + valueAsYaml.trim());
          } else {
            writer.println();
            valueAsYaml.lines().forEach(line -> writer.println("  " + line));
          }
        }

        writeBlankLineIfNeeded(writer, fields, fieldIdx);
      }
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Failed to save YAML file with comments", e);
      throw e;
    }
  }

  private void writeClassComments(Writer writer, T pojoInstance) throws IOException {
    Comment classComment = pojoInstance.getClass().getAnnotation(Comment.class);
    if (classComment != null && classComment.value().length > 0) {
      for (String line : classComment.value()) {
        writer.write("# " + line + System.lineSeparator());
      }
      writer.write(System.lineSeparator());
    }
  }

  private void writeSectionComments(PrintWriter writer, Field field) {
    CommentSection sectionAnnotation = field.getAnnotation(CommentSection.class);
    if (sectionAnnotation != null && sectionAnnotation.value().length > 0) {
      String currentSectionHash = String.join("|", sectionAnnotation.value());
      if (!currentSectionHash.equals(this.lastCommentSectionHash)) {
        if (this.lastCommentSectionHash != null || writer.checkError())
          writer.println();
        for (String commentLine : sectionAnnotation.value())
          writer.println("# " + commentLine);
        this.lastCommentSectionHash = currentSectionHash;
      }
    }
  }

  private void writeFieldComments(PrintWriter writer, Field field) {
    Comment fieldComment = field.getAnnotation(Comment.class);
    if (fieldComment != null) {
      for (String commentLine : fieldComment.value())
        writer.println("# " + commentLine);
    }
  }

  private void writeBlankLineIfNeeded(PrintWriter writer, List<Field> fields, int currentIdx) {
    if (currentIdx < fields.size() - 1) {
      for (int k = currentIdx + 1; k < fields.size(); k++) {
        Field nextField = fields.get(k);
        if (shouldSkipField(nextField))
          continue;
        if (nextField.getAnnotation(Key.class) != null) {
          writer.println();
          break;
        }
      }
    }
  }

  private static class JShepherdPropertyUtils extends PropertyUtils {

    public JShepherdPropertyUtils() {
    }

    @Override
    public Map<String, Property> getPropertiesMap(Class<?> type, BeanAccess bAccess) {
      // Direct implementation to ensure createPropertySet is called and cache issues
      // are avoided
      Set<Property> properties = createPropertySet(type, bAccess);
      Map<String, Property> map = new HashMap<>();
      for (Property property : properties) {
        map.put(property.getName(), property);
      }
      return map;
    }

    @Override
    protected Set<Property> createPropertySet(Class<? extends Object> type, BeanAccess bAccess) {
      Set<Property> properties = new TreeSet<>();

      // Get all fields
      List<Field> fields = ClassUtils.getAllFieldsInHierarchy(type, Object.class);

      for (Field field : fields) {
        if (isIncludable(field)) {
          properties.add(new FieldProperty(field));
        }
      }

      // Apply wrappers
      return properties.stream()
          .map(p -> new KeyAwareProperty(p, type))
          .collect(Collectors.toSet());
    }

    private static boolean isIncludable(Field field) {
      int modifiers = field.getModifiers();
      return !Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers);
    }
  }

  private static class KeyAwareProperty extends Property {
    private final Property delegate;

    public KeyAwareProperty(Property delegate, Class<?> beanClass) {
      super(resolveName(delegate, beanClass), delegate.getType());
      this.delegate = delegate;
    }

    private static String resolveName(Property delegate, Class<?> beanClass) {
      String propName = delegate.getName();
      Key key = delegate.getAnnotation(Key.class);

      // Fallback
      if (key == null) {
        List<Field> fields = ClassUtils.getAllFieldsInHierarchy(beanClass, Object.class);
        for (Field f : fields) {
          if (f.getName().equals(propName)) {
            key = f.getAnnotation(Key.class);
            break;
          }
        }
      }

      if (key != null && !key.value().isEmpty()) {
        return key.value();
      }
      return delegate.getName();
    }

    @Override
    public Class<?>[] getActualTypeArguments() {
      return delegate.getActualTypeArguments();
    }

    @Override
    public void set(Object object, Object value) throws Exception {
      delegate.set(object, value);
    }

    @Override
    public Object get(Object object) {
      return delegate.get(object);
    }

    @Override
    public List<Annotation> getAnnotations() {
      return delegate.getAnnotations();
    }

    @Override
    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
      return delegate.getAnnotation(annotationType);
    }

    @Override
    public boolean isReadable() {
      return delegate.isReadable();
    }

    @Override
    public boolean isWritable() {
      return delegate.isWritable();
    }
  }
}
