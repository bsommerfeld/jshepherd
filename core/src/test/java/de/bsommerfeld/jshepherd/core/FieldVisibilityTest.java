package de.bsommerfeld.jshepherd.core;

import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.annotation.Section;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class FieldVisibilityTest {

    private final TestConfig config = new TestConfig();
    private final FieldVisibility visibility = new FieldVisibility(config);

    private Set<String> hiddenNames() {
        return visibility.hiddenFields().stream().map(Field::getName).collect(Collectors.toSet());
    }

    @Test
    void nothingIsHiddenByDefault() {
        assertTrue(visibility.hiddenFields().isEmpty());
        assertFalse(visibility.isHidden("log-changes"));
    }

    @Test
    void hideAndShow_toggleKeysSectionsAndNestedKeys() {
        visibility.hide("log-changes", "debug-options", "database.trace-sql");

        assertEquals(Set.of("logChanges", "debugOptions", "traceSql"), hiddenNames());
        assertTrue(visibility.isHidden("database.trace-sql"));

        visibility.show("log-changes", "database.trace-sql");

        assertEquals(Set.of("debugOptions"), hiddenNames());
    }

    @Test
    void keyWithoutValue_fallsBackToFieldName() {
        visibility.hide("plainName");

        assertEquals(Set.of("plainName"), hiddenNames());
    }

    @Test
    void keyContainingDots_isMatchedAsAWhole() {
        visibility.hide("dotted.key");

        assertEquals(Set.of("dottedKey"), hiddenNames());
    }

    @Test
    void keyInsideNullSection_isResolvedThroughTheDeclaredType() {
        config.database = null;

        visibility.hide("database.trace-sql");

        assertEquals(Set.of("traceSql"), hiddenNames());
    }

    @Test
    void unknownKey_failsRightAway() {
        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> visibility.hide("does-not-exist"));
        assertTrue(e.getMessage().contains("does-not-exist"), e.getMessage());
        assertTrue(e.getMessage().contains("TestConfig"), e.getMessage());

        assertThrows(ConfigurationException.class, () -> visibility.show("database.nope"));
        assertThrows(ConfigurationException.class, () -> visibility.hide("features.entry"));
        assertThrows(ConfigurationException.class, () -> visibility.hide("notPersisted"));
    }

    @Test
    void fileIsOutdated_untilTheVisibilityIsWritten() {
        assertFalse(visibility.isFileOutdated());

        visibility.hide("log-changes");
        assertTrue(visibility.isFileOutdated());

        visibility.markWritten();
        assertFalse(visibility.isFileOutdated());

        visibility.show("log-changes");
        assertTrue(visibility.isFileOutdated());

        visibility.hide("log-changes");
        assertFalse(visibility.isFileOutdated(), "Back to what the file shows");
    }

    @Test
    void bindVisibility_appliesRightAwayAndFollowsTheCondition() {
        visibility.bindVisibility("log-changes", () -> config.debug);
        visibility.bindVisibility("database.trace-sql", () -> config.debug);

        assertEquals(Set.of("logChanges", "traceSql"), hiddenNames());
        assertTrue(visibility.isFileOutdated());

        config.debug = true;
        assertFalse(visibility.isHidden("log-changes"), "isHidden reflects the condition as it is now");
        assertEquals(Set.of("logChanges", "traceSql"), hiddenNames(), "Applied on the next evaluation only");

        visibility.applyBindings();
        assertTrue(visibility.hiddenFields().isEmpty());
    }

    @Test
    void bindVisibility_winsOverShowAndHideOnTheNextEvaluation() {
        visibility.bindVisibility("log-changes", () -> config.debug);

        visibility.show("log-changes");
        assertTrue(visibility.hiddenFields().isEmpty());

        visibility.applyBindings();
        assertEquals(Set.of("logChanges"), hiddenNames());
    }

    @Test
    void bindVisibility_again_replacesThePreviousCondition() {
        visibility.bindVisibility("log-changes", () -> false);
        visibility.bindVisibility("log-changes", () -> true);

        visibility.applyBindings();
        assertTrue(visibility.hiddenFields().isEmpty());
    }

    @Test
    void bindVisibility_rejectsUnknownKeysAndMissingConditions() {
        assertThrows(ConfigurationException.class, () -> visibility.bindVisibility("does-not-exist", () -> true));
        assertThrows(NullPointerException.class, () -> visibility.bindVisibility("log-changes", null));
    }

    // ==================== TEST CONFIGS ====================

    static class TestConfig {
        @Key("debug")
        boolean debug = false;

        @Key("log-changes")
        boolean logChanges = false;

        @Key
        String plainName = "value";

        @Key("dotted.key")
        String dottedKey = "value";

        @Key("features")
        Map<String, Boolean> features = Map.of("entry", true);

        String notPersisted = "value";

        @Section("database")
        DatabaseSettings database = new DatabaseSettings();

        @Section("debug-options")
        DebugOptions debugOptions = new DebugOptions();
    }

    static class DatabaseSettings {
        @Key("pool-size")
        int poolSize = 10;

        @Key("trace-sql")
        boolean traceSql = false;
    }

    static class DebugOptions {
        @Key("show-referrals")
        boolean showReferrals = true;
    }
}
