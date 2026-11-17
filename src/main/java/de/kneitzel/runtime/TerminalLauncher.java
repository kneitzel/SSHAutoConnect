package de.kneitzel.runtime;

import de.kneitzel.SshConfig;

import java.util.*;

public final class TerminalLauncher {
    public static List<String> arguments(SshConfig target) {
        List<String> ssh =
                new ArrayList<>(List.of("ssh", "-p", Integer.toString(target.getPort())));
        if (target.getPrivateKeyPath() != null && !target.getPrivateKeyPath().isBlank())
            ssh.addAll(
                    List.of(
                            "-i",
                            target.getPrivateKeyPath().startsWith("~/")
                                    ? System.getProperty("user.home")
                                            + target.getPrivateKeyPath().substring(1)
                                    : target.getPrivateKeyPath()));
        ssh.add("--");
        ssh.add(target.getUsername() + "@" + target.getHostname());
        return ssh;
    }

    public static void launch(SshConfig target) throws Exception {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        List<String> ssh = arguments(target);
        if (os.startsWith("mac")) {
            String shell =
                    ssh.stream()
                            .map(TerminalLauncher::quote)
                            .collect(java.util.stream.Collectors.joining(" "));
            String apple =
                    "tell application \"Terminal\"\nactivate\ndo script \""
                            + shell.replace("\\", "\\\\").replace("\"", "\\\"")
                            + "\"\nend tell";
            new ProcessBuilder("osascript", "-e", apple).start();
        } else {
            List<String> args =
                    new ArrayList<>(
                            os.startsWith("windows")
                                    ? List.of("wt.exe", "--")
                                    : List.of(target.getXtermCommand(), "-e"));
            args.addAll(ssh);
            new ProcessBuilder(args).start();
        }
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private TerminalLauncher() {}
}
