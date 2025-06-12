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
 */
class JsonPersistenceDelegate<T extends ConfigurablePojo<T>> extends AbstractPersistenceDelegate<T> {
    private final ObjectMapper objectMapper;

    JsonPersistenceDelegate(Path filePath, boolean useComplexSaveWithComments) {
        super(filePath, useComplexSaveWithComments);

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
    protected boolean tryLoadFromFile(T instance) throws Exception {
        Map<String, Object> jsonData = objectMapper.readValue(filePath.toFile(), Map.class);

        if (jsonData != null && !jsonData.isEmpty()) {
            applyDataToInstance(instance, new JsonDataExtractor(jsonData));
            return true;
        }
        return false;
    }

    @Override
    protected void saveSimple(T pojoInstance, Path targetPath) throws IOException {
        Map<String, Object> jsonMap = createJsonMap(pojoInstance);

        try (Writer writer = Files.newBufferedWriter(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            objectMapper.writeValue(writer, jsonMap);
        }
    }

    @Override
    protected void saveWithComments(T pojoInstance, Path targetPath) throws IOException {
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
                    if (hasAnyDocumentedFields) writer.println();
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
            writer.println("*No documented fields found. Use @Comment annotations for field documentation.*");
        }
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
            } catch (IllegalAccessException e) {
                System.err.println("ERROR: Could not access field " + field.getName() + " during JSON map creation.");
            }
        }

        return jsonMap;
    }

    // DataExtractor implementation for JSON
    private static class JsonDataExtractor implements DataExtractor {
        private final Map<String, Object> data;

        JsonDataExtractor(Map<String, Object> data) {
            this.data = data;
        }

        @Override
        public boolean hasValue(String key) {
            return data.containsKey(key);
        }

        @Override
        public Object getValue(String key, Class<?> targetType) {
            return data.get(key);
        }
    }
}