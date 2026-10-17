package me.simonhamour.mysix.download;

import com.google.gson.Gson;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;

/** Only the bundled manifest selects executable downloads. Archives cannot escape the tools directory. */
public final class ToolInstaller {
    public record Artifact(String name, String url, String sha256, String target, String kind, String stripPrefix) {
        public Artifact(String name, String url, String sha256, String target, String kind) {
            this(name, url, sha256, target, kind, null);
        }
    }
    public record Manifest(String bundle, String providerVersion, String providerDirectory,
            List<Artifact> common, Map<String, List<Artifact>> platforms) {}
    @FunctionalInterface public interface Fetcher { void fetch(URI url, Path file) throws IOException; }
    private static final long MAX_DOWNLOAD = 256L * 1024 * 1024, MAX_EXPANDED = 768L * 1024 * 1024;
    private final Fetcher fetcher;
    public ToolInstaller() { this(ToolInstaller::fetchHttps); }
    public ToolInstaller(Fetcher fetcher) { this.fetcher = fetcher; }

    public static Manifest manifest() throws IOException {
        try (var source = ToolInstaller.class.getResourceAsStream("/mysix-tools.json")) {
            if (source == null) throw new IOException("Mysix tool manifest is missing");
            return new Gson().fromJson(new InputStreamReader(source, java.nio.charset.StandardCharsets.UTF_8), Manifest.class);
        }
    }
    public void install(Path root, List<Artifact> artifacts, Consumer<String> progress) throws IOException {
        Files.createDirectories(root);
        for (var artifact : artifacts) {
            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Setup cancelled");
            progress.accept("Installing " + artifact.name());
            installArtifact(root, artifact);
        }
    }
    public void installArtifact(Path root, Artifact artifact) throws IOException {
        if (!artifact.sha256().matches("[0-9a-f]{64}")) throw new IOException("Invalid tool checksum");
        var cache = safe(root, "archives/" + artifact.sha256() + ".download");
        Files.createDirectories(cache.getParent());
        if (!Files.isRegularFile(cache) || !sha256(cache).equals(artifact.sha256())) {
            var part = cache.resolveSibling(cache.getFileName() + ".part-" + UUID.randomUUID());
            try {
                fetcher.fetch(URI.create(artifact.url()), part);
                if (Files.size(part) > MAX_DOWNLOAD || !sha256(part).equals(artifact.sha256())) {
                    throw new IOException("Checksum verification failed for " + artifact.name());
                }
                Files.move(part, cache, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(part); }
        }
        var target = safe(root, artifact.target());
        switch (artifact.kind()) {
            case "file" -> { Files.createDirectories(target.getParent()); Files.copy(cache, target, StandardCopyOption.REPLACE_EXISTING); }
            case "gzip" -> {
                Files.createDirectories(target.getParent());
                try (var in = new GZIPInputStream(Files.newInputStream(cache)); var out = Files.newOutputStream(target)) {
                    copyLimited(in, out, MAX_EXPANDED);
                }
            }
            case "zip" -> {
                Files.createDirectories(target);
                try (var zip = new ZipInputStream(Files.newInputStream(cache))) {
                    long remaining = MAX_EXPANDED; int count = 0;
                    for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                        if (++count > 20000) throw new IOException("Tool archive contains too many files");
                        String name = entry.getName();
                        if (artifact.stripPrefix() != null) {
                            if (!name.startsWith(artifact.stripPrefix())) throw new IOException("Unexpected tool archive root");
                            name = name.substring(artifact.stripPrefix().length());
                            if (name.isEmpty()) continue;
                        }
                        var file = safe(target, name);
                        if (entry.isDirectory()) Files.createDirectories(file);
                        else {
                            Files.createDirectories(file.getParent());
                            try (var out = Files.newOutputStream(file)) { remaining -= copyLimited(zip, out, remaining); }
                        }
                    }
                }
            }
            default -> throw new IOException("Unsupported tool archive type");
        }
    }
    static Path safe(Path root, String relative) throws IOException {
        root = root.toAbsolutePath().normalize();
        var path = root.resolve(relative).normalize();
        if (!path.startsWith(root) || relative.contains("\\") || relative.contains(":")) throw new IOException("Unsafe tool archive path");
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) throw new IOException("Symbolic links are not supported in the tools path");
        }
        return path;
    }
    static long copyLimited(InputStream in, OutputStream out, long limit) throws IOException {
        long total = 0; byte[] buffer = new byte[65536];
        for (int count; (count = in.read(buffer)) != -1;) {
            total += count;
            if (total > limit || Thread.currentThread().isInterrupted()) throw new IOException("Tool transfer exceeded its limit or was cancelled");
            out.write(buffer, 0, count);
        }
        return total;
    }
    public static String sha256(Path file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var in = new DigestInputStream(Files.newInputStream(file), digest)) { in.transferTo(OutputStream.nullOutputStream()); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) { throw new AssertionError(error); }
    }
    private static void fetchHttps(URI uri, Path file) throws IOException {
        for (int redirects = 0; redirects < 6; redirects++) {
            if (!"https".equals(uri.getScheme())) throw new IOException("Tool download requires HTTPS");
            var connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "Project-Mysix-Setup");
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) { uri = uri.resolve(connection.getHeaderField("Location")); continue; }
                if (status != 200) throw new IOException("Tool download returned HTTP " + status);
                if (connection.getContentLengthLong() > MAX_DOWNLOAD) throw new IOException("Tool download is too large");
                try (var in = connection.getInputStream(); var out = Files.newOutputStream(file)) { copyLimited(in, out, MAX_DOWNLOAD); }
                return;
            } finally { connection.disconnect(); }
        }
        throw new IOException("Too many tool download redirects");
    }
}
