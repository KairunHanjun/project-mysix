package me.simonhamour.mysix;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import me.simonhamour.mysix.item.ModItems;
import me.simonhamour.mysix.music.CdSongSelection;
import me.simonhamour.mysix.music.CdRecording;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongs;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** Invoked only by Forge's development GameTest runner. No network downloads. */
public final class ForgeChecks {
    private ForgeChecks() {}
    public static void register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext context) {
        if (Boolean.getBoolean("mysix.soundPhysicsCheck")) {
            net.minecraftforge.data.event.GatherDataEvent.getBus(context.getModBusGroup()).addListener(event ->
                    me.simonhamour.mysix.client.ForgeSoundPhysicsChecks.run());
        }
        var functions = net.minecraftforge.registries.DeferredRegister.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, Mysix.MOD_ID);
        functions.register("recipes", () -> helper -> {
            try { recipes(helper); } catch (Exception error) { throw new IllegalStateException(error); }
        });
        functions.register("anvil", () -> ForgeChecks::anvil);
        functions.register("commands", () -> ForgeChecks::commands);
        functions.register(context.getModBusGroup());
        ForgePlaylistChecks.register(context);
        // Forge's GameTestServer starts with selected packs only. Explicitly
        // require our development pack so a vanilla-only run cannot look green.
        net.minecraftforge.event.AddPackFindersEvent.BUS.addListener(event -> {
            if (!Boolean.getBoolean("forge.gameTestServer") || event.getPackType() != net.minecraft.server.packs.PackType.SERVER_DATA) return;
            event.addRepositorySource(consumer -> {
                for (var modId : java.util.List.of(Mysix.MOD_ID, "jukebox_playlists")) {
                if (!net.minecraftforge.fml.ModList.isLoaded(modId)) continue;
                var root = net.minecraftforge.fml.ModList.getModContainerById(modId).orElseThrow()
                        .getModInfo().getOwningFile().getFile().findResource("");
                var info = new net.minecraft.server.packs.PackLocationInfo(modId + "-tests", Component.literal(modId + " development tests"),
                        net.minecraft.server.packs.repository.PackSource.DEFAULT, java.util.Optional.empty());
                var pack = net.minecraft.server.packs.repository.Pack.readMetaAndCreate(info,
                        new net.minecraft.server.packs.PathPackResources.PathResourcesSupplier(root),
                        net.minecraft.server.packs.PackType.SERVER_DATA,
                        new net.minecraft.server.packs.PackSelectionConfig(true, net.minecraft.server.packs.repository.Pack.Position.BOTTOM, false));
                if (pack == null) throw new IllegalStateException("Missing Mysix development test pack metadata");
                consumer.accept(pack);
                }
            });
        });
    }
    public static void recipes(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        helper.assertTrue(ModItems.CDS.size() == 16, "All colors must register");
        for (var entry : ModItems.CDS.entrySet()) {
            var color = entry.getKey();
            try (var input = ForgeChecks.class.getResourceAsStream("/data/mysix/recipe/" + color.getName() + "_cd.json")) {
                helper.assertTrue(input != null, "Missing recipe for " + color);
                var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                var recipe = (ShapedRecipe) Recipe.DIRECT_CODEC.parse(ops, json).getOrThrow();
                var grid = new ArrayList<ItemStack>();
                for (int i = 0; i < 9; i++) grid.add(new ItemStack(Items.GLASS_PANE));
                grid.set(4, new ItemStack(Items.REDSTONE));
                grid.set(7, new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(color.getName() + "_dye"))));
                var crafting = CraftingInput.of(3, 3, grid);
                helper.assertTrue(recipe.matches(crafting, level), "Recipe must match " + color);
                helper.assertTrue(recipe.assemble(crafting).is(entry.getValue().get()), "Recipe must craft correct color");
                grid.set(4, new ItemStack(Items.DIRT));
                helper.assertTrue(!recipe.matches(CraftingInput.of(3, 3, grid), level), "Wrong center must not craft");
            }
        }
        helper.succeed();
    }
    public static void anvil(GameTestHelper helper) {
        var player = helper.makeMockServerPlayer();
        var menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
        var disc = new ItemStack(ModItems.CDS.values().iterator().next().get());
        menu.getSlot(0).set(disc);
        menu.setItemName("C418 - Cat");
        var named = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        helper.assertTrue(!named.isEmpty() && JukeboxSong.fromStack(named).orElseThrow().is(JukeboxSongs.CAT), "Anvil must select Cat");
        menu.setItemName("Dhyo Haw - Ada Aku Disini");
        var external = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        helper.assertTrue(!external.isEmpty() && !external.has(DataComponents.JUKEBOX_PLAYABLE), "External name must clear vanilla song");
        CdRecording.remember(external, external.getCustomName().getString(), "Yikl044w8d0");
        helper.assertTrue(CdRecording.pinnedId(external.copy()).orElseThrow().equals("Yikl044w8d0"), "Recording must survive stack copy");
        external.set(DataComponents.CUSTOM_NAME, Component.literal("Other Artist - Other Song"));
        helper.assertTrue(CdRecording.pinnedId(external).isEmpty(), "Renaming must invalidate pinned recording");
        helper.succeed();
    }
    public static void commands(GameTestHelper helper) {
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        var mysix = dispatcher.getRoot().getChild("mysix");
        helper.assertTrue(mysix != null && mysix.getChild("setup") != null && mysix.getChild("check") != null
                && mysix.getChild("download") != null, "Server commands must register through Forge");
        helper.succeed();
    }
}
