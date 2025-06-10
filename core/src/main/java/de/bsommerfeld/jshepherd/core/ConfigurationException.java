package de.bsommerfeld.jshepherd.core;

/**
 * Custom runtime exception thrown for errors encountered during configuration loading, saving, parsing, or other
 * configuration-related operations within the JShepherd library.
 *
 * <p>This exception is used to indicate that a problem specific to the
 * configuration process has occurred, which may prevent the application from being configured correctly.</p>
 */
public class ConfigurationException extends RuntimeException {

    /**
     * Constructs a new configuration exception with the specified detail message. The cause is not initialized, and may
     * subsequently be initialized by a call to {@link #initCause}.
     *
     * @param message the detail message. The detail message is saved for later retrieval by the {@link #getMessage()}
     *                method.
     */
    public ConfigurationException(String message) {
        super(message);
    }

    /**
     * Constructs a new configuration exception with the specified detail message and cause.
     *
     * <p>Note that the detail message associated with {@code cause} is
     * *not* automatically incorporated in this runtime exception's detail message.</p>
     *
     * @param message the detail message (which is saved for later retrieval by the {@link #getMessage()} method).
     * @param cause   the cause (which is saved for later retrieval by the {@link #getCause()} method). (A {@code null}
     *                value is permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}