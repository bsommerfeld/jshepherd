package de.bsommerfeld.jshepherd.core;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Default {@link Config} implementation backing the plain-POJO API.
 */
final class ConfigHandle<T> implements Config<T> {

    private final T instance;
    private final PersistenceDelegate<T> delegate;
    private final FieldVisibility fieldVisibility;

    private volatile ConfigurationWatcher watcher;
    private volatile Runnable autoReloadListener;

    ConfigHandle(T instance, PersistenceDelegate<T> delegate) {
        this.instance = instance;
        this.delegate = delegate;
        this.fieldVisibility = new FieldVisibility(instance);
        if (delegate instanceof AbstractPersistenceDelegate<T> abstractDelegate) {
            abstractDelegate._setFieldVisibility(fieldVisibility);
        }
    }

    @Override
    public T get() {
        return instance;
    }

    @Override
    public void save() {
        delegate.save(instance);
        fieldVisibility.markWritten();

        // Our own write must not be mistaken for an external change.
        ConfigurationWatcher activeWatcher = this.watcher;
        if (activeWatcher != null) {
            activeWatcher.refreshSnapshot();
        }
    }

    @Override
    public void reload() {
        delegate.reload(instance);
        invokePostInjectMethods();
    }

    /**
     * Runs the {@code @PostInject} methods, then rewrites the file if they (or the
     * bound conditions) changed the field visibility compared to what the file
     * shows.
     */
    void invokePostInjectMethods() {
        PostInjectInvoker.invoke(instance, null, delegate.getLastLoadIssues(), fieldVisibility);
        fieldVisibility.applyBindings();
        if (fieldVisibility.isFileOutdated()) {
            save();
        }
    }

    @Override
    public void show(String... keys) {
        fieldVisibility.show(keys);
    }

    @Override
    public void hide(String... keys) {
        fieldVisibility.hide(keys);
    }

    @Override
    public void bindVisibility(String key, Predicate<? super T> visibleWhen) {
        Objects.requireNonNull(visibleWhen, "visibleWhen");
        fieldVisibility.bindVisibility(key, () -> visibleWhen.test(instance));
    }

    @Override
    public List<LoadIssue> getLastLoadIssues() {
        return delegate.getLastLoadIssues();
    }

    @Override
    public void setOnAutoReload(Runnable listener) {
        this.autoReloadListener = listener;
    }

    @Override
    public boolean isAutoReloadActive() {
        return watcher != null;
    }

    @Override
    public void stopAutoReload() {
        ConfigurationWatcher activeWatcher = this.watcher;
        if (activeWatcher != null) {
            activeWatcher.stop();
            this.watcher = null;
        }
    }

    void _setWatcher(ConfigurationWatcher watcher) {
        this.watcher = watcher;
    }

    void notifyAutoReloadListener() {
        Runnable listener = autoReloadListener;
        if (listener != null) {
            listener.run();
        }
    }
}
