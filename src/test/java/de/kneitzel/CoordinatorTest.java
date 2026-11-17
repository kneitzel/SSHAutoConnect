package de.kneitzel;

import static de.kneitzel.model.CommandEvent.*;

import static org.junit.jupiter.api.Assertions.*;

import de.kneitzel.model.*;
import de.kneitzel.runtime.*;
import de.kneitzel.security.Credentials;

import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

class CoordinatorTest {
    static class Fake implements SshTransport {
        boolean connected = true;
        Set<Integer> failPorts = new HashSet<>();
        List<String> calls = new CopyOnWriteArrayList<>();
        Set<String> failCommands = new HashSet<>();
        boolean block;

        public boolean connected() {
            return connected;
        }

        public void forward(PortMapping m) throws Exception {
            calls.add("forward:" + m.getLocalPort());
            if (failPorts.remove(m.getLocalPort())) throw new Exception("port busy");
        }

        public void stopForward(PortMapping m) {
            calls.add("stop:" + m.getLocalPort());
        }

        public CommandResult execute(NamedCommand c, AtomicBoolean cancel, long deadline)
                throws Exception {
            calls.add(c.getName());
            while (block && !cancel.get() && System.nanoTime() < deadline) Thread.sleep(5);
            if (cancel.get() || System.nanoTime() >= deadline)
                return CommandResult.unconfirmed("interrupted");
            return failCommands.contains(c.getName())
                    ? CommandResult.failed("exit failed")
                    : new CommandResult(CommandResult.Outcome.SUCCEEDED, 0, "done");
        }

        public void transfer(
                boolean upload,
                Path local,
                String remote,
                AtomicBoolean cancel,
                DoubleConsumer progress,
                BooleanSupplier overwrite) {}

        public void close() {
            connected = false;
            calls.add("close");
        }
    }

    static NamedCommand assign(
            SshConfig t,
            List<CommandAssignment> list,
            CommandEvent event,
            String name,
            boolean required) {
        NamedCommand c = new NamedCommand();
        c.setName(name);
        c.setCommand(name);
        t.getCommands().add(c);
        CommandAssignment a = new CommandAssignment(c.getId(), event);
        a.setRequired(required);
        list.add(a);
        return c;
    }

    static void await(CompletableFuture<?> future) throws Exception {
        future.get(5, TimeUnit.SECONDS);
    }

    @Test
    void preservesWorkingForwardingAndDoesNotRepeatSuccessfulPreparation() throws Exception {
        SshConfig t = new SshConfig();
        PortMapping a = new PortMapping(), b = new PortMapping();
        b.setLocalPort(8081);
        t.getPortMappings().addAll(List.of(a, b));
        assign(t, b.getAssignments(), BEFORE_FORWARDING_START, "prepare", true);
        Fake f = new Fake();
        f.failPorts.add(8081);
        try (TargetCoordinator c = new TargetCoordinator(t, (s, creds) -> f, x -> {})) {
            await(c.connect(Credentials.EMPTY));
            assertEquals(TargetCoordinator.State.PARTLY_CONNECTED, c.state());
            await(c.retry());
            assertEquals(TargetCoordinator.State.CONNECTED, c.state());
            assertEquals(1, Collections.frequency(f.calls, "forward:8080"));
            assertEquals(2, Collections.frequency(f.calls, "forward:8081"));
            assertEquals(1, Collections.frequency(f.calls, "prepare"));
        }
    }

    @Test
    void requiredSessionFailureBlocksMappingsAndRetryResumes() throws Exception {
        SshConfig t = new SshConfig();
        t.getPortMappings().add(new PortMapping());
        assign(t, t.getAssignments(), AFTER_SSH_CONNECTED, "session", true);
        Fake f = new Fake();
        f.failCommands.add("session");
        try (TargetCoordinator c = new TargetCoordinator(t, (s, creds) -> f, x -> {})) {
            await(c.connect(Credentials.EMPTY));
            assertEquals(TargetCoordinator.State.PARTLY_CONNECTED, c.state());
            assertFalse(f.calls.contains("forward:8080"));
            f.failCommands.clear();
            await(c.retry());
            assertEquals(TargetCoordinator.State.CONNECTED, c.state());
        }
    }

