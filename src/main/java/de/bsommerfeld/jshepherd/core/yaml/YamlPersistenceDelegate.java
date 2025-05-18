package de.bsommerfeld.jshepherd.core.yaml;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.CommentSection;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.ConfigurationException;
import de.bsommerfeld.jshepherd.core.PersistenceDelegate;
import de.bsommerfeld.jshepherd.utils.ClassUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

// T is the concrete POJO type extending the now generic ConfigurablePojo
class YamlPersistenceDelegate<T extends ConfigurablePojo<T>> implements PersistenceDelegate<T> {
    private final Path filePath;
    private final Class<T> pojoClass;
    private final Yaml yaml; // For loading the whole POJO and for simple dump
    private final Yaml valueDumper; // For dumping individual field values in complex save
    private final boolean useComplexSaveWithComments;
    private String lastCommentSectionHash = null;

    YamlPersistenceDelegate(Path filePath, Class<T> pojoClass, boolean useComplexSaveWithComments) {
        this.filePath = filePath;
        this.pojoClass = pojoClass;
        this.useComplexSaveWithComments = useComplexSaveWithComments;

        // Main Yaml instance configuration
        DumperOptions mainDumperOptions = new DumperOptions();
        mainDumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        mainDumperOptions.setPrettyFlow(true);
        mainDumperOptions.setIndent(2);
        mainDumperOptions.setIndicatorIndent(1);
        mainDumperOptions.setSplitLines(false);
        mainDumperOptions.setAllowUnicode(true);
        mainDumperOptions.setExplicitStart(false); // No "---" for main document dump
        mainDumperOptions.setExplicitEnd(false);   // No "..."

        Representer representer = new Representer(mainDumperOptions);
        representer.getPropertyUtils().setSkipMissingProperties(true);

        LoaderOptions loaderOptions = new LoaderOptions();
        this.yaml = new Yaml(new Constructor(pojoClass, loaderOptions), representer, mainDumperOptions);

        // Yaml instance for dumping individual values (no document markers)
        DumperOptions valueDumperOptions = new DumperOptions();
        valueDumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK); // Or AUTO
        valueDumperOptions.setIndent(2); // Will be relative to its context
        valueDumperOptions.setIndicatorIndent(1);
        valueDumperOptions.setSplitLines(false);
        valueDumperOptions.setAllowUnicode(true);
        valueDumperOptions.setExplicitStart(false);
        valueDumperOptions.setExplicitEnd(false);
        this.valueDumper = new Yaml(new Representer(valueDumperOptions), valueDumperOptions);
    }

    @Override
    public T loadInitial(Supplier<T> defaultPojoSupplier) {
        T instance;
        boolean fileExisted = Files.exists(filePath);
        if (fileExisted) {
            try (Reader reader = Files.newBufferedReader(filePath)) {
                instance = yaml.load(reader);
                if (instance == null) {
                    System.out.println("INFO: Config file '" + filePath + "' was empty or only comments. Using defaults.");
                    instance = defaultPojoSupplier.get();
                }
            } catch (Exception e) {
                System.err.println("WARNING: Initial load/parse of '" + filePath + "' failed. Using defaults. Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                instance = defaultPojoSupplier.get();
            }
        } else {
            System.out.println("INFO: Config file '" + filePath + "' not found. Creating with defaults.");
            instance = defaultPojoSupplier.get();
        }

        // Save if defaults were used because file didn't exist or was empty/problematic
        if (!fileExisted || (instance != null && isEffectivelyEmptyOrNewlyDefault(instance, fileExisted))) {
            System.out.println("INFO: Saving initial/default configuration to: " + filePath);
            save(instance); // This save will use the configured comment strategy
        }
        return instance;
    }

    private boolean isEffectivelyEmptyOrNewlyDefault(T instance, boolean fileExistedBeforeLoad) {
        if (!fileExistedBeforeLoad) return true; // If file didn't exist, it's newly default

        // If file existed but loading resulted in a POJO that yaml.load() might return as null (empty/comments-only)
        // or if it's indistinguishable from a fresh default (this part is hard to check reliably if defaults have values).
        // A simpler check: if instance came from defaultPojoSupplier directly because loading failed.
        // The loadInitial logic already re-assigns to default if yaml.load() is null or throws.
        // So if `instance` is not null here AND fileExisted, it might have been loaded.
        // The critical case for resaving is if yaml.load() returned null for an existing file.
        // The current logic in loadInitial already covers creating from default and then calls save().
        // This helper might be redundant if loadInitial's logic is robust.
        // Let's rely on: save is called if instance == defaultPojoSupplier.get() (conceptually)
        // or if file was empty (yaml.load() == null). The current loadInitial handles this.
        return false; // The logic in loadInitial is the primary driver for initial save.
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
                try { Files.delete(tempFilePath); } catch (IOException ignored) {}
            }
        }
    }

    private void saveSimpleDump(T pojoInstance, Path targetPath) throws IOException {
        try (Writer writer = Files.newBufferedWriter(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            Comment classComment = pojoInstance.getClass().getAnnotation(Comment.class);
            if (classComment != null && classComment.value().length > 0) {
                for (String line : classComment.value()) {
                    writer.write("# " + line + System.lineSeparator());
                }
                writer.write(System.lineSeparator());
            }
            yaml.dump(pojoInstance, writer);
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

                String yamlKey = keyAnnotation.value().isEmpty() ? field.getName() : keyAnnotation.value();

                CommentSection sectionAnnotation = field.getAnnotation(CommentSection.class);
                if (sectionAnnotation != null && sectionAnnotation.value().length > 0) {
                    String currentSectionHash = String.join("|", sectionAnnotation.value());
                    if (!currentSectionHash.equals(this.lastCommentSectionHash)) {
                        if (this.lastCommentSectionHash != null || writer.checkError() /* cheap way to check if anything written */ ) writer.println(); // Blank line before new section unless it's the very first thing after header
                        for (String commentLine : sectionAnnotation.value()) writer.println("# " + commentLine);
                        this.lastCommentSectionHash = currentSectionHash;
                    }
                }

                Comment fieldComment = field.getAnnotation(Comment.class);
                if (fieldComment != null && fieldComment.value().length > 0) {
                    for (String commentLine : fieldComment.value()) writer.println("# " + commentLine);
                }

                writer.print(yamlKey + ":"); // e.g., "test-map:" or "test-list:"
                Object value;
                try {
                    value = field.get(pojoInstance);
                } catch (IllegalAccessException e) {
                    System.err.println("ERROR: Could not access field " + field.getName() + " during save.");
                    continue;
                }

                if (value == null) {
                    writer.println(" null"); // Standard YAML for null
                } else {
                    String valueAsYaml = this.valueDumper.dump(value); // Use pre-configured valueDumper

                    // Remove a single trailing newline if SnakeYAML adds one to the overall dumped fragment
                    if (valueAsYaml.endsWith(System.lineSeparator())) {
                        valueAsYaml = valueAsYaml.substring(0, valueAsYaml.length() - System.lineSeparator().length());
                    }
                    // Note: Do NOT do a global .trim() on valueAsYaml here, as internal leading spaces
                    // (if any, from SnakeYAML's own formatting for nested items) are significant.

                    boolean isScalarOrFlowCollection = !(value instanceof List || value instanceof Map) && !valueAsYaml.contains(System.lineSeparator());
                    if (value instanceof List && ((List<?>)value).isEmpty()) isScalarOrFlowCollection = true; // Empty list as []
                    if (value instanceof Map && ((Map<?,?>)value).isEmpty()) isScalarOrFlowCollection = true;  // Empty map as {}


                    if (isScalarOrFlowCollection) {
                        // For scalars or collections dumped in flow style (e.g., "[]", "{}")
                        writer.println(" " + valueAsYaml.trim()); // .trim() here is safe for single-line values
                    } else {
                        // For block style lists, maps, or other multi-line scalars
                        writer.println(); // Start the block value on a new line
                        valueAsYaml.lines().forEach(line -> {
                            // Consistently indent every line of the dumped block value
                            writer.println("  " + line);
                        });
                    }
                }

                // Logic for blank line after entry (from previous solution)
                boolean addBlankLine = false;
                if (fieldIdx < fields.size() - 1) {
                    for (int k = fieldIdx + 1; k < fields.size(); k++) {
                        Field nextField = fields.get(k);
                        if (Modifier.isStatic(nextField.getModifiers()) || Modifier.isTransient(nextField.getModifiers())) continue;
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

    @Override
    public void reload(T pojoInstanceToUpdate) {
        if (!Files.exists(filePath)) {
            System.out.println("INFO: Configuration file '" + filePath + "' not found on reload attempt. Current values in instance remain.");
            return;
        }
        try (Reader reader = Files.newBufferedReader(filePath)) {
            T freshPojo = yaml.load(reader);
            if (freshPojo != null) {
                updatePojoFields(pojoInstanceToUpdate, freshPojo);
                System.out.println("INFO: Configuration reloaded into existing instance from " + filePath);
            } else {
                System.out.println("INFO: Configuration file '" + filePath + "' was empty or unparsable on reload. Current values in instance remain (likely defaults).");
            }
        } catch (IOException e) {
            throw new ConfigurationException("Error reloading configuration from " + filePath, e);
        } catch (Exception e) {
            throw new ConfigurationException("Error parsing YAML during reload from " + filePath, e);
        }
    }

    private void updatePojoFields(T target, T source) {
        if (source == null || !target.getClass().isAssignableFrom(source.getClass())) { // Check assignability
            System.err.println("WARNING: Cannot update POJO fields during reload. Source is null or types incompatible.");
            return;
        }
        List<Field> fields = ClassUtils.getAllFieldsInHierarchy(target.getClass(), ConfigurablePojo.class);
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object valueFromSource = field.get(source);
                field.set(target, valueFromSource);
            } catch (IllegalAccessException e) {
                System.err.println("WARNING: Could not update field '" + field.getName() + "' during reload: " + e.getMessage());
            }
        }
    }
}
