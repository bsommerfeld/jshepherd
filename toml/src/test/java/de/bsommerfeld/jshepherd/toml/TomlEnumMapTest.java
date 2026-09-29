package de.bsommerfeld.jshepherd.toml;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.annotation.Section;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.ConfigurationLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Maps keyed by enums (issue #14): the generated TOML must be valid and the
 * keys must come back as enum constants, both at root level and inside a
 * {@code @Section}.
 */
@DisplayName("TOML Enum-Keyed Map Tests")
class TomlEnumMapTest {

    @TempDir
    Path tempDir;

    enum FlagType {
        PVP("PVP"),
        MOBS("Mobs"),
        EXPLOSIONS("Explosions");

        private final String commonName;

        FlagType(String commonName) {
            this.commonName = commonName;
        }

        // Must not leak into the file; enums are always written by name()
        @Override
        public String toString() {
            return commonName;
        }
    }

    static class RootConfig extends ConfigurablePojo<RootConfig> {
        @Key("default_flags")
        @Comment({"The flags a quarter gets when it's created",
                "This will not affect already existing quarters"})
        private Map<FlagType, Boolean> defaultFlags = Map.of(FlagType.MOBS, true, FlagType.PVP, false);

        @Key("flag_weights")
        private Map<FlagType, Integer> flagWeights = Map.of(FlagType.EXPLOSIONS, 3);
    }

    static class SectionConfig extends ConfigurablePojo<SectionConfig> {
        @Section("quarters")
        private QuarterSettings quarters = new QuarterSettings();
    }

    static class QuarterSettings {
        @Key("allowed_flags")
        private List<FlagType> allowedFlags = List.of(FlagType.PVP, FlagType.MOBS, FlagType.EXPLOSIONS);

        @Key("default_flags")
        @Comment({"The flags a quarter gets when it's created",
                "This will not affect already existing quarters"})
        private Map<FlagType, Boolean> defaultFlags = Map.of(FlagType.MOBS, true, FlagType.PVP, false);

        @Key("max_size")
        private int maxSize = 64;
    }

    static class MapSectionConfig extends ConfigurablePojo<MapSectionConfig> {
        @Section("flags")
        private Map<FlagType, Boolean> flags = Map.of(FlagType.PVP, true);
    }

    @Test
    @DisplayName("Section that is itself an enum-keyed map round-trips")
    void mapSectionRoundTrip() throws IOException {
        Path configPath = tempDir.resolve("map-section.toml");

        ConfigurationLoader.from(configPath).withComments().load(MapSectionConfig::new);

        String content = Files.readString(configPath);
        assertValidToml(content);
        assertTrue(content.contains("[flags]\nPVP = true"), content);

        MapSectionConfig reloaded = ConfigurationLoader.load(configPath, MapSectionConfig::new);
        assertEquals(Map.of(FlagType.PVP, true), reloaded.flags);
    }

    @Test
    @DisplayName("Root enum-keyed map is written by constant name and reloads as enum keys")
    void rootEnumMapRoundTrip() throws IOException {
        Path configPath = tempDir.resolve("root.toml");

        ConfigurationLoader.from(configPath).withComments().load(RootConfig::new);

        String content = Files.readString(configPath);
        assertValidToml(content);
        assertTrue(content.contains("MOBS = true"), content);
        assertTrue(content.contains("PVP = false"), content);
        assertFalse(content.contains("Mobs"), "toString() must not be used for keys:\n" + content);

        RootConfig reloaded = ConfigurationLoader.load(configPath, RootConfig::new);
        assertEquals(Map.of(FlagType.MOBS, true, FlagType.PVP, false), reloaded.defaultFlags);
        assertEquals(Map.of(FlagType.EXPLOSIONS, 3), reloaded.flagWeights);
        assertEquals(Boolean.TRUE, reloaded.defaultFlags.get(FlagType.MOBS));
    }

    @Test
    @DisplayName("Enum-keyed map inside a section produces valid TOML with comments")
    void sectionEnumMapWithComments() throws IOException {
        Path configPath = tempDir.resolve("section-comments.toml");

        ConfigurationLoader.from(configPath).withComments().load(SectionConfig::new);

        String content = Files.readString(configPath);
        assertValidToml(content);
        assertTrue(content.contains("MOBS = true"), content);
        assertTrue(content.contains("max_size = 64"), "Fields after the map must still be written:\n" + content);

        SectionConfig reloaded = ConfigurationLoader.load(configPath, SectionConfig::new);
        assertEquals(Map.of(FlagType.MOBS, true, FlagType.PVP, false), reloaded.quarters.defaultFlags);
        assertEquals(List.of(FlagType.PVP, FlagType.MOBS, FlagType.EXPLOSIONS), reloaded.quarters.allowedFlags);
        assertEquals(64, reloaded.quarters.maxSize);
    }

    @Test
    @DisplayName("Enum-keyed map inside a section produces valid TOML without comments")
    void sectionEnumMapSimple() throws IOException {
        Path configPath = tempDir.resolve("section-simple.toml");

        ConfigurationLoader.load(configPath, SectionConfig::new);

        String content = Files.readString(configPath);
        assertValidToml(content);

        SectionConfig reloaded = ConfigurationLoader.load(configPath, SectionConfig::new);
        assertEquals(Map.of(FlagType.MOBS, true, FlagType.PVP, false), reloaded.quarters.defaultFlags);
    }

    @Test
    @DisplayName("Hand-written enum-keyed map loads into enum keys")
    void handWrittenEnumMapLoads() throws IOException {
        Path configPath = tempDir.resolve("hand-written.toml");
        Files.writeString(configPath, """
                [quarters]
                default_flags = { EXPLOSIONS = true, PVP = true }
                """);

        SectionConfig config = ConfigurationLoader.load(configPath, SectionConfig::new);

        assertEquals(Map.of(FlagType.EXPLOSIONS, true, FlagType.PVP, true), config.quarters.defaultFlags);
    }

    private static void assertValidToml(String content) {
        TomlParseResult result = Toml.parse(content);
        assertFalse(result.hasErrors(), () -> "Invalid TOML " + result.errors() + ":\n" + content);
    }
}
