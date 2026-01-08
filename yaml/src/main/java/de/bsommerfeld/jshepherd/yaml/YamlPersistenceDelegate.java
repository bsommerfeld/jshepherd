package de.bsommerfeld.jshepherd.yaml;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.annotation.Section;
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
    // Allow loading configs with obsolete keys that no longer exist in the POJO
    propertyUtils.setSkipMissingProperties(true);
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
    try (PrintWriter writer = new PrintWriter(
        Files.newBufferedWriter(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
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

      writeClassComments(writer, pojoInstance);

      // Write non-section fields first (root level)
      List<Field> rootFields = getNonSectionFields(pojoInstance.getClass(), ConfigurablePojo.class);
      for (int i = 0; i < rootFields.size(); i++) {
        Field field = rootFields.get(i);
        writeFieldWithComments(writer, field, pojoInstance, "");
        if (i < rootFields.size() - 1) {
          writer.println();
        }
      }

      // Write sections
      List<Field> sectionFields = getSectionFields(pojoInstance.getClass(), ConfigurablePojo.class);
      for (Field sectionField : sectionFields) {
        writer.println();
        writeSectionWithComments(writer, sectionField, pojoInstance);
      }
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Failed to save YAML file with comments", e);
      throw e;
    }
  }

  private void writeFieldWithComments(PrintWriter writer, Field field, Object instance,
      String indent) throws IOException {
    field.setAccessible(true);
    String yamlKey = resolveKey(field);

    writeIndentedFieldComments(writer, field, indent);

    Object value;
    try {
      value = field.get(instance);
    } catch (IllegalAccessException e) {
      LOGGER.log(Level.SEVERE, "Could not access field " + field.getName(), e);
      return;
    }

    writer.print(indent + yamlKey + ":");

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
        String nestedIndent = indent + "  ";
        valueAsYaml.lines().forEach(line -> writer.println(nestedIndent + line));
      }
    }
  }

  private void writeSectionWithComments(PrintWriter writer, Field sectionField, Object parentInstance)
      throws IOException {
    sectionField.setAccessible(true);
    Object sectionPojo;
    try {
      sectionPojo = sectionField.get(parentInstance);
    } catch (IllegalAccessException e) {
      LOGGER.log(Level.SEVERE, "Could not access section field " + sectionField.getName(), e);
      return;
    }

    if (sectionPojo == null) {
      return;
    }

    String sectionName = resolveSectionName(sectionField);

    // Write section comments (from @Comment on section field)
    writeFieldComments(writer, sectionField);
    writer.println(sectionName + ":");

    // Write nested fields with indent
    List<Field> nestedFields = getSectionPojoFields(sectionPojo);
    for (int i = 0; i < nestedFields.size(); i++) {
      Field nestedField = nestedFields.get(i);
      writeFieldWithComments(writer, nestedField, sectionPojo, "  ");
      if (i < nestedFields.size() - 1) {
        writer.println();
      }
    }
  }

  private void writeIndentedFieldComments(PrintWriter writer, Field field, String indent) {
    Comment fieldComment = field.getAnnotation(Comment.class);
    if (fieldComment != null) {
      for (String commentLine : fieldComment.value()) {
        writer.println(indent + "# " + commentLine);
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

      // Find corresponding field for annotation lookup
      Field field = null;
      List<Field> fields = ClassUtils.getAllFieldsInHierarchy(beanClass, Object.class);
      for (Field f : fields) {
        if (f.getName().equals(propName)) {
          field = f;
          break;
        }
      }

      if (field != null) {
        // Check @Section first (takes precedence for nested POJOs)
        Section section = field.getAnnotation(Section.class);
        if (section != null && !section.value().isEmpty()) {
          return section.value();
        }

        // Then check @Key
        Key key = field.getAnnotation(Key.class);
        if (key != null && !key.value().isEmpty()) {
          return key.value();
        }
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
