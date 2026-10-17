package me.simonhamour.mysix.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClientCommandRoutingTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static FabricClientCommandSource sourceWithCheats(boolean allowed) {
        var source = mock(FabricClientCommandSource.class);
        var player = mock(LocalPlayer.class);
        var permissions = mock(PermissionSet.class);
        when(source.getPlayer()).thenReturn(player);
        when(player.permissions()).thenReturn(permissions);
        when(permissions.hasPermission(Permissions.COMMANDS_GAMEMASTER)).thenReturn(allowed);
        return source;
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "mysix download Dhyo Haw - Ada Aku Disini",
            "mysix download https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "mysix download Björk - Jóga"
    })
    void downloadReachesServerOnceWithOriginalQuery(String command) throws Exception {
        var dispatcher = new CommandDispatcher<FabricClientCommandSource>();
        var source = sourceWithCheats(true);
        var packets = new ArrayList<ServerboundChatCommandPacket>();
        MysixClient.registerCommands(dispatcher, (sender, packet) -> {
            assertSame(source, sender);
            packets.add(packet);
        });

        assertEquals(1, dispatcher.execute(command, source));
        assertEquals(1, packets.size());
        assertEquals(command, packets.getFirst().command());
        verify(source, never()).sendError(any());
    }

    @Test
    void missingSongDoesNotSendPacket() {
        var dispatcher = new CommandDispatcher<FabricClientCommandSource>();
        var packets = new ArrayList<ServerboundChatCommandPacket>();
        MysixClient.registerCommands(dispatcher, (source, packet) -> packets.add(packet));
        assertThrows(CommandSyntaxException.class,
                () -> dispatcher.execute("mysix download", sourceWithCheats(true)));
        assertTrue(packets.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysix play", "mysix play dQw4w9WgXcQ", "mysix stop"})
    void playbackCommandsStillParse(String command) {
        var dispatcher = new CommandDispatcher<FabricClientCommandSource>();
        MysixClient.registerCommands(dispatcher, (source, packet) -> fail("Unexpected server command"));
        var parsed = dispatcher.parse(command, sourceWithCheats(true));
        assertFalse(parsed.getReader().canRead());
        assertNotNull(parsed.getContext().getCommand());
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysix download Dhyo Haw - Ada Aku Disini", "mysix play",
            "mysix play Yikl044w8d0", "mysix stop"})
    void cheatsDisabledRejectsEveryTestCommand(String command) {
        var dispatcher = new CommandDispatcher<FabricClientCommandSource>();
        MysixClient.registerCommands(dispatcher, (source, packet) -> fail("Must not forward without permission"));
        var source = sourceWithCheats(false);
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute(command, source));
        // Fabric filters the displayed command tree with each node's canUse predicate.
        assertFalse(dispatcher.getRoot().getChild("mysix").canUse(source));
    }
}
