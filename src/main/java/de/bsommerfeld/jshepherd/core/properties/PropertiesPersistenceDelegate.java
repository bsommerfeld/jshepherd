package de.bsommerfeld.jshepherd.core.properties;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.CommentSection;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.ConfigurationException;
import de.bsommerfeld.jshepherd.core.PersistenceDelegate;
import de.bsommerfeld.jshepherd.utils.ClassUtils;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

/**
 * Implementation of PersistenceDelegate for Java Properties format.
 * This class handles loading, saving, and reloading of configuration objects in Properties format.
 *
 * Note: Properties format has limitations:
 * - Only supports String key-value pairs (complex objects are serialized as strings)
 * - Lists and Maps are serialized using comma-separated values and key=value notation
 * - Comments are supported using # prefix
 *
 * @param <T> The type of ConfigurablePojo this delegate handles.
 */
class PropertiesPersistenceDelegate<T extends ConfigurablePojo<T>> implements PersistenceDelegate<T> {
    private final Path filePath;
    private final boolean useComplexSaveWithComments;
    private String lastCommentSectionHash = null;

    // Date formatters for Properties serialization
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_TIME;

    PropertiesPersistenceDelegate(Path filePath, boolean useComplexSaveWithComments) {
        this.filePath = filePath;
        this.useComplexSaveWithComments = useComplexSaveWithComments;
    }

