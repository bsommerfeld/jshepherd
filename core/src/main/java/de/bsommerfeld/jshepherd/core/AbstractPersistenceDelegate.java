package de.bsommerfeld.jshepherd.core;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.annotation.Section;
import de.bsommerfeld.jshepherd.utils.ClassUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract base class for all persistence delegates.
 * Provides common functionality for file operations, type conversion,
 * comment writing, and reflection-based data mapping.
 */
public abstract class AbstractPersistenceDelegate<T> implements PersistenceDelegate<T> {

    private static final Logger LOGGER = Logger.getLogger(AbstractPersistenceDelegate.class.getName());

    private static final Map<Class<?>, Function<Number, Object>> NUMERIC_CONVERTERS = Map.ofEntries(
            Map.entry(int.class, Number::intValue),
            Map.entry(Integer.class, Number::intValue),
            Map.entry(float.class, Number::floatValue),
            Map.entry(Float.class, Number::floatValue),
            Map.entry(double.class, Number::doubleValue),
            Map.entry(Double.class, Number::doubleValue),
            Map.entry(long.class, Number::longValue),
            Map.entry(Long.class, Number::longValue),
            Map.entry(short.class, Number::shortValue),
            Map.entry(Short.class, Number::shortValue),
            Map.entry(byte.class, Number::byteValue),
            Map.entry(Byte.class, Number::byteValue));

    /**
     * Safety cap for recursive {@code @Section} nesting, protecting against
     * accidental cycles in section POJO graphs.
     */
    protected static final int MAX_SECTION_DEPTH = 16;

    protected final Path filePath;
    protected final boolean useComplexSaveWithComments;

    private final List<LoadIssue> loadIssues = new ArrayList<>();

    protected AbstractPersistenceDelegate(Path filePath, boolean useComplexSaveWithComments) {
        this.filePath = filePath;
        this.useComplexSaveWithComments = useComplexSaveWithComments;
    }

    @Override
    public final T loadInitial(Supplier<T> defaultPojoSupplier) {
        boolean fileExisted = Files.exists(filePath);

        if (fileExisted) {
            try {
                T defaultInstance = defaultPojoSupplier.get();

                loadIssues.clear();
                if (tryLoadFromFile(defaultInstance)) {
                    publishLoadIssues(defaultInstance);
                    LOGGER.log(Level.FINE, () -> "Configuration loaded from " + filePath);
                    return defaultInstance;
                } else {
                    LOGGER.log(Level.FINE, () -> "Config file '" + filePath + "' was empty. Using defaults.");
                }
            } catch (Exception e) {
                loadIssues.clear();
                Path backupPath = backupUnparseableFile();
                LOGGER.log(Level.WARNING, "Initial load/parse of '" + filePath + "' failed. Using defaults."
                        + (backupPath != null ? " The unparseable file was backed up to '" + backupPath + "'." : ""), e);
            }
        } else {
            LOGGER.log(Level.FINE, () -> "Config file '" + filePath + "' not found. Creating with defaults.");
        }

        T defaultInstance = defaultPojoSupplier.get();
        LOGGER.log(Level.FINE, () -> "Saving initial/default configuration to: " + filePath);
        save(defaultInstance);
        return defaultInstance;
    }

