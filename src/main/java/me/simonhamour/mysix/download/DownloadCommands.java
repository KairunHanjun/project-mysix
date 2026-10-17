package me.simonhamour.mysix.download;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import me.simonhamour.mysix.Mysix;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

public final class DownloadCommands {
    private static volatile ThreadPoolExecutor worker;
    private static volatile YtDlpDownloader downloader;

    private DownloadCommands() {}

    /** Shared bounded worker for gameplay downloads; callbacks run on the worker. */
    public static void downloadAsync(String request,
            java.util.function.Consumer<YtDlpDownloader.DownloadResult> success,
            java.util.function.Consumer<Exception> failure) {
        var executor = worker;
        var service = downloader;
        if (executor == null || service == null) {
            failure.accept(new IllegalStateException("Mysix downloader unavailable; check the server log/config."));
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    success.accept(service.download(request));
                } catch (Exception error) {
                    if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                    failure.accept(error);
                }
            });
        } catch (RejectedExecutionException error) {
            failure.accept(new IllegalStateException("Download queue full; try again shortly."));
        }
    }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                var loader = FabricLoader.getInstance();
                var config = DownloadConfig.load(loader.getConfigDir().resolve("mysix-download.json"));
                Path directory = loader.getGameDir().resolve("mysix-cache/downloads");
                downloader = new YtDlpDownloader(config, directory);
                worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(4), task -> {
                            var thread = new Thread(task, "mysix-download");
                            thread.setDaemon(true);
                            return thread;
                        });
            } catch (Exception error) {
                Mysix.LOGGER.error("Mysix downloader unavailable", error);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            var executor = worker;
            worker = null;
            downloader = null;
            if (executor != null) executor.shutdownNow();
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("mysix")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("download")
                                .then(Commands.argument("song", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            var source = context.getSource();
                                            var server = source.getServer();
                                            var request = StringArgumentType.getString(context, "song");
                                            var executor = worker;
                                            var service = downloader;
                                            if (executor == null || service == null) {
                                                source.sendFailure(Component.literal("Mysix downloader unavailable; check the server log/config."));
                                                return 0;
                                            }
                                            try {
                                                YtDlpDownloader.searchTarget(request);
                                                executor.execute(() -> {
                                                    try {
                                                        var result = service.download(request);
                                                        Mysix.LOGGER.info("Downloaded {} to {} ({} bytes)", result.title(), result.file(), result.bytes());
                                                        server.execute(() -> source.sendSuccess(() -> Component.literal(
                                                                "Downloaded: " + result.title() + "\nFile: " + result.file()
                                                                        + "\nSize: " + result.bytes() + " bytes"
                                                                        + "\nLocal playback test: /mysix play " + result.videoId()), false));
                                                    } catch (InterruptedException error) {
                                                        Thread.currentThread().interrupt();
                                                    } catch (Exception error) {
                                                        Mysix.LOGGER.warn("Music download failed", error);
                                                        server.execute(() -> source.sendFailure(Component.literal(error.getMessage())));
                                                    }
                                                });
                                                source.sendSuccess(() -> Component.literal("Queued music download: " + request), false);
                                                return 1;
                                            } catch (RejectedExecutionException error) {
                                                source.sendFailure(Component.literal("Download queue full; wait for the current jobs."));
                                            } catch (IllegalArgumentException error) {
                                                source.sendFailure(Component.literal(error.getMessage()));
                                            }
                                            return 0;
                                        })))));
    }
}
