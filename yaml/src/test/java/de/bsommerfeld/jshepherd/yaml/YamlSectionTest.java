package de.bsommerfeld.jshepherd.yaml;

import de.bsommerfeld.jshepherd.core.ConfigurationLoader;
import de.bsommerfeld.jshepherd.yaml.SectionTestConfigs.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for @Section annotation YAML output generation.
 * Note: Full round-trip loading with complex structures is a work-in-progress.
 */
@DisplayName("YAML @Section Output Tests")
class YamlSectionTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("Section Output Generation")
    class SectionOutputTests {

        @Test
        @DisplayName("Section creates nested YAML structure with comments")
        void sectionCreatesNestedStructure() throws IOException {
            Path configPath = tempDir.resolve("server.yaml");

            ServerConfig config = ConfigurationLoader.from(configPath)
                    .withComments()
                    .load(ServerConfig::new);

            String content = Files.readString(configPath);

            // Root fields should be at top level
            assertTrue(content.contains("name: TestServer"), "Root field 'name' should be present");
            assertTrue(content.contains("port: 8080"), "Root field 'port' should be present");

            // Section should create nested structure
            assertTrue(content.contains("database:"), "Section header should be present");
            assertTrue(content.contains("  host: localhost"), "Nested field should be indented");
            assertTrue(content.contains("  port: 5432"), "Nested field with same name as root should be indented");

            // Comments should be present
            assertTrue(content.contains("# Server Configuration"), "Class comment should be present");
            assertTrue(content.contains("# Database connection settings"), "Section comment should be present");
            assertTrue(content.contains("# Database hostname"), "Nested field comment should be present");
        }

        @Test
        @DisplayName("Key after Section does NOT belong to section in output")
        void keyAfterSectionIsAtRootLevel() throws IOException {
            Path configPath = tempDir.resolve("mixed-order.yaml");

            MixedOrderConfig config = ConfigurationLoader.from(configPath)
                    .withComments()
                    .load(MixedOrderConfig::new);

            String content = Files.readString(configPath);

            // middle-value should be at root level (no indentation)
            assertTrue(content.contains("middle-value: root-level"),
                    "middle-value should be at root level");
            assertFalse(content.contains("  middle-value:"),
                    "middle-value should NOT be indented (not in section)");

            // trailing-value should also be at root level
            assertTrue(content.contains("trailing-value: 42"),
                    "trailing-value should be at root level");
        }

        @Test
        @DisplayName("Root fields come before sections in output")
        void rootFieldsBeforeSections() throws IOException {
            Path configPath = tempDir.resolve("order-test.yaml");

            MixedOrderConfig config = ConfigurationLoader.from(configPath)
                    .withComments()
                    .load(MixedOrderConfig::new);

            String content = Files.readString(configPath);

            int rootFieldPos = content.indexOf("middle-value:");
            int firstSectionPos = content.indexOf("first-section:");

            assertTrue(rootFieldPos < firstSectionPos,
                    "Root fields should appear before sections regardless of declaration order");
        }

        @Test
        @DisplayName("Config with only sections (no root keys)")
        void configWithOnlySections() throws IOException {
            Path configPath = tempDir.resolve("sections-only.yaml");

            SectionsOnlyConfig config = ConfigurationLoader.from(configPath)
                    .withComments()
                    .load(SectionsOnlyConfig::new);

            String content = Files.readString(configPath);

            assertTrue(content.contains("alpha:"), "Alpha section should be present");
            assertTrue(content.contains("beta:"), "Beta section should be present");
            assertTrue(content.contains("# Alpha section"), "Alpha section comment should be present");
        }

        @Test
        @DisplayName("Explicit section name overrides field name")
        void explicitSectionName() throws IOException {
            Path configPath = tempDir.resolve("explicit-name.yaml");

            ExplicitNameConfig config = ConfigurationLoader.from(configPath)
                    .withComments()
                    .load(ExplicitNameConfig::new);

            String content = Files.readString(configPath);

            assertTrue(content.contains("explicit-section-name:"),
                    "Explicit section name should be used");
            assertFalse(content.contains("myField:"),
                    "Field name should NOT be used when explicit name given");
        }

        @Test
        @DisplayName("Null section is skipped gracefully")
        void nullSectionSkipped() throws IOException {
            Path configPath = tempDir.resolve("null-section.yaml");

            NullSectionConfig config = ConfigurationLoader.from(configPath)
                    .withComments()
                    .load(NullSectionConfig::new);

            String content = Files.readString(configPath);

            assertTrue(content.contains("name: test"), "Regular field should be present");
            assertFalse(content.contains("optional:"), "Null section should not appear");
        }

        @Test
        @DisplayName("Section with collections works correctly")
        void sectionWithCollections() throws IOException {
            Path configPath = tempDir.resolve("collections.yaml");

            SectionWithCollections config = ConfigurationLoader.from(configPath)
                    .withComments()
                    .load(SectionWithCollections::new);

            String content = Files.readString(configPath);

            assertTrue(content.contains("data:"), "Section should be present");
            assertTrue(content.contains("tags:"), "List field should be present");
        }
    }

    @Nested
    @DisplayName("Section Comments")
    class SectionCommentTests {

        @Test
        @DisplayName("Comments on both section and nested fields")
        void commentsOnBothLevels() throws IOException {
            Path configPath = tempDir.resolve("comments.yaml");

            ServerConfig config = ConfigurationLoader.from(configPath)
                    .withComments()
                    .load(ServerConfig::new);

            String content = Files.readString(configPath);

            // Section-level comment
            assertTrue(content.contains("# Database connection settings"),
                    "Section comment should be present");

            // Nested field comments
            assertTrue(content.contains("# Database hostname"),
                    "Nested field comment should be present");
            assertTrue(content.contains("# Database port"),
                    "Another nested field comment should be present");
        }
    }

    @Nested
    @DisplayName("Empty Collections (issue #12)")
    class EmptyCollectionTests {

        @Test
        @DisplayName("Empty collections are written inline, not split across two lines")
        void emptyCollectionsAreInline() throws IOException {
            Path configPath = tempDir.resolve("empty-with-comments.yaml");

            ConfigurationLoader.from(configPath)
                    .withComments()
                    .load(EmptyCollectionConfig::new);

            String content = Files.readString(configPath);

            assertFalse(content.contains("[\n"), "Empty list must not break the line after '['");
            assertFalse(content.contains("{\n"), "Empty map must not break the line after '{'");

            assertTrue(content.contains("root-list: []"), "Root list should be inline");
            assertTrue(content.contains("root-map: {}"), "Root map should be inline");
            assertTrue(content.contains("  list-of-items: []"), "Section list should be inline and indented");
            assertTrue(content.contains("      list-of-items: []"),
                    "List in a third-level section should keep the section's indent");
            assertTrue(content.contains("      map-of-items: {}"),
                    "Map in a third-level section should keep the section's indent");
        }

        @Test
        @DisplayName("Empty collections in nested sections stay loadable")
        void emptyCollectionsRoundTrip() throws IOException {
            Path configPath = tempDir.resolve("empty-roundtrip.yaml");

            ConfigurationLoader.from(configPath)
                    .withComments()
                    .load(EmptyCollectionConfig::new);

            EmptyCollectionConfig reloaded = ConfigurationLoader.from(configPath)
                    .withComments()
                    .load(EmptyCollectionConfig::new);

            assertTrue(reloaded.getRootList().isEmpty(), "Root list should reload as empty");
            assertTrue(reloaded.getRootMap().isEmpty(), "Root map should reload as empty");
            assertTrue(reloaded.getSectionFirst().getListOfItems().isEmpty(),
                    "Section list should reload as empty");
            assertTrue(reloaded.getSectionFirst().getFirst().getFirst().getListOfItems().isEmpty(),
                    "Deeply nested list should reload as empty");
            assertTrue(reloaded.getSectionFirst().getFirst().getFirst().getMapOfItems().isEmpty(),
                    "Deeply nested map should reload as empty");
        }

        @Test
        @DisplayName("Empty collections are inline without comments too")
        void emptyCollectionsInlineWithoutComments() throws IOException {
            Path configPath = tempDir.resolve("empty-simple.yaml");

            ConfigurationLoader.from(configPath)
                    .withoutComments()
                    .load(EmptyCollectionConfig::new);

            String content = Files.readString(configPath);

            assertFalse(content.contains("[\n"), "Empty list must not break the line after '['");
            assertFalse(content.contains("{\n"), "Empty map must not break the line after '{'");
            // List.of() returns a shared singleton, so without special handling
            // SnakeYAML emits the second occurrence as an alias to the first.
            assertFalse(content.contains("&id"), "Empty collections must not be emitted as anchors");
            assertFalse(content.contains("*id"), "Empty collections must not be emitted as aliases");
        }
    }
}
