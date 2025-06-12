module de.bsommerfeld.jshepherd.toml {
    exports de.bsommerfeld.jshepherd.toml;
    
    requires de.bsommerfeld.jshepherd.core;
    requires org.tomlj;
    requires com.google.auto.service;
    
    provides de.bsommerfeld.jshepherd.core.PersistenceDelegateFactory
        with de.bsommerfeld.jshepherd.toml.TomlPersistenceDelegateFactory;
}