package de.bsommerfeld.jshepherd.core.yaml;

import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.PersistenceDelegate;

import java.nio.file.Path;

public class YamlPersistenceDelegateFactory { // Public factory
    public static <T extends ConfigurablePojo<T>> PersistenceDelegate<T> create(
            Path filePath, Class<T> pojoClass, boolean useComplexSaveWithComments) {
        return new YamlPersistenceDelegate<>(filePath, pojoClass, useComplexSaveWithComments);
    }
}
