package de.kneitzel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * Default implementation of FileOperations using actual file system operations.
 *
 * @author Konrad Neitzel
 * @version 1.1-SNAPSHOT
 */
public class DefaultFileOperations implements FileOperations {

    private final Gson gson;

    /**
     * Constructor initializing Gson for JSON operations.
     */
    public DefaultFileOperations() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public boolean fileExists(String filePath) {
        return new java.io.File(filePath).exists();
    }

    @Override
    public Map<String, SshConfig> readConfigs(String filePath) throws IOException {
        try (FileReader reader = new FileReader(filePath)) {
            Type type = new TypeToken<Map<String, SshConfig>>() {
            }.getType();
            return gson.fromJson(reader, type);
        }
    }

    @Override
    public void writeConfigs(String filePath, Map<String, SshConfig> configs) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            gson.toJson(configs, writer);
        }
    }
}
