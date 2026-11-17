package de.kneitzel.config;

import com.google.gson.*;

import de.kneitzel.*;
import de.kneitzel.model.*;
import de.kneitzel.security.Credentials;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class LegacyMigration {
    public record Review(
            AppConfig config, Map<String, Credentials> credentials, List<String> notices) {
        @Override
        public String toString() {
            return "Migration review[credentials redacted]";
        }
    }

    public static Review read(Path legacy) throws IOException {
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(legacy));
            if (!parsed.isJsonObject())
                throw new IllegalArgumentException("Expected map of legacy entries");
            AppConfig config = new AppConfig();
            Map<String, Credentials> credentials = new HashMap<>();
            List<String> notices = new ArrayList<>();
            for (var entry : parsed.getAsJsonObject().entrySet()) {
                Profile p = new Profile();
                p.setName(entry.getKey());
                SshConfig t = new SshConfig();
                p.getTargets().add(t);
                config.getProfiles().add(p);
                if (!entry.getValue().isJsonObject()) {
                    notices.add(p.getName() + ": invalid entry; correct or explicitly exclude");
                    continue;
                }
                JsonObject o = entry.getValue().getAsJsonObject();
                try {
                    t.setName(string(o, "name", p.getName()));
                    t.setHostname(string(o, "hostname", ""));
                    t.setUsername(string(o, "username", ""));
                    t.setPort(o.has("port") ? o.get("port").getAsInt() : 22);
                    t.setPrivateKeyPath(string(o, "privateKeyPath", ""));
                    t.setAutoConnect(o.has("autoConnect") && o.get("autoConnect").getAsBoolean());
                    t.setUploadDirectory(string(o, "uploadDirectory", "/tmp/"));
                    t.setXtermCommand(string(o, "xtermCommand", "xterm"));
                    credentials.put(t.getId(), new Credentials(string(o, "password", ""), ""));
                    if (o.has("portMappings") && !o.get("portMappings").isJsonNull()) {
                        int index = 0;
                        for (JsonElement element : o.getAsJsonArray("portMappings")) {
                            index++;
                            JsonObject m = element.getAsJsonObject();
                            PortMapping mapping = new PortMapping();
                            mapping.setLocalPort(m.get("localPort").getAsInt());
                            mapping.setRemotePort(m.get("remotePort").getAsInt());
                            t.getPortMappings().add(mapping);
                            command(
                                    t,
                                    mapping,
                                    "Start " + index + ":",
                                    string(m, "startCommand", ""),
                                    true);
                            command(
                                    t,
                                    mapping,
                                    "Stop " + index + ":",
                                    string(m, "stopCommand", ""),
                                    false);
                        }
                    }
                } catch (RuntimeException e) {
                    t.setHostname("");
                    notices.add(
                            p.getName()
                                    + ": incomplete/invalid entry; correct or explicitly exclude");
                }
            }
            notices.add(
                    "Review all entries before accepting. No connections or commands run until"
                            + " review is saved.");
            notices.add(
                    "The original file is preserved and still contains its plaintext passwords.");
            notices.add(
                    "Imported Stop commands have no event assignments. Password saving defaults"
                            + " off.");
            return new Review(config, credentials, notices);
        } catch (RuntimeException e) {
            throw new IOException("Invalid legacy configuration; original preserved", e);
        }
    }

    private static String string(JsonObject o, String key, String fallback) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
    }

    private static void command(
            SshConfig t, PortMapping mapping, String name, String text, boolean start) {
        if (text.isBlank()) return;
        NamedCommand c = new NamedCommand();
        c.setName(name);
        c.setCommand(text);
        t.getCommands().add(c);
        if (start)
            mapping.getAssignments()
                    .add(new CommandAssignment(c.getId(), CommandEvent.BEFORE_FORWARDING_START));
    }

    private LegacyMigration() {}
}
