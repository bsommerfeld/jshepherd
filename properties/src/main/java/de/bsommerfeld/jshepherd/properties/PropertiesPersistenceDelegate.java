package de.bsommerfeld.jshepherd.properties;

import de.bsommerfeld.jshepherd.core.AbstractPersistenceDelegate;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of PersistenceDelegate for the Java {@code .properties} format.
 *
 * <p>Properties files are flat, so hierarchical data is mapped to dotted keys:
 * {@code @Section("database")} fields become {@code database.url=...} entries,
 * and {@code Map} fields become {@code map-key.entry=...} entries. Lists are
 * stored comma-separated. Files are read and written as UTF-8.</p>
 *
 * <p>Limitation: list elements that themselves contain commas are not
 * round-trippable in this format.</p>
 */
class PropertiesPersistenceDelegate<T> extends AbstractPersistenceDelegate<T> {

    private static final Logger LOGGER = Logger.getLogger(PropertiesPersistenceDelegate.class.getName());

    PropertiesPersistenceDelegate(Path filePath, boolean useComplexSaveWithComments) {
        super(filePath, useComplexSaveWithComments);
    }

    // ==================== LOADING ====================

    @Override
    protected boolean tryLoadFromFile(T instance) throws Exception {
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(filePath)) {
            props.load(reader);
        }
        if (props.isEmpty()) {
            return false;
        }

        for (Field field : getNonSectionFields(instance.getClass(), ConfigurablePojo.class)) {
            applyValue(instance, field, props, "");
        }

