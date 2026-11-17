package de.kneitzel.runtime;

import static de.kneitzel.model.CommandEvent.*;

import de.kneitzel.*;
import de.kneitzel.config.Configurations;
import de.kneitzel.model.*;
import de.kneitzel.security.Credentials;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** Owns one immutable settings snapshot and tracks invocation success per assignment. */
@lombok.extern.slf4j.Slf4j
public final class TargetCoordinator implements AutoCloseable {
    public enum State {
        NOT_CONNECTED,
        CONNECTING,
        PARTLY_CONNECTED,
        CONNECTED,
        DISCONNECTING
    }

    public record Snapshot(
            State state,
            boolean busy,
            boolean transferring,
            Map<String, String> mappings,
            Map<String, CommandResult> commands,
            String message) {}

    private final SshConfig settings;
    private final SshTransport.Factory factory;
    private final Consumer<Snapshot> observer;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean disconnectRequested = new AtomicBoolean();
    private final AtomicBoolean busy = new AtomicBoolean(),
            cancel = new AtomicBoolean(),
            transferBusy = new AtomicBoolean(),
            transferCancel = new AtomicBoolean();
    private final Set<String> attempted = new LinkedHashSet<>(), active = new LinkedHashSet<>();
    private final Map<String, CommandResult> results = new ConcurrentHashMap<>();
    private final Map<String, String> mappingStatus = new ConcurrentHashMap<>();
    private volatile SshTransport transport;
    private volatile State state = State.NOT_CONNECTED;
    private volatile String message = "";

    public TargetCoordinator(
            SshConfig settings, SshTransport.Factory factory, Consumer<Snapshot> observer) {
        this.settings = Configurations.copy(settings, SshConfig.class);
        this.factory = factory;
        this.observer = observer;
    }

    public SshConfig settings() {
        return Configurations.copy(settings, SshConfig.class);
    }

    public boolean connected() {
        SshTransport t = transport;
        return t != null && t.connected();
    }

    public boolean busy() {
        return busy.get();
    }

    public boolean transferring() {
        return transferBusy.get();
    }

    public State state() {
        return state;
    }

    public void cancelCommand() {
        cancel.set(true);
    }

    public void cancelTransfer() {
        transferCancel.set(true);
        SshTransport t = transport;
        if (t != null) t.cancelTransfer();
    }

    public CompletableFuture<Void> connect(Credentials credentials) {
        if (!busy.get() && !connected()) disconnectRequested.set(false);
        return operation(
                () -> {
                    if (connected()) return;
                    state = State.CONNECTING;
                    results.clear();
                    attempted.clear();
                    active.clear();
                    mappingStatus.clear();
                    publish();
                    transport = factory.open(settings, credentials);
                    if (cancel.get()) {
                        transport.close();
                        throw new IllegalStateException("Connection cancelled");
                    }
                    setup();
                });
    }

    public CompletableFuture<Void> retry() {
        return operation(
                () -> {
                    requireConnected();
                    setup();
                });
    }

    private void setup() throws Exception {
        boolean sessionReady =
                event(settings.getAssignments(), AFTER_SSH_CONNECTED, false, Long.MAX_VALUE);
        if (sessionReady)
            for (PortMapping mapping : settings.getPortMappings()) {
                if (cancel.get()) break;
                if (!active.contains(mapping.getId())) {
                    attempted.add(mapping.getId());
                    if (!event(
                            mapping.getAssignments(),
                            BEFORE_FORWARDING_START,
                            false,
                            Long.MAX_VALUE)) {
                        mappingStatus.put(mapping.getId(), "Required preparation failed");
                        continue;
                    }
                    try {
                        transport.forward(mapping);
                        active.add(mapping.getId());
                        mappingStatus.put(mapping.getId(), "Forwarding active");
                    } catch (Exception e) {
                        mappingStatus.put(mapping.getId(), "Forwarding failed: " + safe(e));
                        continue;
                    }
                }
                if (!event(mapping.getAssignments(), AFTER_FORWARDING_START, false, Long.MAX_VALUE))
                    mappingStatus.put(
                            mapping.getId(), "Forwarding active; required command failed");
                else mappingStatus.put(mapping.getId(), "Forwarding active");
            }
        updateState();
        publish();
    }

