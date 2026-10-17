package me.simonhamour.mysix.compat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.atomic.AtomicReference;
import me.simonhamour.mysix.item.ModItems;
import me.simonhamour.mysix.music.OnlineJukebox;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledIfSystemProperty(named = "mysix.visualTests", matches = "true")
class VisualJukeboxIntegrationTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        assertEquals(16, ModItems.CDS.size());
        var lookup = net.minecraft.data.registries.VanillaRegistries.createWorldLookup();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup)
                .forEach(pending -> pending.apply());
        me.simonhamour.mysix.testing.ItemTagBootstrap.refresh();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void actualHolderAnimatesExternalPlaybackAndEjectionClearsDisplay(boolean staticDiscs) throws Exception {
        var level = mock(ServerLevel.class);
        var state = new AtomicReference<>(Blocks.JUKEBOX.defaultBlockState());
        var box = new JukeboxBlockEntity(BlockPos.ZERO, state.get()); box.setLevel(level);
        when(level.getBlockState(BlockPos.ZERO)).thenAnswer(call -> state.get());
        when(level.setBlock(eq(BlockPos.ZERO), any(), anyInt())).thenAnswer(call -> {
            state.set(call.getArgument(1)); box.setBlockState(state.get()); return true;
        });
        when(level.getRandom()).thenReturn(RandomSource.create(1));
        when(level.addFreshEntity(any())).thenReturn(true);
        var type = Class.forName("de.tomalbrc.visualjukebox.JukeboxHolder");
        var holder = type.getConstructor(JukeboxBlockEntity.class).newInstance(box);
        var holderField = JukeboxBlockEntity.class.getDeclaredField("visualjukebox$holder");
        holderField.setAccessible(true); holderField.set(box, holder);
        var elementField = type.getDeclaredField("discElement"); elementField.setAccessible(true);
        Object element = elementField.get(holder);
        var configType = Class.forName("de.tomalbrc.visualjukebox.ModConfig");
        var config = configType.getConstructor().newInstance();
        configType.getField("staticDiscs").setBoolean(config, staticDiscs);
        var configGetter = configType.getMethod("getInstance");
        var tick = type.getMethod("tick");
        var time = type.getMethod("getTime");
        var stopped = type.getDeclaredField("stopped"); stopped.setAccessible(true);
        try (var settings = mockStatic(configType); var audio = mockStatic(OnlineJukebox.class)) {
            settings.when(() -> configGetter.invoke(null)).thenReturn(config);
            var disc = new ItemStack(ModItems.CDS.get(DyeColor.RED));
            disc.set(DataComponents.CUSTOM_NAME, Component.literal("Dhyo Haw - Ada Aku Disini"));
            disc.remove(DataComponents.JUKEBOX_PLAYABLE);
            box.setTheItem(disc);
            assertTrue(box.getBlockState().getValue(JukeboxBlock.HAS_RECORD));
            tick.invoke(holder);
            assertEquals(0L, time.invoke(holder), "No rotation during download");
            assertTrue(stopped.getBoolean(holder));
            audio.when(() -> OnlineJukebox.isPlayingAt(box)).thenReturn(true);
            tick.invoke(holder);
            assertEquals(1L, time.invoke(holder)); assertFalse(stopped.getBoolean(holder));
            assertFalse(box.getSongPlayer().isPlaying(), "No vanilla audio should start for animation");
            var shown = (ItemStack) element.getClass().getMethod("getItem").invoke(element);
            assertTrue(ItemStack.matches(disc, shown), "Display must keep the actual colored CD and name");
            audio.when(() -> OnlineJukebox.isPlayingAt(box)).thenReturn(false);
            tick.invoke(holder);
            assertEquals(1L, time.invoke(holder)); assertTrue(stopped.getBoolean(holder));
            box.popOutTheItem();
            assertTrue(((ItemStack) element.getClass().getMethod("getItem").invoke(element)).isEmpty());
            verify(level, times(1)).addFreshEntity(any());
        }
    }
}
