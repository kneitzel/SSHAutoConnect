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
 * GUI frame for downloading files via SFTP.
 * <p>
 * This class provides a graphical user interface for downloading
 * files over an existing SSH connection. The dialog includes
 * remote file path input, local target selection, and progress display.
 *
 * @author Konrad Neitzel
 * @version 1.1-SNAPSHOT
 * @since 1.0
 */
@Slf4j
public class DownloadFrame extends JFrame {

    /**
     * Supplier for the SSH session
     */
    private final Supplier<Session> sessionSupplier;

    /**
     * Default remote directory for downloads
     */
    private final String remoteDirectory;

    /**
     * Input field for the remote file
     */
    private JTextField remoteFileField;

    /**
     * Input field for the local target path
     */
    private JTextField localFileField;

    /**
     * Progress bar for download progress
     */
    private JProgressBar progressBar;

    /**
     * Log area for status messages
     */
    private JTextArea logArea;

    /**
     * Constructor for the download frame.
     *
     * @param sessionSupplier Supplier for the SSH session
     * @param remoteDirectory Default directory on the remote server
     */
    public DownloadFrame(Supplier<Session> sessionSupplier, String remoteDirectory) {
        this.sessionSupplier = sessionSupplier;
        this.remoteDirectory = remoteDirectory;

        initializeUI();
    }

    /**
     * Initializes the user interface of the download dialog.
     * <p>
     * Creates all UI components for remote file path input,
     * local target selection, progress display, and log output.
     */
    private void initializeUI() {
        setTitle("File Download");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        // Main panel
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Remote file path
        gbc.gridx = 0;
        gbc.gridy = 0;
        mainPanel.add(new JLabel("Remote File:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        remoteFileField = new JTextField(remoteDirectory, 30);
        mainPanel.add(remoteFileField, gbc);

        // Local file selection
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        mainPanel.add(new JLabel("Local Path:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        localFileField = new JTextField(30);
        mainPanel.add(localFileField, gbc);

        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JButton browseButton = new JButton("Browse");
        browseButton.addActionListener(e -> browseLocalPath());
        mainPanel.add(browseButton, gbc);

        // Progress bar
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
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
        JButton downloadButton = new JButton("Download");
        JButton closeButton = new JButton("Close");

        downloadButton.addActionListener(e -> downloadFile());
        closeButton.addActionListener(e -> dispose());

        buttonPanel.add(downloadButton);
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    /**
     * Opens a file dialog to select the local target directory.
     * <p>
     * Displays a file chooser for files and directories and
     * sets the selected path in the local path field.
     */
    private void browseLocalPath() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            localFileField.setText(selectedFile.getAbsolutePath());
        }
    }

    /**
     * Starts the download process for the specified remote file.
     * <p>
     * Validates the input, establishes an SFTP connection, and
     * starts the download in a background thread with progress monitoring.
     */
    private void downloadFile() {
        String remotePath = remoteFileField.getText().trim();
        String localPath = localFileField.getText().trim();

        if (remotePath.isEmpty() || localPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bitte geben Sie sowohl entfernten als auch lokalen Pfad an!",
                    "Fehler", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Download in background thread
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

                    publish("Starte Download: " + remotePath);

                    // Progress monitor
                    SftpProgressMonitor monitor = new SftpProgressMonitor() {
                        private long transferred = 0;

                        private long total = 0;

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
                            if (total > 0) {
                                int percent = (int) ((transferred * 100) / total);
                                SwingUtilities.invokeLater(() -> {
                                    progressBar.setValue(percent);
                                    progressBar.setString(percent + "%");
                                });
                            }
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

                    sftpChannel.get(remotePath, localPath, monitor, ChannelSftp.OVERWRITE);
                    sftpChannel.disconnect();

                    publish("Download erfolgreich abgeschlossen!");

                } catch (Exception e) {
                    publish("Fehler beim Download: " + e.getMessage());
                    log.error("Download failed", e);
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
