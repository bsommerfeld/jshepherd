module de.bsommerfeld.jshepherd.properties {
    exports de.bsommerfeld.jshepherd.properties;
    
    requires de.bsommerfeld.jshepherd.core;
    requires com.google.auto.service;
    
    provides de.bsommerfeld.jshepherd.core.PersistenceDelegateFactory
        with de.bsommerfeld.jshepherd.properties.PropertiesPersistenceDelegateFactory;
}