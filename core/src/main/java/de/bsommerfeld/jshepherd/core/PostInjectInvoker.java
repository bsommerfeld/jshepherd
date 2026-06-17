package de.bsommerfeld.jshepherd.core;

import de.bsommerfeld.jshepherd.annotation.PostInject;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Invokes {@code @PostInject} methods across a class hierarchy. Shared by both
 * the extends-based API ({@code ConfigurablePojo}) and the plain
 * {@code @Configuration} API ({@code Config} handle).
 *
 * <p>Supported method shapes:</p>
 * <ul>
 * <li>no parameters</li>
 * <li>a single {@code List<LoadIssue>} parameter — receives the per-key issues
 * of the load that just completed, so plain POJOs (which have no
 * {@code getLastLoadIssues()} method) can validate them too</li>
 * </ul>
 */
final class PostInjectInvoker {

    private PostInjectInvoker() {
    }

    /**
     * @param target    the configuration object
     * @param stopClass hierarchy walk stops at this class (exclusive); null
     *                  walks up to Object
     * @param issues    the load issues to pass to single-parameter methods
     */
    static void invoke(Object target, Class<?> stopClass, List<LoadIssue> issues) {
        // Overridden methods are de-duplicated by name (methods are
        // parameterless or take the single well-known parameter).
        Set<String> invokedMethodNames = new HashSet<>();
        Class<?> currentClass = target.getClass();
        while (currentClass != null && currentClass != Object.class && currentClass != stopClass) {
            for (Method method : currentClass.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(PostInject.class) || !invokedMethodNames.add(method.getName())) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    if (method.getParameterCount() == 0) {
                        method.invoke(target);
                    } else if (method.getParameterCount() == 1
                            && method.getParameterTypes()[0].isAssignableFrom(List.class)) {
                        method.invoke(target, issues);
                    } else {
                        throw new ConfigurationException("@PostInject method '" + method.getName()
                                + "' must take no parameters or a single List<LoadIssue> parameter");
                    }
                } catch (ConfigurationException e) {
                    throw e;
                } catch (Exception e) {
                    throw new ConfigurationException("Failed to invoke @PostInject method: " + method.getName(), e);
                }
            }
            currentClass = currentClass.getSuperclass();
        }
    }
}
