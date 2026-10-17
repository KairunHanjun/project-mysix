package me.simonhamour.mysix.compat.mixin;

import me.simonhamour.mysix.compat.AdditionalAdditionsCompat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "one.dqu.additionaladditions.feature.pocket_jukebox.PocketJukeboxItem", remap = false)
public abstract class AdditionsPocketItemMixin {
    @Inject(method = "insertDisc", at = @At("RETURN"))
    private void mysix$startPocket(ItemStack pocket, ItemStack disc, Player player, CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValue() && !player.level().isClientSide()) AdditionalAdditionsCompat.pocketInserted(pocket, player);
    }
}
