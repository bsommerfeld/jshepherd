package de.bsommerfeld.jshepherd.core;

import java.util.List;

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

    final void _invokePostInjectMethods() {
        PostInjectInvoker.invoke(this, ConfigurablePojo.class, lastLoadIssues);
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
     */
    @SuppressWarnings("unchecked")
    public void reload() {
        if (persistenceDelegate == null) {
            throw new IllegalStateException("Configuration POJO not properly initialized. Cannot reload.");
        }
        persistenceDelegate.reload((SELF) this);
        _invokePostInjectMethods();
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
