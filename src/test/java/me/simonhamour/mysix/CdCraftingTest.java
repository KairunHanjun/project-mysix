package me.simonhamour.mysix;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.lang.reflect.Proxy;
import me.simonhamour.mysix.item.ModItems;
import me.simonhamour.mysix.music.CdSongSelection;
import me.simonhamour.mysix.music.CdJukeboxInteraction;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongPlayer;
import net.minecraft.world.item.JukeboxSongs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.util.RandomSource;
import org.mockito.ArgumentCaptor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Exercises real Minecraft recipe decoding and matching without a game window or world. */
class CdCraftingTest {
    private static RegistryOps<JsonElement> ops;
    private static RegistryAccess jukeboxRegistries;

    @BeforeAll
    static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        assertEquals(16, ModItems.CDS.size());
        var worldLookup = VanillaRegistries.createWorldLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(worldLookup)
                .forEach(pending -> pending.apply());
        var songs = new MappedRegistry<JukeboxSong>(Registries.JUKEBOX_SONG, Lifecycle.stable());
        worldLookup.lookupOrThrow(Registries.JUKEBOX_SONG).listElements().forEach(holder ->
                Registry.register(songs, holder.key(), holder.value()));
        songs.freeze();
        jukeboxRegistries = new RegistryAccess.ImmutableRegistryAccess(List.of(songs));
        var builtins = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        ops = RegistryOps.create(JsonOps.INSTANCE, builtins);
        // Advancements reference the data-pack recipe registry, which a full server
        // normally creates. Populate it with the actual decoded recipes here.
        var recipes = new MappedRegistry<Recipe<?>>(Registries.RECIPE, Lifecycle.stable());
        for (DyeColor color : DyeColor.values()) {
            Registry.register(recipes, Mysix.id(color.getName() + "_cd"), recipe(color));
        }
        recipes.freeze();
        ops = RegistryOps.create(JsonOps.INSTANCE, HolderLookup.Provider.create(
                java.util.stream.Stream.concat(builtins.listRegistries(),
                        java.util.stream.Stream.of(recipes, songs))));
    }

    private static JsonElement resource(String path) throws Exception {
        var stream = CdCraftingTest.class.getResourceAsStream("/" + path);
        assertNotNull(stream, "Missing packaged resource: " + path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }

    private static ShapedRecipe recipe(DyeColor color) throws Exception {
        var decoded = Recipe.DIRECT_CODEC.parse(ops,
                resource("data/mysix/recipe/" + color.getName() + "_cd.json"));
        return assertInstanceOf(ShapedRecipe.class, decoded.getOrThrow());
    }

    private static Item dye(DyeColor color) {
        return BuiltInRegistries.ITEM.getValue(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(
                        "minecraft", color.getName() + "_dye"));
    }

    private static List<ItemStack> grid(DyeColor color) {
        var grid = new ArrayList<ItemStack>();
        for (int i = 0; i < 9; i++) grid.add(new ItemStack(Items.GLASS_PANE));
        grid.set(4, new ItemStack(Items.REDSTONE));
        grid.set(7, new ItemStack(dye(color)));
        return grid;
    }

    @ParameterizedTest @EnumSource(DyeColor.class)
    void craftsExactlyOneCdOfTheRequestedColor(DyeColor color) throws Exception {
        var input = CraftingInput.of(3, 3, grid(color));
        var recipe = recipe(color);
        assertTrue(recipe.matches(input, null));
        ItemStack result = recipe.assemble(input);
        assertSame(ModItems.CDS.get(color), result.getItem());
        assertEquals(1, result.getCount());
        assertEquals(1, result.getMaxStackSize());
    }

    @ParameterizedTest @EnumSource(DyeColor.class)
    void rejectsEveryOtherDye(DyeColor color) throws Exception {
        var recipe = recipe(color);
        for (DyeColor other : DyeColor.values()) {
            if (other != color) assertFalse(recipe.matches(
                    CraftingInput.of(3, 3, grid(other)), null), other.getName());
        }
    }

    @ParameterizedTest @EnumSource(DyeColor.class)
    void rejectsMissingOrReplacedIngredients(DyeColor color) throws Exception {
        var recipe = recipe(color);
        for (int slot = 0; slot < 9; slot++) {
            var missing = grid(color);
            missing.set(slot, ItemStack.EMPTY);
            assertFalse(recipe.matches(CraftingInput.of(3, 3, missing), null), "Missing slot " + slot);
            var replaced = grid(color);
            replaced.set(slot, new ItemStack(Items.DIRT));
            assertFalse(recipe.matches(CraftingInput.of(3, 3, replaced), null), "Dirt in slot " + slot);
        }
    }

    @ParameterizedTest @EnumSource(DyeColor.class)
    void rejectsTwoByTwoCraftingGrid(DyeColor color) throws Exception {
        assertFalse(recipe(color).matches(CraftingInput.of(2, 2,
                Collections.nCopies(4, new ItemStack(Items.GLASS_PANE))), null));
    }

    @ParameterizedTest @EnumSource(DyeColor.class)
    void recipeBookUnlockParsesWithMinecraftCodec(DyeColor color) throws Exception {
        Advancement.CODEC.parse(ops, resource("data/mysix/advancement/recipes/"
                + color.getName() + "_cd.json")).getOrThrow();
    }

    @ParameterizedTest @EnumSource(DyeColor.class)
    void jukeboxAcceptsCdAndResolvesItsPlayableSong(DyeColor color) {
        var stack = new ItemStack(ModItems.CDS.get(color));
        var jukebox = new JukeboxBlockEntity(BlockPos.ZERO, Blocks.JUKEBOX.defaultBlockState());
        assertTrue(jukebox.canPlaceItem(0, stack));
        assertFalse(jukebox.canPlaceItem(0, new ItemStack(Items.DIRT)));
        var song = JukeboxSong.fromStack(stack).orElseThrow();
        assertTrue(song.is(JukeboxSongs.CAT));
    }

    @ParameterizedTest @EnumSource(DyeColor.class)
    void vanillaSongPlayerEmitsStartAndStopEventsForCd(DyeColor color) {
        var events = new ArrayList<Integer>();
        // The real song player runs against a minimal world boundary. No game
        // window, network, audio device, or mocked playback algorithm is involved.
        var world = (LevelAccessor) Proxy.newProxyInstance(
                LevelAccessor.class.getClassLoader(), new Class<?>[]{LevelAccessor.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "registryAccess": return jukeboxRegistries;
                        case "levelEvent":
                            for (Object arg : args) {
                                if (arg instanceof Integer event) {
                                    events.add(event);
                                    break;
                                }
                            }
                            return null;
                        case "gameEvent": return null;
                        default: throw new UnsupportedOperationException(method.toString());
                    }
                });
        var song = JukeboxSong.fromStack(new ItemStack(ModItems.CDS.get(color))).orElseThrow();
        int[] changes = {0};
        var player = new JukeboxSongPlayer(() -> changes[0]++, BlockPos.ZERO);
        assertFalse(player.isPlaying());
        player.play(world, song);
        assertTrue(player.isPlaying());
        assertSame(song.value(), player.getSong());
        assertEquals(0, player.getTicksSinceSongStarted());
        assertEquals(List.of(1010), events);
        player.stop(world, Blocks.JUKEBOX.defaultBlockState());
        assertFalse(player.isPlaying());
        assertEquals(List.of(1010, 1011), events);
        assertEquals(2, changes[0]);
    }

    private static AnvilMenu anvil() {
        // Only the player/world boundary is mocked; this runs the actual anvil
        // calculation and our injected callback, not a recreation of its logic.
        Player player = mock(Player.class);
        when(player.registryAccess()).thenReturn(jukeboxRegistries);
        return new AnvilMenu(1, new Inventory(player, new EntityEquipment()));
    }

    @ParameterizedTest @EnumSource(DyeColor.class)
    void actualAnvilRenameSelectsSongWithoutChangingOriginalStack(DyeColor color) {
        var input = new ItemStack(ModItems.CDS.get(color));
        var menu = anvil();
        menu.getSlot(AnvilMenu.INPUT_SLOT).set(input);
        assertTrue(menu.setItemName("Lena Raine - Pigstep"));
        var result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        assertFalse(result.isEmpty());
        assertTrue(JukeboxSong.fromStack(result).orElseThrow().is(JukeboxSongs.PIGSTEP));
        assertEquals("Lena Raine - Pigstep", result.getCustomName().getString());
        assertEquals(1, result.getCount());
        assertTrue(JukeboxSong.fromStack(input).orElseThrow().is(JukeboxSongs.CAT));
        // Changing the preview updates it rather than retaining Pigstep.
        assertTrue(menu.setItemName("C418 - Stal"));
        assertTrue(JukeboxSong.fromStack(menu.getSlot(AnvilMenu.RESULT_SLOT).getItem())
                .orElseThrow().is(JukeboxSongs.STAL));
    }

    @ParameterizedTest @EnumSource(DyeColor.class)
    void invalidAnvilRenameRemovesStaleSongAndCanBeCorrected(DyeColor color) {
        var menu = anvil();
        menu.getSlot(AnvilMenu.INPUT_SLOT).set(new ItemStack(ModItems.CDS.get(color)));
        menu.setItemName("C418 - Cat Live");
        var result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        assertFalse(result.isEmpty());
        assertTrue(JukeboxSong.fromStack(result).isEmpty());
        assertFalse(new JukeboxBlockEntity(BlockPos.ZERO, Blocks.JUKEBOX.defaultBlockState())
                .canPlaceItem(0, result));
        menu.setItemName("C418 - Cat");
        assertTrue(JukeboxSong.fromStack(menu.getSlot(AnvilMenu.RESULT_SLOT).getItem())
                .orElseThrow().is(JukeboxSongs.CAT));
    }

    @ParameterizedTest @EnumSource(DyeColor.class)
    void removingAnvilNameRestoresDefaultSong(DyeColor color) {
        var stack = new ItemStack(ModItems.CDS.get(color));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Lena Raine - Pigstep"));
        assertTrue(CdSongSelection.update(stack, jukeboxRegistries));
        var menu = anvil();
        menu.getSlot(AnvilMenu.INPUT_SLOT).set(stack);
        menu.setItemName("");
        var result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        assertFalse(result.isEmpty());
        assertNull(result.getCustomName());
        assertTrue(JukeboxSong.fromStack(result).orElseThrow().is(JukeboxSongs.CAT));
    }

    @org.junit.jupiter.api.Test
    void vanillaMusicDiscsAreNeverModified() {
        var stack = new ItemStack(Items.MUSIC_DISC_CAT);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Lena Raine - Pigstep"));
        var before = stack.get(DataComponents.JUKEBOX_PLAYABLE);
        assertFalse(CdSongSelection.update(stack, jukeboxRegistries));
        assertSame(before, stack.get(DataComponents.JUKEBOX_PLAYABLE));
    }

    @ParameterizedTest @EnumSource(DyeColor.class)
    void renamedSongSurvivesItemSerializationAndDoesNotChangeAnotherCd(DyeColor color) {
        var stack = new ItemStack(ModItems.CDS.get(color));
        var other = new ItemStack(ModItems.CDS.get(color));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Aaron Cherof - Relic"));
        assertTrue(CdSongSelection.update(stack, jukeboxRegistries));
        var encoded = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
        var restored = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
        assertEquals("Aaron Cherof - Relic", restored.getCustomName().getString());
        assertTrue(JukeboxSong.fromStack(restored).orElseThrow().is(JukeboxSongs.RELIC));
        assertTrue(JukeboxSong.fromStack(other).orElseThrow().is(JukeboxSongs.CAT));
        assertSame(stack.getItem(), restored.getItem());
    }

    @org.junit.jupiter.api.Test
    void resolvedRecordingBelongsToDiscSurvivesSavingAndInvalidatesOnRename() {
        var disc = new ItemStack(ModItems.CDS.get(DyeColor.RED));
        disc.set(DataComponents.CUSTOM_NAME, Component.literal("Dhyo Haw - Ada Aku Disini"));
        me.simonhamour.mysix.music.CdRecording.remember(disc, "Dhyo Haw - Ada Aku Disini", "Yikl044w8d0");
        var encoded = ItemStack.CODEC.encodeStart(ops, disc).getOrThrow();
        var restored = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
        assertEquals("Yikl044w8d0", me.simonhamour.mysix.music.CdRecording.pinnedId(restored).orElseThrow());
        assertTrue(me.simonhamour.mysix.music.CdRecording.pinnedId(
                new ItemStack(ModItems.CDS.get(DyeColor.RED))).isEmpty());
        restored.set(DataComponents.CUSTOM_NAME, Component.literal("Different Artist - Different Song"));
        assertTrue(me.simonhamour.mysix.music.CdRecording.pinnedId(restored).isEmpty());
        assertEquals("Yikl044w8d0", me.simonhamour.mysix.music.CdRecording.pinnedId(disc).orElseThrow());
    }

    private record JukeboxFixture(Level level, Player player, JukeboxBlockEntity box,
            ItemStack stack, BlockHitResult hit) {}

    private static JukeboxFixture jukeboxFixture(DyeColor color, boolean client) {
        var level = mock(Level.class);
        when(level.isClientSide()).thenReturn(client);
        when(level.registryAccess()).thenReturn(jukeboxRegistries);
        when(level.getRandom()).thenReturn(RandomSource.create(1));
        when(level.addFreshEntity(any(Entity.class))).thenReturn(true);
        var state = new java.util.concurrent.atomic.AtomicReference<>(Blocks.JUKEBOX.defaultBlockState());
        var box = new JukeboxBlockEntity(BlockPos.ZERO, state.get());
        box.setLevel(level);
        when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(box);
        when(level.getBlockState(BlockPos.ZERO)).thenAnswer(invocation -> state.get());
        when(level.setBlock(eq(BlockPos.ZERO), any(), anyInt())).thenAnswer(invocation -> {
            state.set(invocation.getArgument(1));
            box.setBlockState(state.get());
            return true;
        });
        var stack = new ItemStack(ModItems.CDS.get(color));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Unknown Artist - Unknown Song"));
        var player = mock(Player.class);
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(stack);
        return new JukeboxFixture(level, player, box, stack,
                new BlockHitResult(Vec3.ZERO, Direction.UP, BlockPos.ZERO, false));
    }

    @ParameterizedTest @EnumSource(DyeColor.class)
    void unknownSongIsEjectedExactlyOnceWithHotbarErrorInSurvivalAndCreative(DyeColor color) {
        for (boolean creative : new boolean[]{false, true}) {
            var f = jukeboxFixture(color, false);
            when(f.player().hasInfiniteMaterials()).thenReturn(creative);
            assertSame(InteractionResult.SUCCESS, CdJukeboxInteraction.use(
                    f.player(), f.level(), InteractionHand.MAIN_HAND, f.hit()));
            assertTrue(f.stack().isEmpty(), "The original held disc must be transferred, not copied");
            assertTrue(f.box().getTheItem().isEmpty());
            assertFalse(f.level().getBlockState(BlockPos.ZERO).getValue(
                    net.minecraft.world.level.block.JukeboxBlock.HAS_RECORD));
            assertFalse(f.box().getSongPlayer().isPlaying());
            var dropped = ArgumentCaptor.forClass(Entity.class);
            verify(f.level(), times(1)).addFreshEntity(dropped.capture());
            var entity = assertInstanceOf(ItemEntity.class, dropped.getValue());
            assertSame(ModItems.CDS.get(color), entity.getItem().getItem());
            assertEquals(1, entity.getItem().getCount());
            assertEquals("Unknown Artist - Unknown Song", entity.getItem().getCustomName().getString());
            verify(f.player()).sendOverlayMessage(any(Component.class));
            verify(f.player(), never()).sendSystemMessage(any(Component.class));
            // A duplicate callback cannot eject a second copy from the empty hand.
            CdJukeboxInteraction.use(f.player(), f.level(), InteractionHand.MAIN_HAND, f.hit());
            verify(f.level(), times(1)).addFreshEntity(any(Entity.class));
            verify(f.player(), times(1)).sendOverlayMessage(any(Component.class));
        }
    }

    @ParameterizedTest @EnumSource(DyeColor.class)
    void clientSendsInteractionWithoutEjectingOrConsumingCd(DyeColor color) {
        var f = jukeboxFixture(color, true);
        assertSame(InteractionResult.SUCCESS, CdJukeboxInteraction.use(
                f.player(), f.level(), InteractionHand.MAIN_HAND, f.hit()));
        assertEquals(1, f.stack().getCount());
        assertTrue(f.box().getTheItem().isEmpty());
        verify(f.level(), never()).addFreshEntity(any(Entity.class));
        verify(f.player(), never()).sendOverlayMessage(any(Component.class));
    }

    @ParameterizedTest @EnumSource(DyeColor.class)
    void recognizedNameStillPassesToVanillaInsertion(DyeColor color) {
        var f = jukeboxFixture(color, false);
        f.stack().set(DataComponents.CUSTOM_NAME, Component.literal("Lena Raine - Pigstep"));
        assertSame(InteractionResult.PASS, CdJukeboxInteraction.use(
                f.player(), f.level(), InteractionHand.MAIN_HAND, f.hit()));
        assertTrue(JukeboxSong.fromStack(f.stack()).orElseThrow().is(JukeboxSongs.PIGSTEP));
        assertEquals(1, f.stack().getCount());
        verify(f.level(), never()).addFreshEntity(any(Entity.class));
        verify(f.player(), never()).sendOverlayMessage(any(Component.class));
    }

    @ParameterizedTest @EnumSource(DyeColor.class)
    void unknownHeldDiscDoesNotReplaceOrEjectAnOccupiedJukebox(DyeColor color) {
        var f = jukeboxFixture(color, false);
        var playing = new ItemStack(ModItems.CDS.get(color));
        f.box().setTheItem(playing);
        assertSame(InteractionResult.PASS, CdJukeboxInteraction.use(
                f.player(), f.level(), InteractionHand.MAIN_HAND, f.hit()));
        assertSame(playing, f.box().getTheItem());
        assertEquals(1, f.stack().getCount());
        verify(f.level(), never()).addFreshEntity(any(Entity.class));
        verify(f.player(), never()).sendOverlayMessage(any(Component.class));
    }
}
