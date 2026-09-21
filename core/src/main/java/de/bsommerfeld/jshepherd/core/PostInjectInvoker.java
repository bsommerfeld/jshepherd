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
 * <li>a {@code List<LoadIssue>} parameter — receives the per-key issues
 * of the load that just completed, so plain POJOs (which have no
 * {@code getLastLoadIssues()} method) can validate them too</li>
 * <li>a {@link FieldVisibility} parameter — lets plain POJOs (which have no
 * {@code show(...)}/{@code hide(...)} methods) control which fields are
 * written to the file</li>
 * </ul>
 *
 * <p>Both parameters may be combined, in any order.</p>
 */
final class PostInjectInvoker {

    private PostInjectInvoker() {
    }

    /**
     * @param target    the configuration object
     * @param stopClass hierarchy walk stops at this class (exclusive); null
     *                  walks up to Object
     * @param issues     the load issues to pass to {@code List<LoadIssue>} parameters
     * @param visibility the field visibility to pass to {@code FieldVisibility}
     *                   parameters
     */
    static void invoke(Object target, Class<?> stopClass, List<LoadIssue> issues, FieldVisibility visibility) {
        // Overridden methods are de-duplicated by name (methods only take
        // the well-known parameters).
        Set<String> invokedMethodNames = new HashSet<>();
        Class<?> currentClass = target.getClass();
        while (currentClass != null && currentClass != Object.class && currentClass != stopClass) {
            for (Method method : currentClass.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(PostInject.class) || !invokedMethodNames.add(method.getName())) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    method.invoke(target, resolveArguments(method, issues, visibility));
                } catch (ConfigurationException e) {
                    throw e;
                } catch (Exception e) {
                    throw new ConfigurationException("Failed to invoke @PostInject method: " + method.getName(), e);
                }
            }
            currentClass = currentClass.getSuperclass();
        }
    }

    private static Object[] resolveArguments(Method method, List<LoadIssue> issues, FieldVisibility visibility) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            if (parameterTypes[i] == FieldVisibility.class) {
                arguments[i] = visibility;
            } else if (parameterTypes[i].isAssignableFrom(List.class)) {
                arguments[i] = issues;
            } else {
                throw new ConfigurationException("@PostInject method '" + method.getName()
                        + "' may only take a List<LoadIssue> and/or a FieldVisibility parameter");
            }
        }
        return arguments;
    }
}
