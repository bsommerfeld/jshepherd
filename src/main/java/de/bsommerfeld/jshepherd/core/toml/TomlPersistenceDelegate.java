package de.bsommerfeld.jshepherd.core.toml;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.CommentSection;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.ConfigurationException;
import de.bsommerfeld.jshepherd.core.PersistenceDelegate;
import de.bsommerfeld.jshepherd.utils.ClassUtils;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

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
import java.util.*;
import java.util.function.Supplier;

/**
 * Implementation of PersistenceDelegate for TOML format using TOMLJ.
 * This class handles loading, saving, and reloading of configuration objects in TOML format.
 *
 * @param <T> The type of ConfigurablePojo this delegate handles.
 */
class TomlPersistenceDelegate<T extends ConfigurablePojo<T>> implements PersistenceDelegate<T> {
    private final Path filePath;
    private final boolean useComplexSaveWithComments;
    private String lastCommentSectionHash = null;

    TomlPersistenceDelegate(Path filePath, boolean useComplexSaveWithComments) {
        this.filePath = filePath;
        this.useComplexSaveWithComments = useComplexSaveWithComments;
    }

    @Override
    public T loadInitial(Supplier<T> defaultPojoSupplier) {
        boolean fileExisted = Files.exists(filePath);

        if (fileExisted) {
            try {
                T defaultInstance = defaultPojoSupplier.get();

                // Try to parse TOML
                TomlParseResult tomlResult = Toml.parse(filePath);

                if (tomlResult != null && !tomlResult.isEmpty()) {
                    applyTomlDataToInstance(defaultInstance, tomlResult);
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

    private void applyTomlDataToInstance(T target, TomlParseResult tomlData) {
        List<Field> fields = ClassUtils.getAllFieldsInHierarchy(target.getClass(), ConfigurablePojo.class);
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }

            Key keyAnnotation = field.getAnnotation(Key.class);
            if (keyAnnotation == null) continue;

            String tomlKey = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

            if (tomlData.contains(tomlKey)) {
                try {
                    field.setAccessible(true);
                    Object tomlValue = getTomlValue(tomlData, tomlKey, field.getType());

                    if (tomlValue != null) {
                        Object convertedValue = convertNumericTypeIfNeeded(tomlValue, field.getType());
                        field.set(target, convertedValue);
                    }
                } catch (IllegalAccessException e) {
                    System.err.println("WARNING: Could not set field '" + field.getName() + "' during TOML application: " + e.getMessage());
                } catch (IllegalArgumentException e) {
                    System.err.println("WARNING: Type mismatch for field '" + field.getName() + "': " + e.getMessage());
                }
            }
        }
    }

    private Object getTomlValue(TomlParseResult tomlData, String key, Class<?> targetType) {
        if (targetType == String.class) {
            return tomlData.getString(key);
        } else if (targetType == Integer.class || targetType == int.class) {
            Long value = tomlData.getLong(key);
            return value != null ? value.intValue() : null;
        } else if (targetType == Long.class || targetType == long.class) {
            return tomlData.getLong(key);
        } else if (targetType == Double.class || targetType == double.class) {
            return tomlData.getDouble(key);
        } else if (targetType == Float.class || targetType == float.class) {
            Double value = tomlData.getDouble(key);
            return value != null ? value.floatValue() : null;
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            return tomlData.getBoolean(key);
        } else if (targetType == LocalDate.class) {
            return tomlData.getLocalDate(key);
        } else if (targetType == LocalDateTime.class) {
            return tomlData.getLocalDateTime(key);
        } else if (targetType == LocalTime.class) {
            return tomlData.getLocalTime(key);
        } else if (targetType.isArray() || List.class.isAssignableFrom(targetType)) {
            TomlArray array = tomlData.getArray(key);
            if (array == null) return null;

            List<Object> list = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                list.add(array.get(i));
            }
            return list;
        } else if (Map.class.isAssignableFrom(targetType)) {
            TomlTable table = tomlData.getTable(key);
            if (table == null) return null;

            Map<String, Object> map = new HashMap<>();
            for (String tableKey : table.keySet()) {
                map.put(tableKey, table.get(tableKey));
            }
            return map;
        }

        // For other types, try to get as Object
        return tomlData.get(key);
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

            // Convert POJO to TOML format
            List<Field> fields = ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }

                Key keyAnnotation = field.getAnnotation(Key.class);
                if (keyAnnotation == null) continue;

                String tomlKey = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

                try {
                    field.setAccessible(true);
                    Object value = field.get(pojoInstance);

                    if (value != null) {
                        writeTomlValue(writer, tomlKey, value);
                    }
                    // Skip null values in TOML - they don't have a standard representation
                } catch (IllegalAccessException e) {
                    System.err.println("ERROR: Could not access field " + field.getName() + " during save.");
                }
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

                String tomlKey = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

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
                        writeTomlValue(writer, tomlKey, value);
                    }
                    // Skip null values in TOML
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

    private void writeTomlValue(PrintWriter writer, String key, Object value) {
        if (value instanceof String) {
            writer.println(key + " = \"" + escapeString((String) value) + "\"");
        } else if (value instanceof Number || value instanceof Boolean) {
            writer.println(key + " = " + value);
        } else if (value instanceof LocalDate) {
            writer.println(key + " = " + value);
        } else if (value instanceof LocalDateTime) {
            writer.println(key + " = " + value);
        } else if (value instanceof LocalTime) {
            writer.println(key + " = " + value);
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                writer.println(key + " = []");
            } else {
                writer.print(key + " = [");
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (item instanceof String) {
                        writer.print("\"" + escapeString((String) item) + "\"");
                    } else {
                        writer.print(item);
                    }
                    if (i < list.size() - 1) {
                        writer.print(", ");
                    }
                }
                writer.println("]");
            }
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            if (map.isEmpty()) {
                // For empty maps, we can use inline table syntax
                writer.println(key + " = {}");
            } else {
                // For non-empty maps, use TOML table syntax
                writer.println("[" + key + "]");
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object entryKey = entry.getKey();
                    Object entryValue = entry.getValue();
                    if (entryValue instanceof String) {
                        writer.println(entryKey + " = \"" + escapeString((String) entryValue) + "\"");
                    } else if (entryValue instanceof Number || entryValue instanceof Boolean) {
                        writer.println(entryKey + " = " + entryValue);
                    } else {
                        writer.println(entryKey + " = \"" + entryValue + "\"");
                    }
                }
            }
        } else {
            // For unknown types, convert to string and escape
            writer.println(key + " = \"" + escapeString(value.toString()) + "\"");
        }
    }

    private String escapeString(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public void reload(T pojoInstanceToUpdate) {
        if (!Files.exists(filePath)) {
            System.out.println("INFO: Configuration file '" + filePath + "' not found on reload attempt. Current values in instance remain.");
            return; // Fixed: Missing return statement
        }

        try {
            TomlParseResult tomlResult = Toml.parse(filePath);

            if (tomlResult != null && !tomlResult.isEmpty()) {
                applyTomlDataToInstance(pojoInstanceToUpdate, tomlResult);
                System.out.println("INFO: Configuration reloaded into existing instance from " + filePath);
            } else {
                System.out.println("INFO: Configuration file '" + filePath + "' was empty on reload. Current values in instance remain (likely defaults).");
            }
        } catch (IOException e) {
            throw new ConfigurationException("Error reloading configuration from " + filePath, e);
        } catch (Exception e) {
            throw new ConfigurationException("Error parsing TOML during reload from " + filePath, e);
        }
    }
}