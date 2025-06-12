module de.bsommerfeld.jshepherd.yaml {
    exports de.bsommerfeld.jshepherd.yaml;
    opens de.bsommerfeld.jshepherd.yaml to de.bsommerfeld.jshepherd.core, org.yaml.snakeyaml;

    requires de.bsommerfeld.jshepherd.core;
    requires org.yaml.snakeyaml;
    requires com.google.auto.service;

    provides de.bsommerfeld.jshepherd.core.PersistenceDelegateFactory
        with de.bsommerfeld.jshepherd.yaml.YamlPersistenceDelegateFactory;
}
