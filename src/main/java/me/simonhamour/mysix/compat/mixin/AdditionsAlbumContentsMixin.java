package me.simonhamour.mysix.compat.mixin;

import me.simonhamour.mysix.item.MysixCdItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "one.dqu.additionaladditions.feature.album.AlbumContents", remap = false)
public abstract class AdditionsAlbumContentsMixin {
    @Inject(method = "isValidItem", at = @At("HEAD"), cancellable = true)
    private static void mysix$acceptCd(ItemStack disc, CallbackInfoReturnable<Boolean> result) {
        if (disc != null && !disc.isEmpty() && disc.getItem() instanceof MysixCdItem) result.setReturnValue(true);
    }
}