    @Test
    void requiredAfterCommandFailureRetainsTunnelAndRetryOnlyRunsIncompleteCommands()
            throws Exception {
        SshConfig t = new SshConfig();
        PortMapping m = new PortMapping();
        t.getPortMappings().add(m);
        assign(t, m.getAssignments(), AFTER_FORWARDING_START, "after", true);
        assign(t, m.getAssignments(), AFTER_FORWARDING_START, "blocked", true);
        Fake f = new Fake();
        f.failCommands.add("after");
        try (TargetCoordinator c = new TargetCoordinator(t, (s, creds) -> f, x -> {})) {
            await(c.connect(Credentials.EMPTY));
            assertEquals(TargetCoordinator.State.PARTLY_CONNECTED, c.state());
            assertFalse(f.calls.contains("blocked"));
            f.failCommands.clear();
            await(c.retry());
            assertEquals(1, Collections.frequency(f.calls, "forward:8080"));
            assertEquals(1, Collections.frequency(f.calls, "blocked"));
            assertEquals(TargetCoordinator.State.CONNECTED, c.state());
        }
    }

    @Test
    void bestEffortFailureWarnsWithoutChangingConnectionAndDoesNotRepeatOnRetry() throws Exception {
        SshConfig t = new SshConfig();
        assign(t, t.getAssignments(), AFTER_SSH_CONNECTED, "optional", false);
        Fake f = new Fake();
        f.failCommands.add("optional");
        try (TargetCoordinator c = new TargetCoordinator(t, (s, creds) -> f, x -> {})) {
            await(c.connect(Credentials.EMPTY));
            assertEquals(TargetCoordinator.State.CONNECTED, c.state());
            await(c.retry());
            assertEquals(1, Collections.frequency(f.calls, "optional"));
        }
    }

    @Test
    void cleanupRunsForAttemptedMappingsEvenIfPreparationFailedAndNeverVetoesClose()
            throws Exception {
        SshConfig t = new SshConfig();
        PortMapping m = new PortMapping();
        t.getPortMappings().add(m);
        assign(t, m.getAssignments(), BEFORE_FORWARDING_START, "prepare", true);
        assign(t, m.getAssignments(), BEFORE_FORWARDING_STOP, "cleanup1", true);
        assign(t, m.getAssignments(), AFTER_FORWARDING_STOP, "cleanup2", true);
        assign(t, t.getAssignments(), BEFORE_SSH_DISCONNECTED, "session-cleanup", true);
        Fake f = new Fake();
        f.failCommands.addAll(List.of("prepare", "cleanup1"));
        try (TargetCoordinator c = new TargetCoordinator(t, (s, creds) -> f, x -> {})) {
            await(c.connect(Credentials.EMPTY));
            await(c.disconnect(2));
            assertEquals(
                    List.of("prepare", "cleanup1", "cleanup2", "session-cleanup", "close"),
                    f.calls);
            assertFalse(c.connected());
        }
    }

    @Test
    void neverAttemptedMappingsDoNotGetCleanup() throws Exception {
        SshConfig t = new SshConfig();
        PortMapping m = new PortMapping();
        t.getPortMappings().add(m);
        assign(t, t.getAssignments(), AFTER_SSH_CONNECTED, "fail", true);
        assign(t, m.getAssignments(), AFTER_FORWARDING_STOP, "must-not-run", true);
        Fake f = new Fake();
        f.failCommands.add("fail");
        try (TargetCoordinator c = new TargetCoordinator(t, (s, creds) -> f, x -> {})) {
            await(c.connect(Credentials.EMPTY));
            await(c.disconnect(2));
            assertFalse(f.calls.contains("must-not-run"));
        }
    }

