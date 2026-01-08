module de.bsommerfeld.jshepherd.json {
    exports de.bsommerfeld.jshepherd.json;

    opens de.bsommerfeld.jshepherd.json to de.bsommerfeld.jshepherd.core, com.fasterxml.jackson.databind;

    requires de.bsommerfeld.jshepherd.core;
    requires java.logging;
    requires com.fasterxml.jackson.databind;
    requires com.google.auto.service;

    provides de.bsommerfeld.jshepherd.core.PersistenceDelegateFactory
            with de.bsommerfeld.jshepherd.json.JsonPersistenceDelegateFactory;
}
