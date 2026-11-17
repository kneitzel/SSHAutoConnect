package de.kneitzel;

/** Separate launcher permits JavaFX on the application classpath in jpackage. */
public final class Launcher {
    public static void main(String[] args) {
        javafx.application.Application.launch(SSHAutoConnect.class, args);
    }

    private Launcher() {}
}
