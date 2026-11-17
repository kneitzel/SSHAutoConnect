package de.kneitzel.model;

public enum CommandEvent {
    AFTER_SSH_CONNECTED,
    BEFORE_SSH_DISCONNECTED,
    BEFORE_FORWARDING_START,
    AFTER_FORWARDING_START,
    BEFORE_FORWARDING_STOP,
    AFTER_FORWARDING_STOP;

    public boolean sessionEvent() {
        return this == AFTER_SSH_CONNECTED || this == BEFORE_SSH_DISCONNECTED;
    }

    @Override
    public String toString() {
        return name().toLowerCase().replace('_', ' ');
    }
}