    @Override
    public final void save(T pojoInstance) {
        Path parentDir = filePath.getParent();
        Path tempFilePath = null;
        try {
            if (parentDir != null)
                Files.createDirectories(parentDir);
            tempFilePath = Files.createTempFile(
                    parentDir != null ? parentDir : Paths.get("."),
                    filePath.getFileName().toString(),
                    ".tmpjhp");

            if (useComplexSaveWithComments) {
                saveWithComments(pojoInstance, tempFilePath);
            } else {
                saveSimple(pojoInstance, tempFilePath);
            }

            Files.move(tempFilePath, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOGGER.log(Level.FINE, () -> "Configuration saved to " + filePath);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to save configuration to " + filePath, e);
        } finally {
            cleanupTempFile(tempFilePath);
        }
    }

    @Override
    public final void reload(T pojoInstanceToUpdate) {
        if (!Files.exists(filePath)) {
            LOGGER.log(Level.FINE, () -> "Configuration file '" + filePath
                    + "' not found on reload attempt. Current values in instance remain.");
            return;
        }

        try {
            loadIssues.clear();
            if (tryLoadFromFile(pojoInstanceToUpdate)) {
                publishLoadIssues(pojoInstanceToUpdate);
                LOGGER.log(Level.FINE, () -> "Configuration reloaded into existing instance from " + filePath);
            } else {
                LOGGER.log(Level.FINE, () -> "Configuration file '" + filePath
                        + "' was empty on reload. Current values in instance remain.");
            }
        } catch (Exception e) {
            throw new ConfigurationException("Error reloading configuration from " + filePath, e);
        }
    }

    @Override
    public final List<LoadIssue> getLastLoadIssues() {
        return List.copyOf(loadIssues);
    }

    /**
     * Makes the recorded issues available on the instance for the extends-based
     * API. Plain {@code @Configuration} POJOs receive them through the
     * {@code Config} handle (via {@link #getLastLoadIssues()}) instead.
     */
    private void publishLoadIssues(T instance) {
        if (instance instanceof ConfigurablePojo<?> configurablePojo) {
            configurablePojo._setLoadIssues(loadIssues);
        }
    }

    // ==================== SHARED UTILITIES ====================

    /**
     * Converts values to match the target type. Handles numeric widening/narrowing
     * and String-to-Enum conversion.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected final Object convertNumericIfNeeded(Object value, Class<?> targetType) {
        if (value == null || targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        if (value instanceof Number number) {
            Function<Number, Object> converter = NUMERIC_CONVERTERS.get(targetType);
            return converter != null ? converter.apply(number) : value;
        }

        if (value instanceof String text) {
            // Enum stored as its constant name in all formats
            if (targetType.isEnum()) {
                return Enum.valueOf((Class<Enum>) targetType, text);
            }

            // String into a numeric field (e.g. a quoted number in the file, or
            // formats like .properties where every raw value is a string).
            // String fields are never touched by this — only numeric/boolean targets.
            Function<Number, Object> converter = NUMERIC_CONVERTERS.get(targetType);
            if (converter != null) {
                try {
                    return converter.apply(new BigDecimal(text.trim()));
                } catch (NumberFormatException e) {
                    return value; // not a number — let the field assignment fail and warn
                }
            }
            if (targetType == boolean.class || targetType == Boolean.class) {
                if (text.equalsIgnoreCase("true")) return Boolean.TRUE;
                if (text.equalsIgnoreCase("false")) return Boolean.FALSE;
            }
        }

        return value;
    }

    /**
     * Checks if the given type is an enum type.
     */
    protected final boolean isEnumType(Class<?> type) {
        return type.isEnum();
    }

    /**
     * Returns the constant name of an enum value for format-agnostic serialization.
     */
    protected final String serializeEnumValue(Object enumValue) {
        return ((Enum<?>) enumValue).name();
    }

    /**
     * Applies data from parsed format to POJO instance using reflection.
     */
    protected final void applyDataToInstance(T target, DataExtractor extractor) {
        List<Field> fields = ClassUtils.getAllFieldsInHierarchy(target.getClass(), ConfigurablePojo.class);

        for (Field field : fields) {
            if (shouldSkipField(field)) {
                continue;
            }

            Key keyAnnotation = field.getAnnotation(Key.class);
            if (keyAnnotation == null)
                continue;

            String key = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

            if (extractor.hasValue(key)) {
                Object value = null;
                try {
                    field.setAccessible(true);
                    value = extractor.getValue(key, field.getType());

                    if (value != null) {
                        field.set(target, convertValueForField(value, field));
                    }
                } catch (Exception e) {
                    recordLoadIssue(key, value, field.getType(), e);
                }
            }
        }
    }

    /**
     * Converts a raw parsed value to the field's type, including element-wise
     * conversion for typed lists and maps (e.g. TOML integers arrive as Long
     * and must be narrowed for a {@code List<Integer>} field).
     */
    protected final Object convertValueForField(Object value, Field field) {
        Class<?> targetType = field.getType();

        if (value instanceof List<?> list && List.class.isAssignableFrom(targetType)) {
            Class<?> elementType = resolveGenericClass(field, 0);
            if (elementType == null) {
                return value;
            }
            List<Object> converted = new ArrayList<>(list.size());
            for (Object item : list) {
                converted.add(convertNumericIfNeeded(item, elementType));
            }
            return converted;
        }

        if (value instanceof Map<?, ?> map && Map.class.isAssignableFrom(targetType)) {
            Class<?> valueType = resolveGenericClass(field, 1);
            if (valueType == null) {
                return value;
            }
            Map<Object, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                converted.put(entry.getKey(), convertNumericIfNeeded(entry.getValue(), valueType));
            }
            return converted;
        }

        return convertNumericIfNeeded(value, targetType);
    }

