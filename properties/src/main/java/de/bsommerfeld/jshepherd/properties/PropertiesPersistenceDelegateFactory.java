package de.bsommerfeld.jshepherd.properties;

import com.google.auto.service.AutoService;
import de.bsommerfeld.jshepherd.core.PersistenceDelegate;
import de.bsommerfeld.jshepherd.core.PersistenceDelegateFactory;

import java.nio.file.Path;

/**
 * A factory class for creating Properties-specific persistence delegates. This
 * implementation of {@code PersistenceDelegateFactory} supports persistence
 * operations for configuration POJOs in Java {@code .properties} format.
 */
@AutoService(PersistenceDelegateFactory.class)
public class PropertiesPersistenceDelegateFactory implements PersistenceDelegateFactory {

    @Override
    public String[] getSupportedExtensions() {
        return new String[] {"properties"};
    }

    @Override
    public <T> PersistenceDelegate<T> create(
            Path filePath, boolean useComplexSaveWithComments) {
        return new PropertiesPersistenceDelegate<>(filePath, useComplexSaveWithComments);
    }
}
