package de.kneitzel.model;

import de.kneitzel.SshConfig;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
@Setter
public class Profile {
    private String id = UUID.randomUUID().toString();
    private String name = "New profile";
    private List<SshConfig> targets = new ArrayList<>();

    @Override
    public String toString() {
        return name;
    }
}
