package de.kneitzel;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;


/**
 * Starting point of the JavaApp
 */
@Slf4j
public class SSHAutoConnect {

    public final static int COUNT_ECHO_AFTER_TICKS = 5;

    private SystemTray systemTray;
    private TrayIcon trayIcon;
    private SshConfig sshConfig;
    @Getter private Session session = null;
    private CheckboxMenuItem connectItem;
    private Image connectedImage, unconnectedImage;
    private SshConfigFrame configFrame;
    private UploadFrame uploadFrame;
    private DownloadFrame downloadFrame;
    private Menu mappingMenu;
    private PopupMenu popupMenu = new PopupMenu();
    public static String APP_EXECUTABLE;
    private boolean shouldConnect = false;
    private int echoCounter = 0;

    public boolean isConnected() {
        return session != null && session.isConnected();
    }

    public static void main(String[] args) {
        Path appPath = getAppImagePath();
        APP_EXECUTABLE = appPath != null ? appPath.toString() : null;
        SSHAutoConnect connect = new SSHAutoConnect();
        connect.startUp();
    }

    public static Path getAppImagePath() {
        try {
            // Ermittelt den aktuellen Prozess und den Pfad zur ausführbaren Datei
            ProcessHandle currentProcess = ProcessHandle.current();
            Optional<String> cmd = currentProcess.info().command();
            if (cmd.isPresent()) {
                Path exePath = Paths.get(cmd.get());
                return exePath;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void startUp() {
        if (!SystemTray.isSupported()) {
            System.out.println("SystemTray wird nicht unterstützt");
            return;
        }

        try {
            sshConfig = new SshConfig();
            sshConfig.init();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Kann die Konfiguration nicht laden bzw. diese erstellen.", "Fehler", JOptionPane.ERROR_MESSAGE);
            return;
        }

        systemTray = SystemTray.getSystemTray();
        try {
            connectedImage = ImageIO.read(SSHAutoConnect.class.getResource("/connectedIcon.png")); // Icon laden
            unconnectedImage = ImageIO.read(SSHAutoConnect.class.getResource("/unconnectedIcon.png")); // Icon laden
        } catch (IOException e) {
            System.err.println("Icon konnte nicht geladen werden: " + e.getMessage());
            return;
        }

        createPopupMenuContent();

        trayIcon = new TrayIcon(unconnectedImage, "SSH AutoConnect", popupMenu);
        trayIcon.setImageAutoSize(true);

        trayIcon.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                    showConfig();
                }
            }
        });
        try {
            systemTray.add(trayIcon);
        } catch (AWTException e) {
            System.err.println("Fehler beim Hinzufügen des Tray-Icons: " + e.getMessage());
        }

        if (sshConfig.isAutoConnect()) {
            connect();
        }

