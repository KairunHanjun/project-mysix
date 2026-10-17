package me.simonhamour.mysix.download;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Plain Java boundary: resolves one video, then downloads that same video. */
public final class YtDlpDownloader {
    public record ProcessResult(int exitCode, String stdout, String stderr) {}
    public record DownloadResult(String videoId, String title, Path file, long bytes, double durationSeconds) {}

    @FunctionalInterface
    public interface Runner {
        ProcessResult run(List<String> command, Duration timeout, Path logBase)
                throws IOException, InterruptedException;
    }

    private static final Set<String> HOSTS = Set.of("youtube.com", "www.youtube.com",
            "m.youtube.com", "music.youtube.com", "youtu.be", "www.youtu.be");
    private final DownloadConfig config;
    private final Path directory;
    private final Runner runner;
    private final java.util.Map<String, DownloadResult> completed = new java.util.HashMap<>();

    public YtDlpDownloader(DownloadConfig config, Path directory) {
        this(config, directory, YtDlpDownloader::runProcess);
    }

    public YtDlpDownloader(DownloadConfig config, Path directory, Runner runner) {
        this.config = config;
        this.directory = directory.toAbsolutePath().normalize();
        this.runner = runner;
    }

    public static String searchTarget(String request) {
        var text = request.strip();
        if (text.isEmpty() || text.length() > 500 || text.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Enter an artist/title or a YouTube video link (maximum 500 characters)");
        }
        if (text.matches("(?i)^[a-z][a-z0-9+.-]*:.*")) {
            if (!isYouTubeUrl(text)) throw new IllegalArgumentException("Only HTTP(S) YouTube links are supported");
            return text;
        }
        // It is one ProcessBuilder argument, never shell code or yt-dlp options.
        return "ytsearch1:" + text;
    }

