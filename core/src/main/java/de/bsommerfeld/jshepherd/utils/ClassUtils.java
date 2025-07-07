package de.bsommerfeld.jshepherd.utils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClassUtils {
    /**
     * Gets all declared fields from the given class and its superclasses, up to (but not including) Object.class or a
     * specified stop class. Fields from superclasses appear first in the list.
     *
     * @param clazz     The class to introspect.
     * @param stopClass The class at which to stop ascending the hierarchy (e.g., ConfigurablePojo.class).
     *
     * @return A list of all relevant fields.
     */
    public static List<Field> getAllFieldsInHierarchy(Class<?> clazz, Class<?> stopClass) {
        List<Field> fields = new ArrayList<>();
        Class<?> currentClass = clazz;
        // Ascend until the stopClass or Object is reached
        while (currentClass != null && currentClass != Object.class &&
                (stopClass == null || !stopClass.equals(currentClass))) {
            fields.addAll(Arrays.asList(currentClass.getDeclaredFields()));
            currentClass = currentClass.getSuperclass();
        }
        return fields;
    }
}
