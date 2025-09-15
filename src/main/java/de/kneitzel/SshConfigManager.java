package de.kneitzel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manager class for managing multiple SSH configurations.
 * <p>
 * This class manages a collection of SSH configurations and provides
 * functions for loading, saving, adding and removing configurations.
 * The configurations are persistently stored as JSON file in the user directory.
 *
 * @author Konrad Neitzel
 * @version 1.1-SNAPSHOT
 * @since 1.0
 */
@Data
@Slf4j
public class SshConfigManager {

    /**
     * Name of the configuration file in the user directory
     */
    private static final String CONFIG_FILE_NAME = ".sshAutoConnect-configs.json";

    /**
     * File reference to the JSON configuration file
     */
    private final File configFile;

    /**
     * Gson instance for JSON serialization/deserialization
     */
    private final Gson gson;

    /**
     * Map of all SSH configurations, key is the configuration name
     */
    private Map<String, SshConfig> configs;

    /**
     * Constructor for the SSH configuration manager.
     * <p>
     * Initializes the manager, creates the Gson instance for JSON processing
     * and loads existing configurations from the file. If no file exists,
     * a default configuration is created.
     */
    public SshConfigManager() {
        String userHome = System.getProperty("user.home");
        this.configFile = new File(userHome, CONFIG_FILE_NAME);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.configs = new HashMap<>();
        loadConfigs();
    }

    /**
     * Loads SSH configurations from the JSON file.
     * <p>
     * Attempts to read and parse the configuration file. If the file
     * does not exist or an error occurs, a default configuration is created.
     */
    private void loadConfigs() {
        if (!configFile.exists()) {
            log.info("No configuration file found, creating default configuration");
            createDefaultConfig();
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            Type type = new TypeToken<Map<String, SshConfig>>() {
            }.getType();
            Map<String, SshConfig> loadedConfigs = gson.fromJson(reader, type);
            if (loadedConfigs != null) {
                this.configs = loadedConfigs;
            }
            log.info("Configurations loaded: {}", configs.keySet());
        } catch (IOException e) {
            log.error("Error loading configurations", e);
            createDefaultConfig();
        }
    }

    /**
     * Creates a default configuration for new installations.
     * <p>
     * Called when no configuration file exists or it cannot be read.
     * Creates an example configuration with default port mapping.
     */
    private void createDefaultConfig() {
        SshConfig defaultConfig = new SshConfig("Default");
        defaultConfig.setHostname("example.com");
        defaultConfig.setUsername("exampleUser");
        defaultConfig.setPassword("examplePassword");
        defaultConfig.getPortMappings().add(new PortMapping(8080, 8080, "startvnc", "stopvnc"));
        addConfig(defaultConfig);
    }

    /**
     * Adds a new SSH configuration.
     * <p>
     * Validates the configuration name and adds the configuration to the
     * collection. Then automatically saves all configurations.
     *
     * @param config The SSH configuration to add
     * @throws IllegalArgumentException when the configuration name is empty or null
     */
    public void addConfig(SshConfig config) {
        if (config.getName() == null || config.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Configuration name must not be empty");
        }
        configs.put(config.getName(), config);
        saveConfigs();
    }

    /**
     * Saves all SSH configurations to the JSON file.
     * <p>
     * Serializes the current configuration collection as JSON and
     * writes it to the configuration file in the user directory.
     */
    private void saveConfigs() {
        try (FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(configs, writer);
            log.info("Configurations saved");
        } catch (IOException e) {
            log.error("Error saving configurations", e);
        }
    }

    /**
     * Removes an SSH configuration.
     * <p>
     * Deletes the configuration with the specified name from the collection
     * and saves the changes persistently.
     *
     * @param name Name of the configuration to remove
     */
    public void removeConfig(String name) {
        configs.remove(name);
        saveConfigs();
    }

    /**
     * Returns an SSH configuration by name.
     *
     * @param name Name of the configuration to find
     * @return SSH configuration or null if not found
     */
    public SshConfig getConfig(String name) {
        return configs.get(name);
    }

    /**
     * Returns all SSH configurations.
     *
     * @return Collection of all managed SSH configurations
     */
    public Collection<SshConfig> getAllConfigs() {
        return configs.values();
    }

    /**
     * Returns all configuration names.
     *
     * @return Set of all configuration names
     */
    public Set<String> getConfigNames() {
        return configs.keySet();
    }

    /**
     * Checks if a configuration with the specified name exists.
     *
     * @param name Name of the configuration to check
     * @return true if a configuration with this name exists
     */
    public boolean hasConfig(String name) {
        return configs.containsKey(name);
    }

    /**
     * Updates an existing SSH configuration.
     * <p>
     * Replaces the existing configuration with the same name with the
     * passed configuration and saves the changes.
     *
     * @param config The updated SSH configuration
     */
    public void updateConfig(SshConfig config) {
        if (config.getName() != null && configs.containsKey(config.getName())) {
            configs.put(config.getName(), config);
            saveConfigs();
        }
    }
}
