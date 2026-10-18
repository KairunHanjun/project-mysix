package me.simonhamour.mysix.compat;

import java.util.*;
import me.simonhamour.mysix.Mysix;
import me.simonhamour.mysix.music.CdSongSelection;
import me.simonhamour.mysix.music.OnlineJukebox;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;

public final class AdditionalAdditionsCompat {
    private static AdditionalAdditionsApi api;
    private static final Map<JukeboxBlockEntity, BlockAlbum> albums = new IdentityHashMap<>();
    private static final Map<UUID, PocketAlbum> pockets = new HashMap<>();
    private static final Map<UUID, String> selectedPocket = new HashMap<>();
    private AdditionalAdditionsCompat() {}

    public static boolean supported() {
        return FabricLoader.getInstance().getModContainer("additionaladditions")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString().equals("10.0.14")).orElse(false);
    }
    public static void initialize() {
        if (!supported()) return;
        try { api = new AdditionalAdditionsApi(); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Additional Additions API mismatch", error); }
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            var held = player.getItemInHand(hand);
            var state = level.getBlockState(hit.getBlockPos());
            if (player.isSpectator() || !state.is(Blocks.JUKEBOX) || state.getValue(JukeboxBlock.HAS_RECORD)
                    || !api.isAlbum(held) || !api.containsExternal(held)) return InteractionResult.PASS;
            if (!level.isClientSide() && player instanceof ServerPlayer owner
                    && level.getBlockEntity(hit.getBlockPos()) instanceof JukeboxBlockEntity box && box.getTheItem().isEmpty()) {
                var album = held.split(1);
                box.setTheItem(album);
                api.albumState(box, -1);
                albums.put(box, new BlockAlbum(owner, (ServerLevel) level, box, album));
            }
            return InteractionResult.SUCCESS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            albums.values().removeIf(album -> {
                if (album.active()) return false;
                album.playlist.cancel(); return true;
            });
            var present = new HashSet<UUID>();
            for (var player : server.getPlayerList().getPlayers()) {
                discoverCreativePocket(player);
                var device = findPocket(player, null);
                if (device.isEmpty()) continue;
                present.add(player.getUUID());
                String epoch = epoch(device.get());
                var old = pockets.get(player.getUUID());
                if (old == null || !old.epoch.equals(epoch)) {
                    if (old != null) old.playlist.cancel();
                    old = new PocketAlbum(player, epoch, AdditionalAdditionsApi.stored(device.get()));
                    pockets.put(player.getUUID(), old);
                }
                old.playlist.tick();
            }
            pockets.entrySet().removeIf(entry -> {
                if (present.contains(entry.getKey())) return false;
                entry.getValue().playlist.cancel(); return true;
            });
            selectedPocket.keySet().retainAll(server.getPlayerList().getPlayers().stream()
                    .map(ServerPlayer::getUUID).toList());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            albums.values().forEach(album -> album.playlist.cancel()); albums.clear();
            pockets.values().forEach(album -> album.playlist.cancel()); pockets.clear();
            selectedPocket.clear();
        });
    }

    /** Called by a low-priority HEAD hook so the upstream album-tail hook cannot skip external tracks. */
    public static boolean tickAlbum(Level level, JukeboxBlockEntity box) {
        if (api == null || !api.isAlbum(box.getTheItem()) || !api.containsExternal(box.getTheItem())) return false;
        if (level.isClientSide()) return true;
        var playback = albums.get(box);
        api.albumState(box, playback == null ? -1 : playback.playlist.index());
        if (playback != null) {
            box.getSongPlayer().tick(level, box.getBlockState());
            if (playback.nativeTrack != null && !box.getSongPlayer().isPlaying()) {
                playback.nativeTrack.finished(); playback.nativeTrack = null;
            }
            playback.playlist.tick();
        }
        return true;
    }

    public static boolean containsExternal(ItemStack media) { return api != null && api.containsExternal(media); }

    public static void pocketInserted(ItemStack pocket, net.minecraft.world.entity.player.Player owner) {
        if (api == null) return;
        if (!api.containsExternal(AdditionalAdditionsApi.stored(pocket))) {
            selectedPocket.put(owner.getUUID(), "");
            return;
        }
        var data = pocket.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putString("mysix_pocket_epoch", UUID.randomUUID().toString());
        pocket.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        selectedPocket.put(owner.getUUID(), epoch(pocket));
    }
    private static String epoch(ItemStack pocket) {
        return pocket.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .getStringOr("mysix_pocket_epoch", "");
    }
    /** Creative slot updates carry client-built contents without calling server insertDisc. */
    private static void discoverCreativePocket(ServerPlayer player) {
        if (!player.isAlive() || !api.pocketEnabled()) return;
        String selected = null;
        for (var stack : pocketCandidates(player)) {
            if (!api.isPocket(stack) || !epoch(stack).isEmpty()
                    || !api.containsExternal(AdditionalAdditionsApi.stored(stack))) continue;
            pocketInserted(stack, player);
            if (selected == null) selected = epoch(stack);
        }
        if (selected != null) {
            selectedPocket.put(player.getUUID(), selected);
            player.getInventory().setChanged();
        }
    }
    private static List<ItemStack> pocketCandidates(ServerPlayer player) {
        var candidates = new ArrayList<ItemStack>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) candidates.add(player.getInventory().getItem(i));
        candidates.add(player.containerMenu.getCarried());
        return candidates;
    }
    private static Optional<ItemStack> findPocket(ServerPlayer player, String epoch) {
        if (!player.isAlive() || !api.pocketEnabled()) return Optional.empty();
        String selected = selectedPocket.get(player.getUUID());
        if ("".equals(selected) || (epoch != null && selected != null && !epoch.equals(selected))) return Optional.empty();
        return pocketCandidates(player).stream().filter(stack -> api.isPocket(stack) && !epoch(stack).isEmpty()
                && (epoch == null || epoch.equals(epoch(stack)))
                && (selected == null || selected.equals(epoch(stack)))
                && api.containsExternal(AdditionalAdditionsApi.stored(stack))).findFirst();
    }

    private static OnlineJukebox.PersonalSource source(ItemStack disc, DiscPlaylist.Track track,
            boolean personal, ServerLevel level, BlockPos pos) {
        return new OnlineJukebox.PersonalSource() {
            public ItemStack disc() { return disc; }
            public boolean isActive() { return track.current(); }
            public void save() { track.save(disc); }
            public void stop() { track.cancel(); }
            public void finished() { track.finished(); }
            public void failed() { track.failed(); }
            public boolean personal() { return personal; }
            public ServerLevel level(ServerPlayer player) { return level; }
            public BlockPos position(ServerPlayer player) { return pos; }
            public boolean needsDuration() { return true; }
        };
    }

    private static final class BlockAlbum implements DiscPlaylist.Media {
        final ServerPlayer owner;
        final ServerLevel level;
        final JukeboxBlockEntity box;
        final ItemStack album;
        final DiscPlaylist playlist;
        DiscPlaylist.Track nativeTrack;
        BlockAlbum(ServerPlayer owner, ServerLevel level, JukeboxBlockEntity box, ItemStack album) {
            this.owner = owner; this.level = level; this.box = box; this.album = album;
            playlist = new DiscPlaylist(this, (index, disc, track) -> {
                api.albumState(box, index);
                if (BackpackPlaybackController.isOnlineDisc(disc)) {
                    return OnlineJukebox.tryPlayPersonal(owner, source(disc, track, false, level, box.getBlockPos()));
                }
                CdSongSelection.update(disc, level.registryAccess());
                var song = JukeboxSong.fromStack(disc);
                if (song.isEmpty()) return false;
                nativeTrack = track; box.getSongPlayer().play(level, song.get()); return true;
            });
        }
        public List<ItemStack> tracks() { return api.tracks(album); }
        public boolean active() {
            return level.hasChunkAt(box.getBlockPos()) && level.getBlockEntity(box.getBlockPos()) == box
                    && box.getTheItem() == album;
        }
        public void save(int index, ItemStack disc) { api.writeTrack(album, index, disc); box.setChanged(); }
        public void stopped(boolean failed) {
            if (active()) {
                box.getSongPlayer().stop(level, box.getBlockState());
                api.albumState(box, playlist.index());
                if (failed) box.popOutTheItem();
            }
        }
    }

    private static final class PocketAlbum implements DiscPlaylist.Media {
        final ServerPlayer owner;
        final String epoch;
        ItemStack expected;
        final DiscPlaylist playlist;
        PocketAlbum(ServerPlayer owner, String epoch, ItemStack stored) {
            this.owner = owner; this.epoch = epoch; expected = stored.copy();
            playlist = new DiscPlaylist(this, (index, disc, track) -> {
                var source = source(disc, track, true, (ServerLevel) owner.level(), owner.blockPosition());
                if (BackpackPlaybackController.isOnlineDisc(disc)) return OnlineJukebox.tryPlayPersonal(owner, source);
                CdSongSelection.update(disc, owner.registryAccess());
                var song = JukeboxSong.fromStack(disc);
                return song.isPresent() && OnlineJukebox.tryPlayPersonalSong(owner, source, song.get().value());
            });
        }
        public List<ItemStack> tracks() { return api.tracks(expected); }
        public boolean active() {
            return findPocket(owner, epoch).filter(pocket -> ItemStack.matches(
                    expected, AdditionalAdditionsApi.stored(pocket))).isPresent();
        }
        public void save(int index, ItemStack disc) {
            findPocket(owner, epoch).ifPresent(pocket -> {
                var stored = AdditionalAdditionsApi.stored(pocket);
                if (api.isAlbum(stored)) api.writeTrack(stored, index, disc);
                else stored = disc.copy();
                AdditionalAdditionsApi.store(pocket, stored); expected = stored.copy();
                owner.getInventory().setChanged();
            });
        }
        public void stopped(boolean failed) {} // Keep the stored disc/album; reinsertion starts a new epoch.
    }
}
