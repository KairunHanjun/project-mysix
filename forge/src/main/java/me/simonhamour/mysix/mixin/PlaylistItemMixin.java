package me.simonhamour.mysix.mixin;

import me.simonhamour.mysix.compat.PlaylistCompat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JukeboxBlockEntity.class)
public abstract class PlaylistItemMixin {
    @Inject(method = "setTheItem", at = @At("TAIL"))
    private void mysix$playlistChanged(ItemStack item, CallbackInfo ci) {
        var box = (JukeboxBlockEntity) (Object) this;
        if (box.getLevel() != null && !box.getLevel().isClientSide()
                && net.minecraftforge.fml.ModList.isLoaded("jukebox_playlists")) PlaylistCompat.changed(box);
    }
}
