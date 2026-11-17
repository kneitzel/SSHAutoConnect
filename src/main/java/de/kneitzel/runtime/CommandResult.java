package de.kneitzel.runtime;

public record CommandResult(Outcome outcome, int exitCode, String output) {
    public enum Outcome {
        SUCCEEDED,
        FAILED,
        UNCONFIRMED
    }

    public boolean succeeded() {
        return outcome == Outcome.SUCCEEDED;
    }

    public static CommandResult failed(String message) {
        return new CommandResult(Outcome.FAILED, -1, message);
    }

    public static CommandResult unconfirmed(String message) {
        return new CommandResult(Outcome.UNCONFIRMED, -1, message);
    }
}
