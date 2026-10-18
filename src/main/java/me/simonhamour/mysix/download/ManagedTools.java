package me.simonhamour.mysix.download;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/** Installed by an owner command; no downloads or process startup on the Minecraft tick thread. */
public final class ManagedTools implements AutoCloseable {
    private final ToolPlatform platform;
    private final ToolInstaller.Manifest manifest;
    private final Path root, bin, server, logs;
    private final Map<String, String> environment;
    private volatile Process provider;
    private volatile int port;
    private volatile boolean closed;

    public ManagedTools(Path gameDirectory) throws IOException {
        platform = ToolPlatform.detect(); manifest = ToolInstaller.manifest();
        root = ToolInstaller.safe(gameDirectory, "mysix-tools/" + manifest.bundle() + "/" + platform.id());
        bin = root.resolve("bin"); server = root.resolve(manifest.providerDirectory()); logs = root.resolve("logs");
        environment = Map.of("DENO_DIR", root.resolve("deno-cache").toString());
    }
    public String platform() { return platform.id(); }
    public Path root() { return root; }
    private Path tool(String name) { return bin.resolve(platform.executable(name)); }
    public boolean installed() {
        return Files.isRegularFile(root.resolve("ready")) && List.of("yt-dlp", "deno", "ffmpeg", "ffprobe").stream()
                .allMatch(name -> Files.isRegularFile(tool(name))) && Files.isDirectory(server.resolve("node_modules"))
                && Files.isRegularFile(root.resolve("plugins/bgutil-ytdlp-pot-provider.zip"));
    }
    public void install(Consumer<String> progress) throws IOException, InterruptedException {
        if (closed) throw new IOException("Server is stopping");
        progress.accept("Detected " + platform.id());
        if (installed()) { progress.accept("Pinned tools are already installed"); return; }
        var selected = manifest.platforms().get(platform.id());
        if (selected == null) throw new IOException("No tool bundle for " + platform.id());
        var artifacts = new ArrayList<>(selected); artifacts.addAll(manifest.common());
        new ToolInstaller().install(root, artifacts, progress);
        Files.createDirectories(logs);
        if (!platform.windows()) {
            for (String name : List.of("yt-dlp", "deno", "ffmpeg", "ffprobe")) {
                Files.setPosixFilePermissions(tool(name), Set.of(PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
            }
        }
        for (String name : List.of("yt-dlp", "deno", "ffmpeg", "ffprobe")) {
            var check = ToolProcess.run(List.of(tool(name).toString(), name.startsWith("ff") ? "-version" : "--version"),
                    root, environment, Duration.ofSeconds(20), logs.resolve("check-" + name));
            requireSuccess(check, name + " cannot run on this host");
        }
        progress.accept("Installing locked token-helper dependencies; this can take several minutes");
        var result = ToolProcess.run(List.of(tool("deno").toString(), "install", "--prod", "--allow-scripts=npm:canvas", "--frozen"),
                server, environment, Duration.ofMinutes(8), logs.resolve("provider-install"));
        requireSuccess(result, "Token-helper dependency installation failed");
        Files.writeString(root.resolve("ready"), manifest.bundle() + "\n");
    }

    public YtDlpDownloader downloader(DownloadConfig config, Path directory) throws IOException {
        if (!installed()) throw new IOException("Managed tools are incomplete. Server owner: run /mysix setup");
        var command = new ArrayList<>(List.of(tool("yt-dlp").toString(), "--ffmpeg-location", bin.toString(),
                "--no-js-runtimes", "--js-runtimes", "deno:" + tool("deno"),
                "--no-plugin-dirs"));
        var managed = new DownloadConfig(command, config.timeoutSeconds(), config.maxFileMiB(), true);
        return new YtDlpDownloader(managed, directory, new AnonymousYouTubeRunner(
                (args, timeout, log) -> ToolProcess.run(args, root, environment, timeout, log),
                (args, timeout, log) -> {
            long started = System.nanoTime();
            ensureProvider();
            var remaining = timeout.minusNanos(System.nanoTime() - started);
            if (remaining.isNegative() || remaining.isZero()) throw new IOException("Token helper startup exhausted the request timeout");
            var actual = new ArrayList<>(args);
            actual.addAll(actual.indexOf("--"), List.of("--plugin-dirs", root.resolve("plugins").toString(),
                    "--extractor-args", "youtube:player_client=mweb", "--extractor-args",
                    "youtubepot-bgutilhttp:base_url=http://127.0.0.1:" + port));
            return ToolProcess.run(actual, root, environment, remaining, log);
        }));
    }

    public synchronized void ensureProvider() throws IOException, InterruptedException {
        if (closed) throw new IOException("Server is stopping");
        if (!installed()) throw new IOException("Run /mysix setup first");
        if (provider != null && provider.isAlive() && healthy()) return;
        ToolProcess.stop(provider);
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { port = socket.getLocalPort(); }
        var builder = new ProcessBuilder(tool("deno").toString(), "run", "--allow-env", "--allow-net",
                "--allow-ffi=.", "--allow-read=.", "../src/main.ts", "--host", "127.0.0.1", "--port", Integer.toString(port))
                .directory(server.resolve("node_modules").toFile())
                // Upstream stdout prints generated tokens. Keep them out of persistent diagnostics.
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.appendTo(logs.resolve("provider.stderr.log").toFile()));
        builder.environment().putAll(environment);
        provider = builder.start();
        if (closed) { ToolProcess.stop(provider); throw new IOException("Server is stopping"); }
        try {
            long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
            while (!closed && provider.isAlive() && System.nanoTime() < deadline) {
                if (healthy()) return;
                Thread.sleep(200);
            }
            throw new IOException("Token helper did not become ready. Check mysix-tools logs");
        } catch (IOException | InterruptedException error) { ToolProcess.stop(provider); throw error; }
    }
    private boolean healthy() throws InterruptedException {
        try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
            var response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/ping"))
                    .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && JsonParser.parseString(response.body()).getAsJsonObject()
                    .get("version").getAsString().equals(manifest.providerVersion());
        } catch (IOException | RuntimeException error) { return false; }
    }
    private void requireSuccess(YtDlpDownloader.ProcessResult result, String message) throws IOException {
        if (result.exitCode() != 0) throw new IOException(message + ". See " + logs + " (exit " + result.exitCode() + ")");
    }
    @Override public void close() { closed = true; ToolProcess.stop(provider); }
}
