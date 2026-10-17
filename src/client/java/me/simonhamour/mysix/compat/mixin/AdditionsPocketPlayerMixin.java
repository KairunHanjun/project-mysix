package me.simonhamour.mysix.compat.mixin;

import me.simonhamour.mysix.compat.AdditionalAdditionsApi;
import me.simonhamour.mysix.compat.AdditionalAdditionsCompat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "one.dqu.additionaladditions.feature.pocket_jukebox.PocketJukeboxPlayer", remap = false)
public abstract class AdditionsPocketPlayerMixin {
    @Shadow public abstract void stop();
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void mysix$serverOwnsPlaylist(@Coerce Object album, Player player, ItemStack pocket, CallbackInfo callback) {
        if (AdditionalAdditionsCompat.containsExternal(AdditionalAdditionsApi.stored(pocket))) {
            stop();
            callback.cancel();
        }
    }
}