        Timer checkTimer = new Timer();
        checkTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkConnecttionAlive();
            }
        }, 0L, 2000L);
    }

    private void createPopupMenuContent() {
        popupMenu.removeAll();

        MenuItem openConfigItem = new MenuItem("Open Config");
        openConfigItem.addActionListener(e -> showConfig());

        MenuItem openUploadItem = new MenuItem("Open Upload");
        openUploadItem.addActionListener(e -> showUpload());

        MenuItem openDownloadItem = new MenuItem("Open Download");
        openDownloadItem.addActionListener(e -> showDownload());

        connectItem = new CheckboxMenuItem("Connect");
        connectItem.addItemListener(e -> doConnect());

        MenuItem xtermItem = new MenuItem("xterm");
        xtermItem.addActionListener(e -> {
            executeCommand(sshConfig.getXtermCommand());
        });

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> {
            systemTray.remove(trayIcon);
            System.exit(0);
        });

        mappingMenu = new Menu("start/stop");

        popupMenu.add(openConfigItem);
        popupMenu.add(openUploadItem);
        popupMenu.add(openDownloadItem);
        popupMenu.add(connectItem);
        popupMenu.addSeparator();
        popupMenu.add(xtermItem);
        popupMenu.addSeparator();
        popupMenu.add(mappingMenu);
        popupMenu.addSeparator();
        popupMenu.add(exitItem);

        for (PortMapping mapping : sshConfig.getPortMappings()) {
            if (mappingMenu.getItemCount() > 0) {
                mappingMenu.addSeparator();
            }

            MenuItem mappingItem;
            if (!mapping.getStartCommand().isEmpty()) {
                mappingItem = new MenuItem("start " + mapping.toString());
                mappingItem.addActionListener(e -> executeCommand(mapping.getStartCommand()));
                mappingMenu.add(mappingItem);
            }

            if (!mapping.getStopCommand().isEmpty()) {
                mappingItem = new MenuItem("stop " + mapping.toString());
                mappingItem.addActionListener(e -> executeCommand(mapping.getStopCommand()));
                mappingMenu.add(mappingItem);
            }
        }
    }

    private void doConnect() {
        if (isConnected()) {
            shouldConnect = false;
            disconnect();
        } else {
            connect();
        }
    }

    private void checkConnecttionAlive() {
        if (!shouldConnect) {
            return;
        }

        if (!isConnected()) {
            shouldConnect = false;
            setIconStatus();
            trayIcon.displayMessage("SSH AutoConnect", "Verbindung wurde getrennt - reconnecting", TrayIcon.MessageType.WARNING);
            connect();
        } else {
            echoCounter++;
            if (echoCounter >= COUNT_ECHO_AFTER_TICKS) {
                echoCounter = 0;
                sendKeepAliveCommand();
            }
        }
    }

    private void showUpload() {
        if (session == null || !session.isConnected()) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            if (uploadFrame == null || !uploadFrame.isVisible()) {
                uploadFrame = new UploadFrame(this::getSession, sshConfig.getUploadDirectory());
                uploadFrame.setVisible(true);
            } else {
                uploadFrame.setExtendedState(Frame.NORMAL);
                uploadFrame.toFront();
                uploadFrame.requestFocus();
            }
        });
    }

    private void showDownload() {
        if (session == null || !session.isConnected()) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            if (downloadFrame == null || !downloadFrame.isVisible()) {
                downloadFrame = new DownloadFrame(this::getSession, sshConfig.getUploadDirectory());
                downloadFrame.setVisible(true);
            } else {
                downloadFrame.setExtendedState(Frame.NORMAL);
                downloadFrame.toFront();
                downloadFrame.requestFocus();
            }
        });
    }

    private void showConfig() {
        SwingUtilities.invokeLater(() -> {
            if (configFrame == null || !configFrame.isVisible()) {
                configFrame = new SshConfigFrame(sshConfig, this::doConnect, this::createPopupMenuContent, APP_EXECUTABLE, trayIcon);
                configFrame.setVisible(true);
            } else {
                configFrame.setExtendedState(Frame.NORMAL);
                configFrame.toFront();
                configFrame.requestFocus();
            }
        });
    }

    private synchronized void disconnect() {
        if (!isConnected()) {
            return;
        }

        session.disconnect();
        trayIcon.displayMessage("SSH AutoConnect", "Verbindung getrennt", TrayIcon.MessageType.INFO);
        setIconStatus();
    }

    public synchronized void connect() {
        if (isConnected()) {
            return;
        }

        log.atInfo().log("Connecting ...");
        String remoteHost = sshConfig.getHostName();
        String user = sshConfig.getUserName();
        String password = sshConfig.getPassword();
        int sshPort = 22;

        StringBuilder detailedMessage = new StringBuilder();

        try {
            session = new JSch().getSession(user, remoteHost, sshPort);
            session.setPassword(password);

            // HostKeyChecking deaktivieren, um Verbindungen zu unbekannten Hosts zu erlauben
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("ForwardX11", "yes");

            // ServerAliveInterval auf 10 Sekunden setzen
            session.setConfig("ServerAliveInterval", "10000");
            session.setConfig("ServerAliveCountMax", "3");
            session.connect();

            detailedMessage.append("OK   : SSH connected!\n");
            log.atInfo()
                    .addKeyValue("remote host", remoteHost)
                    .addKeyValue("ssh port", sshPort)
                    .log("Connected to remote host.");

            for(PortMapping mapping : sshConfig.getPortMappings()) {
                try {
                    int assignedPort = session.setPortForwardingL(mapping.getLocalPort(), "localhost", mapping.getRemotePort());
                    log.atInfo()
                            .addKeyValue("local port", mapping.getLocalPort())
                            .addKeyValue("remote port", mapping.getRemotePort())
                            .log("local port weitergeleitet auf remote port");
                    detailedMessage.append("OK   : Port " + mapping.getLocalPort() + " verbunden\n");
                } catch (JSchException e) {
                    log.atError()
                            .addKeyValue("local port", mapping.getLocalPort())
                            .addKeyValue("remote port", mapping.getRemotePort())
                            .log("local port kann nicht weitergeleitet werden auf remote port");
                    detailedMessage.append("WARN : Port " + mapping.getLocalPort() + " NICHT verbunden\n");
                }
            }

            trayIcon.displayMessage("SSH AutoConnect", detailedMessage.toString(), TrayIcon.MessageType.INFO);
            shouldConnect = true;
        } catch (Exception e) {
            log.atError()
                    .setCause(e)
                    .log("Problem while connecting to remote host");
        }

        setIconStatus();
    }

    private void setIconStatus() {
        connectItem.setState(isConnected());
        if (isConnected()) {
            trayIcon.setImage(connectedImage);
        } else {
            trayIcon.setImage(unconnectedImage);
        }
    }

    public void sendKeepAliveCommand() {
        try {
            log.atInfo().log("Sending keepalive command ...");
            ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand("echo");
            channelExec.connect();
            channelExec.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String executeCommand(String command) {
        if (!isConnected()) {
            log.atWarn()
                    .addKeyValue("command", command)
                    .log("Not connected so cannot execute command!");
            return null;
        }

        ChannelExec channelExec = null;
        StringBuilder outputBuffer = new StringBuilder();

        try {
            // Kanal für die Befehlsausführung öffnen
            channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand(command);

            InputStream inputStream = channelExec.getInputStream();
            channelExec.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                outputBuffer.append(line).append("\n");
            }

            // Überprüfen, ob der Befehl erfolgreich war
            int exitStatus = channelExec.getExitStatus();
            if (exitStatus != 0) {
                log.atWarn()
                        .addKeyValue("command", command)
                        .addKeyValue("output", outputBuffer.toString())
                        .addKeyValue("exitStatus", exitStatus)
                        .log("Command executed unsuccessfully!");
            } else {
                log.atInfo()
                        .addKeyValue("command", command)
                        .addKeyValue("output", outputBuffer.toString())
                        .log("Command executed successfully");
            }

        } catch (Exception e) {
            log.atError()
                    .setCause(e)
                    .addKeyValue("command", command)
                    .log("Problem while executing command");
        } finally {
            if (channelExec != null) {
                channelExec.disconnect();
            }
        }

        return outputBuffer.toString();
    }
}