        for (Field sectionField : getSectionFields(instance.getClass(), ConfigurablePojo.class)) {
            loadSection(instance, sectionField, props, "", 1);
        }
        return true;
    }

    private void loadSection(Object owner, Field sectionField, Properties props, String parentPrefix, int depth) {
        if (depth > MAX_SECTION_DEPTH) {
            LOGGER.log(Level.WARNING, "Maximum section nesting depth (" + MAX_SECTION_DEPTH
                    + ") exceeded at '" + parentPrefix + "'. Check for cyclic @Section references.");
            return;
        }

        String prefix = parentPrefix + resolveSectionName(sectionField) + ".";
        if (!hasAnyKeyWithPrefix(props, prefix)) {
            return;
        }
        try {
            sectionField.setAccessible(true);
            Object sectionPojo = sectionField.get(owner);
            if (sectionPojo == null) {
                sectionPojo = instantiate(sectionField.getType());
                if (sectionPojo == null) {
                    return;
                }
                sectionField.set(owner, sectionPojo);
            }
            for (Field nestedField : getSectionPojoFields(sectionPojo)) {
                applyValue(sectionPojo, nestedField, props, prefix);
            }
            for (Field subsectionField : getSectionPojoSubsectionFields(sectionPojo)) {
                loadSection(sectionPojo, subsectionField, props, prefix, depth + 1);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error applying section '" + prefix + "'", e);
        }
    }

    private void applyValue(Object target, Field field, Properties props, String prefix) {
        String key = prefix + resolveKey(field);
        String raw = null;
        try {
            field.setAccessible(true);
            Class<?> type = field.getType();

            if (Map.class.isAssignableFrom(type)) {
                Map<String, Object> map = readMapEntries(props, key + ".", resolveGenericClass(field, 1));
                if (map != null) {
                    field.set(target, map);
                }
                return;
            }

            raw = props.getProperty(key);
            if (raw == null) {
                return;
            }

            if (List.class.isAssignableFrom(type)) {
                field.set(target, parseList(raw, resolveGenericClass(field, 0)));
            } else {
                field.set(target, convertScalar(raw, type));
            }
        } catch (Exception e) {
            recordLoadIssue(key, raw, field.getType(), e);
        }
    }

    private Object convertScalar(String raw, Class<?> type) {
        if (type == String.class) {
            return raw;
        }
        if (type == LocalDate.class) {
            return LocalDate.parse(raw);
        }
        if (type == LocalDateTime.class) {
            return LocalDateTime.parse(raw);
        }
        // Numbers, booleans and enums via the shared coercion logic
        return convertNumericIfNeeded(raw, type);
    }

    private List<Object> parseList(String raw, Class<?> elementType) {
        List<Object> list = new ArrayList<>();
        if (raw.isBlank()) {
            return list;
        }
        for (String item : raw.split(",")) {
            String trimmed = item.trim();
            list.add(elementType != null ? convertScalar(trimmed, elementType) : trimmed);
        }
        return list;
    }

    /**
     * Collects all entries below the given dotted prefix into a map, or returns
     * null if the file contains none (so existing defaults are preserved).
     */
    private Map<String, Object> readMapEntries(Properties props, String prefix, Class<?> valueType) {
        Map<String, Object> map = null;
        for (String name : props.stringPropertyNames()) {
            if (!name.startsWith(prefix)) {
                continue;
            }
            if (map == null) {
                map = new LinkedHashMap<>();
            }
            String subKey = name.substring(prefix.length());
            String raw = props.getProperty(name);
            map.put(subKey, valueType != null ? convertScalar(raw, valueType) : raw);
        }
        return map;
    }

    private boolean hasAnyKeyWithPrefix(Properties props, String prefix) {
        return props.stringPropertyNames().stream().anyMatch(name -> name.startsWith(prefix));
    }

    private Object instantiate(Class<?> type) {
        try {
            Constructor<?> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not instantiate nested section " + type.getSimpleName(), e);
            return null;
        }
    }

    // ==================== SAVING ====================

    @Override
    protected void saveSimple(T pojoInstance, Path targetPath) throws IOException {
        doSave(pojoInstance, targetPath, false);
    }

    @Override
    protected void saveWithComments(T pojoInstance, Path targetPath) throws IOException {
        doSave(pojoInstance, targetPath, true);
    }

    private void doSave(T pojoInstance, Path targetPath, boolean withComments) throws IOException {
        try (PrintWriter writer = new PrintWriter(
                Files.newBufferedWriter(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {

            if (withComments) {
                writeClassComments(writer, pojoInstance);
            }

            boolean first = true;
            for (Field field : getNonSectionFields(pojoInstance.getClass(), ConfigurablePojo.class)) {
                first = writeField(writer, field, pojoInstance, "", withComments, first);
            }

            for (Field sectionField : getSectionFields(pojoInstance.getClass(), ConfigurablePojo.class)) {
                first = writeSection(writer, sectionField, pojoInstance, "", withComments, first, 1);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save properties file", e);
            throw e;
        }
    }

    /**
     * Writes a section and recursively all of its nested sections with dotted
     * key prefixes. Returns the new value of the caller's "first entry" flag.
     */
    private boolean writeSection(PrintWriter writer, Field sectionField, Object owner,
            String parentPrefix, boolean withComments, boolean first, int depth) {
        if (depth > MAX_SECTION_DEPTH) {
            LOGGER.log(Level.WARNING, "Maximum section nesting depth (" + MAX_SECTION_DEPTH
                    + ") exceeded at '" + parentPrefix + "'. Check for cyclic @Section references.");
            return first;
        }

        try {
            sectionField.setAccessible(true);
            Object sectionPojo = sectionField.get(owner);
            if (sectionPojo == null) {
                return first;
            }
            if (!first) {
                writer.println();
            }
            if (withComments) {
                writeFieldComments(writer, sectionField);
            }
            String prefix = parentPrefix + resolveSectionName(sectionField) + ".";
            boolean firstNested = true;
            for (Field nestedField : getSectionPojoFields(sectionPojo)) {
                firstNested = writeField(writer, nestedField, sectionPojo, prefix, withComments, firstNested);
            }
            for (Field subsectionField : getSectionPojoSubsectionFields(sectionPojo)) {
                writeSection(writer, subsectionField, sectionPojo, prefix, withComments, false, depth + 1);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error writing section field " + sectionField.getName(), e);
        }
        return false;
    }

    /**
     * Writes one field (with optional comment block). Returns the new value of
     * the caller's "first entry" flag.
     */
    private boolean writeField(PrintWriter writer, Field field, Object instance,
            String prefix, boolean withComments, boolean first) {
        Object value;
        try {
            field.setAccessible(true);
            value = field.get(instance);
        } catch (IllegalAccessException e) {
            LOGGER.log(Level.WARNING, "Could not access field " + field.getName(), e);
            return first;
        }
        if (value == null) {
            return first;
        }

        if (!first && withComments) {
            writer.println();
        }
        if (withComments) {
            writeFieldComments(writer, field);
        }

        String key = prefix + resolveKey(field);
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                writeEntry(writer, key + "." + entry.getKey(), entry.getValue());
            }
        } else {
            writeEntry(writer, key, value);
        }
        return false;
    }

    private void writeEntry(PrintWriter writer, String key, Object value) {
        writer.println(escapeKey(key) + "=" + escapeValue(serializeValue(value)));
    }

    private String serializeValue(Object value) {
        if (value instanceof Enum<?>) {
            return serializeEnumValue(value);
        }
        if (value instanceof List<?> list) {
            StringBuilder joined = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    joined.append(", ");
                }
                joined.append(serializeValue(list.get(i)));
            }
            return joined.toString();
        }
        return String.valueOf(value);
    }

    // ==================== ESCAPING ====================

    private String escapeKey(String key) {
        return escapeCommon(key)
                .replace("=", "\\=")
                .replace(":", "\\:")
                .replace(" ", "\\ ")
                .replace("#", "\\#")
                .replace("!", "\\!");
    }

    private String escapeValue(String value) {
        String escaped = escapeCommon(value);
        // Leading whitespace would be trimmed by Properties.load
        if (escaped.startsWith(" ")) {
            escaped = "\\" + escaped;
        }
        return escaped;
    }

    private String escapeCommon(String text) {
        return text.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
