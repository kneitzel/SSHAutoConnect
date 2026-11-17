package de.kneitzel;

import static org.junit.jupiter.api.Assertions.*;

import de.kneitzel.config.*;
import de.kneitzel.model.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;

class RepositoryTest {
    @TempDir Path temp;

    static AppConfig config() {
        AppConfig c = new AppConfig();
        Profile p = new Profile();
        p.setName("Office");
        SshConfig t = new SshConfig();
        t.setHostname("example.test");
        t.setUsername("user");
        p.getTargets().add(t);
        c.getProfiles().add(p);
        return c;
    }

    @Test
    void savesAndLoadsActualRepositoryAndPreservesIdentity() throws Exception {
        Path file = temp.resolve("nested/config.json");
        ConfigurationRepository r = new ConfigurationRepository(file);
        AppConfig c = config();
        r.save(c);
        AppConfig loaded = r.load();
        assertEquals(c.getProfiles().getFirst().getId(), loaded.getProfiles().getFirst().getId());
        assertFalse(Files.readString(file).contains("connected"));
        loaded.getProfiles().getFirst().setName("Renamed");
        r.save(loaded);
        assertEquals(c.getProfiles().getFirst().getId(), r.load().getProfiles().getFirst().getId());
    }

    @Test
    void malformedFileLocksSaveWithoutChangingBytes() throws Exception {
        Path file = temp.resolve("config.json");
        Files.writeString(file, "broken JSON");
        ConfigurationRepository r = new ConfigurationRepository(file);
        assertThrows(IOException.class, r::load);
        assertThrows(IOException.class, () -> r.save(config()));
        assertEquals("broken JSON", Files.readString(file));
    }

    @Test
    void unsupportedVersionAndMissingVersionCannotLoad() throws Exception {
        for (String text : new String[] {"{\"schemaVersion\":999,\"profiles\":[]}", "{}", "null"}) {
            Path file = temp.resolve("config.json");
            Files.writeString(file, text);
            assertThrows(IOException.class, () -> new ConfigurationRepository(file).load());
            assertEquals(text, Files.readString(file));
        }
    }

    @Test
    void rejectsMissingStableIdsAndFractionalSchemaVersions() throws Exception {
        var gson = new com.google.gson.Gson();
        var tree = gson.toJsonTree(config()).getAsJsonObject();
        tree.getAsJsonArray("profiles").get(0).getAsJsonObject().remove("id");
        Path file = temp.resolve("config.json");
        Files.writeString(file, tree.toString());
        assertThrows(IOException.class, () -> new ConfigurationRepository(file).load());
        Files.writeString(file, "{\"schemaVersion\":1.5,\"profiles\":[]}");
        assertThrows(IOException.class, () -> new ConfigurationRepository(file).load());
    }

    @Test
    void failedReplacementPreservesPreviousDocumentAndRemovesTemporaryFile() throws Exception {
        Path file = temp.resolve("config.json");
        new ConfigurationRepository(file).save(config());
        String before = Files.readString(file);
        ConfigurationRepository failing =
                new ConfigurationRepository(file) {
                    @Override
                    protected void replace(Path a, Path b) throws IOException {
                        throw new IOException("injected failure");
                    }
                };
        assertThrows(IOException.class, () -> failing.save(config()));
        assertEquals(before, Files.readString(file));
        try (var files = Files.list(temp)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void rejectsDuplicateNamesDanglingAssignmentsAndDuplicatePorts() {
        AppConfig c = config();
        Profile duplicate = config().getProfiles().getFirst();
        c.getProfiles().add(duplicate);
        assertThrows(IllegalArgumentException.class, () -> Configurations.validate(c));
        c.getProfiles().removeLast();
        SshConfig t = c.getProfiles().getFirst().getTargets().getFirst();
        t.getAssignments().add(new CommandAssignment("missing", CommandEvent.AFTER_SSH_CONNECTED));
        assertThrows(IllegalArgumentException.class, () -> Configurations.validate(c));
        t.getAssignments().clear();
        t.getPortMappings().add(new PortMapping());
        t.getPortMappings().add(new PortMapping());
        assertThrows(IllegalArgumentException.class, () -> Configurations.validate(c));
    }

    @Test
    void nativeDirectoryResolution() {
        Path home = temp.resolve("home");
        assertEquals(
                home.resolve("Library/Application Support/SSHAutoConnect"),
                de.neitzel.core.config.ApplicationDirectories.configurationDirectory(
                        "SSHAutoConnect", "macOS", home, java.util.Map.of()));
        assertEquals(
                temp.resolve("xdg/SSHAutoConnect"),
                de.neitzel.core.config.ApplicationDirectories.configurationDirectory(
                        "SSHAutoConnect",
                        "Linux",
                        home,
                        java.util.Map.of("XDG_CONFIG_HOME", temp.resolve("xdg").toString())));
        assertEquals(
                temp.resolve("roaming/SSHAutoConnect"),
                de.neitzel.core.config.ApplicationDirectories.configurationDirectory(
                        "SSHAutoConnect",
                        "Windows 11",
                        home,
                        java.util.Map.of("APPDATA", temp.resolve("roaming").toString())));
    }

    @Test
    void migrationPreservesSourceAndConvertsNamedCommandsWithoutEnablingStops() throws Exception {
        Path legacy = temp.resolve("legacy.json");
        String original =
                "{\"Office\":{\"hostname\":\"host\",\"username\":\"user\",\"password\":\"private-secret\",\"connected\":true,\"autoConnect\":true,\"portMappings\":[{\"localPort\":8080,\"remotePort\":80,\"startCommand\":\"start"
                    + " service\",\"stopCommand\":\"stop service\"}]}}";
        Files.writeString(legacy, original);
        var review = LegacyMigration.read(legacy);
        SshConfig t = review.config().getProfiles().getFirst().getTargets().getFirst();
        assertTrue(t.isAutoConnect());
        assertFalse(t.isSaveCredentials());
        assertEquals("private-secret", review.credentials().get(t.getId()).password());
        assertEquals("Start 1:", t.getCommands().getFirst().getName());
        assertEquals("Stop 1:", t.getCommands().getLast().getName());
        assertEquals(1, t.getPortMappings().getFirst().getAssignments().size());
        assertEquals(
                t.getCommands().getFirst().getId(),
                t.getPortMappings().getFirst().getAssignments().getFirst().getCommandId());
        assertEquals(original, Files.readString(legacy));
        Path migrated = temp.resolve("config.json");
        new ConfigurationRepository(migrated).save(review.config());
        assertFalse(Files.readString(migrated).contains("private-secret"));
        assertFalse(review.toString().contains("private-secret"));
    }

    @Test
    void invalidLegacyEntriesRemainForCorrectionOrExplicitExclusion() throws Exception {
        Path legacy = temp.resolve("legacy.json");
        Files.writeString(
                legacy, "{\"bad\":null,\"valid\":{\"hostname\":\"host\",\"username\":\"user\"}}");
        var review = LegacyMigration.read(legacy);
        assertEquals(2, review.config().getProfiles().size());
        assertThrows(
                IllegalArgumentException.class, () -> Configurations.validate(review.config()));
        review.config().getProfiles().removeFirst();
        assertDoesNotThrow(() -> Configurations.validate(review.config()));
    }
}
