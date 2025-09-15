package de.kneitzel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the SshConfig class.
 *
 * @author Konrad Neitzel
 * @version 1.1-SNAPSHOT
 */
class SshConfigTest {

    private SshConfig sshConfig;

    @BeforeEach
    void setUp() {
        sshConfig = new SshConfig();
    }

    @Test
    @DisplayName("Should create SshConfig with default values")
    void shouldCreateSshConfigWithDefaultValues() {
        // Then
        assertNull(sshConfig.getName());
        assertNull(sshConfig.getHostname());
        assertEquals(22, sshConfig.getPort());
        assertNull(sshConfig.getUsername());
        assertNull(sshConfig.getPrivateKeyPath());
        assertNull(sshConfig.getPassword());
        assertNotNull(sshConfig.getPortMappings());
        assertTrue(sshConfig.getPortMappings().isEmpty());
        assertFalse(sshConfig.isConnected());
        assertFalse(sshConfig.isAutoConnect());
        assertEquals("xterm", sshConfig.getXtermCommand());
        assertEquals("/tmp/", sshConfig.getUploadDirectory());
    }

    @Test
    @DisplayName("Should create SshConfig with name constructor")
    void shouldCreateSshConfigWithNameConstructor() {
        // Given
        String configName = "test-config";

        // When
        SshConfig config = new SshConfig(configName);

        // Then
        assertEquals(configName, config.getName());
        assertNotNull(config.getPortMappings());
        assertTrue(config.getPortMappings().isEmpty());
    }

    @Test
    @DisplayName("Should create SshConfig with all parameters constructor")
    void shouldCreateSshConfigWithAllParametersConstructor() {
        // Given
        String name = "test-config";
        String hostname = "example.com";
        int port = 2222;
        String username = "testuser";
        String privateKeyPath = "/path/to/key";
        String password = "password";
        List<PortMapping> portMappings = new ArrayList<>();
        portMappings.add(new PortMapping(8080, 80, null, null));
        boolean connected = true;
        boolean autoConnect = true;
        String xtermCommand = "gnome-terminal";
        String uploadDirectory = "/home/user/";

        // When
        SshConfig config = new SshConfig(name, hostname, port, username, privateKeyPath,
                password, portMappings, connected, autoConnect,
                xtermCommand, uploadDirectory);

        // Then
        assertEquals(name, config.getName());
        assertEquals(hostname, config.getHostname());
        assertEquals(port, config.getPort());
        assertEquals(username, config.getUsername());
        assertEquals(privateKeyPath, config.getPrivateKeyPath());
        assertEquals(password, config.getPassword());
        assertEquals(portMappings, config.getPortMappings());
        assertEquals(connected, config.isConnected());
        assertEquals(autoConnect, config.isAutoConnect());
        assertEquals(xtermCommand, config.getXtermCommand());
        assertEquals(uploadDirectory, config.getUploadDirectory());
    }

    @Test
    @DisplayName("Should set and get all properties correctly")
    void shouldSetAndGetAllPropertiesCorrectly() {
        // Given
        String name = "new-config";
        String hostname = "newhost.com";
        int port = 2222;
        String username = "newuser";
        String privateKeyPath = "/new/path/to/key";
        String password = "newpassword";
        List<PortMapping> portMappings = new ArrayList<>();
        portMappings.add(new PortMapping(9090, 90, "start", "stop"));
        boolean connected = true;
        boolean autoConnect = true;
        String xtermCommand = "konsole";
        String uploadDirectory = "/uploads/";

        // When
        sshConfig.setName(name);
        sshConfig.setHostname(hostname);
        sshConfig.setPort(port);
        sshConfig.setUsername(username);
        sshConfig.setPrivateKeyPath(privateKeyPath);
        sshConfig.setPassword(password);
        sshConfig.setPortMappings(portMappings);
        sshConfig.setConnected(connected);
        sshConfig.setAutoConnect(autoConnect);
        sshConfig.setXtermCommand(xtermCommand);
        sshConfig.setUploadDirectory(uploadDirectory);

        // Then
        assertEquals(name, sshConfig.getName());
        assertEquals(hostname, sshConfig.getHostname());
        assertEquals(port, sshConfig.getPort());
        assertEquals(username, sshConfig.getUsername());
        assertEquals(privateKeyPath, sshConfig.getPrivateKeyPath());
        assertEquals(password, sshConfig.getPassword());
        assertEquals(portMappings, sshConfig.getPortMappings());
        assertEquals(connected, sshConfig.isConnected());
        assertEquals(autoConnect, sshConfig.isAutoConnect());
        assertEquals(xtermCommand, sshConfig.getXtermCommand());
        assertEquals(uploadDirectory, sshConfig.getUploadDirectory());
    }

    @Test
    @DisplayName("Should handle null port mappings list")
    void shouldHandleNullPortMappingsList() {
        // When
        sshConfig.setPortMappings(null);

        // Then
        assertNull(sshConfig.getPortMappings());
    }

    @Test
    @DisplayName("Should allow modification of port mappings list")
    void shouldAllowModificationOfPortMappingsList() {
        // Given
        PortMapping mapping1 = new PortMapping(8080, 80, null, null);
        PortMapping mapping2 = new PortMapping(9090, 90, null, null);

        // When
        sshConfig.getPortMappings().add(mapping1);
        sshConfig.getPortMappings().add(mapping2);

        // Then
        assertEquals(2, sshConfig.getPortMappings().size());
        assertTrue(sshConfig.getPortMappings().contains(mapping1));
        assertTrue(sshConfig.getPortMappings().contains(mapping2));
    }
}
