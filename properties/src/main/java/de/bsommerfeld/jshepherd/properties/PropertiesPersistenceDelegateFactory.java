package de.bsommerfeld.jshepherd.properties;

import com.google.auto.service.AutoService;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.PersistenceDelegate;
import de.bsommerfeld.jshepherd.core.PersistenceDelegateFactory;
import java.nio.file.Path;

/**
 * Factory class responsible for creating persistence delegates for handling configuration files
 * using the "properties" file format. This class implements the {@link PersistenceDelegateFactory}
 * interface to provide support for configuration persistence with .properties files.
 */
@AutoService(PersistenceDelegateFactory.class)
public class PropertiesPersistenceDelegateFactory implements PersistenceDelegateFactory {

  @Override
  public String[] getSupportedExtensions() {
    return new String[] {"properties"};
  }

  @Override
  public <T extends ConfigurablePojo<T>> PersistenceDelegate<T> create(
          Path filePath, boolean useComplexSaveWithComments) {
    return new PropertiesPersistenceDelegate<>(filePath, useComplexSaveWithComments);
  }
}
