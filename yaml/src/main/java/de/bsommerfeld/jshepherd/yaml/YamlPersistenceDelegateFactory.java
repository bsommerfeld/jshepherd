package de.bsommerfeld.jshepherd.yaml;

import com.google.auto.service.AutoService;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.PersistenceDelegate;
import de.bsommerfeld.jshepherd.core.PersistenceDelegateFactory;
import java.nio.file.Path;

/**
 * A factory class for creating YAML-specific persistence delegates. This implementation of {@code
 * PersistenceDelegateFactory} supports persistence operations for configuration POJOs in YAML
 * format.
 */
@AutoService(PersistenceDelegateFactory.class)
public class YamlPersistenceDelegateFactory implements PersistenceDelegateFactory {

  @Override
  public String[] getSupportedExtensions() {
    return new String[] {"yml", "yaml"};
  }

  @Override
  public <T extends ConfigurablePojo<T>> PersistenceDelegate<T> create(
      Path filePath, boolean useComplexSaveWithComments) {
    return new YamlPersistenceDelegate<>(filePath, useComplexSaveWithComments);
  }
}
