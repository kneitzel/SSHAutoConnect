package de.kneitzel.model;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CommandAssignment {
    private String id = UUID.randomUUID().toString();
    private String commandId;
    private CommandEvent event;
    private boolean required;

    public CommandAssignment() {}

    public CommandAssignment(String commandId, CommandEvent event) {
        this.commandId = commandId;
        this.event = event;
    }
}
