module de.bsommerfeld.jshepherd.json {
    exports de.bsommerfeld.jshepherd.json;
    
    requires de.bsommerfeld.jshepherd.core;
    requires com.fasterxml.jackson.databind;
    requires com.google.auto.service;
    
    provides de.bsommerfeld.jshepherd.core.PersistenceDelegateFactory
        with de.bsommerfeld.jshepherd.json.JsonPersistenceDelegateFactory;
}