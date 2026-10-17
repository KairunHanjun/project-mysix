package me.simonhamour.mysix.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import me.simonhamour.mysix.download.LocalAudioFiles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.server.permissions.Permissions;

public final class MysixClient implements ClientModInitializer {
    private static DownloadedSound playing;

    @Override
    public void onInitializeClient() {
        JukeboxPlayback.initialize();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                registerCommands(dispatcher, (source, packet) -> {
                    var connection = source.getClient().getConnection();
                    if (connection == null) {
                        source.sendError(Component.literal("Join a world before downloading music."));
                        return;
                    }
                    // sendCommand re-enters Fabric's client dispatcher. Send the unsigned
                    // command packet directly; the server still checks operator permissions.
                    connection.send(packet);
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop());
    }

    static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher,
            BiConsumer<FabricClientCommandSource, ServerboundChatCommandPacket> sendToServer) {
        dispatcher.register(ClientCommands.literal("mysix")
                        .requires(source -> source.getPlayer() != null && source.getPlayer()
                                .permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(ClientCommands.literal("download")
                                .then(ClientCommands.argument("song", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            sendToServer.accept(context.getSource(),
                                                    new ServerboundChatCommandPacket(context.getInput()));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("play")
                                .executes(context -> play(context.getSource(), null))
                                .then(ClientCommands.argument("videoId", StringArgumentType.word())
                                        .executes(context -> play(context.getSource(),
                                                StringArgumentType.getString(context, "videoId")))))
                        .then(ClientCommands.literal("stop").executes(context -> {
                            stop();
                            context.getSource().sendFeedback(Component.literal("Mysix playback stopped."));
                            return 1;
                        })));
    }

    private static int play(FabricClientCommandSource source, String videoId) {
        try {
            Path directory = FabricLoader.getInstance().getGameDir().resolve("mysix-cache/downloads");
            var file = LocalAudioFiles.find(directory, videoId);
            stop();
            var sound = new DownloadedSound(file, message -> Minecraft.getInstance().execute(() ->
                    source.sendError(Component.literal("Cannot decode Mysix audio: " + message))));
            playing = sound;
            var result = Minecraft.getInstance().getSoundManager().play(sound);
            if (result == SoundEngine.PlayResult.NOT_STARTED) {
                playing = null;
                source.sendError(Component.literal("Audio did not start. Check Master and Jukebox/Note Blocks volume, then retry."));
                return 0;
            }
            source.sendFeedback(Component.literal("Playing local download: " + file.getFileName()
                    + " (Jukebox/Note Blocks volume). Use /mysix stop to stop."));
            return 1;
        } catch (Exception error) {
            source.sendError(Component.literal(error.getMessage()));
            return 0;
        }
    }

    private static void stop() {
        if (playing != null) {
            Minecraft.getInstance().getSoundManager().stop(playing);
            playing = null;
        }
    }
}
