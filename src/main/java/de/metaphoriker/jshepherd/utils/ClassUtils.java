package de.metaphoriker.jshepherd.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClassUtils {
    /**
     * Retrieves the class hierarchy for the provided {@code class}.
     * @param clazz The class.
     * @return  list of classes in the hierarchy.
     */
    public static List<Class<?>> getHierarchy(Class<?> clazz) {
        List<Class<?>> classHierarchy = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            classHierarchy.add(clazz);
            clazz = clazz.getSuperclass();
        }
        Collections.reverse(classHierarchy); // super classes first
        return classHierarchy;
    }

}
