package de.metaphoriker.jshepherd.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as the opener of a section in a config.
 * This annotation is used alongside {@link Comment} in order to allow the opener field to have its own comment.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CommentSection {
    /** The description of the section. */
    String[] value();
}
