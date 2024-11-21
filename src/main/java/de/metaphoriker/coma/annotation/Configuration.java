package de.metaphoriker.coma.annotation;

import de.metaphoriker.coma.ConfigurationType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Configuration {
  /**
   * Specifies the fully qualified name of the configuration file.
   *
   * <p>This represents the file name, including the extension.
   *
   * <p>Example usage:
   *
   * <pre>
   *     {@code fileName = "config.yml"}
   * </pre>
   */
  String fileName();

  /**
   * Specifies the type of configuration format.
   *
   * @return the default configuration type, which is ConfigurationType.YAML.
   */
  ConfigurationType type() default ConfigurationType.YAML;
}
