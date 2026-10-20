package me.simonhamour.mysix.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import org.junit.jupiter.api.Test;

class ForgeAudioTest {
    @Test void forgeStreamHookDecodesActualMonoVorbisWithoutSoundHardware() throws Exception {
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("/assets/mysix/sounds/download_test.ogg")) {
            assertNotNull(input); bytes = input.readAllBytes();
        }
        var downloaded = DownloadedSound.personal(bytes, 0, message -> fail(message));
        SoundInstance forgeSound = downloaded;
        assertEquals(SoundSource.RECORDS, forgeSound.getSource());
        try (var audio = forgeSound.getStream(null, forgeSound.getSound(), false).get(10, TimeUnit.SECONDS)) {
            assertEquals(1, audio.getFormat().getChannels());
            assertTrue(audio.read(4096).hasRemaining());
        }
    }
}
