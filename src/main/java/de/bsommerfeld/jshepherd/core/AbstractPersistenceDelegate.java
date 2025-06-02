package de.bsommerfeld.jshepherd.core;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.CommentSection;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.utils.ClassUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Abstract base class for all persistence delegates.
 * Provides common functionality for file operations, type conversion, and reflection-based data mapping.
 */
public abstract class AbstractPersistenceDelegate<T extends ConfigurablePojo<T>> implements PersistenceDelegate<T> {

    protected final Path filePath;
    protected final boolean useComplexSaveWithComments;
    protected String lastCommentSectionHash = null;

    private static final Map<Class<?>, Function<Number, Object>> NUMERIC_CONVERTERS;

    static {
        Map<Class<?>, Function<Number, Object>> converters = new HashMap<>();
        converters.put(int.class, Number::intValue);
        converters.put(Integer.class, Number::intValue);
        converters.put(float.class, Number::floatValue);
        converters.put(Float.class, Number::floatValue);
        converters.put(double.class, Number::doubleValue);
        converters.put(Double.class, Number::doubleValue);
        converters.put(long.class, Number::longValue);
        converters.put(Long.class, Number::longValue);
        converters.put(short.class, Number::shortValue);
        converters.put(Short.class, Number::shortValue);
        converters.put(byte.class, Number::byteValue);
        converters.put(Byte.class, Number::byteValue);
        NUMERIC_CONVERTERS = Collections.unmodifiableMap(converters);
    }

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

                if (tryLoadFromFile(defaultInstance)) {
                    System.out.println("INFO: Configuration loaded from " + filePath);
                    return defaultInstance;
                } else {
                    System.out.println("INFO: Config file '" + filePath + "' was empty. Using defaults.");
                }
            } catch (Exception e) {
                System.err.println("WARNING: Initial load/parse of '" + filePath + "' failed. Using defaults. Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        } else {
            System.out.println("INFO: Config file '" + filePath + "' not found. Creating with defaults.");
        }

        T defaultInstance = defaultPojoSupplier.get();
        System.out.println("INFO: Saving initial/default configuration to: " + filePath);
        save(defaultInstance);
        return defaultInstance;
    }

    @Override
    public final void save(T pojoInstance) {
        Path parentDir = filePath.getParent();
        Path tempFilePath = null;
        try {
            if (parentDir != null) Files.createDirectories(parentDir);
            tempFilePath = Files.createTempFile(
                    parentDir != null ? parentDir : Paths.get("."),
                    filePath.getFileName().toString(),
                    ".tmpjhp"
            );

            if (useComplexSaveWithComments) {
                saveWithComments(pojoInstance, tempFilePath);
            } else {
                saveSimple(pojoInstance, tempFilePath);
            }

            Files.move(tempFilePath, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            System.out.println("INFO: Configuration saved to " + filePath);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to save configuration to " + filePath, e);
        } finally {
            cleanupTempFile(tempFilePath);
        }
    }

    @Override
    public final void reload(T pojoInstanceToUpdate) {
        if (!Files.exists(filePath)) {
            System.out.println("INFO: Configuration file '" + filePath + "' not found on reload attempt. Current values in instance remain.");
            return;
        }

        try {
            if (tryLoadFromFile(pojoInstanceToUpdate)) {
                System.out.println("INFO: Configuration reloaded into existing instance from " + filePath);
            } else {
                System.out.println("INFO: Configuration file '" + filePath + "' was empty on reload. Current values in instance remain (likely defaults).");
            }
        } catch (Exception e) {
            throw new ConfigurationException("Error reloading configuration from " + filePath, e);
        }
    }

    // ==================== SHARED UTILITIES ====================

    /**
     * Converts numeric values between different Number types if needed.
     */
    protected final Object convertNumericIfNeeded(Object value, Class<?> targetType) {
        if (value == null || targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        if (value instanceof Number) {
            Function<Number, Object> converter = NUMERIC_CONVERTERS.get(targetType);
            return converter != null ? converter.apply((Number) value) : value;
        }

        return value;
    }

    /**
     * Applies data from parsed format to POJO instance using reflection.
     */
    protected final void applyDataToInstance(T target, DataExtractor extractor) {
        List<Field> fields = ClassUtils.getAllFieldsInHierarchy(target.getClass(), ConfigurablePojo.class);

        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }

            Key keyAnnotation = field.getAnnotation(Key.class);
            if (keyAnnotation == null) continue;

            String key = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

            if (extractor.hasValue(key)) {
                try {
                    field.setAccessible(true);
                    Object value = extractor.getValue(key, field.getType());

                    if (value != null) {
                        Object convertedValue = convertNumericIfNeeded(value, field.getType());
                        field.set(target, convertedValue);
                    }
                } catch (IllegalAccessException e) {
                    System.err.println("WARNING: Could not set field '" + field.getName() + "' during data application: " + e.getMessage());
                } catch (IllegalArgumentException e) {
                    System.err.println("WARNING: Type conversion failed for field '" + field.getName() + "': " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("WARNING: Error processing field '" + field.getName() + "': " + e.getMessage());
                }
            }
        }
    }

    /**
     * Helper for cleaning up temp files.
     */
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