    private boolean event(
            List<CommandAssignment> assignments, CommandEvent event, boolean cleanup, long deadline)
            throws Exception {
        for (CommandAssignment assignment : assignments) {
            if (assignment.getEvent() != event) continue;
            if (System.nanoTime() >= deadline) {
                message = "Cleanup deadline reached; remote cleanup incomplete";
                return false;
            }
            String key = (cleanup ? "cleanup:" : "") + assignment.getId();
            CommandResult previous = results.get(key);
            if (!cleanup && previous != null && (previous.succeeded() || !assignment.isRequired()))
                continue;
            if (!cleanup && cancel.get()) return false;
            NamedCommand command = command(assignment.getCommandId());
            CommandResult result;
            try {
                result =
                        transport.execute(
                                command, cleanup ? new AtomicBoolean() : cancel, deadline);
            } catch (Exception e) {
                result = CommandResult.unconfirmed("Remote outcome unconfirmed: " + safe(e));
            }
            results.put(key, result);
            if (cleanup && !result.succeeded()) {
                message =
                        "Remote cleanup incomplete: "
                                + command.getName()
                                + " ("
                                + result.outcome()
                                + ")";
                log.warn("{} [{}]", message, settings.getId());
            }
            publish();
            if (!result.succeeded() && assignment.isRequired() && !cleanup) return false;
        }
        return true;
    }

    private NamedCommand command(String id) {
        return settings.getCommands().stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown command"));
    }

    public CompletableFuture<Void> run(String commandId) {
        return operation(
                () -> {
                    requireConnected();
                    NamedCommand command = command(commandId);
                    CommandResult result;
                    try {
                        result = transport.execute(command, cancel, Long.MAX_VALUE);
                    } catch (Exception e) {
                        result =
                                CommandResult.unconfirmed("Remote outcome unconfirmed: " + safe(e));
                    }
                    results.put("manual:" + commandId, result);
                    message = "Manual command: " + result.outcome();
                    publish();
                });
    }