    private static boolean isYouTubeUrl(String value) {
        try {
            var uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                    && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443 || uri.getPort() == 80);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private ArrayList<String> command() {
        var command = new ArrayList<>(config.ytDlpCommand());
        command.addAll(List.of("--ignore-config", "--no-playlist", "--no-progress",
                "--js-runtimes", "deno", "--remote-components", "ejs:github",
                "--socket-timeout", "10", "--retries", "1", "--extractor-retries", "1"));
        return command;
    }

    public synchronized DownloadResult download(String request) throws IOException, InterruptedException {
        var target = searchTarget(request);
        var cached = completed.get(target);
        if (cached != null && Files.isRegularFile(cached.file()) && Files.size(cached.file()) == cached.bytes()) {
            LocalAudioFiles.requireVorbis(cached.file());
            return cached;
        }
        // A resolved CD carries this canonical URL. Reuse its completed Ogg and
        // yt-dlp sidecar after a server restart without performing another search.
        var direct = java.util.regex.Pattern.compile("https://www\\.youtube\\.com/watch\\?v=([A-Za-z0-9_-]{11})")
                .matcher(target);
        if (direct.matches()) {
            var id = direct.group(1);
            var file = directory.resolve(id + ".ogg");
            var info = directory.resolve(id + ".info.json");
            try {
                if (Files.isRegularFile(file) && Files.size(file) > 0
                        && Files.size(file) <= config.maxFileMiB() * 1024L * 1024L
                        && Files.isRegularFile(info) && Files.size(info) <= 4 * 1024 * 1024) {
                    LocalAudioFiles.requireVorbis(file);
                    var json = JsonParser.parseString(Files.readString(info)).getAsJsonObject();
                    double seconds = json.has("duration") ? json.get("duration").getAsDouble() : 0;
                    if (id.equals(json.get("id").getAsString()) && Double.isFinite(seconds) && seconds >= 0) {
                        var finished = new DownloadResult(id, json.get("title").getAsString(), file, Files.size(file), seconds);
                        completed.put(target, finished);
                        return finished;
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                // Incomplete or corrupt cache: resolve/download the same pinned video.
            }
        }
        Files.createDirectories(directory);
        var logs = directory.resolve("logs");
        Files.createDirectories(logs);
        var job = UUID.randomUUID().toString();
        var resolve = command();
        resolve.addAll(List.of("--skip-download", "--dump-single-json", "--", target));
        var metadata = runner.run(resolve, Duration.ofSeconds(config.timeoutSeconds()), logs.resolve(job + "-search"));
        checkSuccess(metadata, "Song lookup");
        String id, title, url;
        double duration;
        try {
            var json = JsonParser.parseString(metadata.stdout()).getAsJsonObject();
            if (json.has("entries")) {
                var entries = json.getAsJsonArray("entries");
                if (entries.isEmpty() || !entries.get(0).isJsonObject()) throw new IllegalArgumentException("No results");
                json = entries.get(0).getAsJsonObject();
            }
            id = json.get("id").getAsString();
            title = json.get("title").getAsString();
            url = json.get("webpage_url").getAsString();
            duration = json.has("duration") && !json.get("duration").isJsonNull()
                    ? json.get("duration").getAsDouble() : 0;
            if (!Double.isFinite(duration) || duration < 0) duration = 0;
            if (!id.matches("[A-Za-z0-9_-]{11}") || !isYouTubeUrl(url)) throw new IllegalArgumentException("Invalid video metadata");
        } catch (RuntimeException error) {
            throw new IOException("yt-dlp returned no valid YouTube recording", error);
        }

        // IDs, not user-provided titles, determine filenames. No shell involved.
        var output = directory.resolve(id + ".%(ext)s");
        IOException failure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            var download = command();
            download.addAll(List.of("-f", "bestaudio", "--max-filesize", config.maxFileMiB() + "M",
                    "--extract-audio", "--audio-format", "vorbis", "--audio-quality", "5",
                    "--postprocessor-args", "ExtractAudio+ffmpeg_o:-ac 1",
                    "--write-info-json", "--print", "after_move:MYSIX_FILE:%(filepath)s",
                    "-o", output.toString(), "--", url));
            try {
                var result = runner.run(download, Duration.ofSeconds(config.timeoutSeconds()), logs.resolve(job + "-download-" + attempt));
                checkSuccess(result, "Audio download");
                var line = result.stdout().lines().filter(s -> s.startsWith("MYSIX_FILE:")).findFirst()
                        .orElseThrow(() -> new IOException("yt-dlp did not report a completed audio file"));
                var file = Path.of(line.substring("MYSIX_FILE:".length())).toAbsolutePath().normalize();
                if (!file.startsWith(directory) || !file.getFileName().toString().startsWith(id + ".")
                        || !file.toString().endsWith(".ogg")) {
                    throw new IOException("yt-dlp reported an unexpected output path");
                }
                if (!Files.isRegularFile(file) || Files.size(file) == 0) throw new IOException("Downloaded audio is missing or empty");
                if (Files.size(file) > config.maxFileMiB() * 1024L * 1024L) throw new IOException("Downloaded audio exceeds the size limit");
                LocalAudioFiles.requireVorbis(file);
                var finished = new DownloadResult(id, title, file, Files.size(file), duration);
                completed.put(target, finished);
                return finished;
            } catch (IOException error) {
                failure = error;
            }
        }
        throw new IOException("Download failed after retrying the same video: " + failure.getMessage(), failure);
    }

    private static void checkSuccess(ProcessResult result, String stage) throws IOException {
        if (result.exitCode() != 0) {
            var detail = result.stderr().lines().filter(s -> s.startsWith("ERROR:")).reduce((a, b) -> b)
                    .orElse("yt-dlp exited with code " + result.exitCode());
            throw new IOException(stage + " failed: " + detail.substring(0, Math.min(detail.length(), 350)));
        }
    }

    private static ProcessResult runProcess(List<String> command, Duration timeout, Path logBase)
            throws IOException, InterruptedException {
        var stdout = Path.of(logBase + ".stdout.log");
        var stderr = Path.of(logBase + ".stderr.log");
        Process process;
        try {
            process = new ProcessBuilder(command).redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
        } catch (IOException error) {
            throw new IOException("Cannot start yt-dlp. Install yt-dlp and Deno on the server, or set ytDlpCommand in config/mysix-download.json", error);
        }
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IOException("yt-dlp timed out after " + timeout.toSeconds() + " seconds");
            }
            return new ProcessResult(process.exitValue(), readLimited(stdout), readLimited(stderr));
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private static String readLimited(Path path) throws IOException {
        try (var stream = Files.newInputStream(path)) {
            return new String(stream.readNBytes(4 * 1024 * 1024), StandardCharsets.UTF_8);
        }
    }
}
