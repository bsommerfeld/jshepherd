// Open module so JUnit can reflect into test classes without explicit opens per package.
open module de.bsommerfeld.jshepherd.integration.test {
    requires de.bsommerfeld.jshepherd.core;
    requires de.bsommerfeld.jshepherd.json;
    requires de.bsommerfeld.jshepherd.yaml;
    requires de.bsommerfeld.jshepherd.toml;

    requires org.junit.jupiter.api;
    requires org.junit.jupiter.params;

    // Required to call ServiceLoader.load(PersistenceDelegateFactory.class) directly.
    uses de.bsommerfeld.jshepherd.core.PersistenceDelegateFactory;
}
