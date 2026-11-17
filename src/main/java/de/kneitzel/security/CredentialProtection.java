package de.kneitzel.security;

import com.google.gson.Gson;

import de.kneitzel.model.SecretEnvelope;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

import javax.crypto.*;
import javax.crypto.spec.*;

/** AES-GCM with target identity authenticated as associated data; keys stay in OS storage. */
public final class CredentialProtection {
    private final KeyStoreAccess store;
    private final SecureRandom random = new SecureRandom();
    private final Gson gson = new Gson();

    public CredentialProtection(KeyStoreAccess store) {
        this.store = store;
    }

    public SecretEnvelope encrypt(String targetId, Credentials credentials, SecretEnvelope existing)
            throws Exception {
        String id = existing == null ? UUID.randomUUID().toString() : existing.keyId();
        String encoded = existing == null ? null : store.read(id);
        if (encoded == null) {
            if (existing != null)
                throw new IllegalStateException(
                        "Original encryption key missing. Confirm credential replacement before"
                                + " saving.");
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            encoded = Base64.getEncoder().encodeToString(bytes);
            Arrays.fill(bytes, (byte) 0);
            store.write(id, encoded);
            if (!encoded.equals(store.read(id)))
                throw new IllegalStateException("Credential store did not retain key");
        }
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        byte[] clear = gson.toJson(credentials).getBytes(StandardCharsets.UTF_8);
        try {
            return new SecretEnvelope(
                    1,
                    id,
                    Base64.getEncoder().encodeToString(nonce),
                    Base64.getEncoder()
                            .encodeToString(
                                    cipher(Cipher.ENCRYPT_MODE, encoded, nonce, targetId)
                                            .doFinal(clear)));
        } finally {
            Arrays.fill(clear, (byte) 0);
        }
    }

    public Credentials decrypt(String targetId, SecretEnvelope envelope) throws Exception {
        if (envelope == null) return Credentials.EMPTY;
        if (envelope.version() != 1)
            throw new IllegalArgumentException("Unsupported credential format");
        String encoded = store.read(envelope.keyId());
        if (encoded == null) throw new IllegalStateException("Encryption key missing");
        byte[] nonce = Base64.getDecoder().decode(envelope.nonce());
        if (nonce.length != 12) throw new IllegalArgumentException("Invalid credential nonce");
        byte[] clear =
                cipher(Cipher.DECRYPT_MODE, encoded, nonce, targetId)
                        .doFinal(Base64.getDecoder().decode(envelope.ciphertext()));
        try {
            return gson.fromJson(new String(clear, StandardCharsets.UTF_8), Credentials.class);
        } finally {
            Arrays.fill(clear, (byte) 0);
        }
    }

    private Cipher cipher(int mode, String encoded, byte[] nonce, String target) throws Exception {
        byte[] key = Base64.getDecoder().decode(encoded);
        if (key.length != 32) throw new IllegalArgumentException("Invalid encryption key");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(target.getBytes(StandardCharsets.UTF_8));
            return cipher;
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }
}
