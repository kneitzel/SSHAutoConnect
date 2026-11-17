package de.kneitzel.model;

public record SecretEnvelope(int version, String keyId, String nonce, String ciphertext) {}
