package de.kneitzel.runtime;

import com.jcraft.jsch.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Base64;

/** Reject changed keys; confirm unknown fingerprints before atomically persisting trust. */
public final class VerifiedHostKeys implements HostKeyRepository {
    public interface Trust {
        boolean accept(String host, String fingerprint);
    }

    private HostKeyRepository delegate;
    private final Path file;
    private final Trust trust;
    private final Object lock;

    public VerifiedHostKeys(JSch jsch, Path file, Trust trust, Object lock) throws Exception {
        this.file = file;
        this.trust = trust;
        this.lock = lock;
        if (Files.exists(file)) jsch.setKnownHosts(file.toString());
        delegate = jsch.getHostKeyRepository();
    }

    @Override
    public int check(String host, byte[] key) {
        synchronized (lock) {
            try {
                if (Files.exists(file)) {
                    JSch fresh = new JSch();
                    fresh.setKnownHosts(file.toString());
                    delegate = fresh.getHostKeyRepository();
                }
            } catch (Exception e) {
                return CHANGED;
            }
            int result = delegate.check(host, key);
            if (result != NOT_INCLUDED) return result;
            try {
                // A different key algorithm for an already-known host also requires deliberate
                // external correction.
                HostKey[] known = delegate.getHostKey(host, null);
                if (known != null && known.length > 0) return CHANGED;
                String fp =
                        "SHA256:"
                                + Base64.getEncoder()
                                        .withoutPadding()
                                        .encodeToString(
                                                MessageDigest.getInstance("SHA-256").digest(key));
                if (!trust.accept(host, fp)) return NOT_INCLUDED;
                Path parent = file.toAbsolutePath().getParent();
                Files.createDirectories(parent);
                HostKey entry = new HostKey(host, key);
                // Append preserves comments, hashed entries and other SSH-client configuration.
                String prefix =
                        Files.exists(file)
                                        && Files.size(file) > 0
                                        && !Files.readString(file).endsWith("\n")
                                ? "\n"
                                : "";
                Files.writeString(
                        file,
                        prefix + host + " " + entry.getType() + " " + entry.getKey() + "\n",
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
                delegate.add(entry, null);
                return OK;
            } catch (Exception e) {
                return NOT_INCLUDED;
            }
        }
    }

    @Override
    public void add(HostKey key, UserInfo ui) {
        throw new UnsupportedOperationException("Trust must pass fingerprint confirmation");
    }

    @Override
    public void remove(String host, String type) {
        throw new UnsupportedOperationException("Edit known_hosts explicitly");
    }

    @Override
    public void remove(String host, String type, byte[] key) {
        throw new UnsupportedOperationException("Edit known_hosts explicitly");
    }

    @Override
    public String getKnownHostsRepositoryID() {
        return file.toString();
    }

    @Override
    public HostKey[] getHostKey() {
        return delegate.getHostKey();
    }

    @Override
    public HostKey[] getHostKey(String host, String type) {
        return delegate.getHostKey(host, type);
    }
}
