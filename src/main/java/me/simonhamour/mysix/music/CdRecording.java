package me.simonhamour.mysix.music;

import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Pin a successful search to this CD. A rename automatically invalidates the pin. */
public final class CdRecording {
    private CdRecording() {}

    public static Optional<String> pinnedId(ItemStack disc) {
        if (disc.getCustomName() == null) return Optional.empty();
        var tag = disc.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getCompoundOrEmpty("mysix_recording");
        if (!disc.getCustomName().getString().equals(tag.getStringOr("query", ""))) return Optional.empty();
        return tag.getString("video_id").filter(id -> id.matches("[A-Za-z0-9_-]{11}"));
    }

    public static void remember(ItemStack disc, String query, String videoId) {
        if (!videoId.matches("[A-Za-z0-9_-]{11}")) throw new IllegalArgumentException("Invalid video ID");
        var root = disc.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var recording = new CompoundTag();
        recording.putString("query", query);
        recording.putString("video_id", videoId);
        root.put("mysix_recording", recording);
        disc.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }
}
