package de.kneitzel;

import com.jcraft.jsch.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Vector;
import java.util.function.Supplier;

public class DownloadFrame extends JFrame {

    private static final String title = "Remote File Browser";
    private Supplier<Session> sessionSupplier;
    private String currentDirectory;
    private JList<String> fileList;
    private DefaultListModel<String> listModel;

    public DownloadFrame(Supplier<Session> sessionSupplier, String initialDirectory) {
        this.sessionSupplier = sessionSupplier;
        this.currentDirectory = initialDirectory;
        this.listModel = new DefaultListModel<>();

        setTitle(title);
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        fileList = new JList<>(listModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        loadDirectoryContents(currentDirectory);

        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = fileList.locationToIndex(e.getPoint());
                    String selectedItem = listModel.getElementAt(index);
                    handleDoubleClick(selectedItem);
                }
            }
        });

        add(new JScrollPane(fileList), BorderLayout.CENTER);
        setVisible(true);
    }

    private void loadDirectoryContents(String directory) {
        listModel.clear();

        try {
            ChannelSftp sftpChannel = (ChannelSftp) sessionSupplier.get().openChannel("sftp");
            sftpChannel.connect();

            Vector<ChannelSftp.LsEntry> files = sftpChannel.ls(directory);

            for (ChannelSftp.LsEntry entry : files) {
                listModel.addElement(entry.getFilename());
            }

            sftpChannel.disconnect();

            setTitle(title + ": " + directory);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleDoubleClick(String selectedItem) {
        if (selectedItem.equals("..")) {
            currentDirectory = currentDirectory.substring(0, currentDirectory.lastIndexOf('/'));
            if (currentDirectory.isEmpty()) {
                currentDirectory = "/";
            }
        } else {
            try {
                ChannelSftp sftpChannel = (ChannelSftp) sessionSupplier.get().openChannel("sftp");
                sftpChannel.connect();

                String path = currentDirectory + "/" + selectedItem;
                SftpATTRS attrs = sftpChannel.stat(path);

                if (attrs.isDir()) {
                    // Wechsle in das Verzeichnis
                    currentDirectory = path;
                } else {
                    // Lade die Datei herunter
                    downloadFile(sftpChannel, path);
                }

                sftpChannel.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        loadDirectoryContents(currentDirectory);
    }

    private void downloadFile(ChannelSftp sftpChannel, String remoteFilePath) {
        try (InputStream inputStream = sftpChannel.get(remoteFilePath);
             FileOutputStream outputStream = new FileOutputStream(remoteFilePath.substring(remoteFilePath.lastIndexOf('/') + 1))) {

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            JOptionPane.showMessageDialog(this, "Datei " + remoteFilePath + " erfolgreich heruntergeladen.");
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Fehler beim Herunterladen der Datei: " + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }
}
