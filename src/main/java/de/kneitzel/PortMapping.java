package de.kneitzel;

import de.kneitzel.model.CommandAssignment;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
@Setter
public class PortMapping {
    private String id = UUID.randomUUID().toString();
    private int localPort = 8080;
    private int remotePort = 8080;
    private List<CommandAssignment> assignments = new ArrayList<>();

    @Override
    public String toString() {
        return localPort + " → localhost:" + remotePort;
    }
}
