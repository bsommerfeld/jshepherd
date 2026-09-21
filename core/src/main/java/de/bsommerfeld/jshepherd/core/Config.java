package de.bsommerfeld.jshepherd.core;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

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
     * {@code @PostInject} methods. If those show or hide fields (see
     * {@link FieldVisibility}) in response to the reloaded values, the file is
     * rewritten right away.
     */
    void reload();

    /**
     * Makes the given keys (or sections) appear in the configuration file again.
     * See {@link FieldVisibility} for the key syntax and semantics.
     *
     * @throws ConfigurationException if a key does not exist in the configuration
     */
    void show(String... keys);

    /**
     * Leaves the given keys (or sections) out of the configuration file. See
     * {@link FieldVisibility} for the key syntax and semantics.
     *
     * @throws ConfigurationException if a key does not exist in the configuration
     */
    void hide(String... keys);

    /**
     * Binds the visibility of the given key (or section) to a condition on the
     * POJO, e.g. {@code config.bindVisibility("log-changes", AppConfig::isDebug)}:
     * the key appears in the file while the condition holds and is left out
     * otherwise. Bound through the handle, the file follows on the next
     * {@link #save()} or {@link #reload()}. See
     * {@link FieldVisibility#bindVisibility(String, BooleanSupplier)}
     * for when the condition is evaluated.
     *
     * @throws ConfigurationException if the key does not exist in the configuration
     */
    void bindVisibility(String key, Predicate<? super T> visibleWhen);

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
