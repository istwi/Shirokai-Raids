package com.shirokai.raids;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

final class JsonFiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static <T> T copy(T value, Class<T> type) { return GSON.fromJson(GSON.toJson(value), type); }
    static <T> T read(Path path, Class<T> type) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("Arquivo ausente ou inválido: " + path);
        try {
            T value = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
            if (value == null) throw new IOException("JSON vazio: " + path);
            return value;
        } catch (RuntimeException e) { throw new IOException("JSON inválido: " + path, e); }
    }
    static void write(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(value), StandardCharsets.UTF_8);
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            // Same-directory replacement; Windows compatible. Never open a directory as a file.
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    static void defaultFile(Path path, Object value) throws IOException {
        if (!Files.exists(path)) write(path, value);
    }
}
