package me.simonhamour.mysix.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import me.simonhamour.mysix.item.ModItems;
import me.simonhamour.mysix.music.CdRecording;
import org.junit.jupiter.api.*;

class DiscPlaylistTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); assertEquals(16, ModItems.CDS.size());
        var lookup = net.minecraft.data.registries.VanillaRegistries.createWorldLookup();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup)
                .forEach(pending -> pending.apply());
    }
    private static final class Fixture implements DiscPlaylist.Media {
        final List<ItemStack> discs = new ArrayList<>(List.of(new ItemStack(Items.MUSIC_DISC_CAT),
                BackpackPlaybackControllerTest.disc(), new ItemStack(Items.MUSIC_DISC_PIGSTEP)));
        final List<Integer> started = new ArrayList<>();
        final List<DiscPlaylist.Track> callbacks = new ArrayList<>();
        boolean active = true, failed;
        int stops;
        final DiscPlaylist playlist = new DiscPlaylist(this, (index, disc, callback) -> {
            started.add(index); callbacks.add(callback); return true;
        });
        public List<ItemStack> tracks() { return discs; }
        public boolean active() { return active; }
        public void save(int index, ItemStack disc) { discs.set(index, disc.copy()); }
        public void stopped(boolean failed) { stops++; this.failed = failed; }
    }

    @Test void mixedPlaylistAdvancesExactlyOnceAndNeverOverlapsTracks() {
        var f = new Fixture(); f.playlist.tick(); f.playlist.tick(); assertEquals(List.of(0), f.started);
        f.callbacks.getFirst().finished(); f.callbacks.getFirst().finished(); f.playlist.tick();
        assertEquals(List.of(0, 1), f.started);
        f.callbacks.get(1).finished(); f.playlist.tick(); assertEquals(List.of(0, 1, 2), f.started);
        f.callbacks.get(2).finished(); f.playlist.tick(); assertTrue(f.playlist.stopped());
        assertEquals(1, f.stops); assertFalse(f.failed);
    }

    @Test void resolutionWritesBackOnlyIntoCurrentAlbumTrack() {
        var f = new Fixture(); f.playlist.tick(); f.callbacks.getFirst().finished(); f.playlist.tick();
        var disc = f.discs.get(1).copy();
        CdRecording.remember(disc, disc.getCustomName().getString(), "Yikl044w8d0");
        f.callbacks.get(1).save(disc);
        assertEquals("Yikl044w8d0", CdRecording.pinnedId(f.discs.get(1)).orElseThrow());
        assertTrue(CdRecording.pinnedId(f.discs.get(0)).isEmpty());
    }

    @Test void removingContainerCancelsPendingAudioAndLateSaves() {
        var f = new Fixture(); f.playlist.tick(); var pending = f.callbacks.getFirst();
        f.active = false; f.playlist.tick(); pending.finished(); pending.save(BackpackPlaybackControllerTest.disc());
        assertTrue(f.playlist.stopped()); assertEquals(List.of(0), f.started);
        assertSame(Items.MUSIC_DISC_CAT, f.discs.getFirst().getItem()); assertEquals(1, f.stops);
    }

    @Test void failedDownloadStopsPlaylistWithoutSelectingSubstituteOrNextTrack() {
        var f = new Fixture(); f.playlist.tick(); f.callbacks.getFirst().failed(); f.playlist.tick();
        assertTrue(f.failed); assertTrue(f.playlist.stopped()); assertEquals(List.of(0), f.started);
        assertEquals(3, f.discs.size());
    }

    @Test void staleTrackCannotCancelOrOverwriteFollowingTrack() {
        var f = new Fixture(); f.playlist.tick(); var stale = f.callbacks.getFirst();
        stale.finished(); f.playlist.tick(); stale.cancel(); stale.failed(); stale.save(ItemStack.EMPTY);
        assertFalse(f.playlist.stopped()); assertTrue(f.callbacks.get(1).current());
        assertFalse(f.discs.getFirst().isEmpty());
    }

    @Test void missingOptionalModDoesNotActivateMixinOrApi() {
        org.junit.jupiter.api.Assumptions.assumeFalse(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("additionaladditions"));
        assertFalse(AdditionalAdditionsCompat.supported());
        assertFalse(new me.simonhamour.mysix.compat.mixin.AdditionsMixinPlugin().shouldApplyMixin("unused", "unused"));
        assertDoesNotThrow(AdditionalAdditionsCompat::initialize);
    }
}
