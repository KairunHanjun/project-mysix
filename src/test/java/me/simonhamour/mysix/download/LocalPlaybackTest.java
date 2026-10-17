package me.simonhamour.mysix.download;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import me.simonhamour.mysix.client.DownloadedSound;
import net.minecraft.sounds.SoundSource;
import net.minecraft.client.sounds.SoundManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocalPlaybackTest {
    @TempDir Path directory;

    private Path audio(String id) throws Exception {
        var path = directory.resolve(id + ".ogg");
        try (var source = getClass().getResourceAsStream("/assets/mysix/sounds/download_test.ogg")) {
            assertNotNull(source);
            Files.copy(source, path);
        }
        return path;
    }

    @Test
    void selectsNewestCompletedOggAndIgnoresPartialDownloads() throws Exception {
        var old = audio("aaaaaaaaaaa");
        var latest = audio("bbbbbbbbbbb");
        Files.setLastModifiedTime(old, FileTime.fromMillis(1000));
        Files.setLastModifiedTime(latest, FileTime.fromMillis(2000));
        Files.writeString(directory.resolve("ccccccccccc.ogg.part"), "incomplete");
        assertEquals(latest, LocalAudioFiles.find(directory, null));
        assertEquals(old, LocalAudioFiles.find(directory, "aaaaaaaaaaa"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../escape", "../../aaaaaaaaaaa", "test", "https://youtube.com", "aaaaaaaaaaa.ogg"})
    void rejectsPathsInsteadOfVideoIds(String id) {
        assertThrows(java.io.IOException.class, () -> LocalAudioFiles.find(directory, id));
    }

    @Test
    void rejectsMissingAndRenamedNonOggFiles() throws Exception {
        assertThrows(java.io.IOException.class, () -> LocalAudioFiles.find(directory, null));
        var fake = directory.resolve("aaaaaaaaaaa.ogg");
        Files.writeString(fake, "This is not Vorbis audio, regardless of its filename");
        assertThrows(java.io.IOException.class, () -> LocalAudioFiles.find(directory, "aaaaaaaaaaa"));
    }

    @Test
    void minecraftDecoderReadsPcmWithoutAnAudioDevice() throws Exception {
        var file = audio("aaaaaaaaaaa");
        var error = new AtomicReference<String>();
        var sound = new DownloadedSound(file, error::set);
        assertEquals(SoundSource.RECORDS, sound.getSource());
        assertTrue(sound.isRelative());
        assertFalse(sound.isLooping());
        try (var stream = sound.getAudioStream(null, null, false).get(10, TimeUnit.SECONDS)) {
            assertEquals(1, stream.getFormat().getChannels());
            assertTrue(stream.read(4096).hasRemaining());
        }
        assertNull(error.get());
    }

    @Test
    void resolvesStreamingSoundWhenResourceRegistryIsEmpty() throws Exception {
        var manager = mock(SoundManager.class);
        var file = audio("aaaaaaaaaaa");
        var sound = new DownloadedSound(file, message -> fail(message));
        assertNull(manager.getSoundEvent(sound.getIdentifier()));
        clearInvocations(manager);

        // These are the resolution gates SoundEngine.play checks before opening audio.
        var event = sound.getOrResolve(manager);
        assertNotNull(event);
        assertSame(event, sound.getSoundEvent());
        assertNotNull(sound.getSound());
        assertNotSame(SoundManager.EMPTY_SOUND, sound.getSound());
        assertNotSame(SoundManager.INTENTIONALLY_EMPTY_SOUND, sound.getSound());
        assertEquals(1, event.getWeight());
        assertTrue(sound.getSound().shouldStream());
        assertFalse(sound.getSound().shouldPreload());
        assertEquals(1.0f, sound.getVolume());
        assertEquals(1.0f, sound.getPitch());
        assertSame(event, sound.getOrResolve(manager));
        verifyNoInteractions(manager);

        // Resolution must still lead to real PCM from our external file.
        try (var stream = sound.getAudioStream(null, sound.getSound().getPath(), false)
                .get(10, TimeUnit.SECONDS)) {
            assertTrue(stream.read(4096).hasRemaining());
        }
    }

    @Test
    void jukeboxPlaybackIsPositionalAndSeeksPastElapsedFrames() throws Exception {
        byte[] audio = Files.readAllBytes(audio("aaaaaaaaaaa"));
        var pos = new net.minecraft.core.BlockPos(10, 64, -5);
        var sound = DownloadedSound.jukebox(audio, pos, 0.025, message -> fail(message));
        assertFalse(sound.isRelative());
        assertEquals(10.5, sound.getX());
        assertEquals(64.5, sound.getY());
        assertEquals(-4.5, sound.getZ());
        assertEquals(net.minecraft.client.resources.sounds.SoundInstance.Attenuation.LINEAR, sound.getAttenuation());
        assertEquals(64, sound.getSound().getAttenuationDistance());
        try (var stream = sound.getAudioStream(null, null, false).get(10, TimeUnit.SECONDS)) {
            assertTrue(stream.read(4096).hasRemaining());
        }
    }

    @Test
    void personalBackpackAudioStaysRelativeToWearerAndDecodesReceivedBytes() throws Exception {
        var sound = DownloadedSound.personal(Files.readAllBytes(audio("aaaaaaaaaaa")), 0,
                message -> fail(message));
        assertTrue(sound.isRelative());
        assertEquals(net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE, sound.getAttenuation());
        assertEquals(SoundSource.RECORDS, sound.getSource());
        assertNotNull(sound.getOrResolve(null));
        try (var stream = sound.getAudioStream(null, null, false).get(10, TimeUnit.SECONDS)) {
            assertTrue(stream.read(4096).hasRemaining());
        }
    }

    @Test
    void decoderReportsMissingFilesAsFailures() {
        var error = new AtomicReference<String>();
        var sound = new DownloadedSound(directory.resolve("missing.ogg"), error::set);
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> sound.getAudioStream(null, null, false).get(10, TimeUnit.SECONDS));
        assertNotNull(error.get());
    }
}
