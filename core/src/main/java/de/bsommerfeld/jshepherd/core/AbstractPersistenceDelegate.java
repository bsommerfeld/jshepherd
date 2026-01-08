package de.bsommerfeld.jshepherd.core;

import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.utils.ClassUtils;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract base class for all persistence delegates.
 * Provides common functionality for file operations, type conversion, and
 * reflection-based data mapping.
 */
public abstract class AbstractPersistenceDelegate<T extends ConfigurablePojo<T>> implements PersistenceDelegate<T> {

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

    protected final Path filePath;
    protected final boolean useComplexSaveWithComments;
    protected String lastCommentSectionHash = null;

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
                    LOGGER.info(() -> "Configuration loaded from " + filePath);
                    return defaultInstance;
                } else {
                    LOGGER.info(() -> "Config file '" + filePath + "' was empty. Using defaults.");
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Initial load/parse of '" + filePath + "' failed. Using defaults.", e);
            }
        } else {
            LOGGER.info(() -> "Config file '" + filePath + "' not found. Creating with defaults.");
        }

        T defaultInstance = defaultPojoSupplier.get();
        LOGGER.info(() -> "Saving initial/default configuration to: " + filePath);
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
            LOGGER.info(() -> "Configuration saved to " + filePath);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to save configuration to " + filePath, e);
        } finally {
            cleanupTempFile(tempFilePath);
        }
    }

    @Override
    public final void reload(T pojoInstanceToUpdate) {
        if (!Files.exists(filePath)) {
            LOGGER.info(() -> "Configuration file '" + filePath
                    + "' not found on reload attempt. Current values in instance remain.");
            return;
        }

        try {
            if (tryLoadFromFile(pojoInstanceToUpdate)) {
                LOGGER.info(() -> "Configuration reloaded into existing instance from " + filePath);
            } else {
                LOGGER.info(() -> "Configuration file '" + filePath
                        + "' was empty on reload. Current values in instance remain.");
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
            if (shouldSkipField(field)) {
                continue;
            }

            Key keyAnnotation = field.getAnnotation(Key.class);
            if (keyAnnotation == null)
                continue;

            String key = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

            if (extractor.hasValue(key)) {
                try {
                    field.setAccessible(true);
                    Object value = extractor.getValue(key, field.getType());

                    if (value != null) {
                        Object convertedValue = convertNumericIfNeeded(value, field.getType());
                        field.set(target, convertedValue);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error processing field '" + field.getName() + "'", e);
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

    /**
     * Checks if a field should be skipped during persistence operations.
     * Skips static and transient fields.
     */
    protected final boolean shouldSkipField(Field field) {
        return Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers());
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