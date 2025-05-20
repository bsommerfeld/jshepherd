package de.bsommerfeld.jshepherd.core;

/**
 * Abstract base class for configuration POJOs that can be saved and reloaded. Uses a self-referential generic type
 * 'SELF' to ensure type safety with the PersistenceDelegate.
 *
 * @param <SELF> The concrete type of the class extending ConfigurablePojo.
 */
public abstract class ConfigurablePojo<SELF extends ConfigurablePojo<SELF>> {

    transient PersistenceDelegate<SELF> persistenceDelegate;

    // Package-private: only ConfigurationLoader should set this.
    @SuppressWarnings("unchecked") // The delegate passed will be specific to SELF
    final void _setPersistenceDelegate(PersistenceDelegate<?> delegate) {
        // This cast is now safe because ConfigurationLoader will pass a PersistenceDelegate<SELF>
        this.persistenceDelegate = (PersistenceDelegate<SELF>) delegate;
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
    }

    /**
     * Reloads the state of this configuration object from its persistent store. The fields of this instance will be
     * updated with the reloaded values.
     */
    @SuppressWarnings("unchecked")
    public void reload() {
        if (persistenceDelegate == null) {
            throw new IllegalStateException("Configuration POJO not properly initialized. Cannot reload.");
        }
        persistenceDelegate.reload((SELF) this);
    }
}
