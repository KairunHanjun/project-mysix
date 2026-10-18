package me.simonhamour.mysix.download;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnonymousYouTubeRunnerTest {
    @TempDir Path directory;
    private static final List<String> ARGS = List.of("yt-dlp", "--", "https://www.youtube.com/watch?v=dQw4w9WgXcQ");
    private static final Duration TIMEOUT = Duration.ofSeconds(90);
    private static final YtDlpDownloader.ProcessResult OK = new YtDlpDownloader.ProcessResult(0, "ok", "");

    @Test void successfulDefaultRouteDoesNotStartHelper() throws Exception {
        var runner = new AnonymousYouTubeRunner((a,t,l) -> OK,
                (a,t,l) -> { fail("No helper needed"); return null; });
        assertSame(OK, runner.run(ARGS, TIMEOUT, directory.resolve("job")));
        assertEquals("default-clients: exit 0\n", Files.readString(directory.resolve("job.route.log")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Sign in to confirm you're not a bot", "Sign in to confirm you’re not a bot",
            "HTTP Error 403: Forbidden", "Requested format is not available", "No video formats found"})
    void accessFailureTriesHelperOnceWithSameVideoAndRemainingBudget(String error) throws Exception {
        var calls = new ArrayList<String>();
        var runner = new AnonymousYouTubeRunner((a,t,l) -> {
            assertEquals(ARGS, a); calls.add("default");
            assertTrue(l.toString().endsWith("-default"));
            return new YtDlpDownloader.ProcessResult(1, "", error);
        }, (a,t,l) -> {
            assertEquals(ARGS, a); calls.add("token");
            assertTrue(t.compareTo(TIMEOUT) < 0 && t.isPositive());
            assertTrue(l.toString().endsWith("-token")); return OK;
        });
        assertSame(OK, runner.run(ARGS, TIMEOUT, directory.resolve("job")));
        assertEquals(List.of("default", "token"), calls);
        assertTrue(Files.readString(directory.resolve("job.route.log")).contains("mweb-token-helper: exit 0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Video unavailable", "Sign in to confirm your age", "disk full", "Unsupported URL"})
    void unrelatedFailuresDoNotTryAnotherRoute(String error) throws Exception {
        var failed = new YtDlpDownloader.ProcessResult(1, "", error);
        var runner = new AnonymousYouTubeRunner((a,t,l) -> failed,
                (a,t,l) -> { fail("Not an anonymous route failure"); return null; });
        assertSame(failed, runner.run(ARGS, TIMEOUT, directory.resolve("job")));
    }

    @Test void unavailableHelperKeepsOriginalAccessFailureAndRecordsRoute() throws Exception {
        var failed = new YtDlpDownloader.ProcessResult(1, "", "HTTP Error 403");
        var runner = new AnonymousYouTubeRunner((a,t,l) -> failed,
                (a,t,l) -> { throw new IOException("Helper cannot start"); });
        assertSame(failed, runner.run(ARGS, TIMEOUT, directory.resolve("job")));
        assertTrue(Files.readString(directory.resolve("job.route.log")).contains("helper: unavailable"));
    }

    @Test void helperCancellationPropagates() {
        var runner = new AnonymousYouTubeRunner((a,t,l) -> new YtDlpDownloader.ProcessResult(1, "", "HTTP Error 403"),
                (a,t,l) -> { throw new InterruptedException("Stopping"); });
        assertThrows(InterruptedException.class, () -> runner.run(ARGS, TIMEOUT, directory.resolve("job")));
    }

    @Test void exhaustedBudgetDoesNotStartHelper() throws Exception {
        var failed = new YtDlpDownloader.ProcessResult(1, "", "HTTP Error 403");
        var runner = new AnonymousYouTubeRunner((a,t,l) -> failed,
                (a,t,l) -> { fail("Budget exhausted"); return null; });
        assertSame(failed, runner.run(ARGS, Duration.ZERO, directory.resolve("job")));
    }
}
