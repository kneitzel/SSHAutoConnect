package de.kneitzel.ui;

import lombok.Getter;
import lombok.Setter;

/** Stable presentation draft, with no secrets or nested model cache entries. */
@Getter
@Setter
public class TargetDraft {
    private String profileName = "",
            name = "",
            hostname = "",
            port = "22",
            username = "",
            privateKeyPath = "",
            xtermCommand = "xterm",
            uploadDirectory = "/tmp/";
    private boolean autoConnect, saveCredentials;
}
