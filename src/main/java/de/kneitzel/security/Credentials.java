package de.kneitzel.security;

public record Credentials(String password, String passphrase) {
    public static final Credentials EMPTY = new Credentials("", "");

    @Override
    public String toString() {
        return "Credentials[redacted]";
    }
}
