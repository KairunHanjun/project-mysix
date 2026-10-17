package me.simonhamour.mysix.compat.mixin;

import me.simonhamour.mysix.compat.BackpackPlaybackController;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.tiviacz.travelersbackpack.inventory.upgrades.jukebox.JukeboxWidget", remap = false)
public abstract class BackpackWidgetMixin {
    @Inject(method = "getFromDisk", at = @At("HEAD"), cancellable = true)
    private void mysix$externalAudio(ItemStack stack, CallbackInfoReturnable<JukeboxSong> result) {
        if (BackpackPlaybackController.isOnlineDisc(stack)) result.setReturnValue(null);
    }
}
