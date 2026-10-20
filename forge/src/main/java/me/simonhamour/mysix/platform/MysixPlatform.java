package me.simonhamour.mysix.platform;

import java.nio.file.Path;
import java.util.function.Consumer;
import com.mojang.brigadier.CommandDispatcher;
import me.simonhamour.mysix.music.OnlineJukebox;
import me.simonhamour.mysix.network.JukeboxAudioPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Only Forge-specific common hooks; no client classes are loaded on the server. */
public final class MysixPlatform {
    private static Channel<CustomPacketPayload> audio;
    private static Consumer<JukeboxAudioPayload> clientReceiver = packet -> {};
    private MysixPlatform() {}
    public static Path gameDirectory() { return FMLPaths.GAMEDIR.get(); }
    public static Path configDirectory() { return FMLPaths.CONFIGDIR.get(); }
    public static void onServerStarted(Consumer<MinecraftServer> callback) { ServerStartedEvent.BUS.addListener(event -> callback.accept(event.getServer())); }
    public static void onServerStopping(Consumer<MinecraftServer> callback) { ServerStoppingEvent.BUS.addListener(event -> callback.accept(event.getServer())); }
    public static void onServerTick(Consumer<MinecraftServer> callback) { TickEvent.ServerTickEvent.Post.BUS.addListener(event -> callback.accept(event.server())); }
    public static void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> callback) {
        RegisterCommandsEvent.BUS.addListener(event -> callback.accept(event.getDispatcher()));
    }
    public static void registerAudio() {
        audio = ChannelBuilder.named("mysix:audio").networkProtocolVersion(3)
                .payloadChannel().play().clientbound()
                .addMain(JukeboxAudioPayload.TYPE, JukeboxAudioPayload.CODEC, (packet, context) -> clientReceiver.accept(packet))
                .build();
    }
    public static void clientReceiver(Consumer<JukeboxAudioPayload> receiver) { clientReceiver = receiver; }
    public static boolean canSend(ServerPlayer player) { return audio != null && audio.isRemotePresent(player.connection.getConnection()); }
    public static void send(ServerPlayer player, JukeboxAudioPayload packet) { audio.send(packet, player.connection.getConnection()); }
    public static OnlineJukebox.Route openRoute(ServerLevel level, BlockPos pos) { return null; }
}
