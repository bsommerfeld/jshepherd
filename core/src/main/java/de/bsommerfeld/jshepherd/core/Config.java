package de.bsommerfeld.jshepherd.core;

import java.util.List;

/**
 * Lifecycle handle for a plain {@code @Configuration} POJO loaded via
 * {@code ConfigurationLoader.from(path).loadPlain(...)}.
 *
 * <p>Because the POJO does not extend anything, persistence operations live on
 * this handle instead of on the configuration object itself:</p>
 *
 * <pre>{@code
 * Config<AppConfig> config = ConfigurationLoader.from(path).loadPlain(AppConfig::new);
 *
 * AppConfig app = config.get();
 * app.setPort(9090);
 * config.save();
 * config.reload();
 * }</pre>
 *
 * @param <T> the plain configuration POJO type
 */
public interface Config<T> {

    /**
     * Returns the configuration POJO. The same instance is returned for the
     * lifetime of this handle; {@link #reload()} updates its fields in place.
     */
    T get();

    /**
     * Saves the current state of the POJO to the configuration file.
     */
    void save();

    /**
     * Reloads the POJO's fields from the configuration file and re-runs
     * {@code @PostInject} methods.
     */
    void reload();

    /**
     * Returns the per-key issues from the last load or reload (TOML and
     * Properties formats). See {@link ConfigurablePojo#getLastLoadIssues()} for
     * details on the semantics.
     */
    List<LoadIssue> getLastLoadIssues();

    /**
     * Registers a listener invoked after an automatic reload (requires
     * auto-reload to be enabled via the loader builder). Runs on the watcher
     * thread — keep it short and thread-safe. Pass null to remove.
     */
    void setOnAutoReload(Runnable listener);

    /**
     * Returns whether the configuration file is being watched for changes.
     */
    boolean isAutoReloadActive();

    /**
     * Stops watching the configuration file. {@link #save()} and
     * {@link #reload()} continue to work.
     */
    void stopAutoReload();
}
