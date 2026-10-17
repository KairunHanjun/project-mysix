package me.simonhamour.mysix.compat;

import java.util.List;
import net.minecraft.world.item.ItemStack;

/** One track at a time; late completions can never advance or overwrite another track. */
public final class DiscPlaylist {
    public interface Media {
        List<ItemStack> tracks();
        boolean active();
        void save(int index, ItemStack disc);
        void stopped(boolean failed);
    }
    public interface Track {
        boolean current();
        void save(ItemStack disc);
        void finished();
        void failed();
        void cancel();
    }
    @FunctionalInterface public interface Backend { boolean play(int index, ItemStack disc, Track track); }
    private final Media media;
    private final Backend backend;
    private int index;
    private boolean waiting, stopped;

    public DiscPlaylist(Media media, Backend backend) { this.media = media; this.backend = backend; }

    public void tick() {
        if (stopped) return;
        if (!media.active()) { cancel(); return; }
        if (waiting) return;
        var tracks = media.tracks();
        if (index >= tracks.size()) { cancel(); return; }
        final int selected = index;
        waiting = true;
        var track = new Track() {
            public boolean current() { return !stopped && waiting && index == selected && media.active(); }
            public void save(ItemStack disc) { if (current()) media.save(selected, disc.copy()); }
            public void finished() { if (current()) { index++; waiting = false; } }
            public void failed() { if (current()) { stopped = true; media.stopped(true); } }
            public void cancel() { if (!stopped && index == selected) DiscPlaylist.this.cancel(); }
        };
        if (!backend.play(index, tracks.get(index).copy(), track)) track.failed();
    }

    public void cancel() {
        if (!stopped) { stopped = true; media.stopped(false); }
    }
    public boolean stopped() { return stopped; }
    public int index() { return index; }
}
