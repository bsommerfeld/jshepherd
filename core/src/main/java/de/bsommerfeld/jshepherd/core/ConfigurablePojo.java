package de.bsommerfeld.jshepherd.core;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Abstract base class for configuration POJOs that can be saved and reloaded.
 *
 * <p>
 * This class uses a self-referential generic type parameter ({@code SELF}) to
 * ensure
 * type-safe {@link #save()} and {@link #reload()} operations. The pattern
 * allows the
 * persistence layer to work with your concrete type without requiring casts.
 * </p>
 *
 * <p>
 * <b>Usage:</b>
 * </p>
 * 
 * <pre>{@code
 * // Pass your own class as the type parameter
 * public class AppConfig extends ConfigurablePojo<AppConfig> {
 *     // ...
 * }
 * }</pre>
 *
 * <p>
 * <b>Why this pattern?</b> Without it, {@code reload()} would only know about
 * {@code ConfigurablePojo}, not your specific fields. The self-reference
 * ensures
 * the persistence delegate can properly populate your concrete class.
 * </p>
 *
 * @param <SELF> The concrete type extending this class (pass your own class
 *               name)
 */
public abstract class ConfigurablePojo<SELF extends ConfigurablePojo<SELF>> {

    transient PersistenceDelegate<SELF> persistenceDelegate;
    transient volatile ConfigurationWatcher watcher;
    transient volatile Runnable autoReloadListener;
    transient final FieldVisibility fieldVisibility = new FieldVisibility(this);

    // Package-private: only ConfigurationLoader should set this.
    @SuppressWarnings("unchecked") // The delegate passed will be specific to SELF
    final void _setPersistenceDelegate(PersistenceDelegate<?> delegate) {
        // This cast is now safe because ConfigurationLoader will pass a
        // PersistenceDelegate<SELF>
        this.persistenceDelegate = (PersistenceDelegate<SELF>) delegate;
    }

    // Package-private: only ConfigurationLoader should set this.
    final void _setWatcher(ConfigurationWatcher watcher) {
        this.watcher = watcher;
    }

    transient volatile List<LoadIssue> lastLoadIssues = List.of();

    // Package-private: set by the persistence delegate after each load/reload.
    final void _setLoadIssues(List<LoadIssue> issues) {
        this.lastLoadIssues = List.copyOf(issues);
    }

    /**
     * Returns the values from the last load or reload that could not be applied
     * to their fields (e.g. {@code port = "abc"} for an {@code int} field). The
     * affected fields keep their previous/default values.
     *
     * <p>This list is populated before {@code @PostInject} methods run, so it
     * can be used for custom validation:</p>
     *
     * <pre>{@code
     * @PostInject
     * private void validate() {
     *     if (!getLastLoadIssues().isEmpty()) {
     *         throw new IllegalStateException("Invalid config values: " + getLastLoadIssues());
     *     }
     * }
     * }</pre>
     *
     * <p>Note: per-key issue reporting applies to the TOML and Properties
     * formats. YAML and JSON bind the whole document at once — a type mismatch
     * there fails the entire load loudly instead.</p>
     *
     * @return an immutable list of issues; empty if the last load was clean
     */
    public List<LoadIssue> getLastLoadIssues() {
        return lastLoadIssues;
    }

    /**
     * Runs the {@code @PostInject} methods, then rewrites the file if they (or the
     * bound conditions) changed the field visibility compared to what the file
     * shows.
     */
    final void _invokePostInjectMethods() {
        PostInjectInvoker.invoke(this, ConfigurablePojo.class, lastLoadIssues, fieldVisibility);
        fieldVisibility.applyBindings();
        if (fieldVisibility.isFileOutdated()) {
            save();
        }
    }

    /**
     * Saves the current state of this configuration object to its persistent store.
     */
    @SuppressWarnings("unchecked")
    public void save() {
        if (persistenceDelegate == null) {
            throw new IllegalStateException("Configuration POJO not properly initialized. Cannot save.");
        }
        persistenceDelegate.save((SELF) this); // 'this' is cast to its concrete type SELF
        fieldVisibility.markWritten();

        // Our own write must not be mistaken for an external change.
        ConfigurationWatcher activeWatcher = this.watcher;
        if (activeWatcher != null) {
            activeWatcher.refreshSnapshot();
        }
    }

    /**
     * Reloads the state of this configuration object from its persistent store. The
     * fields of this instance will be
     * updated with the reloaded values.
     *
     * <p>If the {@code @PostInject} methods {@link #show(String...) show} or
     * {@link #hide(String...) hide} fields in response to the reloaded values,
     * the file is rewritten right away.</p>
     */
    @SuppressWarnings("unchecked")
    public void reload() {
        if (persistenceDelegate == null) {
            throw new IllegalStateException("Configuration POJO not properly initialized. Cannot reload.");
        }
        persistenceDelegate.reload((SELF) this);
        _invokePostInjectMethods();
    }

    // ==================== FIELD VISIBILITY ====================

    /**
     * Makes the given keys (or sections) appear in the configuration file again.
     * See {@link FieldVisibility} for the key syntax and semantics.
     *
     * @throws ConfigurationException if a key does not exist in this configuration
     */
    public void show(String... keys) {
        fieldVisibility.show(keys);
    }

    /**
     * Leaves the given keys (or sections) out of the configuration file, e.g.
     * debug options that stay hidden until another setting unlocks them. Call
     * it in the constructor to define the initial state and in a
     * {@code @PostInject} method to react to loaded values. See
     * {@link FieldVisibility} for the key syntax and semantics.
     *
     * @throws ConfigurationException if a key does not exist in this configuration
     */
    public void hide(String... keys) {
        fieldVisibility.hide(keys);
    }

    /**
     * Binds the visibility of the given key (or section) to a condition, e.g.
     * {@code bindVisibility("debug-options", () -> debug)} in the constructor:
     * the key appears in the file while the condition holds and is left out
     * otherwise — no {@code @PostInject} method needed. See
     * {@link FieldVisibility#bindVisibility(String, BooleanSupplier)} for when
     * the condition is evaluated.
     *
     * @throws ConfigurationException if the key does not exist in this configuration
     */
    public void bindVisibility(String key, BooleanSupplier visibleWhen) {
        fieldVisibility.bindVisibility(key, visibleWhen);
    }

    // ==================== AUTO-RELOAD ====================

    /**
     * Registers a listener that is invoked after this configuration has been
     * automatically reloaded because the file changed on disk. Requires
     * auto-reload to be enabled via
     * {@code ConfigurationLoader.from(path).withAutoReload()}.
     *
     * <p>The listener runs on the watcher thread — keep it short and thread-safe.
     * Pass {@code null} to remove a previously registered listener.</p>
     */
    public void setOnAutoReload(Runnable listener) {
        this.autoReloadListener = listener;
    }

    /**
     * Returns whether this configuration is being watched for external file
     * changes.
     */
    public boolean isAutoReloadActive() {
        return watcher != null;
    }

    /**
     * Stops watching the configuration file for external changes. Has no effect
     * if auto-reload was never enabled. Manual {@link #save()} and
     * {@link #reload()} continue to work.
     */
    public void stopAutoReload() {
        ConfigurationWatcher activeWatcher = this.watcher;
        if (activeWatcher != null) {
            activeWatcher.stop();
            this.watcher = null;
        }
    }
}
