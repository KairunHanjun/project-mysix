package me.simonhamour.mysix.download;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class YtDlpDownloaderTest {
    @TempDir Path directory;
    private static final String ID = "dQw4w9WgXcQ";
    private static final String URL = "https://www.youtube.com/watch?v=" + ID;

    private static YtDlpDownloader.ProcessResult metadata() {
        return new YtDlpDownloader.ProcessResult(0,
                "{\"entries\":[{\"id\":\"" + ID + "\",\"title\":\"Test recording\",\"webpage_url\":\"" + URL + "\"}]}", "");
    }

    private YtDlpDownloader.ProcessResult file() throws IOException {
        var audio = directory.resolve(ID + ".ogg");
        byte[] header = new byte[40];
        System.arraycopy(new byte[]{79, 103, 103, 83}, 0, header, 0, 4);
        System.arraycopy(new byte[]{1, 118, 111, 114, 98, 105, 115}, 0, header, 28, 7);
        Files.write(audio, header);
        return new YtDlpDownloader.ProcessResult(0, "MYSIX_FILE:" + audio + "\n", "");
    }

    @Test
    void searchesThenVerifiesACompletedFile() throws Exception {
        var calls = new ArrayList<List<String>>();
        var downloader = new YtDlpDownloader(DownloadConfig.defaults(), directory, (args, timeout, log) -> {
            calls.add(List.copyOf(args));
            return args.contains("--skip-download") ? metadata() : file();
        });
        var result = downloader.download("Dhyo Haw - Ada Aku Disini");
        assertEquals("ytsearch1:Dhyo Haw - Ada Aku Disini", calls.get(0).getLast());
        assertTrue(calls.get(0).contains("--flat-playlist"));
        assertFalse(calls.get(1).contains("--flat-playlist"));
        assertEquals(URL, calls.get(1).getLast());
        assertEquals("--", calls.get(1).get(calls.get(1).size() - 2));
        assertEquals(ID, result.videoId());
        assertEquals("Test recording", result.title());
        assertEquals(40, result.bytes());
        assertTrue(Files.isRegularFile(result.file()));
    }

    @Test void flatSearchEntryUsesUrlFieldAndPinsCanonicalVideo() throws Exception {
        var calls = new ArrayList<List<String>>();
        var service = new YtDlpDownloader(DownloadConfig.defaults(), directory, (args, timeout, log) -> {
            calls.add(List.copyOf(args));
            return args.contains("--skip-download")
                    ? new YtDlpDownloader.ProcessResult(0, "{\"entries\":[{\"id\":\"" + ID
                        + "\",\"title\":\"Flat result\",\"url\":\"https://youtu.be/" + ID + "\",\"duration\":219}]}", "")
                    : file();
        });
        var result = service.download("Artist - Title");
        assertEquals(219, result.durationSeconds());
        assertEquals(URL, calls.get(1).getLast());
    }

    @Test void keepsExplicitManagedRuntimeAndDoesNotReplaceItsPathWithSystemDeno() throws Exception {
        var config = new DownloadConfig(List.of("yt-dlp", "--js-runtimes", "deno:C:/Mysix Tools/deno.exe"), 90, 30, true);
        var calls = new ArrayList<List<String>>();
        var service = new YtDlpDownloader(config, directory, (args, timeout, log) -> {
            calls.add(args); return args.contains("--skip-download") ? metadata() : file();
        });
        service.download("Artist - Title");
        for (var args : calls) {
            assertEquals(1, args.stream().filter("--js-runtimes"::equals).count());
            assertEquals("deno:C:/Mysix Tools/deno.exe", args.get(args.indexOf("--js-runtimes") + 1));
            assertFalse(args.contains("--remote-components"));
        }
    }

    @Test void botChallengeIsReportedWithoutLongCookieInstructionsOrLoginRequest() {
        var service = new YtDlpDownloader(DownloadConfig.defaults(), directory, (args, timeout, log) ->
                new YtDlpDownloader.ProcessResult(1, "", "ERROR: [youtube] Sign in to confirm you're not a bot. Use --cookies-from-browser or --cookies https://example.com"));
        var error = assertThrows(IOException.class, () -> service.download("Artist - Title"));
        assertTrue(error.getMessage().contains("/mysix setup"));
        assertFalse(error.getMessage().contains("--cookies"));
    }

    @Test void managedBotFailureReportsStageAndDiagnosticLocation() {
        var config = new DownloadConfig(List.of("yt-dlp"), 90, 30, true);
        var service = new YtDlpDownloader(config, directory, (args, timeout, log) ->
                new YtDlpDownloader.ProcessResult(1, "", "Sign in to confirm you’re not a bot"));
        var error = assertThrows(IOException.class, () -> service.download("Artist - Title"));
        assertTrue(error.getMessage().contains("Song lookup:"));
        assertTrue(error.getMessage().contains(directory.resolve("logs").toString()));
        assertFalse(error.getMessage().contains("--cookies"));
    }

    @Test
    void repeatedCdQueryReusesFileAndMissingCacheIsDownloadedAgain() throws Exception {
        var calls = new ArrayList<List<String>>();
        var downloader = new YtDlpDownloader(DownloadConfig.defaults(), directory, (args, timeout, log) -> {
            calls.add(List.copyOf(args));
            return args.contains("--skip-download") ? metadata() : file();
        });
        var first = downloader.download("Artist - Title");
        assertSame(first, downloader.download("Artist - Title"));
        assertEquals(2, calls.size(), "Cached CD must not search or download again");
        Files.delete(first.file());
        downloader.download("Artist - Title");
        assertEquals(4, calls.size());
    }

    @Test
    void pinnedRecordingReusesDiskCacheAfterServerRestart() throws Exception {
        file();
        Files.writeString(directory.resolve(ID + ".info.json"), "{\"id\":\"" + ID
                + "\",\"title\":\"Pinned recording\",\"duration\":218}");
        var downloader = new YtDlpDownloader(DownloadConfig.defaults(), directory,
                (args, timeout, log) -> { fail("No network process needed for pinned cache"); return null; });
        var result = downloader.download(URL);
        assertEquals(ID, result.videoId());
        assertEquals(218, result.durationSeconds());
        assertEquals("Pinned recording", result.title());
    }

    @Test
    void retriesSameVideoAfter403WithoutSearchingAgain() throws Exception {
        var calls = new ArrayList<List<String>>();
        var downloader = new YtDlpDownloader(DownloadConfig.defaults(), directory, (args, timeout, log) -> {
            calls.add(List.copyOf(args));
            if (calls.size() == 1) return metadata();
            if (calls.size() == 2) return new YtDlpDownloader.ProcessResult(1, "", "ERROR: HTTP Error 403: Forbidden");
            return file();
        });
        downloader.download("Artist - Title");
        assertEquals(3, calls.size());
        assertEquals(URL, calls.get(1).getLast());
        assertEquals(URL, calls.get(2).getLast());
        assertEquals(1, calls.stream().filter(c -> c.contains("--skip-download")).count());
    }

    @Test
    void emptySearchDoesNotDownloadASubstitute() {
        int[] calls = {0};
        var downloader = new YtDlpDownloader(DownloadConfig.defaults(), directory, (args, timeout, log) -> {
            calls[0]++;
            return new YtDlpDownloader.ProcessResult(0, "{\"entries\":[]}", "");
        });
        assertThrows(IOException.class, () -> downloader.download("Unknown - Song"));
        assertEquals(1, calls[0]);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "MYSIX_FILE:missing.ogg", "MYSIX_FILE:../escape.ogg"})
    void exitZeroWithoutACompletedFileIsNotSuccess(String output) {
        var downloader = new YtDlpDownloader(DownloadConfig.defaults(), directory, (args, timeout, log) ->
                args.contains("--skip-download") ? metadata() : new YtDlpDownloader.ProcessResult(0, output, ""));
        assertThrows(IOException.class, () -> downloader.download("Artist - Title"));
    }

    @Test
    void emptyAudioIsRejected() {
        var downloader = new YtDlpDownloader(DownloadConfig.defaults(), directory, (args, timeout, log) -> {
            if (args.contains("--skip-download")) return metadata();
            var audio = directory.resolve(ID + ".ogg");
            Files.write(audio, new byte[0]);
            return new YtDlpDownloader.ProcessResult(0, "MYSIX_FILE:" + audio, "");
        });
        assertThrows(IOException.class, () -> downloader.download("Artist - Title"));
    }

    @Test
    void cancellationPropagatesInsteadOfRetrying() {
        var downloader = new YtDlpDownloader(DownloadConfig.defaults(), directory, (args, timeout, log) -> {
            throw new InterruptedException("Server stopping");
        });
        assertThrows(InterruptedException.class, () -> downloader.download("Artist - Title"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://youtu.be/dQw4w9WgXcQ"})
    void acceptsExplicitYouTubeLinks(String url) {
        assertEquals(url, YtDlpDownloader.searchTarget(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "file:///etc/passwd", "http://localhost/audio", "https://youtube.com.evil.example/test", "ytsearch99:test", "https://user@youtube.com/watch?v=x", "Artist\nTitle"})
    void rejectsInvalidRequests(String request) {
        assertThrows(IllegalArgumentException.class, () -> YtDlpDownloader.searchTarget(request));
    }

    @Test
    void shellMetacharactersRemainLiteralSearchText() {
        var request = "Artist - Title & echo $(hello)";
        assertEquals("ytsearch1:" + request, YtDlpDownloader.searchTarget(request));
    }

    @Test
    void configCreatesDefaultsAndCanLoadAnExecutablePathWithSpaces() throws Exception {
        var path = directory.resolve("config/mysix-download.json");
        assertEquals(DownloadConfig.defaults(), DownloadConfig.load(path));
        Files.writeString(path, "{\"ytDlpCommand\":[\"C:/Program Files/yt-dlp.exe\"],\"timeoutSeconds\":30,\"maxFileMiB\":10}");
        assertEquals(List.of("C:/Program Files/yt-dlp.exe"), DownloadConfig.load(path).ytDlpCommand());
    }
}
