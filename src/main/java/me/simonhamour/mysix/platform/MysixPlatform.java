package me.simonhamour.mysix.platform;

import java.nio.file.Path;
import java.util.function.Consumer;
import com.mojang.brigadier.CommandDispatcher;
import me.simonhamour.mysix.music.OnlineJukebox;
import me.simonhamour.mysix.network.JukeboxAudioPayload;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Fabric bindings for the core shared with the separate Forge build. */
public final class MysixPlatform {
    private MysixPlatform() {}
    public static Path gameDirectory() { return FabricLoader.getInstance().getGameDir(); }
    public static Path configDirectory() { return FabricLoader.getInstance().getConfigDir(); }
    public static void onServerStarted(Consumer<MinecraftServer> callback) { ServerLifecycleEvents.SERVER_STARTED.register(callback::accept); }
    public static void onServerStopping(Consumer<MinecraftServer> callback) { ServerLifecycleEvents.SERVER_STOPPING.register(callback::accept); }
    public static void onServerTick(Consumer<MinecraftServer> callback) { ServerTickEvents.END_SERVER_TICK.register(callback::accept); }
    public static void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> callback) {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> callback.accept(dispatcher));
    }
    public static void registerAudio() { PayloadTypeRegistry.clientboundPlay().register(JukeboxAudioPayload.TYPE, JukeboxAudioPayload.CODEC); }
    public static boolean canSend(ServerPlayer player) { return ServerPlayNetworking.canSend(player, JukeboxAudioPayload.TYPE); }
    public static void send(ServerPlayer player, JukeboxAudioPayload packet) { ServerPlayNetworking.send(player, packet); }
    public static OnlineJukebox.Route openRoute(ServerLevel level, BlockPos pos) {
        return me.simonhamour.mysix.compat.SculkRadioCompat.open(level, pos);
    }
}
