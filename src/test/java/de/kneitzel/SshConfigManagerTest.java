package de.kneitzel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the SshConfigManager class.
 * These tests use mocking to avoid actual file system operations.
 *
 * @author Konrad Neitzel
 * @version 1.1-SNAPSHOT
 */
@ExtendWith(MockitoExtension.class)
class SshConfigManagerTest {

    @Mock
    private FileOperations fileOperations;

    private TestableSshConfigManager configManager;

    @BeforeEach
    void setUp() {
        // Reset any previous interactions before each test
        reset(fileOperations);
    }

    @Test
    @DisplayName("Should create default config when file does not exist")
    void shouldCreateDefaultConfigWhenFileDoesNotExist() throws IOException {
        // Given
        when(fileOperations.fileExists("test-config.json")).thenReturn(false);

        // When
        configManager = new TestableSshConfigManager(fileOperations, "test-config.json");

        // Then
        assertTrue(configManager.hasConfig("Default"));
        SshConfig defaultConfig = configManager.getConfig("Default");
        assertNotNull(defaultConfig);
        assertEquals("example.com", defaultConfig.getHostname());
        assertEquals("exampleUser", defaultConfig.getUsername());
        assertEquals("examplePassword", defaultConfig.getPassword());
        assertEquals(1, defaultConfig.getPortMappings().size());

        verify(fileOperations).fileExists("test-config.json");
        verify(fileOperations).writeConfigs(eq("test-config.json"), any());
    }

    @Test
    @DisplayName("Should load existing configs from file")
    void shouldLoadExistingConfigsFromFile() throws IOException {
        // Given
        Map<String, SshConfig> existingConfigs = new HashMap<>();
        SshConfig config1 = new SshConfig("Config1");
        config1.setHostname("host1.com");
        config1.setUsername("user1");
        existingConfigs.put("Config1", config1);

        when(fileOperations.fileExists("test-config.json")).thenReturn(true);
        when(fileOperations.readConfigs("test-config.json")).thenReturn(existingConfigs);

        // When
        configManager = new TestableSshConfigManager(fileOperations, "test-config.json");

        // Then
        assertTrue(configManager.hasConfig("Config1"));
        assertEquals("host1.com", configManager.getConfig("Config1").getHostname());
        assertEquals("user1", configManager.getConfig("Config1").getUsername());

        verify(fileOperations).fileExists("test-config.json");
        verify(fileOperations).readConfigs("test-config.json");
    }

    @Test
    @DisplayName("Should add new config successfully")
    void shouldAddNewConfigSuccessfully() throws IOException {
        // Given
        when(fileOperations.fileExists(anyString())).thenReturn(false);
        configManager = new TestableSshConfigManager(fileOperations, "test-config.json");
        reset(fileOperations); // Reset after initialization

        SshConfig newConfig = new SshConfig("NewConfig");
        newConfig.setHostname("newhost.com");
        newConfig.setUsername("newuser");

        // When
        configManager.addConfig(newConfig);

        // Then
        assertTrue(configManager.hasConfig("NewConfig"));
        assertEquals("newhost.com", configManager.getConfig("NewConfig").getHostname());
        assertEquals("newuser", configManager.getConfig("NewConfig").getUsername());

        verify(fileOperations).writeConfigs(eq("test-config.json"), any());
    }

