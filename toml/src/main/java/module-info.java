module de.bsommerfeld.jshepherd.toml {
    exports de.bsommerfeld.jshepherd.toml;

    opens de.bsommerfeld.jshepherd.toml to de.bsommerfeld.jshepherd.core;

    requires de.bsommerfeld.jshepherd.core;
    requires java.logging;
    requires org.tomlj;
    requires com.google.auto.service;

    provides de.bsommerfeld.jshepherd.core.PersistenceDelegateFactory
            with de.bsommerfeld.jshepherd.toml.TomlPersistenceDelegateFactory;
}
