package de.bsommerfeld.jshepherd.json;

import com.google.auto.service.AutoService;
import de.bsommerfeld.jshepherd.core.*;
import java.nio.file.Path;

/**
 * A factory class for creating JSON-specific persistence delegates. This implementation of {@code
 * PersistenceDelegateFactory} supports persistence operations for configuration POJOs in JSON
 * format.
 */
@AutoService(PersistenceDelegateFactory.class)
public class JsonPersistenceDelegateFactory implements PersistenceDelegateFactory {

  @Override
  public String[] getSupportedExtensions() {
    return new String[] {"json"};
  }

  @Override
  public <T extends ConfigurablePojo<T>> PersistenceDelegate<T> create(
      Path filePath, boolean useComplexSaveWithComments) {
    return new JsonPersistenceDelegate<>(filePath, useComplexSaveWithComments);
  }
}
