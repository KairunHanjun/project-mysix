package me.simonhamour.mysix.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import me.simonhamour.mysix.Mysix;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;

/** Development-only client data launch: real mixin application, no window or audio device. */
public final class ForgeSoundPhysicsChecks {
    private ForgeSoundPhysicsChecks() {}
    public static void run() {
        try {
            require(net.minecraftforge.fml.ModList.isLoaded("sound_physics_remastered"), "Sound Physics must be installed for this check");
            // Loading these through ModLauncher's client classloader applies and validates both mods' mixins.
            var loader = ForgeSoundPhysicsChecks.class.getClassLoader();
            var engine = Class.forName("net.minecraft.client.sounds.SoundEngine", false, loader);
            var channel = Class.forName("com.mojang.blaze3d.audio.Channel", false, loader);
            require(hasMethod(engine, "mysix$openDownloadedAudio"), "Mysix stream hook did not apply");
            require(hasMethod(engine, "tickNonPaused"), "Sound Physics engine hook did not apply");
            require(hasMethod(channel, "$setSelfPosition"), "Sound Physics channel hook did not apply");
            var accessor = Class.forName("com.sonicether.soundphysics.mixin.ChannelAccessor", false, loader);
            require(accessor.isAssignableFrom(channel), "Sound Physics channel accessor did not apply");

            byte[] bytes = Files.readAllBytes(Path.of(System.getProperty("mysix.audioFixture")));
            var sound = DownloadedSound.jukebox(bytes, new BlockPos(5, 70, -8), 0, message -> {
                throw new IllegalStateException(message);
            });
            require(sound.getSource() == SoundSource.RECORDS && !sound.isRelative(), "Jukebox must remain a world-space record sound");
            require(sound.getX() == 5.5 && sound.getY() == 70.5 && sound.getZ() == -7.5, "Sound Physics must receive block-center coordinates");
            try (var stream = sound.getStream(null, sound.getSound(), false).get(10, TimeUnit.SECONDS)) {
                require(stream.getFormat().getChannels() == 1 && stream.read(4096).hasRemaining(), "Positional audio must decode to mono PCM");
            }
            var personal = DownloadedSound.personal(bytes, 0, message -> { throw new IllegalStateException(message); });
            require(personal.isRelative() && personal.getX() == 0 && personal.getY() == 0 && personal.getZ() == 0,
                    "Personal playback must remain listener-relative");
            var report = Path.of(System.getProperty("mysix.audioCheckReport"));
            Files.writeString(report, "PASS: Sound Physics + Mysix client mixins applied; positional mono Ogg decoded; personal sound remains relative.\n");
            Mysix.LOGGER.info("Mysix Sound Physics client transformation and decoder checks passed");
        } catch (Exception error) {
            throw new IllegalStateException("Mysix Sound Physics compatibility check failed", error);
        }
    }
    private static boolean hasMethod(Class<?> type, String fragment) {
        return Arrays.stream(type.getDeclaredMethods()).anyMatch(method -> method.getName().contains(fragment));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
