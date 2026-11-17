package de.kneitzel.runtime;

import de.kneitzel.*;
import de.kneitzel.model.NamedCommand;
import de.kneitzel.security.Credentials;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

public interface SshTransport extends AutoCloseable {
    interface Factory {
        SshTransport open(SshConfig config, Credentials credentials) throws Exception;
    }

    boolean connected();

    void forward(PortMapping mapping) throws Exception;

    void stopForward(PortMapping mapping) throws Exception;

    CommandResult execute(NamedCommand command, AtomicBoolean cancel, long deadlineNanos)
            throws Exception;

    void transfer(
            boolean upload,
            Path local,
            String remote,
            AtomicBoolean cancel,
            DoubleConsumer progress,
            BooleanSupplier overwrite)
            throws Exception;

    /** Closes a blocked transfer channel as well as signalling progress cancellation. */
    default void cancelTransfer() {}

    @Override
    void close();
}
