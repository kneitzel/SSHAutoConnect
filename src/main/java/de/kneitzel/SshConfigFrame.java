package de.kneitzel;

import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * GUI frame for configuring SSH connection parameters.
 *
 * This class provides a graphical user interface for editing
 * SSH configurations. The dialog includes fields for all
 * SSH parameters as well as a table for managing port mappings.
 *
 * @author Konrad Neitzel
 * @version 1.1-SNAPSHOT
 * @since 1.0
 */
@Slf4j
public class SshConfigFrame extends JFrame {

    /** The SSH configuration to be edited */
    private final SshConfig config;

    /** Callback function called when configuration changes */
    private final Consumer<SshConfig> onConfigUpdated;

    // UI Components
    /** Input field for the configuration name */
    private JTextField nameField;

    /** Input field for the hostname */
    private JTextField hostnameField;

    /** Input field for the SSH port */
    private JTextField portField;

    /** Input field for the username */
    private JTextField usernameField;

    /** Password input field */
    private JPasswordField passwordField;

    /** Input field for the path to the private key */
    private JTextField privateKeyField;

    /** Input field for the terminal command */
    private JTextField xtermField;

    /** Input field for the upload directory */
    private JTextField uploadDirField;

    /** Checkbox for auto-connect at startup */
    private JCheckBox autoConnectBox;

    // Port Mappings Table
    /** Table for port mappings */
    private JTable portMappingsTable;

    /** Table model for the port mappings table */
    private DefaultTableModel tableModel;

    /**
     * Constructor for the SSH configuration dialog.
     *
     * Creates a new configuration dialog for the specified SSH configuration.
     *
     * @param config The SSH configuration to edit
     * @param onConfigUpdated Callback function for configuration changes
     */
    public SshConfigFrame(SshConfig config, Consumer<SshConfig> onConfigUpdated) {
        this.config = config;
        this.onConfigUpdated = onConfigUpdated;

        initializeUI();
        loadConfigData();
    }

