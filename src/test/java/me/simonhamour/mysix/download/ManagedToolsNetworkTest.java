package me.simonhamour.mysix.download;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Tag("network")
@EnabledIfSystemProperty(named = "mysix.setupNetworkTests", matches = "true")
class ManagedToolsNetworkTest {
    @Test void installsPinnedToolsStartsAnonymousHelperAndDownloadsPlayableOgg() throws Exception {
        Path base = Path.of(System.getProperty("mysix.setupTestDirectory")); Files.createDirectories(base);
        Path game = Files.createTempDirectory(base, "fresh-");
        try (var tools = new ManagedTools(game)) {
            System.out.println("MYSIX_SETUP_PLATFORM=" + tools.platform());
            tools.install(System.out::println);
            assertTrue(tools.installed());
            tools.ensureProvider(); tools.ensureProvider();
            var service = tools.downloader(DownloadConfig.defaults(), game.resolve("downloads-" + java.util.UUID.randomUUID()));
            var result = service.download(System.getProperty("mysix.setupTestSong", "Dhyo Haw - Ada Aku Disini"));
            assertTrue(result.bytes() > 1024); LocalAudioFiles.requireVorbis(result.file());
            var sound = new me.simonhamour.mysix.client.DownloadedSound(result.file(), message -> fail(message));
            try (var audio = sound.getAudioStream(null, null, false).get()) {
                assertEquals(1, audio.getFormat().getChannels());
                assertTrue(audio.read(4096).hasRemaining());
            }
            System.out.println("MYSIX_ANONYMOUS_AUDIO=" + result.file() + " BYTES=" + result.bytes());
            var cached = service.download("https://www.youtube.com/watch?v=" + result.videoId());
            assertEquals(result.file(), cached.file());
        }
    }
}
