package de.bsommerfeld.jshepherd.core;

import java.nio.file.Path;
import java.util.function.Supplier;

public class ConfigurationLoader {

    public static <T extends ConfigurablePojo<T>> T load(
            Path filePath,
            Supplier<T> defaultPojoSupplier,
            boolean useComplexSaveWithComments) {

        PersistenceDelegate<T> delegate = determinePersistenceDelegate(filePath, useComplexSaveWithComments);

        T pojoInstance = delegate.loadInitial(defaultPojoSupplier);
        pojoInstance._setPersistenceDelegate(delegate);
        pojoInstance._invokePostInjectMethods();

        return pojoInstance;
    }

    public static <T extends ConfigurablePojo<T>> T load(
            Path filePath, Supplier<T> defaultPojoSupplier) {
        return load(filePath, defaultPojoSupplier, true);
    }

    private static <T extends ConfigurablePojo<T>> PersistenceDelegate<T> determinePersistenceDelegate(
            Path filePath, boolean useComplexSaveWithComments) {
        String fileName = filePath.getFileName().toString();
        String fileExtension = fileName.substring(fileName.lastIndexOf('.') + 1);

        PersistenceDelegateFactory factory = PersistenceDelegateFactoryRegistry.getFactory(fileExtension);
        return factory.create(filePath, useComplexSaveWithComments);
    }
}