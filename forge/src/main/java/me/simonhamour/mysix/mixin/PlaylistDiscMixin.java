package me.simonhamour.mysix.mixin;

import me.simonhamour.mysix.item.MysixCdItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.eyecrasher.jukeboxplaylists.Platform", remap = false)
public abstract class PlaylistDiscMixin {
    @Inject(method = "isDisc", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mysix$recognizeDisc(ItemStack stack, CallbackInfoReturnable<Boolean> result) {
        if (!stack.isEmpty() && stack.getItem() instanceof MysixCdItem) result.setReturnValue(true);
    }
}
