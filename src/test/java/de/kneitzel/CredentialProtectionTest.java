package de.kneitzel;

import static org.junit.jupiter.api.Assertions.*;

import de.kneitzel.model.*;
import de.kneitzel.security.*;

import org.junit.jupiter.api.*;

import java.util.*;

class CredentialProtectionTest {
    static class Store implements KeyStoreAccess {
        Map<String, String> keys = new HashMap<>();

        public String read(String id) {
            return keys.get(id);
        }

        public void write(String id, String value) {
            keys.put(id, value);
        }
    }

    @Test
    void encryptsWithFreshNoncesAndAuthenticatesTargetIdentity() throws Exception {
        Store store = new Store();
        CredentialProtection crypto = new CredentialProtection(store);
        Credentials clear = new Credentials("secret-login", "secret-passphrase");
        var a = crypto.encrypt("target", clear, null);
        var b = crypto.encrypt("target", clear, a);
        assertNotEquals(a.nonce(), b.nonce());
        assertEquals(a.keyId(), b.keyId());
        assertEquals(clear, crypto.decrypt("target", b));
        assertFalse(new com.google.gson.Gson().toJson(a).contains("secret-login"));
        assertFalse(clear.toString().contains("secret"));
        assertThrows(Exception.class, () -> crypto.decrypt("other-target", a));
        byte[] bytes = Base64.getDecoder().decode(a.ciphertext());
        bytes[0] ^= 1;
        var tampered =
                new SecretEnvelope(
                        1, a.keyId(), a.nonce(), Base64.getEncoder().encodeToString(bytes));
        assertThrows(Exception.class, () -> crypto.decrypt("target", tampered));
    }

    @Test
    void missingExistingKeyDoesNotSilentlyCreateReplacement() throws Exception {
        Store store = new Store();
        CredentialProtection crypto = new CredentialProtection(store);
        var envelope = crypto.encrypt("target", Credentials.EMPTY, null);
        store.keys.clear();
        assertThrows(Exception.class, () -> crypto.decrypt("target", envelope));
        assertThrows(Exception.class, () -> crypto.encrypt("target", Credentials.EMPTY, envelope));
        assertTrue(store.keys.isEmpty());
    }

    @Test
    void unavailableStoreCannotFallBackToFile() {
        CredentialProtection crypto =
                new CredentialProtection(
                        new KeyStoreAccess() {
                            public String read(String id) throws Exception {
                                throw new Exception("locked");
                            }

                            public void write(String id, String value) throws Exception {
                                throw new Exception("locked");
                            }
                        });
        assertThrows(
                Exception.class,
                () -> crypto.encrypt("target", new Credentials("secret", ""), null));
    }
}
