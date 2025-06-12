package de.bsommerfeld.jshepherd.toml;

import com.google.auto.service.AutoService;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.PersistenceDelegate;
import de.bsommerfeld.jshepherd.core.PersistenceDelegateFactory;
import java.nio.file.Path;

/**
 * Factory class that provides persistence delegate instances for working with TOML configuration
 * files. Implements the {@link PersistenceDelegateFactory} interface to handle creation of {@link
 * PersistenceDelegate} instances specific to the TOML file format.
 */
@AutoService(PersistenceDelegateFactory.class)
public class TomlPersistenceDelegateFactory implements PersistenceDelegateFactory {

  @Override
  public String[] getSupportedExtensions() {
    return new String[] {"toml"};
  }

  @Override
  public <T extends ConfigurablePojo<T>> PersistenceDelegate<T> create(
      Path filePath, boolean useComplexSaveWithComments) {
    return new TomlPersistenceDelegate<>(filePath, useComplexSaveWithComments);
  }
}
