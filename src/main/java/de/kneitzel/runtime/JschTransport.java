package de.kneitzel.runtime;

import com.jcraft.jsch.*;

import de.kneitzel.*;
import de.kneitzel.model.NamedCommand;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

public final class JschTransport implements SshTransport {
    private final Session session;
    private volatile ChannelSftp activeTransfer;
    private static final Object HOST_LOCK = new Object();

    private JschTransport(Session session) {
        this.session = session;
    }

    public static Factory factory(Path knownHosts, VerifiedHostKeys.Trust trust) {
        return (config, credentials) -> {
            JSch jsch = new JSch();
            jsch.setHostKeyRepository(new VerifiedHostKeys(jsch, knownHosts, trust, HOST_LOCK));
            if (config.getPrivateKeyPath() != null && !config.getPrivateKeyPath().isBlank()) {
                String path = config.getPrivateKeyPath();
                if (path.startsWith("~/"))
                    path = System.getProperty("user.home") + path.substring(1);
                jsch.addIdentity(path, credentials.passphrase());
            }
            Session session =
                    jsch.getSession(config.getUsername(), config.getHostname(), config.getPort());
            session.setConfig("StrictHostKeyChecking", "yes");
            session.setConfig("PreferredAuthentications", "publickey,password");
            if (credentials.password() != null && !credentials.password().isEmpty())
                session.setPassword(credentials.password());
            session.setServerAliveInterval(5000);
            session.setServerAliveCountMax(2);
            try {
                session.connect(15000);
                return new JschTransport(session);
            } catch (Exception e) {
                session.disconnect();
                throw e;
            }
        };
    }

    @Override
    public boolean connected() {
        return session.isConnected();
    }

    @Override
    public void forward(PortMapping m) throws Exception {
        session.setPortForwardingL("127.0.0.1", m.getLocalPort(), "localhost", m.getRemotePort());
    }

    @Override
    public void stopForward(PortMapping m) throws Exception {
        session.delPortForwardingL("127.0.0.1", m.getLocalPort());
    }

    @Override
    public CommandResult execute(NamedCommand command, AtomicBoolean cancel, long deadline)
            throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        BoundedOutput output = new BoundedOutput(65536);
        try {
            channel.setCommand(command.getCommand());
            channel.setInputStream(null);
            channel.setOutputStream(output);
            channel.setErrStream(output);
            long end =
                    Math.min(
                            deadline,
                            System.nanoTime()
                                    + java.util.concurrent.TimeUnit.SECONDS.toNanos(
                                            command.getTimeoutSeconds()));
            if (cancel.get() || System.nanoTime() >= end)
                return CommandResult.unconfirmed("Cancelled before command started");
            channel.connect(
                    (int)
                            Math.max(
                                    1,
                                    Math.min(
                                            15000,
                                            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                                                    end - System.nanoTime()))));
            while (!channel.isClosed()) {
                if (cancel.get()
                        || Thread.currentThread().isInterrupted()
                        || System.nanoTime() >= end
                        || !session.isConnected())
                    return CommandResult.unconfirmed(
                            "Cancelled, timed out or SSH lost. Remote outcome unconfirmed.\n"
                                    + output);
                Thread.sleep(25);
            }
            int exit = channel.getExitStatus();
            return new CommandResult(
                    exit == 0
                            ? CommandResult.Outcome.SUCCEEDED
                            : exit < 0
                                    ? CommandResult.Outcome.UNCONFIRMED
                                    : CommandResult.Outcome.FAILED,
                    exit,
                    output.toString());
        } finally {
            channel.disconnect();
        }
    }

    @Override
    public void transfer(
            boolean upload,
            Path local,
            String remote,
            AtomicBoolean cancel,
            DoubleConsumer progress,
            BooleanSupplier overwrite)
            throws Exception {
        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        Path temporary = null;
        activeTransfer = channel;
        try {
            if (cancel.get()) throw new IOException("Transfer cancelled");
            channel.connect(15000);
            boolean exists;
            if (upload) {
                try {
                    channel.lstat(remote);
                    exists = true;
                } catch (SftpException e) {
                    if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw e;
                    exists = false;
                }
            } else exists = Files.exists(local);
            if (exists && !overwrite.getAsBoolean()) throw new IOException("Overwrite cancelled");
            SftpProgressMonitor monitor =
                    new SftpProgressMonitor() {
                        long total, done;

                        public void init(int op, String src, String dst, long max) {
                            total = max;
                            progress.accept(0);
                        }

                        public boolean count(long n) {
                            done += n;
                            progress.accept(total > 0 ? Math.min(1d, (double) done / total) : -1d);
                            return !cancel.get() && !Thread.currentThread().isInterrupted();
                        }

                        public void end() {
                            if (!cancel.get()) progress.accept(1);
                        }
                    };
            if (upload) channel.put(local.toString(), remote, monitor, ChannelSftp.OVERWRITE);
            else {
                Path parent = local.toAbsolutePath().getParent();
                temporary = Files.createTempFile(parent, ".ssh-download-", ".tmp");
                channel.get(remote, temporary.toString(), monitor, ChannelSftp.OVERWRITE);
                if (!cancel.get())
                    Files.move(temporary, local, StandardCopyOption.REPLACE_EXISTING);
            }
            if (cancel.get())
                throw new IOException("Transfer cancelled; a remote upload may be incomplete");
        } finally {
            channel.disconnect();
            if (activeTransfer == channel) activeTransfer = null;
            if (temporary != null) Files.deleteIfExists(temporary);
        }
    }

    @Override
    public void cancelTransfer() {
        ChannelSftp channel = activeTransfer;
        if (channel != null) channel.disconnect();
    }

    @Override
    public void close() {
        session.disconnect();
    }

    private static final class BoundedOutput extends OutputStream {
        private final int limit;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private boolean truncated;

        BoundedOutput(int limit) {
            this.limit = limit;
        }

        @Override
        public synchronized void write(int b) {
            if (buffer.size() < limit) buffer.write(b);
            else truncated = true;
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            int count = Math.min(len, limit - buffer.size());
            buffer.write(b, off, count);
            if (count < len) truncated = true;
        }

        @Override
        public synchronized String toString() {
            return buffer.toString(StandardCharsets.UTF_8)
                    + (truncated ? "\n[Output truncated]" : "");
        }
    }
}
