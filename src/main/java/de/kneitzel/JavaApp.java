package de.kneitzel;

import org.jetbrains.annotations.NotNull;


/**
 * Starting point of the JavaApp
 */
public final class JavaApp {

    /**
     * Private Constructor - we never create an instance!
     */
    private JavaApp() {}

    /**
     * Entry point of the application.
     * @param args Commandline parameters.
     */
    public static void main(@NotNull final String[] args) {
        final Greeting greeting = new Greeting(null);
        System.out.println(greeting);
    }
}
