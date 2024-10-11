package de.kneitzel;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class PortMapping {
    private int localPort;
    private int remotePort;
    private String startCommand;
    private String stopCommand;

    public PortMapping(PortMapping mapping) {
        this.localPort = mapping.getLocalPort();
        this.remotePort = mapping.getRemotePort();
        this.startCommand = mapping.getStartCommand();
        this.stopCommand = mapping.getStopCommand();
    }

    @Override
    public String toString() {
        return localPort + ":" + remotePort;
    }
}
