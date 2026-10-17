package me.simonhamour.mysix.item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import me.simonhamour.mysix.Mysix;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.JukeboxSongs;


public final class ModItems {
    public static final Map<DyeColor, Item> CDS = registerCds();

    private ModItems() {}

    private static Map<DyeColor, Item> registerCds() {
    Map<DyeColor, Item> items = new LinkedHashMap<>();
    
    for (DyeColor color : DyeColor.values()) {
            // 1. Generate the ID and Key for this specific color
            Identifier id = Mysix.id(color.getName() + "_cd");
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
            
            // 2. Inject the key into the properties BEFORE creating the Item
            // Vanilla handles insertion, playback synchronization, and ejection.
            // Use Cat as a temporary song until custom streaming is implemented.
            Item.Properties properties = new Item.Properties().setId(key).stacksTo(1)
                    .jukeboxPlayable(JukeboxSongs.CAT);
            Item item = new MysixCdItem(properties);
            
            // 3. Register the item and add it to the map
            Registry.register(BuiltInRegistries.ITEM, key, item);
            items.put(color, item);
        }
        
        return Collections.unmodifiableMap(items);
    }

    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(output -> CDS.values().forEach(output::accept));
    }
}
