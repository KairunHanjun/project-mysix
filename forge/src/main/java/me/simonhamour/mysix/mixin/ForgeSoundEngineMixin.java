package me.simonhamour.mysix.mixin;

import java.util.concurrent.CompletableFuture;
import me.simonhamour.mysix.client.DownloadedSound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Forge 66.0.13 exposes SoundInstance.getStream but SoundEngine still calls the library directly. */
@Mixin(SoundEngine.class)
public abstract class ForgeSoundEngineMixin {
    @Redirect(method = "play", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/sounds/SoundBufferLibrary;getStream(Lnet/minecraft/resources/Identifier;Z)Ljava/util/concurrent/CompletableFuture;"), require = 0)
    private CompletableFuture<AudioStream> mysix$openDownloadedAudio(SoundBufferLibrary library,
            Identifier path, boolean looping, SoundInstance original) {
        if (original instanceof DownloadedSound downloaded) return downloaded.getAudioStream(library, path, looping);
        return library.getStream(path, looping);
    }
}
