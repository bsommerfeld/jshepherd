package de.bsommerfeld.jshepherd.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Adds comments to a configuration field or to the class (for file header). Each string in the array will be a separate
 * comment line, prefixed with '#'.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface Comment {
    /** The comments to the configuration value. */
    String[] value() default {""};
}
