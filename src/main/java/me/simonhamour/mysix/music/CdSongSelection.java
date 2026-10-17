package me.simonhamour.mysix.music;

import me.simonhamour.mysix.item.MysixCdItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.item.JukeboxSongs;

public final class CdSongSelection {
    private CdSongSelection() {}

    /** Returns false for unknown names, missing songs, and non-Mysix items. */
    public static boolean update(ItemStack stack, HolderLookup.Provider registries) {
        if (!(stack.getItem() instanceof MysixCdItem)) return false;
        var name = stack.getCustomName();
        var songKey = name == null ? java.util.Optional.of(JukeboxSongs.CAT)
                : VanillaSongCatalog.find(name.getString())
                        .map(id -> ResourceKey.create(Registries.JUKEBOX_SONG, id));
        var song = songKey.flatMap(key -> registries.lookup(Registries.JUKEBOX_SONG)
                .flatMap(registry -> registry.get(key)));
        if (song.isEmpty()) {
            // Erase a previous selection, rather than playing stale/default audio.
            stack.remove(DataComponents.JUKEBOX_PLAYABLE);
            return false;
        }
        stack.set(DataComponents.JUKEBOX_PLAYABLE, new JukeboxPlayable(song.get()));
        return true;
    }
}
