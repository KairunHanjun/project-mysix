package me.simonhamour.mysix.mixin;

import me.simonhamour.mysix.compat.PlaylistCompat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = JukeboxBlock.class, priority = 1100)
public abstract class PlaylistTickerMixin {
    @Inject(method = "getTicker", at = @At("HEAD"), cancellable = true)
    private <T extends BlockEntity> void mysix$playlistTicker(Level level, BlockState state,
            BlockEntityType<T> type, CallbackInfoReturnable<BlockEntityTicker<T>> result) {
        if (!level.isClientSide() && type == BlockEntityTypes.JUKEBOX
                && net.minecraftforge.fml.ModList.isLoaded("jukebox_playlists")) {
            result.setReturnValue((world, pos, blockState, entity) -> PlaylistCompat.tick((JukeboxBlockEntity) entity));
        }
    }
}
