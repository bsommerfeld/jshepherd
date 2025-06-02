package de.bsommerfeld.jshepherd.core.json;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.CommentSection;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.ConfigurationException;
import de.bsommerfeld.jshepherd.core.PersistenceDelegate;
import de.bsommerfeld.jshepherd.utils.ClassUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Supplier;

/**
 * Implementation of PersistenceDelegate for JSON format using Jackson.
 * This class handles loading, saving, and reloading of configuration objects in JSON format.
 * Note: JSON format does not support comments natively, so comment annotations are ignored.
 *
 * @param <T> The type of ConfigurablePojo this delegate handles.
 */
class JsonPersistenceDelegate<T extends ConfigurablePojo<T>> implements PersistenceDelegate<T> {
    private final Path filePath;
    private final boolean useComplexSaveWithComments;
    private final ObjectMapper objectMapper;
    private String lastCommentSectionHash = null;

    JsonPersistenceDelegate(Path filePath, boolean useComplexSaveWithComments) {
        this.filePath = filePath;
        this.useComplexSaveWithComments = useComplexSaveWithComments;

        // Configure ObjectMapper for pretty printing and better formatting
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Warn user if comments are requested but not supported
        if (useComplexSaveWithComments) {
            System.out.println("WARNING: JSON format does not support comments natively. " +
                    "Comment annotations (@Comment, @CommentSection) will be ignored.");
        }
    }

