package de.kneitzel;

import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Consumer;

/**
 * macOS Dock menu implementation of the MenuSystem interface.
 * Uses reflection to access macOS-specific APIs without requiring
 * compile-time dependencies on macOS libraries.
 *
 * @author Konrad Neitzel
 * @version 1.1-SNAPSHOT
 */
@Slf4j
public class MacOSMenuSystem implements MenuSystem {

    private Object macApplication;

    private Image connectedIcon;

    private Image unconnectedIcon;

    private JMenuBar dockMenuBar;

    @Override
    public boolean initialize(Image connectedIcon, Image unconnectedIcon) {
        this.connectedIcon = connectedIcon;
        this.unconnectedIcon = unconnectedIcon;

        try {
            // Use reflection to access com.apple.eawt.Application
            Class<?> applicationClass = Class.forName("com.apple.eawt.Application");
            Method getApplicationMethod = applicationClass.getMethod("getApplication");
            macApplication = getApplicationMethod.invoke(null);

            // Set dock icon
            Method setDockIconImageMethod = applicationClass.getMethod("setDockIconImage", Image.class);
            setDockIconImageMethod.invoke(macApplication, unconnectedIcon);

            // Enable dock menu
            dockMenuBar = new JMenuBar();

            log.info("macOS Dock menu initialized successfully");
            return true;

        } catch (Exception e) {
            log.error("Failed to initialize macOS Dock menu", e);
            return false;
        }
    }

    @Override
    public void updateMenu(SshConfigManager configManager,
                           Map<String, SshConnection> connections,
                           Consumer<String> connectionToggler,
                           Consumer<String> configEditor) {

        // Clear existing menu
        dockMenuBar.removeAll();

        // Create main menu
        JMenu mainMenu = new JMenu("SSH Auto Connect");

        // Add connection entries
        for (String configName : configManager.getConfigNames()) {
            SshConfig config = configManager.getConfig(configName);
            boolean isConnected = connections.containsKey(configName) &&
                    connections.get(configName).isConnected();

            JMenu configMenu = new JMenu(configName + (isConnected ? " ✓" : ""));

            // Connect/Disconnect item
            JMenuItem connectItem = new JMenuItem(isConnected ? "Disconnect" : "Connect");
            connectItem.addActionListener(e -> connectionToggler.accept(configName));
            configMenu.add(connectItem);

            if (isConnected) {
                SshConnection connection = connections.get(configName);
                addConnectionMenuItems(configMenu, connection);
            }

            configMenu.addSeparator();

            // Edit configuration item
            JMenuItem editItem = new JMenuItem("Edit Configuration");
            editItem.addActionListener(e -> configEditor.accept(configName));
            configMenu.add(editItem);

            mainMenu.add(configMenu);
        }

        mainMenu.addSeparator();

        // Add configuration item
        JMenuItem addConfigItem = new JMenuItem("Add New Configuration");
        addConfigItem.addActionListener(e -> configEditor.accept(null));
        mainMenu.add(addConfigItem);

        mainMenu.addSeparator();

        // Exit item
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        mainMenu.add(exitItem);

        dockMenuBar.add(mainMenu);

        // Update dock menu using reflection
        updateDockMenu();
    }

    /**
     * Adds connection-specific menu items when a connection is active.
     *
     * @param menu       The menu to add items to
     * @param connection The active SSH connection
     */
    private void addConnectionMenuItems(JMenu menu, SshConnection connection) {
        SshConfig config = connection.getConfig();

        // Terminal item
        JMenuItem terminalItem = new JMenuItem("Open Terminal");
        terminalItem.addActionListener(e -> openTerminal(connection));
        menu.add(terminalItem);

        // Upload item
        JMenuItem uploadItem = new JMenuItem("Upload File");
        uploadItem.addActionListener(e -> openUploadDialog(connection));
        menu.add(uploadItem);

        // Download item
        JMenuItem downloadItem = new JMenuItem("Download File");
        downloadItem.addActionListener(e -> openDownloadDialog(connection));
        menu.add(downloadItem);

        // Port forwarding items
        if (!config.getPortMappings().isEmpty()) {
            menu.addSeparator();
            JMenu portMenu = new JMenu("Port Forwardings");

            for (PortMapping mapping : config.getPortMappings()) {
                JMenuItem portItem = new JMenuItem(mapping.toString());
                portItem.setEnabled(false); // Just for display
                portMenu.add(portItem);
            }
            menu.add(portMenu);
        }
    }

