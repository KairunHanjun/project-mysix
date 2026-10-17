package me.simonhamour.mysix.music;

import me.simonhamour.mysix.item.MysixCdItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.phys.BlockHitResult;

public final class CdJukeboxInteraction {
    private CdJukeboxInteraction() {}

    public static InteractionResult use(Player player, Level level,
            InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        var stack = player.getItemInHand(hand);
        var pos = hit.getBlockPos();
        var state = level.getBlockState(pos);
        if (!(stack.getItem() instanceof MysixCdItem) || !state.is(Blocks.JUKEBOX)
                || state.getValue(JukeboxBlock.HAS_RECORD)) return InteractionResult.PASS;
        if (CdSongSelection.update(stack, level.registryAccess())) return InteractionResult.PASS;

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && OnlineJukebox.tryInsert(serverPlayer, serverLevel, pos, stack)) {
            return InteractionResult.SUCCESS;
        }

        // SUCCESS on the client allows Fabric to send the interaction to the
        // server. Only the server transfers and ejects the actual item.
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox
                && jukebox.getTheItem().isEmpty() && !stack.isEmpty()) {
            // Move, never copy: rejection must not manufacture an extra CD,
            // including when the inserting player is in Creative mode.
            var rejected = stack.split(1);
            var message = Component.translatable("message.mysix.unknown_song", rejected.getHoverName());
            jukebox.setTheItem(rejected);
            jukebox.popOutTheItem();
            player.sendOverlayMessage(message);
        }
        return InteractionResult.SUCCESS;
    }
}
