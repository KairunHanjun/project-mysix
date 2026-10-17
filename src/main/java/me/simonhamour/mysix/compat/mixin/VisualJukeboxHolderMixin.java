package me.simonhamour.mysix.compat.mixin;

import me.simonhamour.mysix.compat.BackpackPlaybackController;
import me.simonhamour.mysix.music.OnlineJukebox;
import net.minecraft.world.item.JukeboxSongPlayer;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.tomalbrc.visualjukebox.JukeboxHolder", remap = false)
public abstract class VisualJukeboxHolderMixin {
    @Shadow @Final private JukeboxBlockEntity jukeboxBlockEntity;
    @Shadow public abstract void setStopped(boolean stopped);
    @Unique private Boolean mysix$wasPlaying;

    @Inject(method = "tick", at = @At("HEAD"))
    private void mysix$externalPresentation(CallbackInfo callback) {
        if (!BackpackPlaybackController.isOnlineDisc(jukeboxBlockEntity.getTheItem())) {
            mysix$wasPlaying = null;
            return;
        }
        boolean playing = OnlineJukebox.isPlayingAt(jukeboxBlockEntity);
        if (mysix$wasPlaying == null || mysix$wasPlaying != playing) {
            setStopped(!playing);
            mysix$wasPlaying = playing;
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/JukeboxSongPlayer;isPlaying()Z"))
    private boolean mysix$rotateExternalDisc(JukeboxSongPlayer player) {
        return player.isPlaying() || OnlineJukebox.isPlayingAt(jukeboxBlockEntity);
    }
}
