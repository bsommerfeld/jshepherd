package de.bsommerfeld.jshepherd.core;

import de.bsommerfeld.jshepherd.annotation.PostInject;

import java.util.Arrays;

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

    // Package-private: only ConfigurationLoader should set this.
    @SuppressWarnings("unchecked") // The delegate passed will be specific to SELF
    final void _setPersistenceDelegate(PersistenceDelegate<?> delegate) {
        // This cast is now safe because ConfigurationLoader will pass a
        // PersistenceDelegate<SELF>
        this.persistenceDelegate = (PersistenceDelegate<SELF>) delegate;
    }

    final void _invokePostInjectMethods() {
        Arrays.stream(getClass().getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostInject.class))
                .forEach(method -> {
                    try {
                        method.setAccessible(true);
                        method.invoke(this);
                    } catch (Exception e) {
                        throw new ConfigurationException("Failed to invoke @PostInject method: " + method.getName(), e);
                    }
                });
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
}
