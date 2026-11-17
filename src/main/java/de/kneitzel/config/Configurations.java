package de.kneitzel.config;

import com.google.gson.Gson;

import de.kneitzel.*;
import de.kneitzel.model.*;

import java.util.*;

public final class Configurations {
    private static final Gson GSON = new Gson();

    private Configurations() {}

    public static <T> T copy(T value, Class<T> type) {
        return GSON.fromJson(GSON.toJson(value), type);
    }

    public static void validate(AppConfig config) {
        require(
                config != null && config.getSchemaVersion() == AppConfig.VERSION,
                "Unsupported configuration version");
        require(
                config.getCleanupTimeoutSeconds() > 0 && config.getCleanupTimeoutSeconds() <= 3600,
                "Cleanup timeout must be 1–3600 seconds");
        require(config.getProfiles() != null, "Profiles missing");
        Set<String> ids = new HashSet<>(), names = new HashSet<>();
        for (Profile p : config.getProfiles()) {
            require(p != null, "Null profile");
            id(ids, p.getId());
            text(p.getName(), "Profile name");
            require(names.add(p.getName().strip()), "Duplicate profile name: " + p.getName());
            require(
                    p.getTargets() != null && p.getTargets().size() == 1,
                    "This version requires one target per profile");
            SshConfig t = p.getTargets().getFirst();
            require(t != null, "Null target");
            id(ids, t.getId());
            text(t.getName(), "Target name");
            text(t.getHostname(), "Hostname");
            text(t.getUsername(), "Username");
            port(t.getPort());
            require(
                    !t.getHostname().startsWith("-") && !t.getUsername().startsWith("-"),
                    "Host/user must not start with '-' ");
            require(
                    t.getCommands() != null
                            && t.getPortMappings() != null
                            && t.getAssignments() != null,
                    "Missing target lists");
            Set<String> commands = new HashSet<>();
            Set<String> commandNames = new HashSet<>();
            for (NamedCommand c : t.getCommands()) {
                require(c != null, "Null command");
                id(ids, c.getId());
                commands.add(c.getId());
                text(c.getName(), "Command name");
                text(c.getCommand(), "Command text");
                require(commandNames.add(c.getName().strip()), "Duplicate command name");
                require(
                        c.getTimeoutSeconds() > 0 && c.getTimeoutSeconds() <= 3600,
                        "Command timeout must be 1–3600 seconds");
            }
            assignments(ids, commands, t.getAssignments(), true);
            Set<Integer> ports = new HashSet<>();
            for (PortMapping m : t.getPortMappings()) {
                require(m != null, "Null mapping");
                id(ids, m.getId());
                port(m.getLocalPort());
                port(m.getRemotePort());
                require(ports.add(m.getLocalPort()), "Duplicate local forwarding port");
                assignments(ids, commands, m.getAssignments(), false);
            }
            SecretEnvelope e = t.getEncryptedCredentials();
            if (e != null) {
                require(t.isSaveCredentials(), "Stored credentials require opt-in");
                require(e.version() == 1, "Unsupported credential envelope");
                text(e.keyId(), "Credential key ID");
                text(e.nonce(), "Credential nonce");
                text(e.ciphertext(), "Credential ciphertext");
            }
        }
    }

    private static void assignments(
            Set<String> ids, Set<String> commands, List<CommandAssignment> list, boolean session) {
        require(list != null, "Missing event assignments");
        for (CommandAssignment a : list) {
            require(a != null, "Null assignment");
            id(ids, a.getId());
            require(commands.contains(a.getCommandId()), "Unknown assigned command");
            require(
                    a.getEvent() != null && a.getEvent().sessionEvent() == session,
                    "Invalid command event scope");
        }
    }

    private static void id(Set<String> ids, String id) {
        text(id, "ID");
        require(ids.add(id), "Duplicate ID");
    }

    private static void text(String value, String label) {
        require(value != null && !value.isBlank() && !value.contains("\0"), label + " is required");
    }

    private static void port(int value) {
        require(value > 0 && value <= 65535, "Port must be 1–65535");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