    /**
     * Resolves the class of a field's generic type argument (e.g. the element
     * type of a {@code List<Integer>}), or null if not determinable.
     */
    protected final Class<?> resolveGenericClass(Field field, int index) {
        if (field.getGenericType() instanceof ParameterizedType parameterizedType) {
            Type[] arguments = parameterizedType.getActualTypeArguments();
            if (arguments.length > index && arguments[index] instanceof Class<?> clazz) {
                return clazz;
            }
        }
        return null;
    }

    /**
     * Records a value that could not be applied to its field. The issue is
     * logged and made available to the user via
     * {@link ConfigurablePojo#getLastLoadIssues()} after the load completes,
     * so it can be validated in {@code @PostInject} methods.
     */
    protected final void recordLoadIssue(String key, Object rawValue, Class<?> targetType, Exception cause) {
        String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        loadIssues.add(new LoadIssue(key, rawValue, targetType, message));
        LOGGER.log(Level.WARNING, () -> "Could not apply config value for key '" + key + "' (expected "
                + targetType.getSimpleName() + "): " + message + ". Field keeps its current value.");
    }

    /**
     * Checks if a field should be skipped during persistence operations.
     * Skips static and transient fields.
     */
    protected final boolean shouldSkipField(Field field) {
        return Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers());
    }

    // ==================== COMMENT WRITING UTILITIES ====================

    /**
     * Writes class-level comments (file header) from @Comment annotation on the
     * POJO class.
     */
    protected final void writeClassComments(PrintWriter writer, T pojoInstance) {
        Comment classComment = pojoInstance.getClass().getAnnotation(Comment.class);
        if (classComment != null && classComment.value().length > 0) {
            for (String line : classComment.value()) {
                writer.println("# " + line);
            }
            writer.println();
        }
    }

    /**
     * Writes field-level comments from @Comment annotation.
     */
    protected final void writeFieldComments(PrintWriter writer, Field field) {
        Comment fieldComment = field.getAnnotation(Comment.class);
        if (fieldComment != null) {
            for (String commentLine : fieldComment.value()) {
                writer.println("# " + commentLine);
            }
        }
    }

    /**
     * Resolves the configuration key for a field, using @Key annotation value or
     * field name.
     */
    protected final String resolveKey(Field field) {
        Key keyAnnotation = field.getAnnotation(Key.class);
        if (keyAnnotation == null) {
            return field.getName();
        }
        return keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();
    }

    // ==================== SECTION UTILITIES ====================

    /**
     * Checks if a field is annotated with @Section.
     */
    protected final boolean isSection(Field field) {
        return field.getAnnotation(Section.class) != null;
    }

    /**
     * Resolves the section name from @Section annotation, falling back to @Key or
     * field name.
     */
    protected final String resolveSectionName(Field field) {
        Section sectionAnnotation = field.getAnnotation(Section.class);
        if (sectionAnnotation != null && !sectionAnnotation.value().isEmpty()) {
            return sectionAnnotation.value();
        }
        return resolveKey(field);
    }

    /**
     * Gets all non-section fields (regular @Key fields) from a class.
     * These should be written first, before any sections.
     */
    protected final List<Field> getNonSectionFields(Class<?> clazz, Class<?> stopClass) {
        return ClassUtils.getAllFieldsInHierarchy(clazz, stopClass).stream()
                .filter(f -> !shouldSkipField(f))
                .filter(f -> f.getAnnotation(Key.class) != null)
                .filter(f -> !isSection(f))
                .toList();
    }

    /**
     * Gets all section fields from a class.
     * These should be written after regular fields.
     */
    protected final List<Field> getSectionFields(Class<?> clazz, Class<?> stopClass) {
        return ClassUtils.getAllFieldsInHierarchy(clazz, stopClass).stream()
                .filter(f -> !shouldSkipField(f))
                .filter(this::isSection)
                .toList();
    }

    /**
     * Gets all fields from a nested section POJO that have @Key annotation.
     * Section POJOs don't extend ConfigurablePojo, so we use Object as stop class.
     */
    protected final List<Field> getSectionPojoFields(Object sectionPojo) {
        return ClassUtils.getAllFieldsInHierarchy(sectionPojo.getClass(), Object.class).stream()
                .filter(f -> !shouldSkipField(f))
                .filter(f -> f.getAnnotation(Key.class) != null)
                .filter(f -> !isSection(f))
                .toList();
    }

    /**
     * Gets all nested {@code @Section} fields of a section POJO, enabling
     * recursive section nesting. Guard recursion with
     * {@link #MAX_SECTION_DEPTH}.
     */
    protected final List<Field> getSectionPojoSubsectionFields(Object sectionPojo) {
        return ClassUtils.getAllFieldsInHierarchy(sectionPojo.getClass(), Object.class).stream()
                .filter(f -> !shouldSkipField(f))
                .filter(this::isSection)
                .toList();
    }

    // ==================== PRIVATE HELPERS ====================

    /**
     * Copies an unparseable configuration file aside before it gets replaced with
     * defaults, so user edits are never silently destroyed.
     *
     * @return the backup path, or null if the backup could not be created
     */
    private Path backupUnparseableFile() {
        Path backupPath = filePath.resolveSibling(filePath.getFileName().toString() + ".bak");
        try {
            Files.copy(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            return backupPath;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not back up unparseable config file '" + filePath + "'", e);
            return null;
        }
    }

    private void cleanupTempFile(Path tempFilePath) {
        if (tempFilePath != null && Files.exists(tempFilePath)) {
            try {
                Files.delete(tempFilePath);
            } catch (IOException ignored) {
                // Ignore cleanup failures
            }
        }
    }

    // ==================== ABSTRACT METHODS ====================

    /**
     * Format-specific loading implementation.
     *
     * @return true if data was loaded successfully, false if file was empty
     */
    protected abstract boolean tryLoadFromFile(T instance) throws Exception;

    /**
     * Format-specific simple save implementation.
     */
    protected abstract void saveSimple(T pojoInstance, Path targetPath) throws IOException;

    /**
     * Format-specific save with comments implementation.
     */
    protected abstract void saveWithComments(T pojoInstance, Path targetPath) throws IOException;

    // ==================== HELPER INTERFACES ====================

    /**
     * Abstraction for extracting values from different data formats.
     */
    protected interface DataExtractor {
        boolean hasValue(String key);

        Object getValue(String key, Class<?> targetType);
    }
}