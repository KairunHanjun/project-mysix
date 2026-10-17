package me.simonhamour.mysix.download;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record DownloadConfig(List<String> ytDlpCommand, int timeoutSeconds, int maxFileMiB) {
    public DownloadConfig {
        if (ytDlpCommand == null || ytDlpCommand.isEmpty()
                || ytDlpCommand.stream().anyMatch(s -> s == null || s.isBlank())) {
            throw new IllegalArgumentException("ytDlpCommand must contain an executable and optional arguments");
        }
        ytDlpCommand = List.copyOf(ytDlpCommand);
        if (timeoutSeconds < 1 || timeoutSeconds > 600) throw new IllegalArgumentException("timeoutSeconds must be 1–600");
        if (maxFileMiB < 1 || maxFileMiB > 100) throw new IllegalArgumentException("maxFileMiB must be 1–100");
    }

    public static DownloadConfig defaults() {
        return new DownloadConfig(List.of("yt-dlp"), 90, 30);
    }

    public static DownloadConfig load(Path file) throws IOException {
        var gson = new GsonBuilder().setPrettyPrinting().create();
        if (!Files.exists(file)) {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, gson.toJson(defaults()) + "\n");
            return defaults();
        }
        try {
            var config = gson.fromJson(Files.readString(file), DownloadConfig.class);
            if (config == null) throw new IllegalArgumentException("Empty configuration");
            return config;
        } catch (RuntimeException error) {
            throw new IOException("Invalid Mysix download config: " + error.getMessage(), error);
        }
    }
}
