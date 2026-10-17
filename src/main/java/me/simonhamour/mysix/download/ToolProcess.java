package me.simonhamour.mysix.download;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

final class ToolProcess {
    private ToolProcess() {}
    static YtDlpDownloader.ProcessResult run(List<String> command, Path directory, Map<String, String> environment,
            Duration timeout, Path logBase) throws IOException, InterruptedException {
        Files.createDirectories(logBase.toAbsolutePath().getParent());
        var stdout = Path.of(logBase + ".stdout.log"); var stderr = Path.of(logBase + ".stderr.log");
        var builder = new ProcessBuilder(command).redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
        if (directory != null) builder.directory(directory.toFile());
        builder.environment().putAll(environment);
        var process = builder.start();
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) throw new IOException("Tool timed out after " + timeout.toSeconds() + " seconds");
            return new YtDlpDownloader.ProcessResult(process.exitValue(), read(stdout), read(stderr));
        } finally { stop(process); }
    }
    static String read(Path path) throws IOException {
        try (var in = Files.newInputStream(path)) { return new String(in.readNBytes(4 * 1024 * 1024), StandardCharsets.UTF_8); }
    }
    static void stop(Process process) {
        if (process != null && process.isAlive()) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }
}
