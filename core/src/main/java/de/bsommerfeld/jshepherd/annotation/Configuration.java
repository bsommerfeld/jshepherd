package de.bsommerfeld.jshepherd.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a plain class as a JShepherd configuration, allowing it to be loaded
 * without extending {@code ConfigurablePojo}.
 *
 * <p>Annotated classes are loaded via
 * {@code ConfigurationLoader.from(path).loadPlain(MyConfig::new)}, which
 * returns a {@code Config<T>} handle carrying the lifecycle operations
 * ({@code save()}, {@code reload()}, {@code getLastLoadIssues()}, auto-reload
 * control) — the POJO itself stays completely plain:</p>
 *
 * <pre>{@code
 * @Configuration
 * @Comment("Application settings")
 * public class AppConfig {
 *     @Key("port")
 *     private int port = 8080;
 * }
 *
 * Config<AppConfig> config = ConfigurationLoader.from(path).loadPlain(AppConfig::new);
 * AppConfig app = config.get();
 * config.save();
 * }</pre>
 *
 * <p>All other annotations ({@code @Key}, {@code @Comment}, {@code @Section},
 * {@code @PostInject}) work exactly as with the extends-based API.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Configuration {
}
