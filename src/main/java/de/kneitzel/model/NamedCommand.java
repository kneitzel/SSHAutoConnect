package de.kneitzel.model;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class NamedCommand {
    private String id = UUID.randomUUID().toString();
    private String name = "Command";
    private String command = "";
    private int timeoutSeconds = 30;

    @Override
    public String toString() {
        return name;
    }
}
