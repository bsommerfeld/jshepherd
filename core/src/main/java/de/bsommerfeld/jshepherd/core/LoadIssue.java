package de.bsommerfeld.jshepherd.core;

/**
 * Describes a single value that could not be applied during a load or reload —
 * for example a string that does not parse into a numeric field, or an unknown
 * enum constant.
 *
 * <p>When such an issue occurs, the affected field keeps its current (default)
 * value, a warning is logged, and the issue is recorded on the configuration
 * POJO. Retrieve the issues via {@link ConfigurablePojo#getLastLoadIssues()} —
 * typically inside a {@code @PostInject} method to fail fast:</p>
 *
 * <pre>{@code
 * @PostInject
 * private void validate() {
 *     if (!getLastLoadIssues().isEmpty()) {
 *         throw new IllegalStateException("Invalid config: " + getLastLoadIssues());
 *     }
 * }
 * }</pre>
 *
 * @param key        the configuration key that failed to apply
 * @param rawValue   the raw value found in the file (may be null)
 * @param targetType the Java type of the field the value was meant for
 * @param message    a human-readable description of what went wrong
 */
public record LoadIssue(String key, Object rawValue, Class<?> targetType, String message) {

    @Override
    public String toString() {
        return "'" + key + "' = '" + rawValue + "' (expected " + targetType.getSimpleName() + "): " + message;
    }
}
