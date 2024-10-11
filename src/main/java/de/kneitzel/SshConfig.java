package de.kneitzel;

import lombok.Getter;
import lombok.Setter;

import java.io.*;
import java.security.Key;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@Getter
@Setter
public class SshConfig {


    private transient String userHome = System.getProperty("user.home");
    private transient File configFile = new File(userHome, ".sshAutoConnect.json");

    private static final String ALGORITHM = "AES";
    private static final String SECRET_KEY = "MySuperSecretKey"; // 16-byte Schlüssel

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private String hostName;
    private String userName;
    private String password;
    private boolean autoConnect;
    private String xtermCommand;
    private String uploadDirectory;
    private List<PortMapping> portMappings;

    public SshConfig() {
        this.portMappings = new ArrayList<>();
    }

    public void init() throws IOException {
        if (configFile.exists()) {
            loadFromFile();
        } else {
            createExampleConfig();
            saveToFile();
        }
    }

    public String getPassword() {
        return password != null ? decrypt(password) : null;
    }

    public void setPassword(String password) {
        this.password = password != null ? encrypt(password) : null;
    }

    public void loadFromFile() throws IOException {
        if (!configFile.exists()) {
            createExampleConfig();
            saveToFile();
        }

        try (FileReader reader = new FileReader(configFile)) {
            SshConfig config = gson.fromJson(reader, SshConfig.class);
            this.hostName = config.hostName;
            this.userName = config.userName;
            this.password = config.password;
            this.autoConnect = config.autoConnect;
            this.portMappings = config.portMappings;
            this.uploadDirectory = config.uploadDirectory;
            if (uploadDirectory == null || uploadDirectory.isEmpty()) {
                uploadDirectory = "/tmp/";
            }
        }
    }

    // Konfigurationsdatei speichern
    public void saveToFile() throws IOException {
        try (FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(this, writer);
        }
    }

    // Beispielkonfiguration erstellen
    public void createExampleConfig() {
        setHostName("example.com");
        setUserName("exampleUser");
        setPassword("examplePassword");
        setXtermCommand("xterm");
        setUploadDirectory("/tmp/");
        getPortMappings().add(new PortMapping(8080, 8080, "startvnc", "stopvnc"));
        getPortMappings().add(new PortMapping(8443, 443, "startvnc", "stopvnc"));
        getPortMappings().add(new PortMapping(3306, 3306, "startvnc", "stopvnc"));
    }

    // Verschlüsselungsmethoden
    private static String encrypt(String data) {
        try {
            Key key = generateKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encryptedData = cipher.doFinal(data.getBytes());
            return Base64.getEncoder().encodeToString(encryptedData);
        } catch (Exception e) {
            throw new RuntimeException("Fehler bei der Verschlüsselung", e);
        }
    }

    private static String decrypt(String encryptedData) {
        try {
            Key key = generateKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decodedData = Base64.getDecoder().decode(encryptedData);
            byte[] decryptedData = cipher.doFinal(decodedData);
            return new String(decryptedData);
        } catch (Exception e) {
            throw new RuntimeException("Fehler bei der Entschlüsselung", e);
        }
    }

    private static Key generateKey() {
        return new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
    }
}
