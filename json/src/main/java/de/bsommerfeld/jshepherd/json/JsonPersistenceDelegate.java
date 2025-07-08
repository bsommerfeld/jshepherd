package de.bsommerfeld.jshepherd.json;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.CommentSection;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.core.AbstractPersistenceDelegate;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.utils.ClassUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Implementation of PersistenceDelegate for JSON format using Jackson.
 * This class handles loading, saving, and reloading of configuration objects in JSON format.
 * Note: JSON format does not support comments natively, so comment annotations are ignored.
 * Instead, a separate Markdown documentation file is generated when comments are requested.
 */
class JsonPersistenceDelegate<T extends ConfigurablePojo<T>> extends AbstractPersistenceDelegate<T> {
    private static final String LOG_PREFIX = "[JSON] ";
    private final ObjectMapper objectMapper;
    private String lastCommentSectionHash;

    JsonPersistenceDelegate(Path filePath, boolean useComplexSaveWithComments) {
        super(filePath, useComplexSaveWithComments);

        // Configure ObjectMapper for pretty printing and better formatting
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Warn user if comments are requested but not supported
        if (useComplexSaveWithComments) {
            System.out.println(LOG_PREFIX + "WARNING: JSON format does not support comments natively. " +
                    "Comment annotations (@Comment, @CommentSection) will be ignored. " +
                    "A separate documentation file will be generated instead.");
        }
    }

    /**
     * Attempts to load configuration data from the JSON file into the provided instance.
     * 
     * @param instance The instance to load data into
     * @return true if data was successfully loaded, false otherwise
     * @throws Exception if an error occurs during loading
     */
    @Override
    protected boolean tryLoadFromFile(T instance) throws Exception {
        try {
            Map<String, Object> jsonData = objectMapper.readValue(filePath.toFile(), Map.class);

            if (jsonData != null && !jsonData.isEmpty()) {
                applyDataToInstance(instance, new JsonDataExtractor(jsonData));
                return true;
            }
            return false;
        } catch (IOException e) {
            System.err.println(LOG_PREFIX + "ERROR: Failed to load JSON file: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Saves the configuration data to a JSON file without comments.
     * 
     * @param pojoInstance The instance to save
     * @param targetPath The path to save the file to
     * @throws IOException if an error occurs during saving
     */
    @Override
    protected void saveSimple(T pojoInstance, Path targetPath) throws IOException {
        try {
            Map<String, Object> jsonMap = createJsonMap(pojoInstance);

            try (Writer writer = Files.newBufferedWriter(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                objectMapper.writeValue(writer, jsonMap);
            }
        } catch (IOException e) {
            System.err.println(LOG_PREFIX + "ERROR: Failed to save JSON file: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Saves the configuration data to a JSON file and generates a separate Markdown documentation file
     * containing comments and field information.
     * 
     * @param pojoInstance The instance to save
     * @param targetPath The path to save the JSON file to
     * @throws IOException if an error occurs during saving
     */
    @Override
    protected void saveWithComments(T pojoInstance, Path targetPath) throws IOException {
        try {
            // Save the JSON file normally
            saveSimple(pojoInstance, targetPath);

            // Create documentation file based on the FINAL file path, not the temp path
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
                System.out.println(LOG_PREFIX + "INFO: Configuration documentation saved to " + finalDocPath);
            }
        } catch (IOException e) {
            System.err.println(LOG_PREFIX + "ERROR: Failed to save documentation file: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Generates a Markdown documentation file for the configuration.
     * This file contains all comments and field information from the POJO.
     * 
     * @param pojoInstance The instance to document
     * @param writer The PrintWriter to write the documentation to
     */
    private void generateDocumentationFile(T pojoInstance, PrintWriter writer) {
        String configFileName = filePath.getFileName().toString();

        // Write header
        writer.println("# Configuration Documentation");
        writer.println();
        writer.println("**Configuration File:** `" + configFileName + "`  ");
        writer.println("**Format:** JSON  ");
        writer.println("**Generated:** " + java.time.LocalDateTime.now() + "  ");
        writer.println();
        writer.println("---");
        writer.println();

        // Write class-level comments if present
        Comment classComment = pojoInstance.getClass().getAnnotation(Comment.class);
        if (classComment != null && classComment.value().length > 0) {
            writer.println("## General Information");
            for (String line : classComment.value()) {
                writer.println(line);
            }
            writer.println();
        }

        this.lastCommentSectionHash = null;

        // Get all fields from the class hierarchy
        List<Field> fields = ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
        boolean hasAnyDocumentedFields = false;

        // Process each field
        for (Field field : fields) {
            // Skip static and transient fields
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }

            Key keyAnnotation = field.getAnnotation(Key.class);
            if (keyAnnotation == null) continue;

            String jsonKey = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

            // Handle section comments
            CommentSection sectionAnnotation = field.getAnnotation(CommentSection.class);
            if (sectionAnnotation != null && sectionAnnotation.value().length > 0) {
                String currentSectionHash = String.join("|", sectionAnnotation.value());
                if (!currentSectionHash.equals(this.lastCommentSectionHash)) {
                    if (hasAnyDocumentedFields) writer.println();
                    writer.println("## " + String.join(" / ", sectionAnnotation.value()));
                    writer.println();
                    this.lastCommentSectionHash = currentSectionHash;
                }
            }

            // Handle field comments
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
                    System.err.println(LOG_PREFIX + "WARNING: Could not access field " + field.getName() + 
                            " for documentation: " + e.getMessage());
                }

                writer.println();
                hasAnyDocumentedFields = true;
            }
        }

        // If no documented fields were found, add a note
        if (!hasAnyDocumentedFields) {
            writer.println("## Configuration Fields");
            writer.println();
            writer.println("*No documented fields found. Use @Comment annotations for field documentation.*");
        }
    }

    /**
     * Creates a map of key-value pairs from the POJO instance for JSON serialization.
     * 
     * @param pojoInstance The instance to extract data from
     * @return A map of key-value pairs representing the POJO's data
     */
    private Map<String, Object> createJsonMap(T pojoInstance) {
        Map<String, Object> jsonMap = new LinkedHashMap<>();

        // Get all fields from the class hierarchy
        List<Field> fields = ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);

        // Process each field
        for (Field field : fields) {
            // Skip static and transient fields
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
            } catch (IllegalAccessException e) {
                System.err.println(LOG_PREFIX + "ERROR: Could not access field " + field.getName() + 
                        " during JSON map creation: " + e.getMessage());
            }
        }

        return jsonMap;
    }

    /**
     * Implementation of DataExtractor for JSON data.
     * This class extracts values from a JSON map for applying to a POJO instance.
     */
    private static class JsonDataExtractor implements DataExtractor {
        private final Map<String, Object> data;

        /**
         * Creates a new JsonDataExtractor with the given data map.
         * 
         * @param data The JSON data map
         */
        JsonDataExtractor(Map<String, Object> data) {
            this.data = data;
        }

        /**
         * Checks if the JSON data contains a value for the given key.
         * 
         * @param key The key to check
         * @return true if the key exists in the data, false otherwise
         */
        @Override
        public boolean hasValue(String key) {
            return data.containsKey(key);
        }

        /**
         * Gets the value for the given key from the JSON data.
         * 
         * @param key The key to get the value for
         * @param targetType The expected type of the value
         * @return The value from the JSON data, or null if not found
         */
        @Override
        public Object getValue(String key, Class<?> targetType) {
            return data.get(key);
        }
    }
}
