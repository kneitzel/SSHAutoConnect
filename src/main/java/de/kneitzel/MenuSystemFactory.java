package de.kneitzel;

import lombok.extern.slf4j.Slf4j;

/**
 * Factory class for creating the appropriate MenuSystem implementation
 * based on the current operating system platform.
 *
 * @author Konrad Neitzel
 * @version 1.1-SNAPSHOT
 */
@Slf4j
public class MenuSystemFactory {

    /**
     * Creates and returns the appropriate MenuSystem implementation
     * for the current platform.
     *
     * @return MenuSystem instance suitable for the current platform,
     * or null if no suitable implementation is available
     */
    public static MenuSystem createMenuSystem() {
        String osName = System.getProperty("os.name").toLowerCase();

        // Try macOS implementation first if on macOS
        if (osName.contains("mac")) {
            MacOSMenuSystem macOSSystem = new MacOSMenuSystem();
            if (macOSSystem.isSupported()) {
                log.info("Using macOS Dock menu system");
                return macOSSystem;
            }
            log.warn("macOS detected but Dock menu not supported, falling back to System Tray");
        }

        // Try System Tray implementation for Windows/Linux or as fallback
        SystemTrayMenuSystem traySystem = new SystemTrayMenuSystem();
        if (traySystem.isSupported()) {
            log.info("Using System Tray menu system");
            return traySystem;
        }

        log.error("No supported menu system found for platform: {}", osName);
        return null;
    }

    /**
     * Checks if any menu system is supported on the current platform.
     *
     * @return true if at least one menu system is supported
     */
    public static boolean isMenuSystemSupported() {
        String osName = System.getProperty("os.name").toLowerCase();

        // Check macOS support
        if (osName.contains("mac")) {
            MacOSMenuSystem macOSSystem = new MacOSMenuSystem();
            if (macOSSystem.isSupported()) {
                return true;
            }
        }

        // Check System Tray support
        SystemTrayMenuSystem traySystem = new SystemTrayMenuSystem();
        return traySystem.isSupported();
    }

    /**
     * Returns information about the current platform and supported menu systems.
     *
     * @return Platform information string
     */
    public static String getPlatformInfo() {
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String javaVersion = System.getProperty("java.version");

        StringBuilder info = new StringBuilder();
        info.append("Platform: ").append(osName).append(" ").append(osVersion).append("\n");
        info.append("Java: ").append(javaVersion).append("\n");

        if (osName.toLowerCase().contains("mac")) {
            MacOSMenuSystem macOSSystem = new MacOSMenuSystem();
            info.append("macOS Dock Menu: ").append(macOSSystem.isSupported() ? "Supported" : "Not supported").append("\n");
        }

        SystemTrayMenuSystem traySystem = new SystemTrayMenuSystem();
        info.append("System Tray: ").append(traySystem.isSupported() ? "Supported" : "Not supported").append("\n");

        return info.toString();
    }
}
