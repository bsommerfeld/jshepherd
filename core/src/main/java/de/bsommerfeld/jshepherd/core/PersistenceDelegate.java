package de.bsommerfeld.jshepherd.core;

import java.util.List;
import java.util.function.Supplier;

/**
 * Internal interface for handling the actual persistence mechanics (loading, saving, reloading) for a given
 * configuration POJO type — either a {@code ConfigurablePojo} subclass or a plain {@code @Configuration} class.
 *
 * @param <T> The configuration POJO type this delegate handles.
 */
public interface PersistenceDelegate<T> {
    /**
     * Saves the provided POJO instance to the persistent store.
     *
     * @param pojoInstance The POJO instance to save.
     */
    void save(T pojoInstance);

    /**
     * Reloads data from the persistent store into the provided POJO instance, updating its fields.
     *
     * @param pojoInstanceToUpdate The POJO instance whose fields should be updated.
     */
    void reload(T pojoInstanceToUpdate);

    /**
     * Loads the initial POJO instance from the persistent store, or creates and saves a default if the store is new or
     * empty.
     *
     * @param defaultPojoSupplier Supplier for creating a default POJO instance.
     *
     * @return The loaded or newly created default POJO instance.
     */
    T loadInitial(Supplier<T> defaultPojoSupplier);

    /**
     * Returns the per-key issues recorded during the last load or reload.
     *
     * @return an immutable list; empty if the last load was clean
     */
    default List<LoadIssue> getLastLoadIssues() {
        return List.of();
    }
}
