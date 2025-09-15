package de.kneitzel;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpProgressMonitor;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.function.Supplier;

/**
 * GUI frame for uploading files via SFTP.
 * <p>
 * This class provides a graphical user interface for uploading
 * files over an existing SSH connection. The dialog includes
 * file selection, target directory configuration, and progress display.
 *
 * @author Konrad Neitzel
 * @version 1.1-SNAPSHOT
 * @since 1.0
 */
@Slf4j
public class UploadFrame extends JFrame {

    /**
     * Supplier for the SSH session
     */
    private final Supplier<Session> sessionSupplier;

    /**
     * Default remote directory for uploads
     */
    private final String remoteDirectory;

    /**
     * Input field for the local file
     */
    private JTextField localFileField;

    /**
     * Input field for the remote path
     */
    private JTextField remoteFileField;

    /**
     * Progress bar for upload progress
     */
    private JProgressBar progressBar;

    /**
     * Log area for status messages
     */
    private JTextArea logArea;

    /**
     * Constructor for the upload frame.
     *
     * @param sessionSupplier Supplier for the SSH session
     * @param remoteDirectory Default directory on the remote server
     */
    public UploadFrame(Supplier<Session> sessionSupplier, String remoteDirectory) {
        this.sessionSupplier = sessionSupplier;
        this.remoteDirectory = remoteDirectory;

        initializeUI();
    }

    /**
     * Initializes the user interface of the upload dialog.
     * <p>
     * Creates all UI components for file selection, target configuration,
     * progress display, and log output.
     */
    private void initializeUI() {
        setTitle("File Upload");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        // Main panel
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Local file selection
        gbc.gridx = 0;
        gbc.gridy = 0;
        mainPanel.add(new JLabel("Local File:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        localFileField = new JTextField(30);
        mainPanel.add(localFileField, gbc);

        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JButton browseButton = new JButton("Browse");
        browseButton.addActionListener(e -> browseLocalFile());
        mainPanel.add(browseButton, gbc);

        // Remote file path
        gbc.gridx = 0;
        gbc.gridy = 1;
        mainPanel.add(new JLabel("Remote Path:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        remoteFileField = new JTextField(remoteDirectory, 30);
        mainPanel.add(remoteFileField, gbc);

        // Progress bar
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        mainPanel.add(progressBar, gbc);

        add(mainPanel, BorderLayout.NORTH);

        // Log area
        logArea = new JTextArea(10, 50);
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        add(scrollPane, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton uploadButton = new JButton("Upload");
        JButton closeButton = new JButton("Close");

        uploadButton.addActionListener(e -> uploadFile());
        closeButton.addActionListener(e -> dispose());

        buttonPanel.add(uploadButton);
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    /**
     * Opens a file dialog to select the file to be uploaded.
     * <p>
     * Displays a file chooser and sets the selected file path
     * in the local file field. Also automatically updates the
     * remote path if it is just a directory.
     */
    private void browseLocalFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            localFileField.setText(selectedFile.getAbsolutePath());

            // Set remote filename to local filename if remote path is just directory
            String remotePath = remoteFileField.getText();
            if (remotePath.endsWith("/")) {
                remoteFileField.setText(remotePath + selectedFile.getName());
            }
        }
    }

    /**
     * Starts the upload process for the selected file.
     * <p>
     * Validates the input, establishes an SFTP connection, and
     * starts the upload in a background thread with progress monitoring.
     */
    private void uploadFile() {
        String localPath = localFileField.getText().trim();
        String remotePath = remoteFileField.getText().trim();

        if (localPath.isEmpty() || remotePath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bitte geben Sie sowohl lokalen als auch entfernten Pfad an!",
                    "Fehler", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File localFile = new File(localPath);
        if (!localFile.exists() || !localFile.isFile()) {
            JOptionPane.showMessageDialog(this, "Lokale Datei existiert nicht!",
                    "Fehler", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Upload in background thread
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                Session session = sessionSupplier.get();
                if (session == null || !session.isConnected()) {
                    publish("Fehler: Keine SSH-Verbindung verfügbar!");
                    return null;
                }

                try {
                    ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
                    sftpChannel.connect();

                    publish("Starte Upload: " + localFile.getName());

                    // Progress monitor
                    SftpProgressMonitor monitor = new SftpProgressMonitor() {
                        private long transferred = 0;

                        private long total = localFile.length();

                        @Override
                        public void init(int op, String src, String dest, long max) {
                            total = max;
                            SwingUtilities.invokeLater(() -> {
                                progressBar.setValue(0);
                                progressBar.setString("0%");
                            });
                        }

                        @Override
                        public boolean count(long count) {
                            transferred += count;
                            int percent = (int) ((transferred * 100) / total);
                            SwingUtilities.invokeLater(() -> {
                                progressBar.setValue(percent);
                                progressBar.setString(percent + "%");
                            });
                            return true;
                        }

                        @Override
                        public void end() {
                            SwingUtilities.invokeLater(() -> {
                                progressBar.setValue(100);
                                progressBar.setString("100%");
                            });
                        }
                    };

                    sftpChannel.put(localPath, remotePath, monitor, ChannelSftp.OVERWRITE);
                    sftpChannel.disconnect();

                    publish("Upload erfolgreich abgeschlossen!");

                } catch (Exception e) {
                    publish("Fehler beim Upload: " + e.getMessage());
                    log.error("Upload failed", e);
                }

                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String message : chunks) {
                    logArea.append(message + "\n");
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                }
            }
        };

        worker.execute();
    }
}
