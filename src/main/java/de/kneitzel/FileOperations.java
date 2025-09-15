package de.kneitzel;

import java.io.IOException;
import java.util.Map;

/**
 * Interface for file operations to make SshConfigManager testable.
 * This abstraction allows dependency injection for testing purposes.
 *
 * @author Konrad Neitzel
 * @version 1.1-SNAPSHOT
 */
public interface FileOperations {

    /**
     * Checks if a file exists.
     *
     * @param filePath Path to the file
     * @return true if file exists, false otherwise
     */
    boolean fileExists(String filePath);

    /**
     * Reads SSH configurations from a file.
     *
     * @param filePath Path to the configuration file
     * @return Map of configurations or null if file doesn't exist
     * @throws IOException if reading fails
     */
    Map<String, SshConfig> readConfigs(String filePath) throws IOException;

    /**
     * Writes SSH configurations to a file.
     *
     * @param filePath Path to the configuration file
     * @param configs  Map of configurations to write
     * @throws IOException if writing fails
     */
    void writeConfigs(String filePath, Map<String, SshConfig> configs) throws IOException;
}