    @Override
    public T loadInitial(Supplier<T> defaultPojoSupplier) {
        boolean fileExisted = Files.exists(filePath);

        if (fileExisted) {
            try {
                T defaultInstance = defaultPojoSupplier.get();

                // Try to parse Properties
                Properties properties = new Properties();
                try (InputStream inputStream = Files.newInputStream(filePath)) {
                    properties.load(inputStream);
                }

                if (!properties.isEmpty()) {
                    applyPropertiesDataToInstance(defaultInstance, properties);
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

    private void applyPropertiesDataToInstance(T target, Properties properties) {
        List<Field> fields = ClassUtils.getAllFieldsInHierarchy(target.getClass(), ConfigurablePojo.class);
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }

            Key keyAnnotation = field.getAnnotation(Key.class);
            if (keyAnnotation == null) continue;

            String propKey = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

            if (properties.containsKey(propKey)) {
                try {
                    field.setAccessible(true);
                    String propValue = properties.getProperty(propKey);

                    if (propValue != null && !propValue.trim().isEmpty()) {
                        Object convertedValue = convertStringToFieldType(propValue, field.getType());
                        field.set(target, convertedValue);
                    }
                } catch (IllegalAccessException e) {
                    System.err.println("WARNING: Could not set field '" + field.getName() + "' during Properties application: " + e.getMessage());
                } catch (IllegalArgumentException e) {
                    System.err.println("WARNING: Type conversion failed for field '" + field.getName() + "': " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("WARNING: Error processing field '" + field.getName() + "': " + e.getMessage());
                }
            }
        }
    }

    private Object convertStringToFieldType(String value, Class<?> targetType) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        value = value.trim();

        try {
            if (targetType == String.class) {
                return value;
            } else if (targetType == Integer.class || targetType == int.class) {
                return Integer.parseInt(value);
            } else if (targetType == Long.class || targetType == long.class) {
                return Long.parseLong(value);
            } else if (targetType == Double.class || targetType == double.class) {
                return Double.parseDouble(value);
            } else if (targetType == Float.class || targetType == float.class) {
                return Float.parseFloat(value);
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                return Boolean.parseBoolean(value);
            } else if (targetType == Short.class || targetType == short.class) {
                return Short.parseShort(value);
            } else if (targetType == Byte.class || targetType == byte.class) {
                return Byte.parseByte(value);
            } else if (targetType == LocalDate.class) {
                return LocalDate.parse(value, DATE_FORMATTER);
            } else if (targetType == LocalDateTime.class) {
                return LocalDateTime.parse(value, DATETIME_FORMATTER);
            } else if (targetType == LocalTime.class) {
                return LocalTime.parse(value, TIME_FORMATTER);
            } else if (List.class.isAssignableFrom(targetType)) {
                // Parse comma-separated values as List
                if (value.startsWith("[") && value.endsWith("]")) {
                    value = value.substring(1, value.length() - 1);
                }
                if (value.trim().isEmpty()) {
                    return new ArrayList<>();
                }
                return Arrays.asList(value.split(",\\s*"));
            } else if (Map.class.isAssignableFrom(targetType)) {
                // Parse key=value pairs as Map
                Map<String, String> map = new HashMap<>();
                if (value.startsWith("{") && value.endsWith("}")) {
                    value = value.substring(1, value.length() - 1);
                }
                if (!value.trim().isEmpty()) {
                    String[] pairs = value.split(",\\s*");
                    for (String pair : pairs) {
                        String[] keyValue = pair.split("=", 2);
                        if (keyValue.length == 2) {
                            map.put(keyValue[0].trim(), keyValue[1].trim());
                        }
                    }
                }
                return map;
            } else {
                // For other types, return as string
                return value;
            }
        } catch (Exception e) {
            System.err.println("WARNING: Failed to convert value '" + value + "' to type " + targetType.getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public void save(T pojoInstance) {
        Path parentDir = filePath.getParent();
        Path tempFilePath = null;
        try {
            if (parentDir != null) Files.createDirectories(parentDir);
            tempFilePath = Files.createTempFile(parentDir != null ? parentDir : Paths.get("."), filePath.getFileName().toString(), ".tmpjhp");

            if (useComplexSaveWithComments) {
                saveWithAnnotationDrivenComments(pojoInstance, tempFilePath);
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
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            Comment classComment = pojoInstance.getClass().getAnnotation(Comment.class);
            if (classComment != null && classComment.value().length > 0) {
                for (String line : classComment.value()) {
                    writer.println("# " + line);
                }
                writer.println();
            }

            // Convert POJO to Properties format
            Properties properties = createPropertiesFromPojo(pojoInstance);

            // Write properties in sorted order for consistency
            List<String> sortedKeys = new ArrayList<>(properties.stringPropertyNames());
            Collections.sort(sortedKeys);

            for (String key : sortedKeys) {
                String value = properties.getProperty(key);
                writer.println(escapePropertyKey(key) + "=" + escapePropertyValue(value));
            }
        }
    }

    private void saveWithAnnotationDrivenComments(T pojoInstance, Path targetPath) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            this.lastCommentSectionHash = null;

            Comment classComment = pojoInstance.getClass().getAnnotation(Comment.class);
            if (classComment != null && classComment.value().length > 0) {
                for (String line : classComment.value()) writer.println("# " + line);
                writer.println();
            }

            List<Field> fields = ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
            for (int fieldIdx = 0; fieldIdx < fields.size(); fieldIdx++) {
                Field field = fields.get(fieldIdx);
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Key keyAnnotation = field.getAnnotation(Key.class);
                if (keyAnnotation == null) continue;

                String propKey = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

                CommentSection sectionAnnotation = field.getAnnotation(CommentSection.class);
                if (sectionAnnotation != null && sectionAnnotation.value().length > 0) {
                    String currentSectionHash = String.join("|", sectionAnnotation.value());
                    if (!currentSectionHash.equals(this.lastCommentSectionHash)) {
                        if (this.lastCommentSectionHash != null)
                            writer.println(); // Blank line before new section unless it's the very first thing after header
                        for (String commentLine : sectionAnnotation.value()) writer.println("# " + commentLine);
                        this.lastCommentSectionHash = currentSectionHash;
                    }
                }

                Comment fieldComment = field.getAnnotation(Comment.class);
                if (fieldComment != null && fieldComment.value().length > 0) {
                    for (String commentLine : fieldComment.value()) writer.println("# " + commentLine);
                }

                try {
                    Object value = field.get(pojoInstance);
                    if (value != null) {
                        String stringValue = convertFieldValueToString(value);
                        writer.println(escapePropertyKey(propKey) + "=" + escapePropertyValue(stringValue));
                    }
                    // Skip null values in Properties
                } catch (IllegalAccessException e) {
                    System.err.println("ERROR: Could not access field " + field.getName() + " during save.");
                    continue;
                }

                // Logic for blank line after entry
                boolean addBlankLine = false;
                if (fieldIdx < fields.size() - 1) {
                    for (int k = fieldIdx + 1; k < fields.size(); k++) {
                        Field nextField = fields.get(k);
                        if (Modifier.isStatic(nextField.getModifiers()) || Modifier.isTransient(nextField.getModifiers()))
                            continue;
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

    private Properties createPropertiesFromPojo(T pojoInstance) {
        Properties properties = new Properties();

        List<Field> fields = ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }

            Key keyAnnotation = field.getAnnotation(Key.class);
            if (keyAnnotation == null) continue;

            String propKey = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

            try {
                field.setAccessible(true);
                Object value = field.get(pojoInstance);

                if (value != null) {
                    String stringValue = convertFieldValueToString(value);
                    properties.setProperty(propKey, stringValue);
                }
            } catch (IllegalAccessException e) {
                System.err.println("ERROR: Could not access field " + field.getName() + " during properties creation.");
            }
        }

        return properties;
    }

    private String convertFieldValueToString(Object value) {
        if (value == null) {
            return "";
        }

        if (value instanceof String) {
            return (String) value;
        } else if (value instanceof LocalDate) {
            return ((LocalDate) value).format(DATE_FORMATTER);
        } else if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(DATETIME_FORMATTER);
        } else if (value instanceof LocalTime) {
            return ((LocalTime) value).format(TIME_FORMATTER);
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                return "[]";
            }
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(list.get(i).toString());
            }
            sb.append("]");
            return sb.toString();
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            if (map.isEmpty()) {
                return "{}";
            }
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
            sb.append("}");
            return sb.toString();
        } else {
            return value.toString();
        }
    }

    private String escapePropertyKey(String key) {
        return key.replace(" ", "\\ ")
                .replace(":", "\\:")
                .replace("=", "\\=")
                .replace("#", "\\#")
                .replace("!", "\\!");
    }

    private String escapePropertyValue(String value) {
        return value.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\f", "\\f");
    }

    @Override
    public void reload(T pojoInstanceToUpdate) {
        if (!Files.exists(filePath)) {
            System.out.println("INFO: Configuration file '" + filePath + "' not found on reload attempt. Current values in instance remain.");
            return;
        }

        try {
            Properties properties = new Properties();
            try (InputStream inputStream = Files.newInputStream(filePath)) {
                properties.load(inputStream);
            }

            if (!properties.isEmpty()) {
                applyPropertiesDataToInstance(pojoInstanceToUpdate, properties);
                System.out.println("INFO: Configuration reloaded into existing instance from " + filePath);
            } else {
                System.out.println("INFO: Configuration file '" + filePath + "' was empty on reload. Current values in instance remain (likely defaults).");
            }
        } catch (IOException e) {
            throw new ConfigurationException("Error reloading configuration from " + filePath, e);
        } catch (Exception e) {
            throw new ConfigurationException("Error parsing Properties during reload from " + filePath, e);
        }
    }
}