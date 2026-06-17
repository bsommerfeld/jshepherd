package de.bsommerfeld.jshepherd.core;

import java.util.List;

/**
 * Default {@link Config} implementation backing the plain-POJO API.
 */
final class ConfigHandle<T> implements Config<T> {

    private final T instance;
    private final PersistenceDelegate<T> delegate;

    private volatile ConfigurationWatcher watcher;
    private volatile Runnable autoReloadListener;

    ConfigHandle(T instance, PersistenceDelegate<T> delegate) {
        this.instance = instance;
        this.delegate = delegate;
    }

    @Override
    public T get() {
        return instance;
    }

    @Override
    public void save() {
        delegate.save(instance);

        // Our own write must not be mistaken for an external change.
        ConfigurationWatcher activeWatcher = this.watcher;
        if (activeWatcher != null) {
            activeWatcher.refreshSnapshot();
        }
    }

    @Override
    public void reload() {
        delegate.reload(instance);
        PostInjectInvoker.invoke(instance, null, delegate.getLastLoadIssues());
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
