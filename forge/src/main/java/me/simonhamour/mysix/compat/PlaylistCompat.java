package me.simonhamour.mysix.compat;

import dev.eyecrasher.jukeboxplaylists.PlaylistJukebox;
import dev.eyecrasher.jukeboxplaylists.Playlists;
import java.util.Map;
import java.util.WeakHashMap;
import me.simonhamour.mysix.item.MysixCdItem;
import me.simonhamour.mysix.music.OnlineJukebox;
import me.simonhamour.mysix.platform.MysixPlatform;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;

/** Optional Jukebox Playlists 1.0.1 bridge. Upstream owns all chest swaps and cursor persistence. */
public final class PlaylistCompat {
    private PlaylistCompat() {}
    // Values must not retain their weak key. Live audio sessions already own their source position.
    private static final Map<Playback, Map<JukeboxBlockEntity, State>> CONTROLLERS = new WeakHashMap<>();
    public interface Playback {
        boolean active(JukeboxBlockEntity box);
        void requireDuration(JukeboxBlockEntity box);
        boolean start(JukeboxBlockEntity box);
    }
    private static final Playback LIVE = new Playback() {
        public boolean active(JukeboxBlockEntity box) { return OnlineJukebox.hasSessionAt(box); }
        public void requireDuration(JukeboxBlockEntity box) { OnlineJukebox.requireDurationAt(box); }
        public boolean start(JukeboxBlockEntity box) {
            var level = (ServerLevel) box.getLevel();
            // Automation has no inserting player. Wait for a compatible listener in this dimension.
            return level.players().stream().filter(MysixPlatform::canSend)
                    .min(java.util.Comparator.comparingDouble(p -> box.getBlockPos().distToCenterSqr(p.position())))
                    .map(player -> OnlineJukebox.tryPlayPlaced(player, box)).orElse(false);
        }
    };

    public static boolean external(ItemStack stack) {
        return stack.getItem() instanceof MysixCdItem && stack.getCustomName() != null
                && !stack.has(DataComponents.JUKEBOX_PLAYABLE);
    }

    public static void tick(JukeboxBlockEntity box) { tick(box, LIVE); }

    /** setTheItem is called even when upstream deliberately repeats the identical stack. */
    public static void changed(JukeboxBlockEntity box) {
        CONTROLLERS.values().forEach(states -> states.remove(box));
    }

    /** Exposed for headless tests with a real world and a deterministic offline downloader. */
    public static void tick(JukeboxBlockEntity box, Playback playback) {
        var playlist = (PlaylistJukebox) box;
        if (!(box.getLevel() instanceof ServerLevel level) || box.isRemoved()) return;
        var states = CONTROLLERS.computeIfAbsent(playback, ignored -> new WeakHashMap<>());
        if (!external(box.getTheItem())) {
            states.remove(box);
            playlist.playlist$tick();
            return;
        }
        var state = states.computeIfAbsent(box, ignored -> new State());
        if (state.disc != box.getTheItem()) {
            state.disc = box.getTheItem();
            state.started = false;
            state.advanceAt = -1;
        }
        if (playback.active(box)) {
            state.started = true;
            playback.requireDuration(box);
            return;
        }
        if (state.started) {
            state.started = false;
            state.advanceAt = level.getGameTime() + 20;
        }
        if (state.advanceAt >= 0) {
            if (level.getGameTime() >= state.advanceAt) {
                // If there is no eligible chest, upstream leaves the disc untouched: stay stopped.
                // A genuine repeat calls setTheItem, which resets our state through changed().
                state.advanceAt = Long.MAX_VALUE;
                Playlists.advance(playlist, true);
            }
            return;
        }
        // A synchronous completion/failure must still count as a start; failures eject normally.
        if (playback.start(box)) state.started = true;
    }

    private static final class State {
        ItemStack disc;
        boolean started;
        long advanceAt = -1;
    }
}
