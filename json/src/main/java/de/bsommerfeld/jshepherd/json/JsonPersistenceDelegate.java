package de.bsommerfeld.jshepherd.json;

import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of PersistenceDelegate for JSON format using Jackson.
 * This class handles loading, saving, and reloading of configuration objects in
 * JSON format.
 * Note: JSON format does not support comments natively, so comment annotations
 * are ignored.
 * Instead, a separate Markdown documentation file is generated when comments
 * are requested.
 */
class JsonPersistenceDelegate<T extends ConfigurablePojo<T>> extends AbstractPersistenceDelegate<T> {

    private static final Logger LOGGER = Logger.getLogger(JsonPersistenceDelegate.class.getName());

    private final ObjectMapper objectMapper;

    // Used for markdown documentation generation - tracks section headers
    private String lastDocSectionHash = null;

    JsonPersistenceDelegate(Path filePath, boolean useComplexSaveWithComments) {
        super(filePath, useComplexSaveWithComments);

        // Configure ObjectMapper for pretty printing and better formatting
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        this.objectMapper.setAnnotationIntrospector(new JShepherdAnnotationIntrospector());

        // Warn user if comments are requested but not supported
        if (useComplexSaveWithComments) {
            LOGGER.info(
                    "JSON format does not support comments natively. Comment annotations will be ignored. A separate documentation file will be generated instead.");
        }
    }

    @Override
    protected boolean tryLoadFromFile(T instance) throws Exception {
        try {
            // Jackson's readerForUpdating directly populates the existing instance
            objectMapper.readerForUpdating(instance).readValue(filePath.toFile());
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load JSON file", e);
            throw e;
        }
    }

    @Override
    protected void saveSimple(T pojoInstance, Path targetPath) throws IOException {
        try {
            objectMapper.writeValue(targetPath.toFile(), pojoInstance);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save JSON file", e);
            throw e;
        }
    }

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
                LOGGER.info("Configuration documentation saved to " + finalDocPath);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save documentation file", e);
            throw e;
        }
    }

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

        this.lastDocSectionHash = null;

        // Get all fields from the class hierarchy
        List<Field> fields = ClassUtils.getAllFieldsInHierarchy(pojoInstance.getClass(), ConfigurablePojo.class);
        boolean hasAnyDocumentedFields = false;

        // Process each field
        for (Field field : fields) {
            if (shouldSkipField(field)) {
                continue;
            }

            Key keyAnnotation = field.getAnnotation(Key.class);
            if (keyAnnotation == null)
                continue;

            String jsonKey = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

            // Handle section comments
            CommentSection sectionAnnotation = field.getAnnotation(CommentSection.class);
            if (sectionAnnotation != null && sectionAnnotation.value().length > 0) {
                String currentSectionHash = String.join("|", sectionAnnotation.value());
                if (!currentSectionHash.equals(this.lastDocSectionHash)) {
                    if (hasAnyDocumentedFields)
                        writer.println();
                    writer.println("## " + String.join(" / ", sectionAnnotation.value()));
                    writer.println();
                    this.lastDocSectionHash = currentSectionHash;
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
                    LOGGER.log(Level.WARNING, "Could not access field " + field.getName() + " for documentation", e);
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
     * Custom Jackson Annotation Introspector to support JShepherd annotations.
     */
    private static class JShepherdAnnotationIntrospector extends JacksonAnnotationIntrospector {
        @Override
        public PropertyName findNameForSerialization(Annotated a) {
            Key key = a.getAnnotation(Key.class);
            if (key != null && !key.value().isEmpty()) {
                return PropertyName.construct(key.value());
            } else if (key != null) {
                return super.findNameForSerialization(a);
            }
            return super.findNameForSerialization(a);
        }

        @Override
        public PropertyName findNameForDeserialization(Annotated a) {
            Key key = a.getAnnotation(Key.class);
            if (key != null && !key.value().isEmpty()) {
                return PropertyName.construct(key.value());
            }
            return super.findNameForDeserialization(a);
        }
    }
}