    public CompletableFuture<Void> disconnect(int seconds) {
        disconnectRequested.set(true);
        cancel.set(true);
        cancelTransfer();
        return CompletableFuture.runAsync(
                () -> {
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
                    while (!busy.compareAndSet(false, true)) {
                        if (System.nanoTime() >= deadline) {
                            message = "Cleanup deadline reached; remote cleanup incomplete";
                            forceClose();
                            publish();
                            return;
                        }
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            forceClose();
                            return;
                        }
                    }
                    try {
                        state = State.DISCONNECTING;
                        publish();
                        while (transferBusy.get() && System.nanoTime() < deadline) Thread.sleep(20);
                        SshTransport t = transport;
                        if (t != null && t.connected()) {
                            for (PortMapping mapping : settings.getPortMappings())
                                if (attempted.contains(mapping.getId())) {
                                    event(
                                            mapping.getAssignments(),
                                            BEFORE_FORWARDING_STOP,
                                            true,
                                            deadline);
                                    if (active.contains(mapping.getId()))
                                        try {
                                            t.stopForward(mapping);
                                        } catch (Exception e) {
                                            message =
                                                    "Could not confirm forwarding cleanup: "
                                                            + safe(e);
                                        }
                                    event(
                                            mapping.getAssignments(),
                                            AFTER_FORWARDING_STOP,
                                            true,
                                            deadline);
                                }
                            event(
                                    settings.getAssignments(),
                                    BEFORE_SSH_DISCONNECTED,
                                    true,
                                    deadline);
                        } else if (t != null) message = "SSH lost; remote cleanup unconfirmed";
                    } catch (Exception e) {
                        message = "Cleanup incomplete: " + safe(e);
                    } finally {
                        forceClose();
                        busy.set(false);
                        publish();
                    }
                },
                executor);
    }

    public CompletableFuture<Void> transfer(
            boolean upload,
            Path local,
            String remote,
            DoubleConsumer progress,
            BooleanSupplier overwrite) {
        if (!connected() || disconnectRequested.get())
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Connect this target first"));
        if (!transferBusy.compareAndSet(false, true))
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Transfer already active"));
        transferCancel.set(false);
        if (disconnectRequested.get()) {
            transferBusy.set(false);
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Target is disconnecting"));
        }
        publish();
        return CompletableFuture.runAsync(
                () -> {
                    try {
                        transport.transfer(
                                upload, local, remote, transferCancel, progress, overwrite);
                        message = "Transfer complete";
                    } catch (Exception e) {
                        message =
                                transferCancel.get()
                                        ? "Transfer cancelled; remote uploads may be incomplete"
                                        : "Transfer failed: " + safe(e);
                        throw new CompletionException(e);
                    } finally {
                        transferBusy.set(false);
                        publish();
                    }
                },
                executor);
    }

    private CompletableFuture<Void> operation(ThrowingRunnable work) {
        if (disconnectRequested.get())
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Target is disconnecting"));
        if (!busy.compareAndSet(false, true))
            return CompletableFuture.failedFuture(new IllegalStateException("Target is busy"));
        cancel.set(false);
        if (disconnectRequested.get()) {
            busy.set(false);
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Target is disconnecting"));
        }
        publish();
        return CompletableFuture.runAsync(
                () -> {
                    try {
                        work.run();
                    } catch (Exception e) {
                        message = safe(e);
                        throw new CompletionException(e);
                    } finally {
                        if (state != State.DISCONNECTING) updateState();
                        busy.set(false);
                        publish();
                    }
                },
                executor);
    }

    private void updateState() {
        if (!connected()) {
            state = State.NOT_CONNECTED;
            return;
        }
        boolean complete =
                active.size() == settings.getPortMappings().size()
                        && requiredSucceeded(settings.getAssignments(), AFTER_SSH_CONNECTED);
        for (PortMapping m : settings.getPortMappings())
            complete &=
                    requiredSucceeded(m.getAssignments(), BEFORE_FORWARDING_START)
                            && requiredSucceeded(m.getAssignments(), AFTER_FORWARDING_START);
        state = complete ? State.CONNECTED : State.PARTLY_CONNECTED;
    }

    private boolean requiredSucceeded(List<CommandAssignment> assignments, CommandEvent event) {
        return assignments.stream()
                .filter(a -> a.getEvent() == event && a.isRequired())
                .allMatch(
                        a -> results.containsKey(a.getId()) && results.get(a.getId()).succeeded());
    }

    private void requireConnected() {
        if (!connected()) throw new IllegalStateException("Connect this target first");
    }

    /** Safe when force-closing after an overall deadline while a worker unwinds. */
    private void forceClose() {
        SshTransport t = transport;
        if (t != null) t.close();
        state = State.NOT_CONNECTED;
        mappingStatus.replaceAll((id, value) -> "Not forwarding");
    }

    public void pollConnection() {
        if (!busy.get() && state != State.NOT_CONNECTED && !connected()) {
            forceClose();
            message = "SSH connection lost; remote outcome unconfirmed";
            publish();
        }
    }

    private synchronized void publish() {
        observer.accept(
                new Snapshot(
                        state,
                        busy.get(),
                        transferBusy.get(),
                        Map.copyOf(mappingStatus),
                        Map.copyOf(results),
                        message));
    }

    private static String safe(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    @Override
    public void close() {
        cancel.set(true);
        cancelTransfer();
        forceClose();
        executor.shutdownNow();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
