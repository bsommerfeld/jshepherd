package de.metaphoriker.coma.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigurationValue {
  /** The name of the configuration value inside the configuration file. */
  String name() default "";

  /** The description of the configuration value. */
  String[] description() default {""};
}
