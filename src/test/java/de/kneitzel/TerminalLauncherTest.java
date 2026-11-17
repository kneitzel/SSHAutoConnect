package de.kneitzel;

import static org.junit.jupiter.api.Assertions.*;

import de.kneitzel.runtime.TerminalLauncher;

import org.junit.jupiter.api.Test;

class TerminalLauncherTest {
    @Test
    void argumentsKeepPathsAndHostSeparateWithoutShellExpansion() {
        SshConfig t = new SshConfig();
        t.setPrivateKeyPath("/path with spaces/key");
        t.setUsername("user");
        t.setHostname("host;touch /tmp/no");
        var args = TerminalLauncher.arguments(t);
        assertEquals("/path with spaces/key", args.get(4));
        assertEquals("--", args.get(5));
        assertEquals("user@host;touch /tmp/no", args.getLast());
    }
}