    @Test
    void manualRunDoesNotRepairSetupAndUsesSessionSnapshot() throws Exception {
        SshConfig t = new SshConfig();
        NamedCommand command = assign(t, t.getAssignments(), AFTER_SSH_CONNECTED, "required", true);
        Fake f = new Fake();
        f.failCommands.add("required");
        try (TargetCoordinator c = new TargetCoordinator(t, (s, creds) -> f, x -> {})) {
            await(c.connect(Credentials.EMPTY));
            f.failCommands.clear();
            command.setName("edited");
            await(c.run(command.getId()));
            assertEquals(TargetCoordinator.State.PARTLY_CONNECTED, c.state());
            assertFalse(f.calls.contains("edited"));
            await(c.retry());
            assertEquals(TargetCoordinator.State.CONNECTED, c.state());
        }
    }

    @Test
    void cancellationReleasesBusyStateAndDoesNotAutomaticallyRepeatCommand() throws Exception {
        SshConfig t = new SshConfig();
        NamedCommand command = new NamedCommand();
        command.setCommand("blocking");
        command.setName("blocking");
        t.getCommands().add(command);
        Fake f = new Fake();
        try (TargetCoordinator c = new TargetCoordinator(t, (s, creds) -> f, x -> {})) {
            await(c.connect(Credentials.EMPTY));
            f.block = true;
            var running = c.run(command.getId());
            while (!f.calls.contains("blocking")) Thread.sleep(5);
            assertThrows(Exception.class, () -> await(c.run(command.getId())));
            c.cancelCommand();
            await(running);
            assertFalse(c.busy());
            assertEquals(1, Collections.frequency(f.calls, "blocking"));
            assertEquals(TargetCoordinator.State.CONNECTED, c.state());
        }
    }

    @Test
    void cleanupHasOverallDeadline() throws Exception {
        SshConfig t = new SshConfig();
        assign(t, t.getAssignments(), BEFORE_SSH_DISCONNECTED, "cleanup", true);
        Fake f = new Fake();
        try (TargetCoordinator c = new TargetCoordinator(t, (s, creds) -> f, x -> {})) {
            await(c.connect(Credentials.EMPTY));
            f.block = true;
            long start = System.nanoTime();
            await(c.disconnect(1));
            assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(3));
            assertFalse(c.connected());
        }
    }

    @Test
    void disconnectRejectsNewCommandsAndTransfersWhileCleanupIsRunning() throws Exception {
        SshConfig t = new SshConfig();
        NamedCommand command =
                assign(t, t.getAssignments(), BEFORE_SSH_DISCONNECTED, "blocking-cleanup", true);
        Fake f = new Fake();
        try (TargetCoordinator c =
                new TargetCoordinator(t, (settings, creds) -> f, snapshot -> {})) {
            await(c.connect(Credentials.EMPTY));
            f.block = true;
            var disconnect = c.disconnect(1);
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (!f.calls.contains("blocking-cleanup") && System.nanoTime() < end)
                Thread.sleep(5);
            assertTrue(f.calls.contains("blocking-cleanup"));
            assertThrows(Exception.class, () -> await(c.run(command.getId())));
            assertThrows(
                    Exception.class,
                    () ->
                            await(
                                    c.transfer(
                                            true,
                                            Path.of("unused"),
                                            "unused",
                                            p -> {},
                                            () -> true)));
            await(disconnect);
        }
    }

    @Test
    void lostSessionBecomesNotConnectedWithoutAutomaticReconnect() throws Exception {
        SshConfig t = new SshConfig();
        Fake f = new Fake();
        AtomicInteger opens = new AtomicInteger();
        try (TargetCoordinator c =
                new TargetCoordinator(
                        t,
                        (s, creds) -> {
                            opens.incrementAndGet();
                            return f;
                        },
                        x -> {})) {
            await(c.connect(Credentials.EMPTY));
            f.connected = false;
            c.pollConnection();
            assertEquals(TargetCoordinator.State.NOT_CONNECTED, c.state());
            assertEquals(1, opens.get());
            assertThrows(Exception.class, () -> await(c.run("anything")));
        }
    }
}
