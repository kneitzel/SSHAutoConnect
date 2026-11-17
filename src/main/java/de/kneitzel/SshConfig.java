package de.kneitzel;

import de.kneitzel.model.*;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

/** Persisted target settings; runtime session/credentials are owned separately. */
@Getter
@Setter
public class SshConfig {
    private String id = UUID.randomUUID().toString();
    private String name = "SSH target";
    private String hostname = "";
    private int port = 22;
    private String username = "";
    private String privateKeyPath = "";
    private boolean saveCredentials;
    private SecretEnvelope encryptedCredentials;
    private boolean autoConnect;
    private String xtermCommand = "xterm";
    private String uploadDirectory = "/tmp/";
    private List<PortMapping> portMappings = new ArrayList<>();
    private List<NamedCommand> commands = new ArrayList<>();
    private List<CommandAssignment> assignments = new ArrayList<>();

    @Override
    public String toString() {
        return name;
    }
}
