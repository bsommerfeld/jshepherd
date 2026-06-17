package de.bsommerfeld.jshepherd.integration;

import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.annotation.PostInject;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.ConfigurationLoader;
import de.bsommerfeld.jshepherd.core.LoadIssue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that unconvertible values (e.g. {@code port = "abc"} for an int
 * field) are not silently dropped: the field keeps its default, and the issue
 * is visible via getLastLoadIssues() — including inside @PostInject methods,
 * so users can implement their own strict validation.
 *
 * <p>Applies to TOML and Properties, which bind per-key. YAML and JSON bind
 * the whole document and fail the entire load on a type mismatch instead.</p>
 */
class LoadIssuesTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"toml", "properties"})
    @DisplayName("Unconvertible value is reported via getLastLoadIssues and visible in @PostInject")
    void badValue_isReportedAndVisibleInPostInject(String format) throws Exception {
        Path configPath = tempDir.resolve("issues." + format);
        Files.writeString(configPath, switch (format) {
            case "toml" -> """
                    host = "localhost"
                    port = "abc"
                    """;
            case "properties" -> """
                    host=localhost
                    port=abc
                    """;
            default -> throw new IllegalArgumentException(format);
        });

        IssueConfig config = ConfigurationLoader.from(configPath)
                .withoutComments()
                .load(IssueConfig::new);

        // Field keeps its default, valid keys still load
        assertEquals("localhost", config.host);
        assertEquals(8080, config.port, "Bad value should leave the default untouched");

        // Issue is reported on the instance...
        assertEquals(1, config.getLastLoadIssues().size(), "Exactly one issue expected for " + format);
        LoadIssue issue = config.getLastLoadIssues().get(0);
        assertEquals("port", issue.key());
        assertEquals("abc", issue.rawValue());

        // ...and was already visible while @PostInject ran
        assertNotNull(config.issuesSeenInPostInject, "@PostInject should have run");
        assertEquals(1, config.issuesSeenInPostInject.size(),
                "@PostInject should see the load issues for " + format);
    }

    @ParameterizedTest(name = "Format: {0}")
    @ValueSource(strings = {"toml", "properties"})
    @DisplayName("Clean load reports no issues")
    void cleanLoad_reportsNoIssues(String format) throws Exception {
        Path configPath = tempDir.resolve("clean." + format);
        Files.writeString(configPath, switch (format) {
            case "toml" -> "port = 9090\n";
            case "properties" -> "port=9090\n";
            default -> throw new IllegalArgumentException(format);
        });

        IssueConfig config = ConfigurationLoader.from(configPath)
                .withoutComments()
                .load(IssueConfig::new);

        assertEquals(9090, config.port);
        assertTrue(config.getLastLoadIssues().isEmpty(), "No issues expected for " + format);
    }

    public static class IssueConfig extends ConfigurablePojo<IssueConfig> {
        @Key("host")
        public String host = "default-host";

        @Key("port")
        public int port = 8080;

        public transient List<LoadIssue> issuesSeenInPostInject;

        @PostInject
        private void capture() {
            issuesSeenInPostInject = getLastLoadIssues();
        }
    }
}