    /**
     * Initializes the user interface of the configuration dialog.
     *
     * Creates all UI components, layout managers and event handlers.
     * Builds the interface with configuration fields, port mappings table
     * and action buttons.
     */
    private void initializeUI() {
        setTitle("SSH Configuration - " + config.getName());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        // Main panel with scroll pane
        JPanel mainPanel = new JPanel(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(scrollPane, BorderLayout.CENTER);

        // Top panel for basic configuration
        JPanel configPanel = createConfigPanel();
        mainPanel.add(configPanel, BorderLayout.NORTH);

        // Center panel for port mappings
        JPanel portPanel = createPortMappingsPanel();
        mainPanel.add(portPanel, BorderLayout.CENTER);

        // Bottom panel for buttons
        JPanel buttonPanel = createButtonPanel();
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    /**
     * Creates the panel for basic SSH configuration.
     *
     * Builds a panel with all input fields for SSH parameters:
     * Name, hostname, port, username, password, private key, etc.
     *
     * @return JPanel with configuration fields
     */
    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("SSH Configuration"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Name
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        nameField = new JTextField(20);
        panel.add(nameField, gbc);

        // Hostname
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel("Hostname:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        hostnameField = new JTextField(20);
        panel.add(hostnameField, gbc);

        // Port
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel("Port:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        portField = new JTextField("22", 20);
        panel.add(portField, gbc);

        // Username
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        usernameField = new JTextField(20);
        panel.add(usernameField, gbc);

        // Password
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        passwordField = new JPasswordField(20);
        panel.add(passwordField, gbc);

        // Private Key Path
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel("Private Key:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        JPanel keyPanel = new JPanel(new BorderLayout());
        privateKeyField = new JTextField(20);
        JButton browseButton = new JButton("Browse");
        browseButton.addActionListener(e -> browsePrivateKey());
        keyPanel.add(privateKeyField, BorderLayout.CENTER);
        keyPanel.add(browseButton, BorderLayout.EAST);
        panel.add(keyPanel, gbc);

        // XTerm Command
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel("XTerm Command:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        xtermField = new JTextField("xterm", 20);
        panel.add(xtermField, gbc);

        // Upload Directory
        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel("Upload Directory:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        uploadDirField = new JTextField("/tmp/", 20);
        panel.add(uploadDirField, gbc);

        // Auto Connect
        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.gridwidth = 2;
        autoConnectBox = new JCheckBox("Auto Connect on startup");
        panel.add(autoConnectBox, gbc);

        return panel;
    }

    /**
     * Creates the panel for port mappings management.
     *
     * Builds a panel with a table for port mappings and
     * management buttons (Add, Edit, Remove).
     *
     * @return JPanel with port mappings table and buttons
     */
    private JPanel createPortMappingsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Port Mappings"));

        // Table for port mappings
        String[] columnNames = {"Local Port", "Remote Port", "Start Command", "Stop Command"};
        tableModel = new DefaultTableModel(columnNames, 0);
        portMappingsTable = new JTable(tableModel);
        portMappingsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane tableScrollPane = new JScrollPane(portMappingsTable);
        tableScrollPane.setPreferredSize(new Dimension(600, 200));
        panel.add(tableScrollPane, BorderLayout.CENTER);

        // Buttons for port mapping management
        JPanel mappingButtonPanel = new JPanel(new FlowLayout());
        JButton addMappingButton = new JButton("Add");
        JButton editMappingButton = new JButton("Edit");
        JButton removeMappingButton = new JButton("Remove");

        addMappingButton.addActionListener(e -> addPortMapping());
        editMappingButton.addActionListener(e -> editPortMapping());
        removeMappingButton.addActionListener(e -> removePortMapping());

        mappingButtonPanel.add(addMappingButton);
        mappingButtonPanel.add(editMappingButton);
        mappingButtonPanel.add(removeMappingButton);

        panel.add(mappingButtonPanel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Creates the panel with the main action buttons.
     *
     * Creates Save and Cancel buttons for the dialog.
     *
     * @return JPanel with action buttons
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout());

        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");

        saveButton.addActionListener(e -> saveConfiguration());
        cancelButton.addActionListener(e -> dispose());

        panel.add(saveButton);
        panel.add(cancelButton);

        return panel;
    }

    /**
     * Loads configuration data into the UI fields.
     *
     * Populates all input fields and the port mappings table
     * with values from the SSH configuration.
     */
    private void loadConfigData() {
        nameField.setText(config.getName());
        hostnameField.setText(config.getHostname());
        portField.setText(String.valueOf(config.getPort()));
        usernameField.setText(config.getUsername());
        passwordField.setText(config.getPassword());
        privateKeyField.setText(config.getPrivateKeyPath());
        xtermField.setText(config.getXtermCommand());
        uploadDirField.setText(config.getUploadDirectory());
        autoConnectBox.setSelected(config.isAutoConnect());

        // Load port mappings
        tableModel.setRowCount(0);
        for (PortMapping mapping : config.getPortMappings()) {
            tableModel.addRow(new Object[]{
                    mapping.getLocalPort(),
                    mapping.getRemotePort(),
                    mapping.getStartCommand(),
                    mapping.getStopCommand()
            });
        }
    }

    /**
     * Saves the configuration from the UI fields.
     *
     * Validates inputs, transfers values to the SSH configuration
     * and calls the onConfigUpdated callback.
     */
    private void saveConfiguration() {
        try {
            config.setName(nameField.getText().trim());
            config.setHostname(hostnameField.getText().trim());
            config.setPort(Integer.parseInt(portField.getText().trim()));
            config.setUsername(usernameField.getText().trim());
            config.setPassword(new String(passwordField.getPassword()));
            config.setPrivateKeyPath(privateKeyField.getText().trim());
            config.setXtermCommand(xtermField.getText().trim());
            config.setUploadDirectory(uploadDirField.getText().trim());
            config.setAutoConnect(autoConnectBox.isSelected());

            // Save port mappings
            List<PortMapping> mappings = new ArrayList<>();
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                int localPort = (Integer) tableModel.getValueAt(i, 0);
                int remotePort = (Integer) tableModel.getValueAt(i, 1);
                String startCommand = (String) tableModel.getValueAt(i, 2);
                String stopCommand = (String) tableModel.getValueAt(i, 3);

                mappings.add(new PortMapping(localPort, remotePort,
                        startCommand != null ? startCommand : "",
                        stopCommand != null ? stopCommand : ""));
            }
            config.setPortMappings(mappings);

            onConfigUpdated.accept(config);
            dispose();

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Ungültige Portnummer!", "Fehler", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Fehler beim Speichern: " + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Opens a file dialog for selecting a private SSH key.
     *
     * Shows a FileChooser dialog and sets the selected
     * file path in the private key field.
     */
    private void browsePrivateKey() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setDialogTitle("Private Key auswählen");

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            privateKeyField.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }
    }

    /**
     * Adds a new port mapping to the table.
     *
     * Opens a dialog for entering a new port mapping
     * and adds it to the table upon successful input.
     */
    private void addPortMapping() {
        PortMappingDialog dialog = new PortMappingDialog(this, null);
        dialog.setVisible(true);

        PortMapping mapping = dialog.getPortMapping();
        if (mapping != null) {
            tableModel.addRow(new Object[]{
                    mapping.getLocalPort(),
                    mapping.getRemotePort(),
                    mapping.getStartCommand(),
                    mapping.getStopCommand()
            });
        }
    }

    /**
     * Edits the selected port mapping.
     *
     * Opens a dialog for editing the port mapping
     * selected in the table.
     */
    private void editPortMapping() {
        int selectedRow = portMappingsTable.getSelectedRow();
        if (selectedRow >= 0) {
            PortMapping currentMapping = new PortMapping(
                    (Integer) tableModel.getValueAt(selectedRow, 0),
                    (Integer) tableModel.getValueAt(selectedRow, 1),
                    (String) tableModel.getValueAt(selectedRow, 2),
                    (String) tableModel.getValueAt(selectedRow, 3)
            );

            PortMappingDialog dialog = new PortMappingDialog(this, currentMapping);
            dialog.setVisible(true);

            PortMapping mapping = dialog.getPortMapping();
            if (mapping != null) {
                tableModel.setValueAt(mapping.getLocalPort(), selectedRow, 0);
                tableModel.setValueAt(mapping.getRemotePort(), selectedRow, 1);
                tableModel.setValueAt(mapping.getStartCommand(), selectedRow, 2);
                tableModel.setValueAt(mapping.getStopCommand(), selectedRow, 3);
            }
        } else {
            JOptionPane.showMessageDialog(this, "Bitte wählen Sie eine Port-Weiterleitung aus!", "Hinweis", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Removes the selected port mapping from the table.
     *
     * Deletes the selected row in the table or shows a
     * message if no row is selected.
     */
    private void removePortMapping() {
        int selectedRow = portMappingsTable.getSelectedRow();
        if (selectedRow >= 0) {
            tableModel.removeRow(selectedRow);
        } else {
            JOptionPane.showMessageDialog(this, "Bitte wählen Sie eine Port-Weiterleitung aus!", "Hinweis", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Inner class for the port mapping editing dialog.
     *
     * Modal dialog for entering or editing port mapping parameters.
     */
    private static class PortMappingDialog extends JDialog {
        /** Input field for the local port */
        private JTextField localPortField;
        /** Input field for the remote port */
        private JTextField remotePortField;
        /** Input field for the start command */
        private JTextField startCommandField;
        /** Input field for the stop command */
        private JTextField stopCommandField;
        /** Result of the dialog */
        private PortMapping result;

        /**
         * Constructor for the port mapping dialog.
         *
         * @param parent Parent frame for the modal dialog
         * @param mapping Port mapping to edit or null for new mapping
         */
        public PortMappingDialog(Frame parent, PortMapping mapping) {
            super(parent, "Port Mapping", true);
            initializeUI();

            if (mapping != null) {
                localPortField.setText(String.valueOf(mapping.getLocalPort()));
                remotePortField.setText(String.valueOf(mapping.getRemotePort()));
                startCommandField.setText(mapping.getStartCommand());
                stopCommandField.setText(mapping.getStopCommand());
            }

            pack();
            setLocationRelativeTo(parent);
        }

        /**
         * Initializes the UI of the port mapping dialog.
         *
         * Creates all input fields and buttons for the dialog.
         */
        private void initializeUI() {
            setLayout(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.anchor = GridBagConstraints.WEST;

            // Local Port
            gbc.gridx = 0;
            gbc.gridy = 0;
            add(new JLabel("Local Port:"), gbc);
            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            localPortField = new JTextField(10);
            add(localPortField, gbc);

            // Remote Port
            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.fill = GridBagConstraints.NONE;
            gbc.weightx = 0;
            add(new JLabel("Remote Port:"), gbc);
            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            remotePortField = new JTextField(10);
            add(remotePortField, gbc);

            // Start Command
            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.fill = GridBagConstraints.NONE;
            gbc.weightx = 0;
            add(new JLabel("Start Command:"), gbc);
            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            startCommandField = new JTextField(20);
            add(startCommandField, gbc);

            // Stop Command
            gbc.gridx = 0;
            gbc.gridy = 3;
            gbc.fill = GridBagConstraints.NONE;
            gbc.weightx = 0;
            add(new JLabel("Stop Command:"), gbc);
            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            stopCommandField = new JTextField(20);
            add(stopCommandField, gbc);

            // Buttons
            JPanel buttonPanel = new JPanel(new FlowLayout());
            JButton okButton = new JButton("OK");
            JButton cancelButton = new JButton("Cancel");

            okButton.addActionListener(e -> {
                try {
                    int localPort = Integer.parseInt(localPortField.getText().trim());
                    int remotePort = Integer.parseInt(remotePortField.getText().trim());
                    String startCommand = startCommandField.getText().trim();
                    String stopCommand = stopCommandField.getText().trim();

                    result = new PortMapping(localPort, remotePort, startCommand, stopCommand);
                    dispose();
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Ungültige Portnummer!", "Fehler", JOptionPane.ERROR_MESSAGE);
                }
            });

            cancelButton.addActionListener(e -> dispose());

            buttonPanel.add(okButton);
            buttonPanel.add(cancelButton);

            gbc.gridx = 0;
            gbc.gridy = 4;
            gbc.gridwidth = 2;
            add(buttonPanel, gbc);
        }

        /**
         * Returns the created or edited port mapping.
         *
         * @return Port mapping or null if dialog was canceled
         */
        public PortMapping getPortMapping() {
            return result;
        }
    }
}
