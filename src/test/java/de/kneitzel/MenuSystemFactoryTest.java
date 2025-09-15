package de.kneitzel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the MenuSystemFactory class.
 *
 * @author Konrad Neitzel
 * @version 1.1-SNAPSHOT
 */
class MenuSystemFactoryTest {

    @BeforeEach
    void setUp() {
        // Reset any system properties that might affect tests
    }

    @Test
    @DisplayName("Should create appropriate menu system for current platform")
    void shouldCreateAppropriateMenuSystemForCurrentPlatform() {
        // When
        MenuSystem menuSystem = MenuSystemFactory.createMenuSystem();

        // Then
        assertNotNull(menuSystem, "Menu system should be created for supported platforms");
        assertTrue(menuSystem.isSupported(), "Created menu system should be supported");
    }

    @Test
    @DisplayName("Should detect if menu system is supported")
    void shouldDetectIfMenuSystemIsSupported() {
        // When
        boolean isSupported = MenuSystemFactory.isMenuSystemSupported();

        // Then
        // This should be true on most platforms (Windows, Linux with X11, macOS)
        assertTrue(isSupported, "At least one menu system should be supported on most platforms");
    }

    @Test
    @DisplayName("Should return platform information")
    void shouldReturnPlatformInformation() {
        // When
        String platformInfo = MenuSystemFactory.getPlatformInfo();

        // Then
        assertNotNull(platformInfo);
        assertFalse(platformInfo.isEmpty());
        assertTrue(platformInfo.contains("Platform:"));
        assertTrue(platformInfo.contains("Java:"));
    }

    @Test
    @DisplayName("Should handle macOS detection correctly")
    void shouldHandleMacOSDetectionCorrectly() {
        // Given
        String osName = System.getProperty("os.name").toLowerCase();

        // When
        MenuSystem menuSystem = MenuSystemFactory.createMenuSystem();

        // Then
        if (osName.contains("mac")) {
            // On macOS, should prefer macOS implementation if available
            assertTrue(menuSystem instanceof MacOSMenuSystem || menuSystem instanceof SystemTrayMenuSystem);
        } else {
            // On other platforms, should use SystemTray if available
            if (menuSystem != null) {
                assertTrue(menuSystem instanceof SystemTrayMenuSystem);
            }
        }
    }

    @Test
    @DisplayName("Should provide detailed platform information")
    void shouldProvideDetailedPlatformInformation() {
        // When
        String platformInfo = MenuSystemFactory.getPlatformInfo();

        // Then
        String[] lines = platformInfo.split("\n");
        assertTrue(lines.length >= 3, "Should provide at least platform, Java, and menu system info");

        boolean hasSystemTrayInfo = false;
        boolean hasMacOSInfo = false;

        for (String line : lines) {
            if (line.contains("System Tray:")) {
                hasSystemTrayInfo = true;
            }
            if (line.contains("macOS Dock Menu:")) {
                hasMacOSInfo = true;
            }
        }

        assertTrue(hasSystemTrayInfo, "Should always include System Tray information");

        // macOS info should only be present on macOS systems
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("mac")) {
            assertTrue(hasMacOSInfo, "Should include macOS Dock Menu info on macOS");
        }
    }
}
