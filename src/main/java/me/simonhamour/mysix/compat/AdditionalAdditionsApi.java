package me.simonhamour.mysix.compat;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/** Optional public API boundary; all symbols are verified against 10.0.14. */
public final class AdditionalAdditionsApi {
    private final Class<?> pocketItem;
    private final Field albumComponent, pocketConfig;
    private final Method items, setPlaying, setTrack;
    private final Constructor<?> contents;

    public AdditionalAdditionsApi() throws ReflectiveOperationException {
        String root = "one.dqu.additionaladditions.";
        var loader = getClass().getClassLoader();
        pocketItem = Class.forName(root + "feature.pocket_jukebox.PocketJukeboxItem", false, loader);
        var album = Class.forName(root + "feature.album.AlbumContents", false, loader);
        var extension = Class.forName(root + "feature.album.AlbumJukeboxExtension", false, loader);
        var misc = Class.forName(root + "registry.AAMisc", false, loader);
        var config = Class.forName(root + "config.Config", false, loader);
        albumComponent = misc.getField("ALBUM_CONTENTS_COMPONENT");
        pocketConfig = config.getField("POCKET_JUKEBOX");
        items = album.getMethod("items"); contents = album.getConstructor(List.class);
        setPlaying = extension.getMethod("additionaladditions$setPlaying", boolean.class);
        setTrack = extension.getMethod("additionaladditions$setTrack", int.class);
    }

    @SuppressWarnings("unchecked")
    private DataComponentType<Object> albumType() {
        try { return (DataComponentType<Object>) ((Supplier<?>) albumComponent.get(null)).get(); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    public boolean isAlbum(ItemStack stack) { return stack.has(albumType()); }
    @SuppressWarnings("unchecked")
    public List<ItemStack> tracks(ItemStack stack) {
        Object album = stack.get(albumType());
        if (album == null) return stack.isEmpty() ? List.of() : List.of(stack);
        try { return (List<ItemStack>) items.invoke(album); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    public void writeTrack(ItemStack album, int index, ItemStack disc) {
        var list = new ArrayList<>(tracks(album)); list.set(index, disc.copy());
        try { album.set(albumType(), contents.newInstance(list)); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    public boolean containsExternal(ItemStack stack) {
        return tracks(stack).stream().anyMatch(BackpackPlaybackController::isOnlineDisc);
    }
    public boolean isPocket(ItemStack stack) { return pocketItem.isInstance(stack.getItem()); }
    public boolean pocketEnabled() {
        try {
            Object entry = pocketConfig.get(null);
            Object value = entry.getClass().getMethod("get").invoke(entry);
            return (boolean) value.getClass().getMethod("enabled").invoke(value);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    public void albumState(Object jukebox, int track) {
        try {
            setPlaying.invoke(jukebox, false); // Our sequencer owns mixed albums.
            setTrack.invoke(jukebox, track);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    public static ItemStack stored(ItemStack pocket) {
        return pocket.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyOne();
    }
    public static void store(ItemStack pocket, ItemStack disc) {
        pocket.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(disc.copy())));
    }
}
