package me.simonhamour.mysix.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import me.simonhamour.mysix.download.LocalAudioFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import me.simonhamour.mysix.platform.MysixPlatform;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.server.permissions.Permissions;

public final class MysixClient {
    private static DownloadedSound playing;

    public static void initialize() {
        JukeboxPlayback.initialize();
        RegisterClientCommandsEvent.BUS.addListener(event ->
                registerCommands(event.getDispatcher(), (source, packet) -> {
                    var connection = Minecraft.getInstance().getConnection();
                    if (connection == null) {
                        source.sendFailure(Component.literal("Join a world before downloading music."));
                        return;
                    }
                    // Forward server commands without re-entering the client dispatcher.
                    connection.send(packet);
                }));
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> stop());
    }

    static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher,
            BiConsumer<CommandSourceStack, ServerboundChatCommandPacket> sendToServer) {
        dispatcher.register(Commands.literal("mysix")
                        .requires(source -> canSetup(source) || hasCheats(source))
                        .then(Commands.literal("setup")
                                .requires(MysixClient::canSetup)
                                .executes(context -> {
                                    sendToServer.accept(context.getSource(), new ServerboundChatCommandPacket(context.getInput())); return 1;
                                }))
                        .then(Commands.literal("check")
                                .requires(MysixClient::hasCheats)
                                .executes(context -> {
                                    sendToServer.accept(context.getSource(), new ServerboundChatCommandPacket(context.getInput())); return 1;
                                })
                                .then(Commands.argument("song", StringArgumentType.greedyString()).executes(context -> {
                                    sendToServer.accept(context.getSource(), new ServerboundChatCommandPacket(context.getInput())); return 1;
                                })))
                        .then(Commands.literal("download")
                                .requires(MysixClient::hasCheats)
                                .then(Commands.argument("song", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            sendToServer.accept(context.getSource(),
                                                    new ServerboundChatCommandPacket(context.getInput()));
                                            return 1;
                                        })))
                        .then(Commands.literal("play")
                                .requires(MysixClient::hasCheats)
                                .executes(context -> play(context.getSource(), null))
                                .then(Commands.argument("videoId", StringArgumentType.word())
                                        .executes(context -> play(context.getSource(),
                                                StringArgumentType.getString(context, "videoId")))))
                        .then(Commands.literal("stop").requires(MysixClient::hasCheats).executes(context -> {
                            stop();
                            feedback(context.getSource(), Component.literal("Mysix playback stopped."));
                            return 1;
                        })));
    }

    private static boolean hasCheats(CommandSourceStack source) {
        return Minecraft.getInstance().player != null && Minecraft.getInstance().player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
    private static boolean canSetup(CommandSourceStack source) {
        if (Minecraft.getInstance().player == null) return false;
        if (Minecraft.getInstance().player.permissions().hasPermission(Permissions.COMMANDS_OWNER)) return true;
        var client = Minecraft.getInstance();
        var server = client == null ? null : client.getSingleplayerServer();
        return server != null && server.isSingleplayerOwner(Minecraft.getInstance().player.nameAndId());
    }

    private static int play(CommandSourceStack source, String videoId) {
        try {
            Path directory = MysixPlatform.gameDirectory().resolve("mysix-cache/downloads");
            var file = LocalAudioFiles.find(directory, videoId);
            stop();
            var sound = new DownloadedSound(file, message -> Minecraft.getInstance().execute(() ->
                    source.sendFailure(Component.literal("Cannot decode Mysix audio: " + message))));
            playing = sound;
            var result = Minecraft.getInstance().getSoundManager().play(sound);
            if (result == SoundEngine.PlayResult.NOT_STARTED) {
                playing = null;
                source.sendFailure(Component.literal("Audio did not start. Check Master and Jukebox/Note Blocks volume, then retry."));
                return 0;
            }
            feedback(source, Component.literal("Playing local download: " + file.getFileName()
                    + " (Jukebox/Note Blocks volume). Use /mysix stop to stop."));
            return 1;
        } catch (Exception error) {
            source.sendFailure(Component.literal(error.getMessage()));
            return 0;
        }
    }

    private static void feedback(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }

    private static void stop() {
        if (playing != null) {
            Minecraft.getInstance().getSoundManager().stop(playing);
            playing = null;
        }
    }
}
