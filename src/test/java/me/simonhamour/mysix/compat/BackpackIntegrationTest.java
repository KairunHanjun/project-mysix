package me.simonhamour.mysix.compat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import me.simonhamour.mysix.item.ModItems;
import net.minecraft.core.component.DataComponents;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "mysix.backpackTests", matches = "true")
class BackpackIntegrationTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        assertEquals(16, ModItems.CDS.size());
        var lookup = net.minecraft.data.registries.VanillaRegistries.createWorldLookup();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup)
                .forEach(pending -> pending.apply());
    }

    @Test void supportedReleaseAndAllReflectedApiSignaturesMatch() throws Exception {
        assertTrue(FabricLoader.getInstance().isModLoaded("travelersbackpack"));
        assertTrue(TravelersBackpackCompat.supported());
        assertNotNull(new TravelersBackpackCompat.Api());
    }

    @Test void realTransformedBackpackSlotAcceptsMysixExternalCdAndRejectsUnrelatedItems() throws Exception {
        Class<?> type = Class.forName("com.tiviacz.travelersbackpack.inventory.upgrades.jukebox.JukeboxUpgrade$2");
        var handler = mock(type, CALLS_REAL_METHODS);
        var valid = type.getMethod("isItemValid", int.class, ItemStack.class); valid.setAccessible(true);
        var cd = BackpackPlaybackControllerTest.disc();
        assertFalse(cd.has(DataComponents.JUKEBOX_PLAYABLE));
        assertEquals(true, valid.invoke(handler, 0, cd));
        assertEquals(false, valid.invoke(handler, 0, new ItemStack(Items.DIAMOND)));
        assertEquals(true, valid.invoke(handler, 0, new ItemStack(Items.MUSIC_DISC_CAT)));
    }

    @Test void realTransformedWidgetDoesNotPlayCatOverExternalRecording() throws Exception {
        Class<?> type = Class.forName("com.tiviacz.travelersbackpack.inventory.upgrades.jukebox.JukeboxWidget");
        var widget = mock(type, CALLS_REAL_METHODS);
        var get = type.getMethod("getFromDisk", ItemStack.class);
        var cd = BackpackPlaybackControllerTest.disc();
        cd.set(DataComponents.JUKEBOX_PLAYABLE, new ItemStack(Items.MUSIC_DISC_CAT).get(DataComponents.JUKEBOX_PLAYABLE));
        assertNull(get.invoke(widget, cd));
        assertNotNull(get.invoke(widget, new ItemStack(Items.MUSIC_DISC_CAT)));
    }
}
