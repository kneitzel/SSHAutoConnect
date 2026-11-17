package de.kneitzel.model;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
@Setter
public class AppConfig {
    public static final int VERSION = 1;
    private int schemaVersion = VERSION;
    private boolean startInTray;
    private int cleanupTimeoutSeconds = 60;
    private List<Profile> profiles = new ArrayList<>();
}
