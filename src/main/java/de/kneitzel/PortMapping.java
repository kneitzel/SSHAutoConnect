package de.kneitzel;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Port mapping class for defining SSH port forwardings.
 *
 * This class defines a mapping between a local port and a
 * remote port for SSH port forwarding. Additionally, start and
 * stop commands can be defined that are executed when setting up
 * or terminating the port forwarding.
 *
 * @author Konrad Neitzel
 * @version 1.1-SNAPSHOT
 * @since 1.0
 */
@Setter
@Getter
@AllArgsConstructor
public class PortMapping {

    /** Local port on the client system */
    private int localPort;

    /** Remote port on the SSH server */
    private int remotePort;

    /** Command executed when starting the port forwarding (optional) */
    private String startCommand;

    /** Command executed when stopping the port forwarding (optional) */
    private String stopCommand;

    /**
     * Copy constructor for cloning a port mapping.
     *
     * Creates a copy of an existing port mapping with all
     * configured values.
     *
     * @param mapping The port mapping to copy
     */
    public PortMapping(PortMapping mapping) {
        this.localPort = mapping.getLocalPort();
        this.remotePort = mapping.getRemotePort();
        this.startCommand = mapping.getStartCommand();
        this.stopCommand = mapping.getStopCommand();
    }

    /**
     * String representation of the port mapping.
     *
     * Returns a compact representation in the format "localPort:remotePort"
     * suitable for display in user interfaces.
     *
     * @return String representation in format "localPort:remotePort"
     */
    @Override
    public String toString() {
        return localPort + ":" + remotePort;
    }
}
