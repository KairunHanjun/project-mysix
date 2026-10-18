package me.simonhamour.mysix.compat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.function.Supplier;
import me.simonhamour.mysix.item.ModItems;
import me.simonhamour.mysix.music.CdRecording;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import me.simonhamour.mysix.music.OnlineJukebox;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "mysix.additionsTests", matches = "true")
class AdditionsIntegrationTest {
    static AdditionalAdditionsApi api;
    static Item pocketItem;
    static RegistryAccess songs;
    @BeforeAll static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); assertEquals(16, ModItems.CDS.size());
        Class.forName("one.dqu.additionaladditions.registry.AAMisc");
        Class.forName("one.dqu.additionaladditions.core.util.fabric.RegistrarImpl").getMethod("runDeferred").invoke(null);
        var key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("mysix_test", "pocket"));
        pocketItem = (Item) Class.forName("one.dqu.additionaladditions.feature.pocket_jukebox.PocketJukeboxItem")
                .getConstructor(Item.Properties.class).newInstance(new Item.Properties().setId(key).stacksTo(1));
        Registry.register(BuiltInRegistries.ITEM, key, pocketItem);
        var lookup = net.minecraft.data.registries.VanillaRegistries.createWorldLookup();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup)
                .forEach(pending -> pending.apply());
        ((MappedRegistry<Item>) BuiltInRegistries.ITEM).bindAllTagsToEmpty();
        me.simonhamour.mysix.testing.ItemTagBootstrap.refresh();
        var registry = new MappedRegistry<JukeboxSong>(Registries.JUKEBOX_SONG, com.mojang.serialization.Lifecycle.stable());
        lookup.lookupOrThrow(Registries.JUKEBOX_SONG).listElements().forEach(holder ->
                Registry.register(registry, holder.key(), holder.value()));
        songs = new RegistryAccess.ImmutableRegistryAccess(List.of(registry.freeze()));
        api = new AdditionalAdditionsApi();
        AdditionalAdditionsCompat.initialize();
    }

    @Test void releaseAndApiSignaturesMatch() throws Exception {
        assertTrue(AdditionalAdditionsCompat.supported()); assertNotNull(new AdditionalAdditionsApi());
        assertTrue(api.pocketEnabled());
    }

    @Test void realAlbumAcceptsExternalDiscButStillRejectsNonMusicItems() throws Exception {
        var type = Class.forName("one.dqu.additionaladditions.feature.album.AlbumContents");
        var valid = type.getMethod("isValidItem", ItemStack.class);
        var disc = BackpackPlaybackControllerTest.disc();
        assertFalse(disc.has(DataComponents.JUKEBOX_PLAYABLE));
        assertEquals(true, valid.invoke(null, disc));
        assertEquals(true, valid.invoke(null, new ItemStack(Items.MUSIC_DISC_CAT)));
        assertEquals(false, valid.invoke(null, new ItemStack(Items.DIAMOND)));
        var contents = type.getConstructor(List.class).newInstance(List.of());
        var added = type.getMethod("add", ItemStack.class).invoke(contents, disc);
        @SuppressWarnings("unchecked") var items = (List<ItemStack>) type.getMethod("items").invoke(added);
        assertEquals(1, items.size()); assertNotSame(disc, items.getFirst());
    }

    @SuppressWarnings("unchecked")
    static ItemStack album() throws Exception {
        var type = Class.forName("one.dqu.additionaladditions.feature.album.AlbumContents");
        var contents = type.getConstructor(List.class).newInstance(List.of(new ItemStack(Items.MUSIC_DISC_CAT),
                BackpackPlaybackControllerTest.disc(), new ItemStack(Items.MUSIC_DISC_PIGSTEP)));
        var component = (DataComponentType<Object>) ((Supplier<?>) Class.forName("one.dqu.additionaladditions.registry.AAMisc")
                .getField("ALBUM_CONTENTS_COMPONENT").get(null)).get();
        var album = new ItemStack(Items.BUNDLE); album.set(component, contents); return album;
    }

    @Test void cachedRecordingIsSavedInsideRealAlbumComponentAndPocketContainer() throws Exception {
        var album = album(); var disc = api.tracks(album).get(1).copy();
        CdRecording.remember(disc, disc.getCustomName().getString(), "Yikl044w8d0");
        api.writeTrack(album, 1, disc);
        var pocket = new ItemStack(Items.BUNDLE); AdditionalAdditionsApi.store(pocket, album);
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var encoded = ItemStack.CODEC.encodeStart(ops, pocket).getOrThrow();
        var restoredPocket = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
        var restored = AdditionalAdditionsApi.stored(restoredPocket);
        assertEquals("Yikl044w8d0", CdRecording.pinnedId(api.tracks(restored).get(1)).orElseThrow());
        assertTrue(CdRecording.pinnedId(api.tracks(restored).get(0)).isEmpty());
        assertTrue(api.containsExternal(restored));
    }

    @Test void realPocketPlayerLeavesMixedAlbumPlaybackToServer() throws Exception {
        Assumptions.assumeTrue(FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT);
        var type = Class.forName("one.dqu.additionaladditions.feature.pocket_jukebox.PocketJukeboxPlayer");
        var player = mock(type, CALLS_REAL_METHODS);
        var album = album(); var pocket = new ItemStack(Items.BUNDLE); AdditionalAdditionsApi.store(pocket, album);
        var contentsClass = Class.forName("one.dqu.additionaladditions.feature.album.AlbumContents");
        var contents = contentsClass.getConstructor(List.class).newInstance(api.tracks(album));
        type.getMethod("play", contentsClass, net.minecraft.world.entity.player.Player.class, ItemStack.class)
                .invoke(player, contents, null, pocket);
        assertEquals(-1, type.getMethod("getCurrentTrack").invoke(player), "Native client sequencer must not begin");
    }

    private static final class World {
        final ServerLevel level = mock(ServerLevel.class);
        final MinecraftServer server = mock(MinecraftServer.class);
        final ServerPlayer player = mock(ServerPlayer.class);
        final JukeboxBlockEntity box;
        final ItemStack held;
        World(ItemStack held) {
            this.held = held;
            when(level.registryAccess()).thenReturn(songs);
            when(level.getServer()).thenReturn(server);
            when(level.hasChunkAt(BlockPos.ZERO)).thenReturn(true);
            when(level.getRandom()).thenReturn(RandomSource.create(1));
            when(level.addFreshEntity(any())).thenReturn(true);
            when(player.getUUID()).thenReturn(UUID.randomUUID());
            when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(held);
            when(player.level()).thenReturn(level);
            when(player.registryAccess()).thenReturn(songs);
            when(player.isAlive()).thenReturn(true);
            when(player.blockPosition()).thenReturn(BlockPos.ZERO);
            var players = mock(PlayerList.class);
            when(server.getPlayerList()).thenReturn(players);
            when(players.getPlayers()).thenReturn(List.of(player));
            var state = new java.util.concurrent.atomic.AtomicReference<>(Blocks.JUKEBOX.defaultBlockState());
            box = new JukeboxBlockEntity(BlockPos.ZERO, state.get()); box.setLevel(level);
            when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(box);
            when(level.getBlockState(BlockPos.ZERO)).thenAnswer(call -> state.get());
            when(level.setBlock(eq(BlockPos.ZERO), any(), anyInt())).thenAnswer(call -> {
                state.set(call.getArgument(1)); box.setBlockState(state.get()); return true;
            });
        }
        void tickBox() { JukeboxBlockEntity.tick(level, BlockPos.ZERO, level.getBlockState(BlockPos.ZERO), box); }
    }

    @Test void realMixedAlbumTickWaitsForExternalTrackThenStartsVanillaAndCancelsOnEjection() throws Exception {
        var held = album();
        api.writeTrack(held, 0, BackpackPlaybackControllerTest.disc());
        var vanilla = BackpackPlaybackControllerTest.disc();
        vanilla.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("C418 - Cat"));
        api.writeTrack(held, 1, vanilla);
        var world = new World(held);
        var sources = new ArrayList<OnlineJukebox.PersonalSource>();
        try (var audio = mockStatic(OnlineJukebox.class)) {
            audio.when(() -> OnlineJukebox.tryPlayPersonal(eq(world.player), any())).thenAnswer(call -> {
                sources.add(call.getArgument(1)); return true;
            });
            var result = net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(
                    world.player, world.level, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.ZERO, Direction.UP, BlockPos.ZERO, false));
            assertSame(InteractionResult.SUCCESS, result); assertTrue(held.isEmpty());
            world.tickBox(); world.tickBox();
            assertEquals(1, sources.size(), "Native album-tail hook must not advance during download");
            var source = sources.getFirst(); assertFalse(source.personal()); assertTrue(source.isActive());
            CdRecording.remember(source.disc(), source.disc().getCustomName().getString(), "Yikl044w8d0");
            source.save();
            assertEquals("Yikl044w8d0", CdRecording.pinnedId(api.tracks(world.box.getTheItem()).getFirst()).orElseThrow());
            source.finished(); world.tickBox();
            assertTrue(world.box.getSongPlayer().isPlaying(), "Vanilla song must start after external song completes");
            world.box.popOutTheItem(); assertFalse(source.isActive());
            source.save(); assertTrue(world.box.getTheItem().isEmpty());
            verify(world.level, times(1)).addFreshEntity(any());
        }
    }

    @Test void failedAlbumTrackEjectsWholeAlbumAndPreservesItsDiscs() throws Exception {
        var held = album();
        api.writeTrack(held, 0, BackpackPlaybackControllerTest.disc());
        var world = new World(held);
        var sources = new ArrayList<OnlineJukebox.PersonalSource>();
        try (var audio = mockStatic(OnlineJukebox.class)) {
            audio.when(() -> OnlineJukebox.tryPlayPersonal(eq(world.player), any())).thenAnswer(call -> {
                sources.add(call.getArgument(1)); return true;
            });
            net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(
                    world.player, world.level, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.ZERO, Direction.UP, BlockPos.ZERO, false));
            world.tickBox();
            var inserted = world.box.getTheItem();
            sources.getFirst().failed(); sources.getFirst().failed();
            assertTrue(world.box.getTheItem().isEmpty());
            assertEquals(3, api.tracks(inserted).size());
            assertFalse(sources.getFirst().isActive());
            verify(world.level, times(1)).addFreshEntity(any());
        }
    }

    @Test void actualPocketInsertionStartsOneSessionAndRemovingDiscCancelsLateDownload() throws Exception {
        var world = new World(ItemStack.EMPTY);
        var pocket = new ItemStack(pocketItem);
        var inventory = mock(net.minecraft.world.entity.player.Inventory.class);
        when(world.player.getInventory()).thenReturn(inventory);
        when(inventory.getContainerSize()).thenReturn(1);
        when(inventory.getItem(0)).thenReturn(pocket);
        world.player.containerMenu = mock(net.minecraft.world.inventory.AbstractContainerMenu.class);
        when(world.player.containerMenu.getCarried()).thenReturn(ItemStack.EMPTY);
        var method = pocketItem.getClass().getDeclaredMethod("insertDisc", ItemStack.class, ItemStack.class,
                net.minecraft.world.entity.player.Player.class); method.setAccessible(true);
        assertEquals(true, method.invoke(pocketItem, pocket, BackpackPlaybackControllerTest.disc(), world.player));
        var sources = new ArrayList<OnlineJukebox.PersonalSource>();
        try (var audio = mockStatic(OnlineJukebox.class)) {
            audio.when(() -> OnlineJukebox.tryPlayPersonal(eq(world.player), any())).thenAnswer(call -> {
                sources.add(call.getArgument(1)); return true;
            });
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(world.server);
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(world.server);
            assertEquals(1, sources.size()); var source = sources.getFirst(); assertTrue(source.personal());
            CdRecording.remember(source.disc(), source.disc().getCustomName().getString(), "Yikl044w8d0");
            source.save();
            assertEquals("Yikl044w8d0", CdRecording.pinnedId(AdditionalAdditionsApi.stored(pocket)).orElseThrow());
            pocket.remove(DataComponents.CONTAINER); assertFalse(source.isActive());
            source.save(); assertFalse(pocket.has(DataComponents.CONTAINER));
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(world.server);
            assertEquals(1, sources.size());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void creativeSyncedPocketStartsWithoutServerInsertionAndKeepsPlayingAfterStackCopy(boolean carried) {
        var world = new World(ItemStack.EMPTY);
        var pocket = new ItemStack(pocketItem);
        // Creative synchronizes the result of client inventory editing. It does
        // not invoke the server insertDisc hook that assigns our playback epoch.
        AdditionalAdditionsApi.store(pocket, BackpackPlaybackControllerTest.disc());
        assertFalse(pocket.has(DataComponents.CUSTOM_DATA));
        var current = new java.util.concurrent.atomic.AtomicReference<>(pocket);
        var inventory = mock(net.minecraft.world.entity.player.Inventory.class);
        when(world.player.getInventory()).thenReturn(inventory);
        when(inventory.getContainerSize()).thenReturn(1);
        when(inventory.getItem(0)).thenAnswer(call -> carried ? ItemStack.EMPTY : current.get());
        world.player.containerMenu = mock(net.minecraft.world.inventory.AbstractContainerMenu.class);
        when(world.player.containerMenu.getCarried()).thenAnswer(call -> carried ? current.get() : ItemStack.EMPTY);
        var sources = new ArrayList<OnlineJukebox.PersonalSource>();
        try (var audio = mockStatic(OnlineJukebox.class)) {
            audio.when(() -> OnlineJukebox.tryPlayPersonal(eq(world.player), any())).thenAnswer(call -> {
                sources.add(call.getArgument(1)); return true;
            });
            var ticks = net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.invoker();
            ticks.onEndTick(world.server); ticks.onEndTick(world.server);
            assertEquals(1, sources.size(), "Creative insertion must start one server-owned session");
            var source = sources.getFirst(); assertTrue(source.personal()); assertTrue(source.isActive());
            assertFalse(pocket.get(DataComponents.CUSTOM_DATA).copyTag().getStringOr("mysix_pocket_epoch", "").isEmpty());
            current.set(pocket.copy());
            ticks.onEndTick(world.server);
            assertEquals(1, sources.size()); assertTrue(source.isActive());
            CdRecording.remember(source.disc(), source.disc().getCustomName().getString(), "Yikl044w8d0");
            source.save();
            assertEquals("Yikl044w8d0", CdRecording.pinnedId(AdditionalAdditionsApi.stored(current.get())).orElseThrow());
            current.get().remove(DataComponents.CONTAINER);
            ticks.onEndTick(world.server);
            assertFalse(source.isActive());
            source.save(); assertFalse(current.get().has(DataComponents.CONTAINER));
            assertEquals(1, sources.size());
        }
    }

    @Test void discoveringSeveralCreativePocketsDoesNotSwitchBetweenThemEveryTick() {
        var world = new World(ItemStack.EMPTY);
        var first = new ItemStack(pocketItem); var second = new ItemStack(pocketItem);
        AdditionalAdditionsApi.store(first, BackpackPlaybackControllerTest.disc());
        AdditionalAdditionsApi.store(second, BackpackPlaybackControllerTest.disc());
        var inventory = mock(net.minecraft.world.entity.player.Inventory.class);
        when(world.player.getInventory()).thenReturn(inventory);
        when(inventory.getContainerSize()).thenReturn(2);
        when(inventory.getItem(0)).thenReturn(first); when(inventory.getItem(1)).thenReturn(second);
        world.player.containerMenu = mock(net.minecraft.world.inventory.AbstractContainerMenu.class);
        when(world.player.containerMenu.getCarried()).thenReturn(ItemStack.EMPTY);
        var sources = new ArrayList<OnlineJukebox.PersonalSource>();
        try (var audio = mockStatic(OnlineJukebox.class)) {
            audio.when(() -> OnlineJukebox.tryPlayPersonal(eq(world.player), any())).thenAnswer(call -> {
                sources.add(call.getArgument(1)); return true;
            });
            var ticks = net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.invoker();
            ticks.onEndTick(world.server); ticks.onEndTick(world.server); ticks.onEndTick(world.server);
            assertEquals(1, sources.size()); assertTrue(sources.getFirst().isActive());
        }
    }
}