    /**
     * Updates the macOS Dock menu using reflection.
     */
    private void updateDockMenu() {
        try {
            Class<?> applicationClass = macApplication.getClass();
            Method setDockMenuMethod = applicationClass.getMethod("setDockMenu", JMenu.class);

            if (dockMenuBar.getMenuCount() > 0) {
                JMenu mainMenu = dockMenuBar.getMenu(0);
                setDockMenuMethod.invoke(macApplication, mainMenu);
            }

        } catch (Exception e) {
            log.error("Failed to update macOS Dock menu", e);
        }
    }

    /**
     * Opens a terminal for the given SSH connection.
     * Uses macOS-specific terminal command.
     *
     * @param connection The SSH connection to open terminal for
     */
    private void openTerminal(SshConnection connection) {
        try {
            SshConfig config = connection.getConfig();

            // Use macOS Terminal.app
            String command = String.format(
                    "osascript -e 'tell application \"Terminal\" to do script \"ssh %s@%s -p %d\"'",
                    config.getUsername(),
                    config.getHostname(),
                    config.getPort()
            );

            Runtime.getRuntime().exec(command);
            log.info("Terminal opened for connection: {}", config.getName());

        } catch (Exception e) {
            log.error("Failed to open terminal", e);
        }
    }

    /**
     * Opens the upload dialog for the given SSH connection.
     *
     * @param connection The SSH connection for file upload
     */
    private void openUploadDialog(SshConnection connection) {
        SwingUtilities.invokeLater(() -> {
            UploadFrame uploadFrame = new UploadFrame(
                    () -> connection.getSession(),
                    connection.getConfig().getUploadDirectory()
            );
            uploadFrame.setVisible(true);
        });
    }

    /**
     * Opens the download dialog for the given SSH connection.
     *
     * @param connection The SSH connection for file download
     */
    private void openDownloadDialog(SshConnection connection) {
        SwingUtilities.invokeLater(() -> {
            DownloadFrame downloadFrame = new DownloadFrame(
                    () -> connection.getSession(),
                    connection.getConfig().getUploadDirectory()
            );
            downloadFrame.setVisible(true);
        });
    }

    @Override
    public void updateIcon(boolean hasActiveConnections) {
        if (macApplication != null) {
            try {
                Class<?> applicationClass = macApplication.getClass();
                Method setDockIconImageMethod = applicationClass.getMethod("setDockIconImage", Image.class);
                setDockIconImageMethod.invoke(macApplication,
                        hasActiveConnections ? connectedIcon : unconnectedIcon);

            } catch (Exception e) {
                log.error("Failed to update macOS Dock icon", e);
            }
        }
    }

    @Override
    public void showNotification(String title, String message, TrayIcon.MessageType messageType) {
        // macOS notifications can be shown using native notifications
        // For now, we'll use a simple dialog as fallback
        SwingUtilities.invokeLater(() -> {
            int messageTypeInt = switch (messageType) {
                case ERROR -> JOptionPane.ERROR_MESSAGE;
                case WARNING -> JOptionPane.WARNING_MESSAGE;
                default -> JOptionPane.INFORMATION_MESSAGE;
            };

            JOptionPane.showMessageDialog(null, message, title, messageTypeInt);
        });
    }

    @Override
    public boolean isSupported() {
        try {
            // Check if we're on macOS
            String osName = System.getProperty("os.name").toLowerCase();
            if (!osName.contains("mac")) {
                return false;
            }

            // Try to load the Application class
            Class.forName("com.apple.eawt.Application");
            return true;

        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void cleanup() {
        // macOS dock menu cleanup is handled automatically
        if (macApplication != null) {
            try {
                Class<?> applicationClass = macApplication.getClass();
                Method setDockMenuMethod = applicationClass.getMethod("setDockMenu", JMenu.class);
                setDockMenuMethod.invoke(macApplication, (JMenu) null);

            } catch (Exception e) {
                log.warn("Failed to cleanup macOS Dock menu", e);
            }
        }
    }
}
