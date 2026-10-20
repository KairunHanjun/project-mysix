package me.simonhamour.mysix;

import dev.eyecrasher.jukeboxplaylists.PlaylistJukebox;
import dev.eyecrasher.jukeboxplaylists.Playlists;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Consumer;
import me.simonhamour.mysix.compat.PlaylistCompat;
import me.simonhamour.mysix.download.YtDlpDownloader.DownloadResult;
import me.simonhamour.mysix.item.ModItems;
import me.simonhamour.mysix.music.CdRecording;
import me.simonhamour.mysix.music.OnlineJukebox;
import me.simonhamour.mysix.network.JukeboxAudioPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;

/** Real upstream binary and Minecraft block entities; downloads are deterministic and offline. */
public final class ForgePlaylistChecks {
    private ForgePlaylistChecks() {}
    public static void register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext context) {
        var functions = net.minecraftforge.registries.DeferredRegister.create(
                net.minecraft.core.registries.Registries.TEST_FUNCTION, "mysix_playlist");
        functions.register("sequence", () -> ForgePlaylistChecks::sequence);
        functions.register("skip", () -> ForgePlaylistChecks::skip);
        functions.register("failure", () -> helper -> failure(helper, false));
        functions.register("duration", () -> helper -> failure(helper, true));
        functions.register("repeat", () -> ForgePlaylistChecks::repeat);
        functions.register("no_chest", () -> ForgePlaylistChecks::noChest);
        functions.register(context.getModBusGroup());
    }

    private record Request(Consumer<DownloadResult> success, Consumer<Exception> failure) {}
    private static final class Fixture {
        final GameTestHelper helper;
        final JukeboxBlockEntity box;
        final ChestBlockEntity chest;
        final ServerPlayer player;
        final ArrayList<Request> requests = new ArrayList<>();
        final OnlineJukebox engine;
        final PlaylistCompat.Playback playback;
        Fixture(GameTestHelper helper) {
            this.helper = helper;
            helper.assertTrue(net.minecraftforge.fml.ModList.isLoaded("jukebox_playlists"), "Pinned upstream mod must be loaded");
            helper.setBlock(new BlockPos(1, 1, 1), Blocks.JUKEBOX);
            helper.setBlock(new BlockPos(1, 2, 1), Blocks.CHEST);
            box = (JukeboxBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)));
            chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(box.getBlockPos().above());
            helper.assertTrue(box instanceof PlaylistJukebox, "Upstream jukebox mixin must apply");
            helper.assertTrue(dev.eyecrasher.jukeboxplaylists.Platform.isPlaylistContainer(chest.getBlockState()), "Upstream chest tag must load");
            player = helper.makeMockServerPlayer();
            engine = new OnlineJukebox((query, ok, bad) -> requests.add(new Request(ok, bad)), new OnlineJukebox.Transport() {
                public boolean supports(ServerPlayer listener) { return true; }
                public void send(ServerPlayer listener, JukeboxAudioPayload packet) {}
            }, (level, pos) -> null);
            playback = new PlaylistCompat.Playback() {
                public boolean active(JukeboxBlockEntity target) { return engine.hasSession(target); }
                public void requireDuration(JukeboxBlockEntity target) { engine.requireDuration(target); }
                public boolean start(JukeboxBlockEntity target) { return engine.playPlaced(player, target, true); }
            };
        }
        void tick() { engine.tick(helper.getLevel().getServer()); PlaylistCompat.tick(box, playback); }
        ItemStack external(String name) {
            var disc = new ItemStack(ModItems.CDS.values().iterator().next().get());
            disc.set(DataComponents.CUSTOM_NAME, Component.literal(name));
            disc.remove(DataComponents.JUKEBOX_PLAYABLE);
            return disc;
        }
        void complete(int index, double duration) {
            try {
                Path file = Files.createTempFile("mysix-playlist-test-", ".ogg");
                try {
                    Files.write(file, new byte[]{1, 2, 3});
                    requests.get(index).success.accept(new DownloadResult("Yikl044w8d0", "Test Recording", file, 3, duration));
                } finally { Files.deleteIfExists(file); }
            } catch (Exception error) { throw new IllegalStateException(error); }
        }
    }

    private static void sequence(GameTestHelper helper) {
        var f = new Fixture(helper);
        var disc = f.external("Artist - Playlist Song");
        f.chest.setItem(0, disc);
        f.chest.setItem(1, new ItemStack(Items.DIRT));
        f.chest.setItem(2, new ItemStack(Items.MUSIC_DISC_CAT));
        // Call the actual block ticker: Mysix must intercept ahead of upstream's cancelling injection.
        var ticker = ((net.minecraft.world.level.block.JukeboxBlock) Blocks.JUKEBOX).getTicker(helper.getLevel(), f.box.getBlockState(), net.minecraft.world.level.block.entity.BlockEntityTypes.JUKEBOX);
        ticker.tick(helper.getLevel(), f.box.getBlockPos(), f.box.getBlockState(), f.box);
        helper.assertTrue(f.box.getTheItem() == disc, "Empty jukebox must take external CD from chest");
        f.tick();
        helper.assertTrue(f.requests.size() == 1 && f.engine.hasSession(f.box), "Automation must start one download");
        helper.runAtTickTime(4, () -> {
            f.tick();
            helper.assertTrue(f.box.getTheItem() == disc && f.requests.size() == 1, "Pending download must hold playlist");
            f.complete(0, 0.1);
        });
        helper.runAtTickTime(25, () -> {
            f.tick();
            helper.assertTrue(f.box.getTheItem() == disc, "Must not advance before actual recording ends");
        });
        helper.runAtTickTime(28, f::tick);
        helper.runAtTickTime(47, () -> {
            f.tick();
            helper.assertTrue(f.box.getTheItem() == disc, "Keep the one-second gap between tracks");
        });
        helper.runAtTickTime(49, () -> {
            f.tick();
            helper.assertTrue(f.box.getTheItem().is(Items.MUSIC_DISC_CAT), "Skip non-disc slots and advance to native song");
            helper.assertTrue(f.chest.getItem(2) == disc && CdRecording.pinnedId(disc).orElseThrow().equals("Yikl044w8d0"), "Swapping must preserve the complete recording");
            helper.assertTrue(f.box.getSongPlayer().isPlaying(), "Native disc must use normal jukebox playback");
            f.engine.clear(); helper.succeed();
        });
    }

    private static void skip(GameTestHelper helper) {
        var f = new Fixture(helper);
        var old = f.external("Artist - Old Song");
        var next = f.external("Artist - Next Song");
        f.box.setTheItem(old); f.chest.setItem(0, next); f.tick();
        Playlists.advance((PlaylistJukebox) f.box, false); f.tick();
        helper.assertTrue(f.requests.size() == 2 && f.box.getTheItem() == next, "Skipping pending track must start next disc");
        f.complete(0, 10);
        helper.assertTrue(CdRecording.pinnedId(old).isEmpty(), "Late completion must not revive or modify skipped disc");
        f.complete(1, 10);
        helper.assertTrue(CdRecording.pinnedId(next).isPresent(), "Current track must remember recording");
        helper.getLevel().removeBlock(f.box.getBlockPos(), false); f.engine.tick(helper.getLevel().getServer());
        helper.assertTrue(!f.engine.hasSession(f.box), "Breaking source must cancel session");
        helper.assertTrue(f.chest.getItem(0) == old, "Skip must preserve previous CD in chest");
        helper.succeed();
    }

    private static void failure(GameTestHelper helper, boolean missingDuration) {
        var f = new Fixture(helper);
        f.box.setTheItem(f.external("Artist - Unavailable Song"));
        f.chest.setItem(0, new ItemStack(Items.MUSIC_DISC_CAT));
        if (missingDuration) {
            // Adopt a manually inserted recording that completed before the playlist's first tick.
            f.engine.playPlaced(f.player, f.box, false); f.complete(0, 0); f.tick();
        } else {
            f.tick(); f.requests.getFirst().failure.accept(new java.io.IOException("Offline test failure"));
        }
        helper.assertTrue(f.box.getTheItem().isEmpty() && !f.engine.hasSession(f.box), "Failure must eject and release session");
        f.tick();
        helper.assertTrue(f.box.getTheItem().is(Items.MUSIC_DISC_CAT), "Failure must not strand remaining playlist");
        helper.assertTrue(f.requests.size() == 1, "Rejected CD must not cause immediate retry loop");
        helper.succeed();
    }

    private static void repeat(GameTestHelper helper) {
        var f = new Fixture(helper);
        var disc = f.external("Artist - Repeat Song");
        f.box.setTheItem(disc);
        // Also cover adopting a manually inserted CD without starting it twice.
        f.engine.playPlaced(f.player, f.box, false); f.tick();
        helper.assertTrue(f.requests.size() == 1, "Already loading CD must be adopted");
        f.complete(0, 0.1);
        helper.runAtTickTime(24, f::tick);
        helper.runAtTickTime(45, () -> {
            f.tick(); f.tick();
            helper.assertTrue(f.box.getTheItem() == disc && f.requests.size() == 2, "Empty chest must repeat same CD after completion");
            f.engine.clear(); helper.succeed();
        });
    }

    private static void noChest(GameTestHelper helper) {
        var f = new Fixture(helper);
        var disc = f.external("Artist - Play Once");
        f.box.setTheItem(disc); f.tick(); f.complete(0, 0.1);
        helper.getLevel().removeBlock(f.chest.getBlockPos(), false);
        helper.runAtTickTime(24, f::tick);
        helper.runAtTickTime(45, f::tick);
        helper.runAtTickTime(48, () -> {
            f.tick();
            helper.assertTrue(f.box.getTheItem() == disc && f.requests.size() == 1
                    && !f.engine.hasSession(f.box), "Removing chest must leave finished CD stopped");
            helper.succeed();
        });
    }
}
