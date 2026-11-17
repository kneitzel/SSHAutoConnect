package de.kneitzel.config;

import static java.nio.file.StandardOpenOption.*;

import com.google.gson.*;

import de.kneitzel.model.AppConfig;
import de.neitzel.core.config.ApplicationDirectories;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;

/** Invalid input locks this repository against overwrite for the rest of its lifetime. */
public class ConfigurationRepository {
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private boolean blocked;

    public ConfigurationRepository(Path file) {
        this.file = file;
    }

    public static Path defaultPath() {
        return ApplicationDirectories.configurationDirectory("SSHAutoConnect")
                .resolve("config.json");
    }

    public Path path() {
        return file;
    }

    public synchronized AppConfig load() throws IOException {
        if (!Files.exists(file)) return new AppConfig();
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file));
            if (!root.isJsonObject() || !root.getAsJsonObject().has("schemaVersion"))
                throw new IllegalArgumentException("Missing schema version");
            if (!root.getAsJsonObject().get("schemaVersion").isJsonPrimitive()
                    || !root.getAsJsonObject().getAsJsonPrimitive("schemaVersion").isNumber())
                throw new IllegalArgumentException("Schema version must be an integer");
            root.getAsJsonObject().get("schemaVersion").getAsBigDecimal().intValueExact();
            checkStoredIds(root);
            AppConfig config = gson.fromJson(root, AppConfig.class);
            Configurations.validate(config);
            return config;
        } catch (IOException | RuntimeException e) {
            blocked = true;
            throw new IOException("Cannot read configuration. Original preserved: " + file, e);
        }
    }

    private static void checkStoredIds(JsonElement element) {
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        for (String key :
                java.util.List.of(
                        "profiles", "targets", "portMappings", "commands", "assignments")) {
            if (!object.has(key)) continue;
            JsonElement children = object.get(key);
            if (!children.isJsonArray())
                throw new IllegalArgumentException("Invalid collection: " + key);
            for (JsonElement child : children.getAsJsonArray()) {
                if (!child.isJsonObject()
                        || !child.getAsJsonObject().has("id")
                        || !child.getAsJsonObject().get("id").isJsonPrimitive())
                    throw new IllegalArgumentException("Missing stable ID in " + key);
                checkStoredIds(child);
            }
        }
    }

    public synchronized void save(AppConfig config) throws IOException {
        if (blocked)
            throw new IOException("Configuration is protected after a load failure: " + file);
        Configurations.validate(config);
        Path directory = file.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".ssh-config-", ".tmp");
        try {
            if (Files.getFileStore(temporary).supportsFileAttributeView("posix"))
                Files.setPosixFilePermissions(
                        temporary, PosixFilePermissions.fromString("rw-------"));
            byte[] bytes = gson.toJson(config).getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, WRITE, TRUNCATE_EXISTING)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            replace(temporary, file.toAbsolutePath());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    protected void replace(Path temporary, Path destination) throws IOException {
        Files.move(
                temporary,
                destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }
}
