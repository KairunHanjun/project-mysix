package me.simonhamour.mysix.download;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import org.junit.jupiter.api.*;

class SetupCommandPermissionsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void dedicatedServerInstallerRequiresOwnerPermissionNotOrdinaryOperator() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>(); DownloadCommands.registerCommands(dispatcher);
        var source = mock(CommandSourceStack.class); var permissions = mock(PermissionSet.class);
        when(source.permissions()).thenReturn(permissions);
        when(source.getServer()).thenReturn(mock(MinecraftServer.class));
        when(permissions.hasPermission(Permissions.COMMANDS_GAMEMASTER)).thenReturn(true);
        var root = dispatcher.getRoot().getChild("mysix");
        assertTrue(root.canUse(source)); assertFalse(root.getChild("setup").canUse(source));
        assertTrue(root.getChild("check").canUse(source));
        when(permissions.hasPermission(Permissions.COMMANDS_OWNER)).thenReturn(true);
        assertTrue(root.getChild("setup").canUse(source));
    }
    @Test void singleplayerOwnerCanInstallWithoutEnablingCheatOnlyDownloadCommands() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>(); DownloadCommands.registerCommands(dispatcher);
        var source = mock(CommandSourceStack.class); var permissions = mock(PermissionSet.class);
        var server = mock(MinecraftServer.class); var player = mock(ServerPlayer.class);
        when(source.permissions()).thenReturn(permissions); when(source.getServer()).thenReturn(server);
        when(source.getPlayer()).thenReturn(player); when(server.isSingleplayer()).thenReturn(true);
        when(server.isSingleplayerOwner(player.nameAndId())).thenReturn(true);
        var root = dispatcher.getRoot().getChild("mysix");
        assertTrue(root.canUse(source)); assertTrue(root.getChild("setup").canUse(source));
        assertFalse(root.getChild("check").canUse(source)); assertFalse(root.getChild("download").canUse(source));
    }
}
