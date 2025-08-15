package de.bsommerfeld.jshepherd.toml;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * TOML-specific annotation to mark a field to be (de)serialized as a TOML table (section).
 * This has no effect in other formats.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface TomlSection {
    /**
     * Optional explicit table name. If empty, the table name is derived from the field's @Key value
     * or the field name if no @Key value is provided.
     */
    String value() default "";
}
