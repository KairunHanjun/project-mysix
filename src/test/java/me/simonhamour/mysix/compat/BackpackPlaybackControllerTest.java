package me.simonhamour.mysix.compat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import me.simonhamour.mysix.item.ModItems;
import me.simonhamour.mysix.music.CdRecording;
import me.simonhamour.mysix.music.OnlineJukebox;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;

class BackpackPlaybackControllerTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        assertEquals(16, ModItems.CDS.size());
        var lookup = net.minecraft.data.registries.VanillaRegistries.createWorldLookup();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup)
                .forEach(pending -> pending.apply());
    }
    static ItemStack disc() {
        var disc = new ItemStack(ModItems.CDS.get(DyeColor.RED));
        disc.set(DataComponents.CUSTOM_NAME, Component.literal("Dhyo Haw - Ada Aku Disini"));
        disc.remove(DataComponents.JUKEBOX_PLAYABLE);
        return disc;
    }
    static final class Slot implements BackpackPlaybackController.Slot {
        ItemStack disc = BackpackPlaybackControllerTest.disc(); boolean playing = true;
        public ItemStack disc() { return disc; }
        public boolean playing() { return playing; }
        public void save(ItemStack disc) { this.disc = disc.copy(); }
        public void stop() { playing = false; }
    }
    static final class Fixture {
        final ServerPlayer player = mock(ServerPlayer.class);
        final Slot slot = new Slot();
        final ArrayList<OnlineJukebox.PersonalSource> requests = new ArrayList<>();
        boolean wearing = true;
        final BackpackPlaybackController controller = new BackpackPlaybackController(
                p -> wearing ? Optional.of(slot) : Optional.empty(),
                (p, source) -> { assertSame(player, p); requests.add(source); return true; });
        Fixture() { when(player.getUUID()).thenReturn(UUID.randomUUID()); }
        void tick() { controller.tick(List.of(player)); }
    }

    @Test void playFlagStartsExactlyOneSessionAndStopsWhenDisabledOrRemoved() {
        var f = new Fixture(); f.tick(); f.tick();
        assertEquals(1, f.requests.size());
        var source = f.requests.getFirst(); assertTrue(source.isActive());
        f.slot.playing = false; assertFalse(source.isActive()); f.tick();
        f.slot.playing = true; f.tick(); assertEquals(2, f.requests.size());
        f.wearing = false; assertFalse(f.requests.getLast().isActive());
    }

    @Test void successfulResolutionPersistsOnActualStoredCdAndSurvivesMoving() {
        var f = new Fixture(); f.tick();
        var source = f.requests.getFirst();
        CdRecording.remember(source.disc(), source.disc().getCustomName().getString(), "Yikl044w8d0");
        source.save();
        assertEquals("Yikl044w8d0", CdRecording.pinnedId(f.slot.disc).orElseThrow());
        var moved = f.slot.disc.copy(); f.slot.disc = ItemStack.EMPTY;
        assertFalse(source.isActive());
        assertEquals("Yikl044w8d0", CdRecording.pinnedId(moved).orElseThrow());
    }

    @Test void replacingCdWithAnotherSameNamedCdCancelsOldDownloadAndCannotOverwriteReplacement() {
        var f = new Fixture(); f.tick(); var old = f.requests.getFirst();
        f.slot.disc = disc(); f.tick(); assertEquals(2, f.requests.size());
        assertFalse(old.isActive());
        CdRecording.remember(old.disc(), old.disc().getCustomName().getString(), "Yikl044w8d0");
        old.save(); old.stop();
        assertTrue(CdRecording.pinnedId(f.slot.disc).isEmpty());
        assertTrue(f.slot.playing);
    }

    @Test void vanillaAndUnrelatedDiscsDoNotStartExternalPlayback() {
        var f = new Fixture();
        f.slot.disc.set(DataComponents.CUSTOM_NAME, Component.literal("C418 - Cat"));
        f.tick(); assertTrue(f.requests.isEmpty());
        f.slot.disc = new ItemStack(Items.MUSIC_DISC_CAT);
        f.tick(); assertTrue(f.requests.isEmpty());
    }

    @Test void stoppingPreservesCdAndAllowsPlayAgain() {
        var f = new Fixture(); f.tick(); var source = f.requests.getFirst();
        source.stop(); assertFalse(f.slot.playing); assertFalse(f.slot.disc.isEmpty());
        f.tick(); f.slot.playing = true; f.tick(); assertEquals(2, f.requests.size());
    }

    @Test void integrationIsInactiveAndSafeToInitializeWhenBackpackIsAbsent() {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("travelersbackpack"));
        assertFalse(TravelersBackpackCompat.supported());
        assertFalse(new me.simonhamour.mysix.compat.mixin.BackpackMixinPlugin().shouldApplyMixin("unused", "unused"));
        assertDoesNotThrow(TravelersBackpackCompat::initialize);
    }
}
