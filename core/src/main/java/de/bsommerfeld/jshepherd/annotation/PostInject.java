package de.bsommerfeld.jshepherd.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to be invoked after configuration field injection has completed.
 *
 * <p>Methods annotated with {@code @PostInject} are called after all {@code @Key} annotated
 * fields have been populated from the configuration file. Useful for validation, computing
 * derived properties, or other post-loading initialization.</p>
 *
 * <p>Examples:</p>
 * <pre>{@code
 * @PostInject
 * private void updateDerivedProperties() {
 *     this.serverUrl = "http://" + host + ":" + port;
 * }
 *
 * @PostInject
 * private void validateConfiguration() {
 *     if (port < 1 || port > 65535) {
 *         throw new IllegalArgumentException("Port must be between 1 and 65535");
 *     }
 * }
 * }</pre>
 *
 * <p>Requirements: Methods must take no parameters. Multiple methods per class are allowed.
 * Execution order is not guaranteed.</p>
 */

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PostInject {
}

