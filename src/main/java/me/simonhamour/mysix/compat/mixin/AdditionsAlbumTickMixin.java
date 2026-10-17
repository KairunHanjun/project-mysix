package me.simonhamour.mysix.compat.mixin;

import me.simonhamour.mysix.compat.AdditionalAdditionsCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = JukeboxBlockEntity.class, priority = 500)
public abstract class AdditionsAlbumTickMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void mysix$mixedAlbum(Level level, BlockPos pos, BlockState state,
            JukeboxBlockEntity box, CallbackInfo callback) {
        if (AdditionalAdditionsCompat.tickAlbum(level, box)) callback.cancel();
    }
}
