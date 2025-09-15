package de.kneitzel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for the PortMapping class.
 *
 * @author Konrad Neitzel
 * @version 1.1-SNAPSHOT
 */
class PortMappingTest {

    @Test
    @DisplayName("Should create PortMapping with all parameters")
    void shouldCreatePortMappingWithAllParameters() {
        // Given
        int localPort = 8080;
        int remotePort = 80;
        String startCommand = "start.sh";
        String stopCommand = "stop.sh";

        // When
        PortMapping mapping = new PortMapping(localPort, remotePort, startCommand, stopCommand);

        // Then
        assertEquals(localPort, mapping.getLocalPort());
        assertEquals(remotePort, mapping.getRemotePort());
        assertEquals(startCommand, mapping.getStartCommand());
        assertEquals(stopCommand, mapping.getStopCommand());
    }

    @Test
    @DisplayName("Should create copy of PortMapping")
    void shouldCreateCopyOfPortMapping() {
        // Given
        PortMapping original = new PortMapping(8080, 80, "start.sh", "stop.sh");

        // When
        PortMapping copy = new PortMapping(original);

        // Then
        assertEquals(original.getLocalPort(), copy.getLocalPort());
        assertEquals(original.getRemotePort(), copy.getRemotePort());
        assertEquals(original.getStartCommand(), copy.getStartCommand());
        assertEquals(original.getStopCommand(), copy.getStopCommand());
        assertNotSame(original, copy); // Different objects
    }

    @Test
    @DisplayName("Should return correct string representation")
    void shouldReturnCorrectStringRepresentation() {
        // Given
        PortMapping mapping = new PortMapping(8080, 80, "start.sh", "stop.sh");

        // When
        String result = mapping.toString();

        // Then
        assertEquals("8080:80", result);
    }

    @Test
    @DisplayName("Should handle null commands in copy constructor")
    void shouldHandleNullCommandsInCopyConstructor() {
        // Given
        PortMapping original = new PortMapping(8080, 80, null, null);

        // When
        PortMapping copy = new PortMapping(original);

        // Then
        assertEquals(8080, copy.getLocalPort());
        assertEquals(80, copy.getRemotePort());
        assertNull(copy.getStartCommand());
        assertNull(copy.getStopCommand());
    }

    @Test
    @DisplayName("Should set and get values correctly")
    void shouldSetAndGetValuesCorrectly() {
        // Given
        PortMapping mapping = new PortMapping(8080, 80, "start.sh", "stop.sh");

        // When
        mapping.setLocalPort(9090);
        mapping.setRemotePort(90);
        mapping.setStartCommand("new_start.sh");
        mapping.setStopCommand("new_stop.sh");

        // Then
        assertEquals(9090, mapping.getLocalPort());
        assertEquals(90, mapping.getRemotePort());
        assertEquals("new_start.sh", mapping.getStartCommand());
        assertEquals("new_stop.sh", mapping.getStopCommand());
    }
}
