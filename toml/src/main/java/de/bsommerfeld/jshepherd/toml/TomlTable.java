package de.bsommerfeld.jshepherd.toml;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * TOML-specific annotation to mark a field to be (de)serialized as a TOML table.
 * <p>
 * This annotation has no effect in other formats (YAML/JSON/Properties). It is processed
 * only by the TOML module.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface TomlTable {
    /**
     * Optional explicit table name. If empty, the table name is derived from the field's @Key value
     * or the field name if no @Key value is provided.
     */
    String value() default "";
}