    @Override
    public T loadInitial(Supplier<T> defaultPojoSupplier) {
        boolean fileExisted = Files.exists(filePath);

        if (fileExisted) {
            try {
                T defaultInstance = defaultPojoSupplier.get();

                // Try to parse JSON
                Map<String, Object> jsonData = objectMapper.readValue(filePath.toFile(), Map.class);

                if (jsonData != null && !jsonData.isEmpty()) {
                    applyJsonDataToInstance(defaultInstance, jsonData);
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

    private void applyJsonDataToInstance(T target, Map<String, Object> jsonMap) {
        List<Field> fields = ClassUtils.getAllFieldsInHierarchy(target.getClass(), ConfigurablePojo.class);
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }

            Key keyAnnotation = field.getAnnotation(Key.class);
            if (keyAnnotation == null) continue;

            String jsonKey = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

            if (jsonMap.containsKey(jsonKey)) {
                try {
                    field.setAccessible(true);
                    Object jsonValue = jsonMap.get(jsonKey);

                    if (jsonValue != null) {
                        Object convertedValue = convertNumericTypeIfNeeded(jsonValue, field.getType());
                        field.set(target, convertedValue);
                    }
                } catch (IllegalAccessException e) {
                    System.err.println("WARNING: Could not set field '" + field.getName() + "' during JSON application: " + e.getMessage());
                } catch (IllegalArgumentException e) {
                    System.err.println("WARNING: Type mismatch for field '" + field.getName() + "': " + e.getMessage());
                }
            }
        }
    }

    /**
     * Converts the given value to a target numeric type if the type differs and is compatible.
     * This method ensures that numeric types can be safely converted to the desired numeric target type.
     * If the value is null or already matches the target type, it is returned as is.
     *
     * @param value      The value to be converted. It is expected to be either null or an instance of a numeric type.
     * @param targetType The class of the desired target numeric type (e.g., Integer.class, float.class).
     *                   The target type must be a numeric type for the conversion to occur.
     * @return The converted value as an instance of the target numeric type if conversion is required and possible.
     *         If no conversion is needed, the original value is returned. Returns null if the input value is null.
     */
    private Object convertNumericTypeIfNeeded(Object value, Class<?> targetType) {
        if (value == null) return null;

        // No conversion needed when types fit
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        if (value instanceof Number && isNumericType(targetType)) {
            Number numValue = (Number) value;

            if (targetType == float.class || targetType == Float.class) {
                return numValue.floatValue();
            } else if (targetType == double.class || targetType == Double.class) {
                return numValue.doubleValue();
            } else if (targetType == int.class || targetType == Integer.class) {
                return numValue.intValue();
            } else if (targetType == long.class || targetType == Long.class) {
                return numValue.longValue();
            } else if (targetType == short.class || targetType == Short.class) {
                return numValue.shortValue();
            } else if (targetType == byte.class || targetType == Byte.class) {
                return numValue.byteValue();
            }
        }

        return value;
    }

    private boolean isNumericType(Class<?> type) {
        return type == int.class || type == Integer.class ||
                type == long.class || type == Long.class ||
                type == float.class || type == Float.class ||
                type == double.class || type == Double.class ||
                type == short.class || type == Short.class ||
                type == byte.class || type == Byte.class;
    }

    @Override
    public void save(T pojoInstance) {
        Path parentDir = filePath.getParent();
        Path tempFilePath = null;
        try {
            if (parentDir != null) Files.createDirectories(parentDir);
            tempFilePath = Files.createTempFile(parentDir != null ? parentDir : Paths.get("."), filePath.getFileName().toString(), ".tmpjhp");

            if (useComplexSaveWithComments) {
                saveWithDocumentationComments(pojoInstance, tempFilePath);
            } else {
                saveSimpleDump(pojoInstance, tempFilePath);
            }
            Files.move(tempFilePath, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            System.out.println("INFO: Configuration saved to " + filePath);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to save configuration to " + filePath, e);
        } finally {
            if (tempFilePath != null && Files.exists(tempFilePath)) {
                try {
                    Files.delete(tempFilePath);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void saveSimpleDump(T pojoInstance, Path targetPath) throws IOException {
        // Create a map with only @Key annotated fields
        Map<String, Object> jsonMap = createJsonMap(pojoInstance);

        try (Writer writer = Files.newBufferedWriter(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            objectMapper.writeValue(writer, jsonMap);
        }
    }

    private void saveWithDocumentationComments(T pojoInstance, Path targetPath) throws IOException {
        // Save the JSON file normally
        saveSimpleDump(pojoInstance, targetPath);

        // Create documentation file based on the FINAL file path, not the temp path
        // This ensures we always have exactly one documentation file with a predictable name
        String originalFileName = filePath.getFileName().toString();
        String baseName = originalFileName.contains(".")
                ? originalFileName.substring(0, originalFileName.lastIndexOf('.'))
                : originalFileName;

        Path finalDocPath = filePath.getParent() != null
                ? filePath.getParent().resolve(baseName + "-config-documentation.md")
                : Paths.get(baseName + "-config-documentation.md");

        try (PrintWriter docWriter = new PrintWriter(Files.newBufferedWriter(finalDocPath,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            generateDocumentationFile(pojoInstance, docWriter);
            System.out.println("INFO: Configuration documentation saved to " + finalDocPath);
        }
    }

    private void generateDocumentationFile(T pojoInstance, PrintWriter writer) {
        String configFileName = filePath.getFileName().toString();

        writer.println("# Configuration Documentation");
        writer.println();
        writer.println("**Configuration File:** `" + configFileName + "`  ");
        writer.println("**Format:** JSON  ");
        writer.println("**Generated:** " + java.time.LocalDateTime.now() + "  ");
        writer.println();
        writer.println("---");
        writer.println();

        Comment classComment = pojoInstance.getClass().getAnnotation(Comment.class);
        if (classComment != null && classComment.value().length > 0) {
            writer.println("## General Information");
            for (String line : classComment.value()) {
                writer.println(line);
            }
            writer.println();
        }

        this.lastCommentSectionHash = null;

        List<Field> fields = ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
        boolean hasAnyDocumentedFields = false;

        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }

            Key keyAnnotation = field.getAnnotation(Key.class);
            if (keyAnnotation == null) continue;

            String jsonKey = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

            CommentSection sectionAnnotation = field.getAnnotation(CommentSection.class);
            if (sectionAnnotation != null && sectionAnnotation.value().length > 0) {
                String currentSectionHash = String.join("|", sectionAnnotation.value());
                if (!currentSectionHash.equals(this.lastCommentSectionHash)) {
                    if (hasAnyDocumentedFields) writer.println(); // Spacing between sections
                    writer.println("## " + String.join(" / ", sectionAnnotation.value()));
                    writer.println();
                    this.lastCommentSectionHash = currentSectionHash;
                }
            }

            Comment fieldComment = field.getAnnotation(Comment.class);
            if (fieldComment != null && fieldComment.value().length > 0) {
                writer.println("### `" + jsonKey + "`");
                writer.println();
                for (String commentLine : fieldComment.value()) {
                    writer.println(commentLine);
                }
                writer.println();
                writer.println("**Type:** `" + field.getType().getSimpleName() + "`  ");

                try {
                    field.setAccessible(true);
                    Object currentValue = field.get(pojoInstance);
                    if (currentValue != null) {
                        writer.println("**Current Value:** `" + currentValue + "`  ");
                    }
                } catch (IllegalAccessException e) {
                    // Ignore if we can't access the field
                }

                writer.println();
                hasAnyDocumentedFields = true;
            }
        }

        if (!hasAnyDocumentedFields) {
            writer.println("## Configuration Fields");
            writer.println();
            writer.println("No documented configuration fields found. The configuration uses default field names and values.");
            writer.println();
        }

        writer.println("---");
        writer.println();
        writer.println("*This documentation was automatically generated by JShepherd.*");
    }

    private Map<String, Object> createJsonMap(T pojoInstance) {
        Map<String, Object> jsonMap = new LinkedHashMap<>();

        List<Field> fields = ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }

            Key keyAnnotation = field.getAnnotation(Key.class);
            if (keyAnnotation == null) continue;

            String jsonKey = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

            try {
                field.setAccessible(true);
                Object value = field.get(pojoInstance);

                if (value != null) {
                    jsonMap.put(jsonKey, value);
                }
                // Note: JSON naturally handles null values, no need to add explicit nulls
            } catch (IllegalAccessException e) {
                System.err.println("ERROR: Could not access field " + field.getName() + " during save.");
            }
        }

        return jsonMap;
    }

    @Override
    public void reload(T pojoInstanceToUpdate) {
        if (!Files.exists(filePath)) {
            System.out.println("INFO: Configuration file '" + filePath + "' not found on reload attempt. Current values in instance remain.");
            return;
        }

        try {
            Map<String, Object> jsonData = objectMapper.readValue(filePath.toFile(), Map.class);

            if (jsonData != null && !jsonData.isEmpty()) {
                applyJsonDataToInstance(pojoInstanceToUpdate, jsonData);
                System.out.println("INFO: Configuration reloaded into existing instance from " + filePath);
            } else {
                System.out.println("INFO: Configuration file '" + filePath + "' was empty on reload. Current values in instance remain (likely defaults).");
            }
        } catch (IOException e) {
            throw new ConfigurationException("Error reloading configuration from " + filePath, e);
        } catch (Exception e) {
            throw new ConfigurationException("Error parsing JSON during reload from " + filePath, e);
        }
    }
}