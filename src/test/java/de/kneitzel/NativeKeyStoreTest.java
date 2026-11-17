package de.kneitzel;

import static org.junit.jupiter.api.Assertions.*;

import de.kneitzel.security.NativeKeyStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.UUID;

class NativeKeyStoreTest {
    @Test
    @EnabledIfSystemProperty(named = "ssh.native.credentials", matches = "true")
    void realOsStoreRoundTripAndRemoval() throws Exception {
        NativeKeyStore store =
                new NativeKeyStore("de.kneitzel.SSHAutoConnect.test." + UUID.randomUUID());
        try {
            store.write("fixture-key", "synthetic-test-key-not-a-password");
            assertEquals("synthetic-test-key-not-a-password", store.read("fixture-key"));
        } finally {
            store.delete("fixture-key");
        }
    }
}
