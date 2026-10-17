package me.simonhamour.mysix.compat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "mysix.sculkTests", matches = "true")
class SculkRadioIntegrationTest {
    static final String ROOT = "nl.gjorgdy.sculk_radio.";
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        me.simonhamour.mysix.testing.ItemTagBootstrap.refresh();
        assertTrue(SculkRadioCompat.supported());
        SculkRadioCompat.initialize();
    }
    static Object node(String name, BlockPos pos, ServerLevel level) throws Exception {
        var type = Class.forName(ROOT + "objects.nodes." + name);
        var node = type.getConstructor(BlockPos.class).newInstance(pos);
        type.getMethod("init", ServerLevel.class).invoke(node, level);
        return node;
    }
    static void connect(Object from, Object to) throws Exception {
        from.getClass().getMethod("connect", Class.forName(ROOT + "objects.nodes.abstracts.Node")).invoke(from, to);
    }

    @Test void realStreamTraversesRelayAndDisconnectsRemovedSpeaker() throws Exception {
        var level = mock(ServerLevel.class);
        when(level.isLoaded(any(BlockPos.class))).thenReturn(true);
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());
        var radio = node("audio.RadioNode", BlockPos.ZERO.above(), level);
        var relay = node("RelayNode", new BlockPos(12, 1, 0), level);
        var speakerPos = new BlockPos(24, 1, 0);
        var speaker = node("audio.SpeakerNode", speakerPos, level);
        connect(radio, relay); connect(relay, speaker);
        var shrieker = new SculkShriekerBlockEntity(BlockPos.ZERO.above(), Blocks.SCULK_SHRIEKER.defaultBlockState());
        shrieker.setLevel(level);
        var nodeType = Class.forName(ROOT + "objects.nodes.abstracts.Node");
        shrieker.getClass().getMethod("sculkRadio$setNode", nodeType).invoke(shrieker, radio);
        when(level.getBlockEntity(BlockPos.ZERO.above())).thenReturn(shrieker);
        var visuals = Class.forName(ROOT + "utils.VisualUtils");
        try (var ignored = mockStatic(visuals)) {
            var route = SculkRadioCompat.open(level, BlockPos.ZERO);
            assertNotNull(route); assertTrue(route.active());
            assertEquals(List.of(speakerPos), route.outputs().stream().map(output -> output.pos()).toList());
            assertEquals(48, route.outputs().getFirst().range());
            // Use the real upstream connection tick after removing a registered node.
            speaker.getClass().getMethod("afterRemove").invoke(speaker);
            radio.getClass().getMethod("connectionTick").invoke(radio);
            assertTrue(route.outputs().isEmpty());
            route.close(); route.close(); assertFalse(route.active());
        }
    }

    @Test void ordinaryJukeboxDoesNotCreateRadioRoute() {
        assertNull(SculkRadioCompat.open(mock(ServerLevel.class), BlockPos.ZERO));
    }

    @Test void releasedModEntrypointInitializesWithoutVoiceChatOrAudioPlayer() throws Exception {
        var type = Class.forName(ROOT + "SculkRadio");
        var mod = type.getConstructor().newInstance();
        type.getMethod("onInitialize").invoke(mod);
    }
}
