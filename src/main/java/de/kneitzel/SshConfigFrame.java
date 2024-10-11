package de.kneitzel;

import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

@Slf4j
public class SshConfigFrame extends JFrame {

    private static final String APP_NAME = "SSHAutoConnect";

    private JTextField hostNameField;
    private JTextField userNameField;
    private JPasswordField passwordField;
    private JTextField xtermCommandField;
    private JTextField uploadDirectoryField;
    private DefaultListModel<PortMapping> portListModel;
    private JList<PortMapping> portList;
    private SshConfig sshConfig;
    private JCheckBox autoConnectCheckBox;
    private String appExecutable;
    private Runnable recreateMenuAction;
    private TrayIcon trayIcon;

    public SshConfigFrame(SshConfig sshConfig, Runnable connectAction, Runnable recreateMenuAction,  String appExecutable, TrayIcon trayIcon) {
        this.sshConfig = sshConfig;
        this.appExecutable = appExecutable;
        this.recreateMenuAction = recreateMenuAction;
        this.trayIcon = trayIcon;

        setTitle("SSH Config Manager");
        setSize(500, 350);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        // UI Elemente
        JPanel formPanel = new JPanel(new GridLayout(6, 2));
        hostNameField = new JTextField();
        userNameField = new JTextField();
        passwordField = new JPasswordField();
        xtermCommandField = new JTextField();
        autoConnectCheckBox = new JCheckBox();
        uploadDirectoryField = new JTextField();

        formPanel.add(new JLabel("Hostname:"));
        formPanel.add(hostNameField);
        formPanel.add(new JLabel("Username:"));
        formPanel.add(userNameField);
        formPanel.add(new JLabel("Password:"));
        formPanel.add(passwordField);
        formPanel.add(new JLabel("XtermCommand:"));
        formPanel.add(xtermCommandField);
        formPanel.add(new JLabel("Upload Directory:"));
        formPanel.add(uploadDirectoryField);
        formPanel.add(new JLabel("Auto Connect:"));
        formPanel.add(autoConnectCheckBox);

        // Port-Mapping Liste
        portListModel = new DefaultListModel<>();
        portList = new JList<>(portListModel);
        portList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        portList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) { // Doppelklick
                    //int index = portList.locationToIndex(evt.getPoint());
                    editPortMapping();
                }
            }
        });

        JScrollPane listScrollPane = new JScrollPane(portList);

        // Buttons für Port-Mapping Verwaltung
        JPanel buttonPanel = new JPanel(new GridLayout(2, 1));
        JButton addButton = new JButton("+");
        JButton removeButton = new JButton("-");
        JButton editButton = new JButton("Edit");
        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(editButton);

        // Save und Connect Buttons
        JPanel actionPanel = new JPanel();
        JButton saveButton = new JButton("Save");
        JButton connectButton = new JButton("Connect");
        if (appExecutable != null) {
            JButton autoStartButton = new JButton("Switch Auto Start");
            actionPanel.add(autoStartButton);
            autoStartButton.addActionListener(this::switchAutoStart);
        }

        actionPanel.add(saveButton);
        actionPanel.add(connectButton);

        add(formPanel, BorderLayout.NORTH);
        add(listScrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.EAST);
        add(actionPanel, BorderLayout.SOUTH);

        // Laden der Konfigurationsdaten
        loadConfig();

        addButton.addActionListener(e -> addPortMapping());
        removeButton.addActionListener(e -> removeSelectedPortMapping());
        editButton.addActionListener(e -> editPortMapping());
        saveButton.addActionListener(e -> saveConfig());
        connectButton.addActionListener(e -> connectAction.run());
    }

    private void switchAutoStart(ActionEvent actionEvent) {
        boolean isAutoStart = checkAutoStartKey();
        if (isAutoStart) {
            removeAutoStart();
        } else {
            addAutoStart();
        }
    }

    private void addAutoStart() {
        String command = "reg add HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run /v " + APP_NAME + " /t REG_SZ /d \"" + appExecutable + "\" /f";
        log.atInfo()
                .addKeyValue("command", command)
                .log("Adding Autostart Key");
        try {
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor();
            trayIcon.displayMessage("SSH AutoConnect", "Autostart Key hinzugefügt.", TrayIcon.MessageType.INFO);
        } catch (InterruptedException | IOException e) {
            log.atInfo()
                    .addKeyValue("name", APP_NAME)
                    .addKeyValue("executable", appExecutable)
                    .addKeyValue("command", command)
                    .setCause(e)
                    .log("Unable to add Autostart Key");
        }
    }

    private void removeAutoStart() {
        log.info("Removing Autostart Key");
        String command = "reg delete HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run /v " + APP_NAME + " /f";
        try {
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor();

            if (process.exitValue() == 0) {
                log.atInfo()
                        .addKeyValue("name", APP_NAME)
                        .addKeyValue("executable", appExecutable)
                        .addKeyValue("command", command)
                        .log("Removed Autostart Key");
                trayIcon.displayMessage("SSH AutoConnect", "Autostart Key entfernt.", TrayIcon.MessageType.INFO);
            } else {
                log.atInfo()
                        .addKeyValue("name", APP_NAME)
                        .addKeyValue("executable", appExecutable)
                        .addKeyValue("command", command)
                        .addKeyValue("exitValue", process.exitValue())
                        .log("Unable to remove Autostart Key");
            }
        } catch (IOException | InterruptedException e) {
            log.atInfo()
                    .addKeyValue("name", APP_NAME)
                    .addKeyValue("executable", appExecutable)
                    .addKeyValue("command", command)
                    .setCause(e)
                    .log("Unable to remove Autostart Key");
        }
    }

    private boolean checkAutoStartKey() {

        String command = "reg query HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run /v " + APP_NAME;
        try {
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(APP_NAME)) {
                    return true;
                }
            }
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void loadConfig() {
        hostNameField.setText(sshConfig.getHostName());
        userNameField.setText(sshConfig.getUserName());
        xtermCommandField.setText(sshConfig.getXtermCommand());
        uploadDirectoryField.setText(sshConfig.getUploadDirectory());
        autoConnectCheckBox.setSelected(sshConfig.isAutoConnect());
        // Passwort wird nicht angezeigt, Feld bleibt leer
        for (PortMapping mapping : sshConfig.getPortMappings()) {
            portListModel.addElement(mapping);
        }
    }

    private void saveConfig() {
        sshConfig.setHostName(hostNameField.getText());
        sshConfig.setUserName(userNameField.getText());
        sshConfig.setXtermCommand(xtermCommandField.getText());
        sshConfig.setAutoConnect(autoConnectCheckBox.isSelected());
        sshConfig.setUploadDirectory(uploadDirectoryField.getText());
        if (passwordField.getPassword().length > 0) {
            sshConfig.setPassword(new String(passwordField.getPassword()));
        }
        sshConfig.getPortMappings().clear();
        for (int i = 0; i < portListModel.size(); i++) {
            sshConfig.getPortMappings().add(portListModel.getElementAt(i));
        }

        try {
            sshConfig.saveToFile();
            recreateMenuAction.run();
            JOptionPane.showMessageDialog(this, "Konfiguration erfolgreich gespeichert.", "Information", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Fehler beim Speichern der Konfiguration: " + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void editPortMapping() {
        int selectedIndex = portList.getSelectedIndex();
        if (selectedIndex != -1) {
            PortMapping mapping = portListModel.getElementAt(selectedIndex);
            showPortMapping(mapping, "Edit Port Mapping");
        } else {
            JOptionPane.showMessageDialog(this, "Bitte wählen Sie ein Port-Mapping aus, das editiert werden soll.", "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addPortMapping() {
        PortMapping mapping = new PortMapping(5900, 5900, "startCommand", "stopCommand");
        showPortMapping(mapping, "Add Port Mapping");
        portListModel.addElement(mapping);
    }

    private void showPortMapping(PortMapping mapping, String title) {
        JTextField localPortField = new JTextField();
        JTextField remotePortField = new JTextField();
        JTextField startCommandField = new JTextField();
        JTextField stopCommandField = new JTextField();
        JPanel panel = new JPanel(new GridLayout(4, 2));
        panel.add(new JLabel("Local Port:"));
        panel.add(localPortField);
        panel.add(new JLabel("Remote Port:"));
        panel.add(remotePortField);
        panel.add(new JLabel("Start Command:"));
        panel.add(startCommandField);
        panel.add(new JLabel("Stop Command:"));
        panel.add(stopCommandField);

        localPortField.setText(String.valueOf(mapping.getLocalPort()));
        remotePortField.setText(String.valueOf(mapping.getRemotePort()));
        startCommandField.setText(mapping.getStartCommand());
        stopCommandField.setText(mapping.getStopCommand());

        int result = JOptionPane.showConfirmDialog(this, panel, title, JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            try {
                mapping.setLocalPort(Integer.parseInt(localPortField.getText().trim()));
                mapping.setRemotePort(Integer.parseInt(remotePortField.getText().trim()));
                mapping.setStartCommand(startCommandField.getText().trim());
                mapping.setStopCommand(stopCommandField.getText().trim());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Bitte gültige Portnummern eingeben.", "Fehler", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void removeSelectedPortMapping() {
        int selectedIndex = portList.getSelectedIndex();
        if (selectedIndex != -1) {
            portListModel.remove(selectedIndex);
        } else {
            JOptionPane.showMessageDialog(this, "Bitte wählen Sie ein Port-Mapping aus, das entfernt werden soll.", "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }
}
