package de.bsommerfeld.jshepherd.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a comment block that can precede a group of configuration options. Typically placed on the first field of a
 * new conceptual section. A blank line will be inserted before a new section if it's not the first one.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CommentSection {
    /** The description of the section. */
    String[] value();
}
