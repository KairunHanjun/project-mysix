package me.simonhamour.mysix.mixin;

import me.simonhamour.mysix.music.CdSongSelection;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {
    protected AnvilMenuMixin(MenuType<?> type, int id, Inventory inventory,
            ContainerLevelAccess access, ItemCombinerMenuSlotDefinition slots) {
        super(type, id, inventory, access, slots);
    }

    @Inject(method = "createResult", at = @At("RETURN"))
    private void mysix$selectSongAfterRename(CallbackInfo info) {
        var menu = (AnvilMenu) (Object) this;
        var result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        if (!result.isEmpty()) CdSongSelection.update(result, player.registryAccess());
    }
}