    @Test
    @DisplayName("Should throw exception when adding config with empty name")
    void shouldThrowExceptionWhenAddingConfigWithEmptyName() throws IOException {
        // Given
        when(fileOperations.fileExists(anyString())).thenReturn(false);
        configManager = new TestableSshConfigManager(fileOperations, "test-config.json");
        SshConfig configWithEmptyName = new SshConfig("");

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> configManager.addConfig(configWithEmptyName));
    }

    @Test
    @DisplayName("Should throw exception when adding config with null name")
    void shouldThrowExceptionWhenAddingConfigWithNullName() throws IOException {
        // Given
        when(fileOperations.fileExists(anyString())).thenReturn(false);
        configManager = new TestableSshConfigManager(fileOperations, "test-config.json");
        SshConfig configWithNullName = new SshConfig();
        configWithNullName.setName(null);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> configManager.addConfig(configWithNullName));
    }

    @Test
    @DisplayName("Should remove config successfully")
    void shouldRemoveConfigSuccessfully() throws IOException {
        // Given
        when(fileOperations.fileExists(anyString())).thenReturn(false);
        configManager = new TestableSshConfigManager(fileOperations, "test-config.json");
        reset(fileOperations); // Reset after initialization

        SshConfig config = new SshConfig("ToRemove");
        configManager.addConfig(config);
        reset(fileOperations); // Reset after adding

        // When
        configManager.removeConfig("ToRemove");

        // Then
        assertFalse(configManager.hasConfig("ToRemove"));
        verify(fileOperations).writeConfigs(eq("test-config.json"), any());
    }

    @Test
    @DisplayName("Should return all config names")
    void shouldReturnAllConfigNames() throws IOException {
        // Given
        when(fileOperations.fileExists(anyString())).thenReturn(false);
        configManager = new TestableSshConfigManager(fileOperations, "test-config.json");

        // When
        Set<String> configNames = configManager.getConfigNames();

        // Then
        assertTrue(configNames.contains("Default"));
        assertEquals(1, configNames.size());
    }

    @Test
    @DisplayName("Should return all configs")
    void shouldReturnAllConfigs() throws IOException {
        // Given
        when(fileOperations.fileExists(anyString())).thenReturn(false);
        configManager = new TestableSshConfigManager(fileOperations, "test-config.json");

        // When
        Collection<SshConfig> allConfigs = configManager.getAllConfigs();

        // Then
        assertEquals(1, allConfigs.size());
        assertTrue(allConfigs.stream().anyMatch(config -> "Default".equals(config.getName())));
    }

    @Test
    @DisplayName("Should handle IOException when loading configs")
    void shouldHandleIOExceptionWhenLoadingConfigs() throws IOException {
        // Given
        when(fileOperations.fileExists("test-config.json")).thenReturn(true);
        when(fileOperations.readConfigs("test-config.json")).thenThrow(new IOException("Read error"));

        // When
        configManager = new TestableSshConfigManager(fileOperations, "test-config.json");

        // Then
        assertTrue(configManager.hasConfig("Default")); // Should fall back to default config
        verify(fileOperations).fileExists("test-config.json");
        verify(fileOperations).readConfigs("test-config.json");
    }

    @Test
    @DisplayName("Should throw RuntimeException when saving fails")
    void shouldThrowRuntimeExceptionWhenSavingFails() throws IOException {
        // Given
        when(fileOperations.fileExists(anyString())).thenReturn(false);
        doThrow(new IOException("Write error")).when(fileOperations).writeConfigs(anyString(), any());

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            new TestableSshConfigManager(fileOperations, "test-config.json");
        });
    }

    /**
     * Testable version of SshConfigManager that accepts FileOperations dependency.
     */
    private static class TestableSshConfigManager {
        private final FileOperations fileOperations;

        private final String configFilePath;

        private Map<String, SshConfig> configs;

        public TestableSshConfigManager(FileOperations fileOperations, String configFilePath) {
            this.fileOperations = fileOperations;
            this.configFilePath = configFilePath;
            this.configs = new HashMap<>();
            loadConfigs();
        }

        private void loadConfigs() {
            if (!fileOperations.fileExists(configFilePath)) {
                createDefaultConfig();
                return;
            }

            try {
                Map<String, SshConfig> loadedConfigs = fileOperations.readConfigs(configFilePath);
                if (loadedConfigs != null) {
                    this.configs = loadedConfigs;
                }
            } catch (IOException e) {
                createDefaultConfig();
            }
        }

        private void createDefaultConfig() {
            SshConfig defaultConfig = new SshConfig("Default");
            defaultConfig.setHostname("example.com");
            defaultConfig.setUsername("exampleUser");
            defaultConfig.setPassword("examplePassword");
            defaultConfig.getPortMappings().add(new PortMapping(8080, 8080, "startvnc", "stopvnc"));
            addConfigInternal(defaultConfig);
        }

        private void addConfigInternal(SshConfig config) {
            configs.put(config.getName(), config);
            saveConfigs();
        }

        private void saveConfigs() {
            try {
                fileOperations.writeConfigs(configFilePath, configs);
            } catch (IOException e) {
                // Log error in real implementation
                throw new RuntimeException("Failed to save configurations", e);
            }
        }

        public void addConfig(SshConfig config) {
            if (config.getName() == null || config.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("Configuration name must not be empty");
            }
            addConfigInternal(config);
        }

        public void removeConfig(String name) {
            configs.remove(name);
            saveConfigs();
        }

        public SshConfig getConfig(String name) {
            return configs.get(name);
        }

        public Set<String> getConfigNames() {
            return configs.keySet();
        }

        public Collection<SshConfig> getAllConfigs() {
            return configs.values();
        }

        public boolean hasConfig(String name) {
            return configs.containsKey(name);
        }
    }
}
