package de.bsommerfeld.jshepherd.core;

import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.annotation.Section;
import de.bsommerfeld.jshepherd.utils.ClassUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Controls which fields and sections of a configuration are written to its
 * file. Hidden fields are left out of the file (and of generated
 * documentation); they are still read when present, and dropped on the next
 * save.
 *
 * <p>
 * Typical use: debug or expert options that only show up once the user
 * unlocks them through another setting.
 * </p>
 *
 * <pre>{@code
 * public class ServerConfig extends ConfigurablePojo<ServerConfig> {
 *
 *     @Key("debug")
 *     private boolean debug = false;
 *
 *     @Key("log-changes")
 *     private boolean logChanges = false;
 *
 *     public ServerConfig() {
 *         hide("log-changes"); // initial state, applies to the generated default file
 *     }
 *
 *     @PostInject
 *     private void updateVisibility() {
 *         if (debug) show("log-changes"); else hide("log-changes");
 *     }
 * }
 * }</pre>
 *
 * <p>
 * When the visibility simply follows a condition,
 * {@link #bindVisibility(String, BooleanSupplier) bindVisibility(...)} replaces
 * the constructor/{@code @PostInject} pair above with a single line:
 * </p>
 *
 * <pre>{@code
 * public ServerConfig() {
 *     bindVisibility("log-changes", () -> debug);
 * }
 * }</pre>
 *
 * <p>
 * Keys are paths from the configuration root: {@code "log-changes"} for a
 * root-level key, {@code "debug-options"} for a whole section,
 * {@code "database.trace-sql"} for the key {@code trace-sql} inside the section
 * {@code database}. Unknown keys fail right away with a
 * {@link ConfigurationException}.
 * </p>
 *
 * <p>
 * When the visibility differs from what the file shows after the
 * {@code @PostInject} methods of a load, {@code reload()} or auto-reload have
 * run, the file is rewritten right away. Changing the visibility anywhere else
 * takes effect on the next {@code save()}.
 * </p>
 *
 * <p>
 * {@code ConfigurablePojo} subclasses call {@code show(...)}/{@code hide(...)}/
 * {@code bindVisibility(...)} directly. Plain {@code @Configuration} POJOs
 * declare a {@code FieldVisibility} parameter on a {@code @PostInject} method,
 * or use the {@link Config} handle.
 * </p>
 *
 * <p>
 * Note: visibility is tracked per declared field. If the same section class is
 * used for several {@code @Section} fields, hiding a key inside one of them
 * hides it in all of them.
 * </p>
 */
public final class FieldVisibility {

    private final Object config;
    private final Set<Field> hiddenFields = ConcurrentHashMap.newKeySet();
    private final Map<Field, BooleanSupplier> bindings = new ConcurrentHashMap<>();

    /** The hidden fields as of the last write; what the file currently shows. */
    private volatile Set<Field> hiddenInFile = Set.of();

    FieldVisibility(Object config) {
        this.config = config;
    }

    /**
     * Makes the given keys (or sections) appear in the file again.
     *
     * @throws ConfigurationException if a key does not exist in the configuration
     */
    public void show(String... keys) {
        for (String key : keys) {
            hiddenFields.remove(resolve(key));
        }
    }

    /**
     * Leaves the given keys (or sections) out of the file.
     *
     * @throws ConfigurationException if a key does not exist in the configuration
     */
    public void hide(String... keys) {
        for (String key : keys) {
            hiddenFields.add(resolve(key));
        }
    }

    /**
     * Binds the visibility of the given key (or section) to a condition: it
     * appears in the file while the condition holds and is left out otherwise.
     *
     * <p>
     * The condition is evaluated right away, before every save, and after the
     * {@code @PostInject} methods of a load, {@code reload()} or auto-reload —
     * where a changed outcome rewrites the file right away, just like
     * {@link #show(String...) show}/{@link #hide(String...) hide} calls made
     * there. A bound key follows its condition: {@code show}/{@code hide} calls
     * for it only last until the next evaluation. Binding a key again replaces
     * its previous condition.
     * </p>
     *
     * @param visibleWhen evaluated on the thread that saves or (auto-)reloads
     * @throws ConfigurationException if the key does not exist in the configuration
     */
    public void bindVisibility(String key, BooleanSupplier visibleWhen) {
        Objects.requireNonNull(visibleWhen, "visibleWhen");
        Field field = resolve(key);
        bindings.put(field, visibleWhen);
        apply(field, visibleWhen);
    }

    /**
     * Returns whether the given key (or section) is currently hidden.
     *
     * @throws ConfigurationException if the key does not exist in the configuration
     */
    public boolean isHidden(String key) {
        Field field = resolve(key);
        BooleanSupplier visibleWhen = bindings.get(field);
        return visibleWhen != null ? !visibleWhen.getAsBoolean() : hiddenFields.contains(field);
    }

    /** Re-evaluates the conditions of all bound keys. */
    void applyBindings() {
        bindings.forEach(this::apply);
    }

    private void apply(Field field, BooleanSupplier visibleWhen) {
        if (visibleWhen.getAsBoolean()) {
            hiddenFields.remove(field);
        } else {
            hiddenFields.add(field);
        }
    }

    Set<Field> hiddenFields() {
        return Set.copyOf(hiddenFields);
    }

    /** Records that the file shows the current visibility. */
    void markWritten() {
        hiddenInFile = hiddenFields();
    }

    /** Whether the visibility changed since the file was last written. */
    boolean isFileOutdated() {
        return !hiddenInFile.equals(hiddenFields);
    }

    private Field resolve(String key) {
        Field field = find(config.getClass(), config, key);
        if (field == null) {
            throw new ConfigurationException("Cannot change visibility of '" + key + "': no such key or section in "
                    + config.getClass().getSimpleName());
        }
        return field;
    }

    private static Field find(Class<?> scopeClass, Object scope, String path) {
        List<Field> fields = persistedFields(scopeClass);

        // Match the whole remaining path first, so keys containing dots keep working.
        for (Field field : fields) {
            if (nameOf(field).equals(path)) {
                return field;
            }
        }

        for (Field field : fields) {
            String sectionPrefix = nameOf(field) + ".";
            if (!isSection(field) || !path.startsWith(sectionPrefix)) {
                continue;
            }
            Object section = scope != null ? readField(field, scope) : null;
            if (section instanceof Map) {
                continue;
            }
            // A section that is currently null is still addressable through its declared type.
            Class<?> sectionClass = section != null ? section.getClass() : field.getType();
            Field found = find(sectionClass, section, path.substring(sectionPrefix.length()));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<Field> persistedFields(Class<?> scopeClass) {
        return ClassUtils.getAllFieldsInHierarchy(scopeClass, ConfigurablePojo.class).stream()
                .filter(f -> !Modifier.isStatic(f.getModifiers()) && !Modifier.isTransient(f.getModifiers()))
                .filter(f -> f.getAnnotation(Key.class) != null || isSection(f))
                .toList();
    }

    private static boolean isSection(Field field) {
        return field.getAnnotation(Section.class) != null;
    }

    /** The name under which the field appears in the file: section name, else key. */
    private static String nameOf(Field field) {
        Section section = field.getAnnotation(Section.class);
        if (section != null && !section.value().isEmpty()) {
            return section.value();
        }
        Key key = field.getAnnotation(Key.class);
        return key == null || key.value().isEmpty() ? field.getName() : key.value();
    }

    private static Object readField(Field field, Object owner) {
        try {
            field.setAccessible(true);
            return field.get(owner);
        } catch (IllegalAccessException e) {
            throw new ConfigurationException("Could not access field '" + field.getName() + "'", e);
        }
    }
}
