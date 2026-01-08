package de.bsommerfeld.jshepherd.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field to be serialized as a nested section/table in the configuration
 * file.
 *
 * <p>
 * The annotated field must be of a POJO type (not a primitive or
 * ConfigurablePojo).
 * Fields within the nested POJO can use {@link Key} and {@link Comment}
 * annotations
 * as usual.
 * </p>
 *
 * <p>
 * <b>Format-specific behavior:</b>
 * </p>
 * <ul>
 * <li><b>YAML:</b> Rendered as nested indented block</li>
 * <li><b>TOML:</b> Rendered as {@code [table]} section</li>
 * <li><b>JSON:</b> Rendered as nested object</li>
 * </ul>
 *
 * <p>
 * <b>Example:</b>
 * </p>
 * 
 * <pre>{@code
 * @Comment("Database connection settings")
 * @Section("database")
 * private DatabaseSettings database = new DatabaseSettings();
 *
 * public class DatabaseSettings {
 *     @Key("host")
 *     @Comment("Database hostname")
 *     private String host = "localhost";
 *
 *     @Key("port")
 *     private int port = 5432;
 * }
 * }</pre>
 *
 * <p>
 * <b>Note:</b> Section fields are serialized after all root-level {@code @Key}
 * fields.
 * A {@code @Key} field following a {@code @Section} does NOT belong to that
 * section.
 * </p>
 *
 * @see Key
 * @see Comment
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Section {
    /**
     * The section/table name in the configuration file.
     * If empty, the name is derived from the field's {@link Key} annotation,
     * or the field name itself if no {@code @Key} is present.
     */
    String value() default "";
}
