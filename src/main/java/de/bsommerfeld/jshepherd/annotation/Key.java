package de.bsommerfeld.jshepherd.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the key name to be used for this field in the configuration file. If the value is empty, the field name
 * will be used.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Key {
    /** The name of the configuration value inside the configuration file. */
    String value() default "";
}
