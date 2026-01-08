module de.bsommerfeld.jshepherd.core {
    exports de.bsommerfeld.jshepherd.core;
    exports de.bsommerfeld.jshepherd.annotation;
    exports de.bsommerfeld.jshepherd.utils;

    requires java.base;
    requires java.logging;

    uses de.bsommerfeld.jshepherd.core.PersistenceDelegateFactory;
}