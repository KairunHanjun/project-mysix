package me.simonhamour.mysix.download;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ToolInstallerTest {
    @TempDir Path directory;
    @ParameterizedTest @CsvSource({"Windows 11,amd64,windows-x64", "Linux,x86_64,linux-x64",
            "Linux,aarch64,linux-arm64", "Mac OS X,arm64,macos-arm64", "Darwin,x86_64,macos-x64"})
    void detectsPlatformWithoutAskingUser(String os, String cpu, String expected) throws Exception {
        assertEquals(expected, ToolPlatform.detect(os, cpu, false).id());
    }
    @Test void rejectsUnsupportedPlatformsBeforeInstallation() {
        assertThrows(IOException.class, () -> ToolPlatform.detect("Linux", "amd64", true));
        assertThrows(IOException.class, () -> ToolPlatform.detect("Windows 11", "aarch64", false));
        assertThrows(IOException.class, () -> ToolPlatform.detect("FreeBSD", "amd64", false));
        assertThrows(IOException.class, () -> ToolPlatform.detect("Linux", "riscv64", false));
    }
    private ToolInstaller.Artifact artifact(byte[] bytes, String kind, String target) throws Exception {
        var source = directory.resolve("source"); Files.write(source, bytes);
        return new ToolInstaller.Artifact("fixture", "https://github.com/example/test", ToolInstaller.sha256(source), target, kind);
    }
    private static byte[] zip(String name, String content) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(name)); zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
        }
        return bytes.toByteArray();
    }
    @Test void verifiesDownloadReusesVerifiedArchiveAndRepairsCorruptCache() throws Exception {
        byte[] bytes = "test executable".getBytes(); var artifact = artifact(bytes, "file", "bin/tool");
        var count = new AtomicInteger();
        var installer = new ToolInstaller((url, target) -> { count.incrementAndGet(); Files.write(target, bytes); });
        var root = directory.resolve("tools");
        installer.installArtifact(root, artifact); installer.installArtifact(root, artifact);
        assertEquals(1, count.get()); assertEquals("test executable", Files.readString(root.resolve("bin/tool")));
        Files.writeString(root.resolve("archives/" + artifact.sha256() + ".download"), "corrupt");
        installer.installArtifact(root, artifact); assertEquals(2, count.get());
    }
    @Test void wrongChecksumNeverInstallsExecutableAndRemovesTemporaryFile() throws Exception {
        var artifact = artifact("expected".getBytes(), "file", "bin/tool");
        var root = directory.resolve("tools");
        var installer = new ToolInstaller((url, target) -> Files.writeString(target, "tampered"));
        assertThrows(IOException.class, () -> installer.installArtifact(root, artifact));
        assertFalse(Files.exists(root.resolve("bin/tool")));
        try (var files = Files.list(root.resolve("archives"))) { assertEquals(0, files.count()); }
    }
    @ParameterizedTest @ValueSource(strings = {"../escaped", "/escaped", "folder/../../escaped", "C:/escaped", "..\\escaped"})
    void archiveCannotEscapeItsDirectory(String path) throws Exception {
        byte[] bytes = zip(path, "bad"); var artifact = artifact(bytes, "zip", "provider");
        var installer = new ToolInstaller((url, target) -> Files.write(target, bytes));
        assertThrows(IOException.class, () -> installer.installArtifact(directory.resolve("tools"), artifact));
        assertFalse(Files.exists(directory.resolve("escaped")));
    }
    @Test void extractsNestedZipAndGzipAndBoundsExpandedBytes() throws Exception {
        byte[] zipped = zip("server/main.ts", "source"); var zippedArtifact = artifact(zipped, "zip", "provider");
        var root = directory.resolve("tools");
        new ToolInstaller((url, target) -> Files.write(target, zipped)).installArtifact(root, zippedArtifact);
        assertEquals("source", Files.readString(root.resolve("provider/server/main.ts")));
        var bytes = new ByteArrayOutputStream();
        try (var out = new GZIPOutputStream(bytes)) { out.write("binary".getBytes()); }
        var compressed = bytes.toByteArray(); var compressedArtifact = artifact(compressed, "gzip", "bin/ffmpeg");
        new ToolInstaller((url, target) -> Files.write(target, compressed)).installArtifact(root, compressedArtifact);
        assertEquals("binary", Files.readString(root.resolve("bin/ffmpeg")));
        assertThrows(IOException.class, () -> ToolInstaller.copyLimited(new ByteArrayInputStream(new byte[8]), new ByteArrayOutputStream(), 4));
    }
    @Test void shippedManifestPinsEveryDownloadAndExpectedPlatforms() throws Exception {
        var manifest = ToolInstaller.manifest();
        assertEquals(Set.of("windows-x64", "linux-x64", "linux-arm64", "macos-x64", "macos-arm64"), manifest.platforms().keySet());
        var all = new ArrayList<>(manifest.common()); manifest.platforms().values().forEach(all::addAll);
        for (var artifact : all) {
            assertTrue(artifact.sha256().matches("[0-9a-f]{64}"));
            assertTrue(artifact.url().startsWith("https://github.com/"));
            assertTrue(ToolInstaller.safe(directory, artifact.target()).startsWith(directory));
        }
    }
    @Test void stripsPinnedSourceArchiveRootWithoutAllowingTraversal() throws Exception {
        byte[] bytes = zip("release/server/main.ts", "source"); var original = artifact(bytes, "zip", "provider");
        var stripped = new ToolInstaller.Artifact(original.name(), original.url(), original.sha256(),
                original.target(), original.kind(), "release/");
        new ToolInstaller((url, target) -> Files.write(target, bytes)).installArtifact(directory.resolve("tools"), stripped);
        assertEquals("source", Files.readString(directory.resolve("tools/provider/server/main.ts")));
        assertFalse(Files.exists(directory.resolve("tools/provider/release")));
    }
    @Test void managedConfigRoundTripsWithoutLosingManualCommandOrLimits() throws Exception {
        var config = new DownloadConfig(List.of("C:/Tools/yt-dlp.exe", "--cookies", "private.txt"), 120, 20, true);
        var path = directory.resolve("config/mysix-download.json"); config.save(path);
        assertEquals(config, DownloadConfig.load(path));
        assertFalse(Files.exists(path.resolveSibling("mysix-download.json.tmp")));
    }
}
