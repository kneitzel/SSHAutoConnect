package de.kneitzel;

import static org.junit.jupiter.api.Assertions.*;

import de.kneitzel.model.*;
import de.kneitzel.runtime.*;
import de.kneitzel.security.*;

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.shell.ProcessShellFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class SshIntegrationTest {
    @TempDir Path temp;
    private SshServer server;
    private SshConfig target;
    private Path remote;

    @BeforeEach
    void server() throws Exception {
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        SimpleGeneratorHostKeyProvider keys =
                new SimpleGeneratorHostKeyProvider(temp.resolve("hostkey"));
        keys.setAlgorithm("RSA");
        keys.setKeySize(2048);
        server.setKeyPairProvider(keys);
        server.setPasswordAuthenticator(
                (user, password, session) ->
                        user.equals("fixture") && password.equals("fixture-password"));
        server.setPublickeyAuthenticator((user, key, session) -> user.equals("fixture"));
        server.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);
        remote = Files.createDirectory(temp.resolve("remote"));
        server.setFileSystemFactory(new VirtualFileSystemFactory(remote));
        server.setSubsystemFactories(List.of(new SftpSubsystemFactory.Builder().build()));
        server.setCommandFactory(
                (channel, text) ->
                        new ProcessShellFactory(
                                        text,
                                        System.getProperty("os.name").startsWith("Windows")
                                                ? List.of("cmd.exe", "/c", text)
                                                : List.of("/bin/sh", "-c", text))
                                .createShell(channel));
        server.start();
        target = new SshConfig();
        target.setHostname("127.0.0.1");
        target.setPort(server.getPort());
        target.setUsername("fixture");
    }

    @AfterEach
    void close() throws Exception {
        server.stop(true);
    }

    private SshTransport open(VerifiedHostKeys.Trust trust) throws Exception {
        return JschTransport.factory(temp.resolve("known_hosts"), trust)
                .open(target, new Credentials("fixture-password", ""));
    }

    @Test
    void verifiesTrustPersistsKnownHostRunsCommandsAndReusesTrust() throws Exception {
        AtomicInteger prompts = new AtomicInteger();
        try (var t =
                open(
                        (host, fp) -> {
                            assertTrue(fp.startsWith("SHA256:"));
                            prompts.incrementAndGet();
                            return true;
                        })) {
            assertTrue(t.connected());
            NamedCommand command = new NamedCommand();
            command.setCommand(
                    System.getProperty("os.name").startsWith("Windows")
                            ? "echo fixture-output"
                            : "printf fixture-output");
            CommandResult result = t.execute(command, new AtomicBoolean(), Long.MAX_VALUE);
            assertTrue(result.succeeded(), result.toString());
            assertEquals("fixture-output", result.output().strip());
        }
        try (var t =
                open(
                        (host, fp) -> {
                            fail("Known key must not prompt");
                            return false;
                        })) {
            assertTrue(t.connected());
        }
        assertEquals(1, prompts.get());
        assertTrue(Files.size(temp.resolve("known_hosts")) > 0);
    }

    @Test
    void rejectedUnknownHostDoesNotCreateTrustFile() {
        assertThrows(Exception.class, () -> open((host, fp) -> false));
        assertFalse(Files.exists(temp.resolve("known_hosts")));
    }

    @Test
    void changedHostKeyIsBlockedWithoutAcceptancePrompt() throws Exception {
        try (var t = open((host, fp) -> true)) {
            assertTrue(t.connected());
        }
        int oldPort = server.getPort();
        server.stop(true);
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(oldPort);
        server.setPasswordAuthenticator((u, p, session) -> true);
        SimpleGeneratorHostKeyProvider replacement =
                new SimpleGeneratorHostKeyProvider(temp.resolve("different-key"));
        replacement.setAlgorithm("RSA");
        replacement.setKeySize(2048);
        server.setKeyPairProvider(replacement);
        server.start();
        target.setPort(server.getPort());
        assertThrows(
                Exception.class,
                () ->
                        open(
                                (host, fp) -> {
                                    fail("Changed key cannot be accepted");
                                    return true;
                                }));
    }

    @Test
    void forwardsRealSocketTrafficAndReleasesPort() throws Exception {
        try (var echo = new ServerSocket(0);
                var t = open((host, fp) -> true)) {
            var response =
                    CompletableFuture.runAsync(
                            () -> {
                                try (var client = echo.accept()) {
                                    client.getOutputStream()
                                            .write(client.getInputStream().readNBytes(4));
                                } catch (IOException e) {
                                    throw new CompletionException(e);
                                }
                            });
            PortMapping mapping = new PortMapping();
            try (var port = new ServerSocket(0)) {
                mapping.setLocalPort(port.getLocalPort());
            }
            mapping.setRemotePort(echo.getLocalPort());
            t.forward(mapping);
            try (var client = new Socket("127.0.0.1", mapping.getLocalPort())) {
                client.setSoTimeout(3000);
                client.getOutputStream().write(new byte[] {1, 2, 3, 4});
                assertArrayEquals(new byte[] {1, 2, 3, 4}, client.getInputStream().readNBytes(4));
            }
            response.get(3, TimeUnit.SECONDS);
            t.stopForward(mapping);
            assertThrows(IOException.class, () -> new Socket("127.0.0.1", mapping.getLocalPort()));
        }
    }

    @Test
    void sftpUploadDownloadOverwriteAndEmptyFiles() throws Exception {
        try (var t = open((host, fp) -> true)) {
            Path local = temp.resolve("upload.txt");
            Files.writeString(local, "fixture-content");
            t.transfer(
                    true,
                    local,
                    "/uploaded.txt",
                    new AtomicBoolean(),
                    value -> assertTrue(Double.isFinite(value)),
                    () -> true);
            assertEquals("fixture-content", Files.readString(remote.resolve("uploaded.txt")));
            Files.writeString(local, "replacement");
            assertThrows(
                    Exception.class,
                    () ->
                            t.transfer(
                                    true,
                                    local,
                                    "/uploaded.txt",
                                    new AtomicBoolean(),
                                    p -> {},
                                    () -> false));
            assertEquals("fixture-content", Files.readString(remote.resolve("uploaded.txt")));
            Path download = temp.resolve("download.txt");
            t.transfer(false, download, "/uploaded.txt", new AtomicBoolean(), p -> {}, () -> true);
            assertEquals("fixture-content", Files.readString(download));
            Files.writeString(local, "");
            t.transfer(
                    true,
                    local,
                    "/empty",
                    new AtomicBoolean(),
                    p -> assertTrue(Double.isFinite(p)),
                    () -> true);
            assertEquals(0, Files.size(remote.resolve("empty")));
        }
    }

    @Test
    void authenticatesEncryptedPrivateKeyAndRejectsWrongPassphrase() throws Exception {
        com.jcraft.jsch.KeyPair pair =
                com.jcraft.jsch.KeyPair.genKeyPair(
                        new com.jcraft.jsch.JSch(), com.jcraft.jsch.KeyPair.RSA, 2048);
        Path key = temp.resolve("client-key");
        try {
            pair.writePrivateKey(
                    key.toString(),
                    "key-passphrase".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            pair.dispose();
        }
        target.setPrivateKeyPath(key.toString());
        var factory = JschTransport.factory(temp.resolve("known_hosts"), (host, fp) -> true);
        assertThrows(
                Exception.class,
                () -> factory.open(target, new Credentials("", "wrong-passphrase")));
        try (var transport = factory.open(target, new Credentials("", "key-passphrase"))) {
            assertTrue(transport.connected());
        }
    }

    @Test
    void cancelledDownloadPreservesDestinationAndReleasesItsChannel() throws Exception {
        Files.write(remote.resolve("large"), new byte[128 * 1024]);
        Path destination = temp.resolve("existing");
        Files.writeString(destination, "original");
        try (var transport = open((host, fp) -> true)) {
            assertThrows(
                    Exception.class,
                    () ->
                            transport.transfer(
                                    false,
                                    destination,
                                    "/large",
                                    new AtomicBoolean(true),
                                    progress -> {},
                                    () -> true));
            assertEquals("original", Files.readString(destination));
            transport.transfer(
                    false,
                    temp.resolve("next"),
                    "/large",
                    new AtomicBoolean(),
                    progress -> {},
                    () -> true);
            assertEquals(128 * 1024, Files.size(temp.resolve("next")));
            try (var files = Files.list(temp)) {
                assertTrue(
                        files.noneMatch(
                                path ->
                                        path.getFileName()
                                                .toString()
                                                .startsWith(".ssh-download-")));
            }
        }
    }

    @Test
    void timedOutCommandHasUnconfirmedOutcomeAndSessionRemainsUsable() throws Exception {
        Assumptions.assumeFalse(System.getProperty("os.name").startsWith("Windows"));
        try (var t = open((host, fp) -> true)) {
            NamedCommand command = new NamedCommand();
            command.setCommand("sleep 3");
            command.setTimeoutSeconds(1);
            assertEquals(
                    CommandResult.Outcome.UNCONFIRMED,
                    t.execute(command, new AtomicBoolean(), Long.MAX_VALUE).outcome());
            command.setCommand("printf still-connected");
            assertTrue(t.execute(command, new AtomicBoolean(), Long.MAX_VALUE).succeeded());
        }
    }
}
