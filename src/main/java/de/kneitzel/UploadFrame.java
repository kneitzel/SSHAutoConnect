package de.kneitzel;
import com.jcraft.jsch.*;
import javax.swing.*;
import java.awt.*;
import java.awt.dnd.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.function.Supplier;

public class UploadFrame extends JFrame {

    private JLabel label;
    private JButton button;
    private Supplier<Session> sessionSupplier;
    private String remoteDirectory;

    public UploadFrame(Supplier<Session> session, String remoteDirectory) {
        this.sessionSupplier = session;
        this.remoteDirectory = remoteDirectory;

        setTitle("Datei-Upload");
        setSize(400, 200);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout());

        label = new JLabel("Ziehen Sie eine Datei hierher oder wählen Sie eine Datei", SwingConstants.CENTER);
        add(label, BorderLayout.CENTER);

        button = new JButton("Datei auswählen");
        add(button, BorderLayout.SOUTH);

        // Datei-Upload bei Auswahl über den Button
        button.addActionListener(e -> selectAndUploadFile());

        // Drag & Drop Funktionalität hinzufügen
        new DropTarget(label, new DropTargetListener() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {}

            @Override
            public void dragOver(DropTargetDragEvent dtde) {}

            @Override
            public void dropActionChanged(DropTargetDragEvent dtde) {}

            @Override
            public void dragExit(DropTargetEvent dte) {}

            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    Transferable transferable = dtde.getTransferable();

                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        List<File> droppedFiles = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                        if (!droppedFiles.isEmpty()) {
                            File file = droppedFiles.get(0);
                            label.setText("Hochladen: " + file.getName());
                            uploadFile(file);
                        }
                    }

                    dtde.dropComplete(true);

                } catch (Exception ex) {
                    ex.printStackTrace();
                    dtde.dropComplete(false);
                }
            }
        });
    }

    private void selectAndUploadFile() {
        JFileChooser fileChooser = new JFileChooser();
        int returnValue = fileChooser.showOpenDialog(this);

        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            label.setText("Hochladen: " + selectedFile.getName());
            uploadFile(selectedFile);
        }
    }

    private void uploadFile(File file) {
        Channel channel = null;
        ChannelSftp sftp = null;

        try {
            channel = sessionSupplier.get().openChannel("sftp");
            channel.connect();
            sftp = (ChannelSftp) channel;

            sftp.cd(remoteDirectory);
            sftp.put(new FileInputStream(file), file.getName());

            label.setText("Upload erfolgreich: " + file.getName());

        } catch (Exception ex) {
            ex.printStackTrace();
            label.setText("Fehler beim Hochladen: " + file.getName());

        } finally {
            if (sftp != null) {
                sftp.exit();
            }
            if (channel != null) {
                channel.disconnect();
            }
        }
    }
}

