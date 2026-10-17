package me.simonhamour.mysix.download;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Opt-in network test. Uses the same Java service as the in-game command. */
@Tag("network")
@EnabledIfSystemProperty(named = "mysix.networkTests", matches = "true")
class YtDlpNetworkTest {
    @ParameterizedTest
    @ValueSource(strings = {"Dhyo Haw - Ada Aku Disini", "https://www.youtube.com/watch?v=dQw4w9WgXcQ"})
    void downloadsActualAudioAndLeavesAFileOnDisk(String request) throws Exception {
        var directory = Path.of(System.getProperty("mysix.integration.directory"));
        var result = new YtDlpDownloader(DownloadConfig.defaults(), directory).download(request);
        assertTrue(Files.isRegularFile(result.file()));
        assertTrue(result.bytes() > 1024);
        assertEquals(result.bytes(), Files.size(result.file()));
        assertTrue(result.file().toString().endsWith(".ogg"));
        LocalAudioFiles.requireVorbis(result.file());
        var sound = new me.simonhamour.mysix.client.DownloadedSound(result.file(), message -> {});
        try (var audio = sound.getAudioStream(null, null, false).get()) {
            assertEquals(1, audio.getFormat().getChannels());
            assertTrue(audio.read(4096).hasRemaining(), "Minecraft must decode actual PCM audio");
        }
        byte[] original = Files.readAllBytes(result.file());
        var transfer = new me.simonhamour.mysix.network.AudioTransfer(original.length);
        for (int offset = 0; offset < original.length;) {
            int end = Math.min(original.length, offset + me.simonhamour.mysix.network.JukeboxAudioPayload.CHUNK_BYTES);
            transfer.append(offset, java.util.Arrays.copyOfRange(original, offset, end));
            offset = end;
        }
        byte[] received = transfer.finish();
        assertArrayEquals(original, received);
        var jukebox = me.simonhamour.mysix.client.DownloadedSound.jukebox(received,
                new net.minecraft.core.BlockPos(10, 64, 10), 2, message -> fail(message));
        assertNotNull(jukebox.getOrResolve(null));
        assertFalse(jukebox.isRelative());
        try (var audio = jukebox.getAudioStream(null, null, false).get()) {
            assertTrue(audio.read(4096).hasRemaining(), "Transferred jukebox audio must decode after seeking");
        }
        System.out.println("MYSIX_VERIFIED_FILE=" + result.file() + " BYTES=" + result.bytes()
                + " TITLE=" + result.title());
    }
}
