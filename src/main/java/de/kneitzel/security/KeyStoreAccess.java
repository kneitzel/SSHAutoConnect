package de.kneitzel.security;

/** Never falls back to disk. Null means a missing key; exceptions mean unavailable storage. */
public interface KeyStoreAccess {
    String read(String id) throws Exception;

    void write(String id, String value) throws Exception;
}
