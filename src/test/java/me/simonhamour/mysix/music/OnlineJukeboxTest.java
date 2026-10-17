package me.simonhamour.mysix.music;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import me.simonhamour.mysix.download.YtDlpDownloader.DownloadResult;
import me.simonhamour.mysix.item.ModItems;
import me.simonhamour.mysix.network.AudioTransfer;
import me.simonhamour.mysix.network.JukeboxAudioPayload;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class OnlineJukeboxTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        assertEquals(16, ModItems.CDS.size());
        var lookup = net.minecraft.data.registries.VanillaRegistries.createWorldLookup();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup)
                .forEach(pending -> pending.apply());
        ((net.minecraft.core.MappedRegistry<net.minecraft.world.item.Item>)
                net.minecraft.core.registries.BuiltInRegistries.ITEM).bindAllTagsToEmpty();
        me.simonhamour.mysix.testing.ItemTagBootstrap.refresh();
    }

    private record Sent(ServerPlayer player, JukeboxAudioPayload packet) {}
    private final class Fixture {
        final ServerLevel level = mock(ServerLevel.class);
        final MinecraftServer server = mock(MinecraftServer.class);
        final ServerPlayer owner = mock(ServerPlayer.class);
        final ServerPlayer friend = mock(ServerPlayer.class);
        final AtomicLong time = new AtomicLong();
        final ArrayList<Sent> sent = new ArrayList<>();
        final ArrayList<ServerPlayer> players = new ArrayList<>(List.of(owner, friend));
        final JukeboxBlockEntity box;
        final ItemStack held = new ItemStack(ModItems.CDS.get(DyeColor.RED));
        final OnlineJukebox online;
        Consumer<DownloadResult> success;
        Consumer<Exception> failure;

        Fixture() { this((level, pos) -> null); }
        Fixture(OnlineJukebox.Routes routes) {
            var playerList = mock(PlayerList.class);
            when(server.getPlayerList()).thenReturn(playerList);
            when(playerList.getPlayers()).thenReturn(players);
            when(level.getServer()).thenReturn(server);
            when(level.dimension()).thenReturn(Level.OVERWORLD);
            when(level.hasChunkAt(BlockPos.ZERO)).thenReturn(true);
            when(level.getGameTime()).thenAnswer(invocation -> time.get());
            when(level.registryAccess()).thenReturn(RegistryAccess.EMPTY);
            when(level.getRandom()).thenReturn(RandomSource.create(1));
            when(level.addFreshEntity(any())).thenReturn(true);
            doAnswer(invocation -> { invocation.<Runnable>getArgument(0).run(); return null; })
                    .when(server).execute(any(Runnable.class));
            for (var player : players) {
                when(player.getUUID()).thenReturn(UUID.randomUUID());
                when(player.level()).thenReturn(level);
                when(player.position()).thenReturn(new Vec3(1, 1, 1));
                when(player.blockPosition()).thenReturn(BlockPos.ZERO);
            }
            when(playerList.getPlayer(owner.getUUID())).thenReturn(owner);
            var state = new AtomicReference<>(Blocks.JUKEBOX.defaultBlockState());
            box = new JukeboxBlockEntity(BlockPos.ZERO, state.get());
            box.setLevel(level);
            when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(box);
            when(level.getBlockState(BlockPos.ZERO)).thenAnswer(invocation -> state.get());
            when(level.setBlock(eq(BlockPos.ZERO), any(), anyInt())).thenAnswer(invocation -> {
                state.set(invocation.getArgument(1)); box.setBlockState(state.get()); return true;
            });
            held.set(DataComponents.CUSTOM_NAME, Component.literal("Dhyo Haw - Ada Aku Disini"));
            held.remove(DataComponents.JUKEBOX_PLAYABLE);
            online = new OnlineJukebox((query, ok, bad) -> { success = ok; failure = bad; },
                    new OnlineJukebox.Transport() {
                        public boolean supports(ServerPlayer player) { return true; }
                        public void send(ServerPlayer player, JukeboxAudioPayload packet) { sent.add(new Sent(player, packet)); }
                    }, routes);
        }
        void insert() { online.insert(owner, level, BlockPos.ZERO, held); }
        void complete(byte[] audio) throws Exception {
            var path = directory.resolve("Yikl044w8d0.ogg");
            Files.write(path, audio);
            success.accept(new DownloadResult("Yikl044w8d0", "Dhyo Haw - Ada Aku Disini", path, audio.length, 10));
        }
        List<JukeboxAudioPayload> packets(ServerPlayer player) {
            return sent.stream().filter(s -> s.player == player).map(Sent::packet).toList();
        }
    }

    @Test void radioRoutesToRemoteSpeakerAndDisconnectionStopsItsListeners() throws Exception {
        var targets = new ArrayList<OnlineJukebox.Output>();
        var route = mock(OnlineJukebox.Route.class);
        when(route.active()).thenReturn(true);
        when(route.outputs()).thenReturn(targets);
        var f = new Fixture((level, pos) -> route);
        var speaker = new BlockPos(200, 64, 0);
        targets.add(new OnlineJukebox.Output(f.level, speaker, 48));
        when(f.friend.position()).thenReturn(new Vec3(201, 64, 0));
        f.insert(); f.complete(new byte[]{1, 2, 3}); f.time.set(20); f.online.tick(f.server);
        assertTrue(f.packets(f.owner).isEmpty(), "A radio must silence its source jukebox");
        assertEquals(speaker, f.packets(f.friend).getFirst().pos());
        assertEquals(JukeboxAudioPayload.PLAY, f.packets(f.friend).getLast().kind());
        targets.clear(); f.online.tick(f.server);
        assertEquals(JukeboxAudioPayload.STOP, f.packets(f.friend).getLast().kind());
        f.box.popOutTheItem(); f.online.tick(f.server); verify(route).close();
    }

    @Test void switchingToNearestSpeakerKeepsTheOriginalTimelineAndStopsOldSound() throws Exception {
        var targets = new ArrayList<OnlineJukebox.Output>();
        var route = mock(OnlineJukebox.Route.class);
        when(route.active()).thenReturn(true); when(route.outputs()).thenReturn(targets);
        var f = new Fixture((level, pos) -> route);
        var first = new BlockPos(200, 64, 0); var next = new BlockPos(260, 64, 0);
        targets.add(new OnlineJukebox.Output(f.level, first, 48));
        targets.add(new OnlineJukebox.Output(f.level, next, 48));
        when(f.friend.position()).thenReturn(new Vec3(201, 64, 0));
        f.insert(); f.complete(new byte[]{1, 2, 3}); f.time.set(20); f.online.tick(f.server);
        int before = f.packets(f.friend).size();
        when(f.friend.position()).thenReturn(new Vec3(261, 64, 0));
        f.time.set(60); f.online.tick(f.server);
        var packets = f.packets(f.friend).subList(before, f.packets(f.friend).size());
        assertEquals(JukeboxAudioPayload.STOP, packets.getFirst().kind());
        assertEquals(next, packets.get(1).pos());
        assertEquals(20, packets.getLast().startTick(), "Moving between speakers must not restart the song");
    }

    @Test void presentationStateWaitsForPlaybackAndEndsWithTrackOrEjection() throws Exception {
        var f = new Fixture(); f.insert();
        assertFalse(f.online.playingAt(f.box));
        f.complete(new byte[]{1, 2, 3});
        assertFalse(f.online.playingAt(f.box), "Do not animate during preparation");
        f.time.set(20); assertTrue(f.online.playingAt(f.box));
        assertFalse(f.box.getSongPlayer().isPlaying(), "Presentation must not start vanilla Cat");
        f.time.set(221); assertFalse(f.online.playingAt(f.box));
        f.time.set(20); f.box.popOutTheItem(); assertFalse(f.online.playingAt(f.box));
    }

    @Test void twoPlayersReceiveIdenticalAudioAndRemovalStopsBoth() throws Exception {
        var f = new Fixture();
        f.insert();
        assertTrue(f.held.isEmpty());
        assertFalse(f.box.getTheItem().isEmpty());
        assertFalse(f.box.getSongPlayer().isPlaying(), "Do not start vanilla Cat alongside external audio");
        byte[] audio = new byte[JukeboxAudioPayload.CHUNK_BYTES * 5 + 3];
        new Random(1).nextBytes(audio);
        f.complete(audio);
        f.online.tick(f.server);
        assertEquals(4, f.packets(f.owner).stream().filter(p -> p.kind() == JukeboxAudioPayload.CHUNK).count());
        assertFalse(f.packets(f.owner).stream().anyMatch(p -> p.kind() == JukeboxAudioPayload.PLAY));
        f.time.set(20);
        f.online.tick(f.server);
        for (var player : List.of(f.owner, f.friend)) {
            var transfer = new AudioTransfer(audio.length);
            for (var packet : f.packets(player)) {
                if (packet.kind() == JukeboxAudioPayload.CHUNK) transfer.append(packet.offset(), packet.data());
            }
            assertArrayEquals(audio, transfer.finish());
            assertEquals(1, f.packets(player).stream().filter(p -> p.kind() == JukeboxAudioPayload.PLAY).count());
        }
        f.box.popOutTheItem();
        f.online.tick(f.server);
        for (var player : List.of(f.owner, f.friend)) {
            assertEquals(JukeboxAudioPayload.STOP, f.packets(player).getLast().kind());
        }
        verify(f.level, times(1)).addFreshEntity(any(Entity.class));
    }

    @Test void failureEjectsActualCdAndShowsHotbarMessageOnce() {
        var f = new Fixture();
        f.insert();
        f.failure.accept(new java.io.IOException("No song found"));
        assertTrue(f.held.isEmpty());
        assertTrue(f.box.getTheItem().isEmpty());
        verify(f.level, times(1)).addFreshEntity(any(Entity.class));
        verify(f.owner, times(2)).sendOverlayMessage(any(Component.class));
        assertTrue(f.sent.isEmpty());
        f.failure.accept(new java.io.IOException("Repeated callback"));
        verify(f.level, times(1)).addFreshEntity(any(Entity.class));
    }

    @Test void removingCdDuringDownloadPreventsLatePlayback() throws Exception {
        var f = new Fixture();
        f.insert();
        f.box.popOutTheItem();
        f.online.tick(f.server);
        f.complete(new byte[]{1, 2, 3});
        f.time.set(20);
        f.online.tick(f.server);
        assertTrue(f.sent.isEmpty());
        verify(f.level, times(1)).addFreshEntity(any(Entity.class));
    }

    @Test void leavingRangeStopsOnePlayerAndReturningUsesSharedTimeline() throws Exception {
        var f = new Fixture();
        f.insert(); f.complete(new byte[]{1, 2, 3});
        f.time.set(20); f.online.tick(f.server);
        when(f.friend.position()).thenReturn(new Vec3(100, 1, 1));
        f.online.tick(f.server);
        assertEquals(JukeboxAudioPayload.STOP, f.packets(f.friend).getLast().kind());
        assertEquals(JukeboxAudioPayload.PLAY, f.packets(f.owner).getLast().kind());
        when(f.friend.position()).thenReturn(new Vec3(1, 1, 1));
        f.time.set(40); f.online.tick(f.server);
        var packet = f.packets(f.friend).getLast();
        assertEquals(JukeboxAudioPayload.PLAY, packet.kind());
        assertEquals(20, packet.startTick());
    }

    @Test void chunkUnloadAndServerShutdownCancelPendingPlayback() throws Exception {
        for (boolean unload : new boolean[]{false, true}) {
            var f = new Fixture(); f.insert();
            if (unload) {
                when(f.level.hasChunkAt(BlockPos.ZERO)).thenReturn(false);
                f.online.tick(f.server);
            } else f.online.clear();
            f.complete(new byte[]{1, 2, 3});
            f.time.set(20); f.online.tick(f.server);
            assertTrue(f.sent.isEmpty());
        }
    }

    private static final class Personal implements OnlineJukebox.PersonalSource {
        final ItemStack disc;
        boolean active = true;
        int saves;
        Personal(ItemStack disc) { this.disc = disc; }
        public ItemStack disc() { return disc; }
        public boolean isActive() { return active; }
        public void save() { saves++; }
        public void stop() { active = false; }
    }

    @Test void backpackTransfersOnlyToWearerAndStoppingDoesNotDropOrConsumeCd() throws Exception {
        var f = new Fixture(); var source = new Personal(f.held);
        assertTrue(f.online.playPersonal(f.owner, source));
        f.complete(new byte[]{1, 2, 3}); f.time.set(20); f.online.tick(f.server);
        assertEquals(JukeboxAudioPayload.PERSONAL_BEGIN, f.packets(f.owner).getFirst().kind());
        assertEquals(JukeboxAudioPayload.PLAY, f.packets(f.owner).getLast().kind());
        assertTrue(f.packets(f.friend).isEmpty());
        assertEquals(1, source.saves);
        assertEquals("Yikl044w8d0", CdRecording.pinnedId(source.disc).orElseThrow());
        assertEquals(1, f.held.getCount());
        source.active = false; f.online.tick(f.server);
        assertEquals(JukeboxAudioPayload.STOP, f.packets(f.owner).getLast().kind());
        verify(f.level, never()).addFreshEntity(any(Entity.class));
    }

    @Test void backpackDownloadFailureResetsPlayFlagAndPreservesCd() {
        var f = new Fixture(); var source = new Personal(f.held);
        f.online.playPersonal(f.owner, source);
        f.failure.accept(new java.io.IOException("Download unavailable"));
        assertFalse(source.active); assertEquals(1, f.held.getCount());
        assertTrue(f.sent.isEmpty()); verify(f.owner, times(2)).sendOverlayMessage(any(Component.class));
        verify(f.level, never()).addFreshEntity(any(Entity.class));
    }

    @Test void stoppingBackpackDuringLookupCancelsLateResult() throws Exception {
        var f = new Fixture(); var source = new Personal(f.held);
        f.online.playPersonal(f.owner, source);
        source.active = false; f.online.tick(f.server);
        f.complete(new byte[]{1, 2, 3}); f.time.set(20); f.online.tick(f.server);
        assertTrue(f.sent.isEmpty()); assertEquals(0, source.saves);
    }

    @Test void completedBackpackTrackClearsPlayingFlag() throws Exception {
        var f = new Fixture(); var source = new Personal(f.held);
        f.online.playPersonal(f.owner, source); f.complete(new byte[]{1, 2, 3});
        f.time.set(20); f.online.tick(f.server); f.time.set(221); f.online.tick(f.server);
        assertFalse(source.active);
        assertEquals(JukeboxAudioPayload.STOP, f.packets(f.owner).getLast().kind());
    }

    @Test void disconnectDuringBackpackDownloadCannotRetainSessionOrStartLatePlayback() throws Exception {
        var f = new Fixture(); var source = new Personal(f.held);
        f.online.playPersonal(f.owner, source);
        when(f.server.getPlayerList().getPlayer(f.owner.getUUID())).thenReturn(null);
        f.online.tick(f.server); f.complete(new byte[]{1, 2, 3});
        f.time.set(20); f.online.tick(f.server); assertTrue(f.sent.isEmpty());
        assertFalse(source.active);
    }

    @Test void personalVanillaTrackUsesNativeSoundWithoutRunningDownloader() {
        var f = new Fixture(); var source = new Personal(f.held);
        var song = new net.minecraft.world.item.JukeboxSong(
                net.minecraft.sounds.SoundEvents.MUSIC_DISC_CAT,
                Component.literal("C418 - Cat"), 2, 1);
        assertTrue(f.online.playPersonalSong(f.owner, source, song));
        assertNull(f.success, "Native tracks must not call yt-dlp");
        f.time.set(1); f.online.tick(f.server);
        var packet = f.packets(f.owner).getFirst();
        assertEquals(JukeboxAudioPayload.PERSONAL_NATIVE, packet.kind());
        assertEquals("minecraft:music_disc.cat", packet.nativeSound());
        assertTrue(f.packets(f.friend).isEmpty());
        f.time.set(42); f.online.tick(f.server); assertFalse(source.active);
        assertEquals(JukeboxAudioPayload.STOP, f.packets(f.owner).getLast().kind());
    }

    @Test void managedStationaryAlbumReachesNearbyPlayersWithoutFollowingOwner() throws Exception {
        var f = new Fixture();
        var source = new OnlineJukebox.PersonalSource() {
            public ItemStack disc() { return f.held; }
            public boolean isActive() { return true; }
            public void save() {}
            public void stop() {}
            public boolean personal() { return false; }
            public ServerLevel level(ServerPlayer owner) { return f.level; }
            public BlockPos position(ServerPlayer owner) { return BlockPos.ZERO; }
        };
        f.online.playPersonal(f.owner, source); f.complete(new byte[]{1, 2, 3});
        when(f.server.getPlayerList().getPlayer(f.owner.getUUID())).thenReturn(null);
        f.time.set(20); f.online.tick(f.server);
        assertEquals(JukeboxAudioPayload.BEGIN, f.packets(f.friend).getFirst().kind());
        assertEquals(JukeboxAudioPayload.PLAY, f.packets(f.friend).getLast().kind());
    }
}
