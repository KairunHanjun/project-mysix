package me.simonhamour.mysix.download;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/** Two anonymous routes, sharing one timeout budget. Never changes the requested video. */
final class AnonymousYouTubeRunner implements YtDlpDownloader.Runner {
    private final YtDlpDownloader.Runner defaults, tokenHelper;

    AnonymousYouTubeRunner(YtDlpDownloader.Runner defaults, YtDlpDownloader.Runner tokenHelper) {
        this.defaults = defaults;
        this.tokenHelper = tokenHelper;
    }

    @Override public YtDlpDownloader.ProcessResult run(List<String> args, Duration timeout, Path log)
            throws IOException, InterruptedException {
        long started = System.nanoTime();
        var result = defaults.run(args, timeout, Path.of(log + "-default"));
        String status = "default-clients: exit " + result.exitCode() + "\n";
        Files.writeString(Path.of(log + ".route.log"), status);
        if (!retryable(result)) return result;
        var remaining = timeout.minusNanos(System.nanoTime() - started);
        if (remaining.isNegative() || remaining.isZero()) return result;
        try {
            result = tokenHelper.run(args, remaining, Path.of(log + "-token"));
            Files.writeString(Path.of(log + ".route.log"), status + "mweb-token-helper: exit " + result.exitCode() + "\n");
            return result;
        } catch (IOException error) {
            // Keep the original access failure if the optional helper cannot start.
            Files.writeString(Path.of(log + ".route.log"), status + "mweb-token-helper: unavailable\n");
            return result;
        }
    }

    private static boolean retryable(YtDlpDownloader.ProcessResult result) {
        if (result.exitCode() == 0) return false;
        var text = result.stderr().toLowerCase(Locale.ROOT);
        return text.contains("confirm you’re not a bot") || text.contains("confirm you're not a bot")
                || text.contains("http error 403") || text.contains("requested format is not available")
                || text.contains("no video formats found");
    }
}
