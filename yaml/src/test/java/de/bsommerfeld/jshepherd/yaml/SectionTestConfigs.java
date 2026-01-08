package de.bsommerfeld.jshepherd.yaml;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.annotation.Section;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;

import java.util.List;
import java.util.Map;

/**
 * Test configurations for @Section annotation tests.
 */
class SectionTestConfigs {

    // ==================== BASIC SECTION TEST ====================

    @Comment("Server Configuration")
    static class ServerConfig extends ConfigurablePojo<ServerConfig> {

        @Key("name")
        @Comment("Server name")
        private String name = "TestServer";

        @Key("port")
        private int port = 8080;

        @Comment("Database connection settings")
        @Section("database")
        private DatabaseSettings database = new DatabaseSettings();

        @Comment("Cache configuration")
        @Section("cache")
        private CacheSettings cache = new CacheSettings();

        ServerConfig() {
        }

        String getName() {
            return name;
        }

        void setName(String name) {
            this.name = name;
        }

        int getPort() {
            return port;
        }

        void setPort(int port) {
            this.port = port;
        }

        DatabaseSettings getDatabase() {
            return database;
        }

        CacheSettings getCache() {
            return cache;
        }
    }

    static class DatabaseSettings {
        @Key("host")
        @Comment("Database hostname")
        private String host = "localhost";

        @Key("port")
        @Comment("Database port")
        private int port = 5432;

        @Key("username")
        private String username = "admin";

        DatabaseSettings() {
        }

        String getHost() {
            return host;
        }

        void setHost(String host) {
            this.host = host;
        }

        int getPort() {
            return port;
        }

        void setPort(int port) {
            this.port = port;
        }

        String getUsername() {
            return username;
        }
    }

    static class CacheSettings {
        @Key("max-size")
        private int maxSize = 1000;

        @Key("ttl-seconds")
        @Comment("Time to live in seconds")
        private int ttlSeconds = 300;

        CacheSettings() {
        }

        int getMaxSize() {
            return maxSize;
        }

        int getTtlSeconds() {
            return ttlSeconds;
        }
    }

    // ==================== SECTION AFTER KEY TEST ====================

    static class MixedOrderConfig extends ConfigurablePojo<MixedOrderConfig> {

        @Section("first-section")
        private SimpleSection firstSection = new SimpleSection();

        @Key("middle-value")
        @Comment("This is at root level, NOT in first-section")
        private String middleValue = "root-level";

        @Section("second-section")
        private SimpleSection secondSection = new SimpleSection();

        @Key("trailing-value")
        private int trailingValue = 42;

        MixedOrderConfig() {
        }

        SimpleSection getFirstSection() {
            return firstSection;
        }

        String getMiddleValue() {
            return middleValue;
        }

        SimpleSection getSecondSection() {
            return secondSection;
        }

        int getTrailingValue() {
            return trailingValue;
        }
    }

    static class SimpleSection {
        @Key("value")
        private String value = "section-value";

        SimpleSection() {
        }

        String getValue() {
            return value;
        }

        void setValue(String value) {
            this.value = value;
        }
    }

    // ==================== SECTION ONLY (NO ROOT KEYS) ====================

    static class SectionsOnlyConfig extends ConfigurablePojo<SectionsOnlyConfig> {

        @Comment("Alpha section")
        @Section("alpha")
        private SimpleSection alpha = new SimpleSection();

        @Comment("Beta section")
        @Section("beta")
        private SimpleSection beta = new SimpleSection();

        SectionsOnlyConfig() {
        }

        SimpleSection getAlpha() {
            return alpha;
        }

        SimpleSection getBeta() {
            return beta;
        }
    }

    // ==================== EXPLICIT SECTION NAME ====================

    static class ExplicitNameConfig extends ConfigurablePojo<ExplicitNameConfig> {

        @Key("field-name")
        @Section("explicit-section-name")
        private SimpleSection myField = new SimpleSection();

        ExplicitNameConfig() {
        }

        SimpleSection getMyField() {
            return myField;
        }
    }

    // ==================== NULL SECTION ====================

    static class NullSectionConfig extends ConfigurablePojo<NullSectionConfig> {

        @Key("name")
        private String name = "test";

        @Section("optional")
        private SimpleSection optional = null;

        NullSectionConfig() {
        }

        String getName() {
            return name;
        }

        SimpleSection getOptional() {
            return optional;
        }
    }

    // ==================== SECTION WITH COLLECTIONS ====================

    static class SectionWithCollections extends ConfigurablePojo<SectionWithCollections> {

        @Section("data")
        private CollectionSettings data = new CollectionSettings();

        SectionWithCollections() {
        }

        CollectionSettings getData() {
            return data;
        }
    }

    static class CollectionSettings {
        @Key("tags")
        private List<String> tags = List.of("tag1", "tag2");

        @Key("settings")
        private Map<String, String> settings = Map.of("key1", "value1");

        CollectionSettings() {
        }

        List<String> getTags() {
            return tags;
        }

        Map<String, String> getSettings() {
            return settings;
        }
    }
}
