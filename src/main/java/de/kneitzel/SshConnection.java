package de.kneitzel;

import com.jcraft.jsch.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * SSH connection class for managing a single SSH connection.
 *
 * This class encapsulates an SSH connection with JSch and provides functions for:
 * - Connection establishment and termination
 * - Port forwarding setup
 * - Command execution
 * - Terminal integration
 *
 * @author Konrad Neitzel
 * @version 1.1-SNAPSHOT
 * @since 1.0
 */
@Slf4j
@Getter
public class SshConnection {

    /** SSH configuration for this connection */
    private final SshConfig config;

    /** List of open channels for port forwarding */
    private final List<Channel> portForwardChannels;

    /** JSch SSH session */
    private Session session;

    /** Connection status */
    private boolean connected = false;

    /**
     * Constructor for a new SSH connection.
     *
     * Initializes the connection with the specified configuration
     * and creates the list for port forward channels.
     *
     * @param config SSH configuration for this connection
     */
    public SshConnection(SshConfig config) {
        this.config = config;
        this.portForwardChannels = new ArrayList<>();
    }

    /**
     * Establishes an SSH connection.
     *
     * Uses the JSch library to establish an SSH connection to the configured
     * server. Supports both password and key-based authentication. After
     * successful connection establishment, the configured port forwards are set up.
     *
     * @return true if the connection was successfully established
     */
    public boolean connect() {
        try {
            JSch jsch = new JSch();

            // Private Key hinzufügen falls vorhanden
            if (config.getPrivateKeyPath() != null && !config.getPrivateKeyPath().isEmpty()) {
                jsch.addIdentity(config.getPrivateKeyPath());
            }

            session = jsch.getSession(config.getUsername(), config.getHostname(), config.getPort());

            // Password setzen falls vorhanden
            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                session.setPassword(config.getPassword());
            }

            // Host Key Checking deaktivieren (für Demo-Zwecke)
            session.setConfig("StrictHostKeyChecking", "no");

            session.connect();
            connected = true;
            config.setConnected(true);

            log.info("SSH-Verbindung hergestellt zu {}", config.getHostname());

            // Port-Weiterleitungen einrichten
            setupPortForwardings();

            return true;
        } catch (JSchException e) {
            log.error("Fehler beim Verbinden zu {}: {}", config.getHostname(), e.getMessage());
            connected = false;
            config.setConnected(false);
            return false;
        }
    }

    /**
     * Sets up all configured port forwardings.
     *
     * Iterates over all port mappings defined in the configuration
     * and sets up corresponding local port forwards. Also executes
     * optional start commands for each port forward.
     */
    private void setupPortForwardings() {
        for (PortMapping mapping : config.getPortMappings()) {
            try {
                session.setPortForwardingL(mapping.getLocalPort(), "localhost", mapping.getRemotePort());
                log.info("Port-Weiterleitung eingerichtet: {}:{} -> {}:{}",
                        "localhost", mapping.getLocalPort(), config.getHostname(), mapping.getRemotePort());

                // Start-Kommando ausführen falls vorhanden
                if (mapping.getStartCommand() != null && !mapping.getStartCommand().isEmpty()) {
                    executeCommand(mapping.getStartCommand());
                }
            } catch (JSchException e) {
                log.error("Fehler beim Einrichten der Port-Weiterleitung {}: {}",
                        mapping.toString(), e.getMessage());
            }
        }
    }

    /**
     * Executes a command on the remote server.
     *
     * Opens an exec channel and executes the specified command on the
     * SSH server. The channel is added to the list of open channels
     * for later cleanup.
     *
     * @param command The SSH command to execute
     */
    public void executeCommand(String command) {
        try {
            Channel channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);
            channel.connect();
            portForwardChannels.add(channel);
            log.info("Kommando ausgeführt auf {}: {}", config.getHostname(), command);
        } catch (JSchException e) {
            log.error("Fehler beim Ausführen des Kommandos '{}' auf {}: {}",
                    command, config.getHostname(), e.getMessage());
        }
    }

    /**
     * Disconnects the SSH connection and closes all channels.
     *
     * Closes all open port forward channels and disconnects the SSH session.
     * Updates the connection status in the configuration.
     */
    public void disconnect() {
        // Port-Weiterleitungen schließen
        for (Channel channel : portForwardChannels) {
            if (channel.isConnected()) {
                channel.disconnect();
            }
        }
        portForwardChannels.clear();

        // Session schließen
        if (session != null && session.isConnected()) {
            session.disconnect();
            log.info("SSH-Verbindung getrennt von {}", config.getHostname());
        }

        connected = false;
        config.setConnected(false);
    }

    /**
     * Opens a new terminal window for this SSH connection.
     *
     * Starts an external terminal (xterm) with an SSH connection to the
     * configured server. Uses the terminal command defined in the configuration.
     */
    public void openTerminal() {
        try {
            String xtermCommand = config.getXtermCommand() + " -T \"" + config.getName() + "\" -e ssh " +
                    config.getUsername() + "@" + config.getHostname() + " -p " + config.getPort();
            Runtime.getRuntime().exec(xtermCommand);
            log.info("Terminal geöffnet für {}", config.getName());
        } catch (IOException e) {
            log.error("Fehler beim Öffnen des Terminals für {}: {}", config.getName(), e.getMessage());
        }
    }
}
