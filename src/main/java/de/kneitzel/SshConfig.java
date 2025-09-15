package de.kneitzel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * SSH configuration class for storing all parameters of an SSH connection.
 * <p>
 * This class encapsulates all necessary information for an SSH connection
 * including authentication data, port mappings and configuration options.
 * Uses Lombok for automatic generation of getters, setters and constructors.
 *
 * @author Konrad Neitzel
 * @version 1.1-SNAPSHOT
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SshConfig {

    /**
     * Unique name of the SSH configuration
     */
    private String name;

    /**
     * Hostname or IP address of the SSH server
     */
    private String hostname;

    /**
     * SSH port (default: 22)
     */
    private int port = 22;

    /**
     * Username for SSH authentication
     */
    private String username;

    /**
     * Path to private SSH key (optional)
     */
    private String privateKeyPath;

    /**
     * Password for SSH authentication (optional, stored encrypted)
     */
    private String password;

    /**
     * List of configured port forwardings
     */
    private List<PortMapping> portMappings = new ArrayList<>();

    /**
     * Status flag: true when currently connected
     */
    private boolean connected = false;

    /**
     * Flag: true when should connect automatically at startup
     */
    private boolean autoConnect = false;

    /**
     * Command for opening a terminal (default: "xterm")
     */
    private String xtermCommand = "xterm";

    /**
     * Default directory for file uploads on the remote server
     */
    private String uploadDirectory = "/tmp/";

    /**
     * Constructor for a new SSH configuration with name.
     * <p>
     * Creates a new configuration with the specified name and
     * initializes the port mappings list.
     *
     * @param name Unique name of the configuration
     */
    public SshConfig(String name) {
        this.name = name;
        this.portMappings = new ArrayList<>();
    }

    /**
     * Copy constructor for cloning an SSH configuration.
     * <p>
     * Creates a deep copy of an existing configuration,
     * including all port mappings.
     *
     * @param other The SSH configuration to copy
     */
    public SshConfig(SshConfig other) {
        this.name = other.name;
        this.hostname = other.hostname;
        this.port = other.port;
        this.username = other.username;
        this.privateKeyPath = other.privateKeyPath;
        this.password = other.password;
        this.portMappings = new ArrayList<>();
        for (PortMapping mapping : other.portMappings) {
            this.portMappings.add(new PortMapping(mapping));
        }
        this.connected = other.connected;
        this.autoConnect = other.autoConnect;
        this.xtermCommand = other.xtermCommand;
        this.uploadDirectory = other.uploadDirectory;
    }

    /**
     * String representation of the SSH configuration.
     * <p>
     * Returns the name of the configuration, or if not set,
     * the hostname, or as fallback "Unnamed Configuration".
     *
     * @return String representation of the configuration for UI display
     */
    @Override
    public String toString() {
        return name != null ? name : (hostname != null ? hostname : "Unnamed Configuration");
    }
}
