package de.kneitzel;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the SshConnection class.
 * These tests use mocking to avoid actual SSH connections.
 *
 * @author Konrad Neitzel
 * @version 1.1-SNAPSHOT
 */
@ExtendWith(MockitoExtension.class)
class SshConnectionTest {

    @Mock
    private JSch mockJSch;

    @Mock
    private Session mockSession;

    @Mock
    private Channel mockChannel;

    @Mock
    private ChannelExec mockChannelExec;

    private SshConfig testConfig;

    private SshConnection sshConnection;

    @BeforeEach
    void setUp() {
        testConfig = new SshConfig("TestConfig");
        testConfig.setHostname("test.example.com");
        testConfig.setUsername("testuser");
        testConfig.setPassword("testpass");
        testConfig.setPort(22);

        // Add some port mappings
        testConfig.getPortMappings().add(new PortMapping(8080, 80, "start_web", "stop_web"));
        testConfig.getPortMappings().add(new PortMapping(3306, 3306, null, null));

        sshConnection = new SshConnection(testConfig);
    }

    @Test
    @DisplayName("Should initialize SshConnection with correct config")
    void shouldInitializeSshConnectionWithCorrectConfig() {
        // Then
        assertEquals(testConfig, sshConnection.getConfig());
        assertNotNull(sshConnection.getPortForwardChannels());
        assertTrue(sshConnection.getPortForwardChannels().isEmpty());
        assertNull(sshConnection.getSession());
        assertFalse(sshConnection.isConnected());
    }

    @Test
    @DisplayName("Should handle config without port mappings")
    void shouldHandleConfigWithoutPortMappings() {
        // Given
        SshConfig configWithoutMappings = new SshConfig("EmptyConfig");
        configWithoutMappings.setHostname("empty.example.com");
        configWithoutMappings.setUsername("emptyuser");

        // When
        SshConnection connection = new SshConnection(configWithoutMappings);

        // Then
        assertEquals(configWithoutMappings, connection.getConfig());
        assertNotNull(connection.getPortForwardChannels());
        assertTrue(connection.getPortForwardChannels().isEmpty());
    }

    @Test
    @DisplayName("Should handle config with null values gracefully")
    void shouldHandleConfigWithNullValuesGracefully() {
        // Given
        SshConfig configWithNulls = new SshConfig("NullConfig");
        configWithNulls.setHostname("null.example.com");
        configWithNulls.setUsername("nulluser");
        configWithNulls.setPrivateKeyPath(null);
        configWithNulls.setPassword(null);

        // When
        SshConnection connection = new SshConnection(configWithNulls);

        // Then
        assertEquals(configWithNulls, connection.getConfig());
        assertNotNull(connection.getPortForwardChannels());
        assertNull(connection.getConfig().getPrivateKeyPath());
        assertNull(connection.getConfig().getPassword());
    }

    @Test
    @DisplayName("Should validate port mappings structure")
    void shouldValidatePortMappingsStructure() {
        // Given
        PortMapping mapping1 = testConfig.getPortMappings().get(0);
        PortMapping mapping2 = testConfig.getPortMappings().get(1);

        // Then
        assertEquals(8080, mapping1.getLocalPort());
        assertEquals(80, mapping1.getRemotePort());
        assertEquals("start_web", mapping1.getStartCommand());
        assertEquals("stop_web", mapping1.getStopCommand());

        assertEquals(3306, mapping2.getLocalPort());
        assertEquals(3306, mapping2.getRemotePort());
        assertNull(mapping2.getStartCommand());
        assertNull(mapping2.getStopCommand());
    }

    @Test
    @DisplayName("Should maintain separate port forward channels list")
    void shouldMaintainSeparatePortForwardChannelsList() {
        // Given
        SshConnection connection1 = new SshConnection(testConfig);
        SshConnection connection2 = new SshConnection(testConfig);

        // When
        List<Channel> channels1 = connection1.getPortForwardChannels();
        List<Channel> channels2 = connection2.getPortForwardChannels();

        // Then
        assertNotSame(channels1, channels2);
        assertTrue(channels1.isEmpty());
        assertTrue(channels2.isEmpty());
    }

    @Test
    @DisplayName("Should preserve original config object reference")
    void shouldPreserveOriginalConfigObjectReference() {
        // Given
        SshConfig originalConfig = new SshConfig("OriginalConfig");
        originalConfig.setHostname("original.example.com");

        // When
        SshConnection connection = new SshConnection(originalConfig);

        // Then
        assertSame(originalConfig, connection.getConfig());

        // Verify changes to original config are reflected
        originalConfig.setHostname("modified.example.com");
        assertEquals("modified.example.com", connection.getConfig().getHostname());
    }

    @Test
    @DisplayName("Should handle empty port mappings list")
    void shouldHandleEmptyPortMappingsList() {
        // Given
        SshConfig configWithEmptyMappings = new SshConfig("EmptyMappingsConfig");
        configWithEmptyMappings.setHostname("empty.example.com");
        configWithEmptyMappings.setUsername("emptyuser");
        configWithEmptyMappings.setPortMappings(new ArrayList<>());

        // When
        SshConnection connection = new SshConnection(configWithEmptyMappings);

        // Then
        assertEquals(configWithEmptyMappings, connection.getConfig());
        assertTrue(connection.getConfig().getPortMappings().isEmpty());
        assertTrue(connection.getPortForwardChannels().isEmpty());
    }

    @Test
    @DisplayName("Should initialize with default connection state")
    void shouldInitializeWithDefaultConnectionState() {
        // Then
        assertFalse(sshConnection.isConnected());
        assertFalse(testConfig.isConnected());
        assertNull(sshConnection.getSession());
    }

    @Test
    @DisplayName("Should handle config with mixed authentication methods")
    void shouldHandleConfigWithMixedAuthenticationMethods() {
        // Given
        SshConfig mixedAuthConfig = new SshConfig("MixedAuth");
        mixedAuthConfig.setHostname("mixed.example.com");
        mixedAuthConfig.setUsername("mixeduser");
        mixedAuthConfig.setPassword("password123");
        mixedAuthConfig.setPrivateKeyPath("/path/to/key");

        // When
        SshConnection connection = new SshConnection(mixedAuthConfig);

        // Then
        assertEquals("password123", connection.getConfig().getPassword());
        assertEquals("/path/to/key", connection.getConfig().getPrivateKeyPath());
    }
}
