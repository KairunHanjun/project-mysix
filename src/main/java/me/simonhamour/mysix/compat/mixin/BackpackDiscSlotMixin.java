package me.simonhamour.mysix.compat.mixin;

import me.simonhamour.mysix.item.MysixCdItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.tiviacz.travelersbackpack.inventory.upgrades.jukebox.JukeboxUpgrade$2", remap = false)
public abstract class BackpackDiscSlotMixin {
    @Inject(method = "isItemValid", at = @At("HEAD"), cancellable = true)
    private void mysix$acceptCd(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> result) {
        if (slot == 0 && stack.getItem() instanceof MysixCdItem) result.setReturnValue(true);
    }
}
