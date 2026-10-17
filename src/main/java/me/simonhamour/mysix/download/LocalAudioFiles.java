package me.simonhamour.mysix.download;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class LocalAudioFiles {
    private LocalAudioFiles() {}

    public static Path find(Path directory, String videoId) throws IOException {
        var root = directory.toAbsolutePath().normalize();
        if (videoId != null) {
            if (!videoId.matches("[A-Za-z0-9_-]{11}")) throw new IOException("Use the 11-character video ID printed after downloading.");
            var file = root.resolve(videoId + ".ogg");
            if (!Files.isRegularFile(file)) throw new IOException("No local Ogg download for " + videoId + ". Download it first.");
            requireVorbis(file);
            return file;
        }
        if (!Files.isDirectory(root)) throw new IOException("No local downloads yet. Use /mysix download <artist - title> first.");
        try (var files = Files.list(root)) {
            var latest = files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("[A-Za-z0-9_-]{11}\\.ogg"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .orElseThrow(() -> new IOException("No local Ogg downloads. Run /mysix download again to convert older WebM files."));
            requireVorbis(latest);
            return latest;
        }
    }

    public static void requireVorbis(Path file) throws IOException {
        byte[] header;
        try (var input = Files.newInputStream(file)) { header = input.readNBytes(65536); }
        if (header.length < 32 || header[0] != 'O' || header[1] != 'g' || header[2] != 'g' || header[3] != 'S') {
            throw new IOException("Audio is not an Ogg file: " + file.getFileName());
        }
        byte[] identification = {1, 'v', 'o', 'r', 'b', 'i', 's'};
        for (int i = 4; i <= header.length - identification.length; i++) {
            boolean match = true;
            for (int j = 0; j < identification.length; j++) if (header[i + j] != identification[j]) match = false;
            if (match) return;
        }
        throw new IOException("Ogg audio must use Vorbis, not Opus. Redownload with the current Mysix converter.");
    }
}
