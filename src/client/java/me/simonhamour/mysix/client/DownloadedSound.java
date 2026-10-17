package me.simonhamour.mysix.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.io.InputStream;
import net.minecraft.core.BlockPos;
import me.simonhamour.mysix.Mysix;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;

/** Streams the local file through Minecraft's audio engine, without a resource reload. */
public final class DownloadedSound extends AbstractSoundInstance {
    @FunctionalInterface
    private interface InputOpener { InputStream open() throws java.io.IOException; }
    private final InputOpener inputOpener;
    private final Consumer<String> onError;
    private double offsetSeconds;

    public DownloadedSound(Path file, Consumer<String> onError) {
        this(() -> Files.newInputStream(file), onError);
    }

    private DownloadedSound(InputOpener inputOpener, Consumer<String> onError) {
        super(Mysix.id("download_test"), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.inputOpener = inputOpener;
        this.onError = onError;
        volume = 1.0f;
        pitch = 1.0f;
        relative = true;
        attenuation = SoundInstance.Attenuation.NONE;
        // This audio comes from disk through getAudioStream, not a resource pack.
        // Supply both descriptors so SoundEngine can resolve it before opening the file.
        sound = new Sound(getIdentifier(), ConstantFloat.of(1.0f), ConstantFloat.of(1.0f),
                1, Sound.Type.FILE, true, false, 16);
        soundEvent = new WeighedSoundEvents(getIdentifier(), null);
        soundEvent.addSound(sound);
    }

    public static DownloadedSound jukebox(byte[] audio, BlockPos pos, double offsetSeconds,
            Consumer<String> onError) {
        var instance = new DownloadedSound(() -> new java.io.ByteArrayInputStream(audio), onError);
        instance.relative = false;
        instance.x = pos.getX() + 0.5;
        instance.y = pos.getY() + 0.5;
        instance.z = pos.getZ() + 0.5;
        instance.attenuation = SoundInstance.Attenuation.LINEAR;
        instance.sound = new Sound(instance.getIdentifier(), ConstantFloat.of(1.0f), ConstantFloat.of(1.0f),
                1, Sound.Type.FILE, true, false, 64);
        instance.soundEvent = new WeighedSoundEvents(instance.getIdentifier(), null);
        instance.soundEvent.addSound(instance.sound);
        instance.offsetSeconds = Math.max(0, offsetSeconds);
        return instance;
    }

    public static DownloadedSound personal(byte[] audio, double offsetSeconds, Consumer<String> onError) {
        var instance = new DownloadedSound(() -> new java.io.ByteArrayInputStream(audio), onError);
        instance.offsetSeconds = Math.max(0, offsetSeconds);
        return instance;
    }

    @Override
    public WeighedSoundEvents getOrResolve(SoundManager manager) {
        return soundEvent;
    }

    @Override
    public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary library,
            Identifier id, boolean repeatInstantly) {
        return CompletableFuture.supplyAsync(() -> {
            java.io.InputStream input = null;
            try {
                input = inputOpener.open();
                var stream = new JOrbisAudioStream(input);
                var format = stream.getFormat();
                long frames = (long) (offsetSeconds * format.getSampleRate());
                long remaining = frames * format.getFrameSize();
                while (remaining > 0) {
                    var pcm = stream.read((int) Math.min(64 * 1024, remaining));
                    if (!pcm.hasRemaining()) break;
                    remaining -= pcm.remaining();
                }
                return (AudioStream) stream;
            } catch (Exception error) {
                if (input != null) try { input.close(); } catch (java.io.IOException ignored) {}
                onError.accept(error.getMessage());
                throw new CompletionException(error);
            }
        });
    }
}
