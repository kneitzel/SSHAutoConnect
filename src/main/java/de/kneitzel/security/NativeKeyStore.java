package de.kneitzel.security;

import com.github.javakeyring.Keyring;

public final class NativeKeyStore implements KeyStoreAccess {
    private final String service;

    public NativeKeyStore() {
        this("de.kneitzel.SSHAutoConnect");
    }

    public NativeKeyStore(String service) {
        this.service = service;
    }

    @Override
    public synchronized String read(String id) throws Exception {
        try (Keyring keyring = Keyring.create()) {
            return keyring.getPassword(service, id);
        }
    }

    @Override
    public synchronized void write(String id, String value) throws Exception {
        try (Keyring keyring = Keyring.create()) {
            keyring.setPassword(service, id, value);
        }
    }

    public synchronized void delete(String id) throws Exception {
        try (Keyring keyring = Keyring.create()) {
            keyring.deletePassword(service, id);
        }
    }
}
