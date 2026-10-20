package me.simonhamour.mysix.item;

import java.util.LinkedHashMap;
import java.util.Map;
import me.simonhamour.mysix.Mysix;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.JukeboxSongs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class ModItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Mysix.MOD_ID);
    public static final Map<DyeColor, RegistryObject<Item>> CDS = new LinkedHashMap<>();
    static {
        for (var color : DyeColor.values()) {
            String name = color.getName() + "_cd";
            CDS.put(color, ITEMS.register(name, () -> new MysixCdItem(new Item.Properties()
                    .setId(ITEMS.key(name)).stacksTo(1).jukeboxPlayable(JukeboxSongs.CAT))));
        }
    }
    private ModItems() {}
    public static void initialize(FMLJavaModLoadingContext context) {
        ITEMS.register(context.getModBusGroup());
        BuildCreativeModeTabContentsEvent.BUS.addListener(event -> {
            if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) CDS.values().forEach(event::accept);
        });
    }
}
