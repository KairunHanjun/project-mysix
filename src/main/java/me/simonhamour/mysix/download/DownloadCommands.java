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
    private static volatile ManagedTools managed;
    private static final java.util.concurrent.atomic.AtomicBoolean settingUp = new java.util.concurrent.atomic.AtomicBoolean();

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
                    var current = downloader;
                    if (current == null) throw new IllegalStateException("Server downloader has stopped");
                    success.accept(current.download(request));
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
            worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(4), task -> {
                        var thread = new Thread(task, "mysix-download");
                        thread.setDaemon(true);
                        return thread;
                    });
            try {
                var loader = FabricLoader.getInstance();
                var config = DownloadConfig.load(loader.getConfigDir().resolve("mysix-download.json"));
                Path directory = loader.getGameDir().resolve("mysix-cache/downloads");
                if (config.managedTools()) {
                    managed = new ManagedTools(loader.getGameDir());
                    downloader = managed.downloader(config, directory);
                } else downloader = new YtDlpDownloader(config, directory);
            } catch (Exception error) {
                Mysix.LOGGER.error("Mysix downloader unavailable", error);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            var executor = worker;
            worker = null;
            downloader = null;
            if (executor != null) executor.shutdownNow();
            var tools = managed; managed = null;
            if (tools != null) tools.close();
            settingUp.set(false);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> registerCommands(dispatcher));
    }

    static void registerCommands(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
                dispatcher.register(Commands.literal("mysix")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || canSetup(source))
                        .then(Commands.literal("setup")
                                .requires(DownloadCommands::canSetup)
                                .executes(context -> setup(context.getSource())))
                        .then(Commands.literal("check")
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .executes(context -> check(context.getSource(), "Dhyo Haw - Ada Aku Disini"))
                                .then(Commands.argument("song", StringArgumentType.greedyString())
                                        .executes(context -> check(context.getSource(), StringArgumentType.getString(context, "song")))))
                        .then(Commands.literal("download")
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
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
                                                        var current = downloader;
                                                        if (current == null) throw new IllegalStateException("Server downloader has stopped");
                                                        var result = current.download(request);
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
                                        }))));
    }

    private static boolean canSetup(net.minecraft.commands.CommandSourceStack source) {
        var server = source.getServer(); var player = source.getPlayer();
        return source.permissions().hasPermission(Permissions.COMMANDS_OWNER)
                || (server != null && server.isSingleplayer() && player != null && server.isSingleplayerOwner(player.nameAndId()));
    }

    private static int setup(net.minecraft.commands.CommandSourceStack source) {
        var executor = worker;
        if (executor == null) {
            source.sendFailure(Component.literal("Downloader unavailable; fix config/mysix-download.json and restart. See server log."));
            return 0;
        }
        if (!settingUp.compareAndSet(false, true)) {
            source.sendFailure(Component.literal("Mysix setup is already running.")); return 0;
        }
        try {
            executor.execute(() -> {
                ManagedTools candidate = null;
                try {
                    var loader = FabricLoader.getInstance();
                    var configPath = loader.getConfigDir().resolve("mysix-download.json");
                    var config = DownloadConfig.load(configPath);
                    candidate = managed;
                    if (candidate == null) candidate = new ManagedTools(loader.getGameDir());
                    candidate.install(message -> feedback(source, "Mysix setup: " + message));
                    candidate.ensureProvider();
                    var next = candidate.downloader(config, loader.getGameDir().resolve("mysix-cache/downloads"));
                    if (worker != executor || executor.isShutdown()) throw new java.io.InterruptedIOException("Server stopped during setup");
                    new DownloadConfig(config.ytDlpCommand(), config.timeoutSeconds(), config.maxFileMiB(), true).save(configPath);
                    var previous = managed; managed = candidate; candidate = null;
                    if (previous != null && previous != managed) previous.close();
                    downloader = next;
                    feedback(source, "Mysix tools installed and enabled. Testing an anonymous YouTube audio download...");
                    try {
                        var result = next.download("Dhyo Haw - Ada Aku Disini");
                        feedback(source, "Mysix setup complete: audio downloaded and converted without sign-in (" + result.bytes() + " bytes). Try your CD.");
                    } catch (Exception error) {
                        if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                        Mysix.LOGGER.warn("Mysix anonymous setup test failed", error);
                        feedback(source, "Tools are installed, but the YouTube test failed: " + shortError(error)
                                + " Retry with /mysix check <YouTube link or artist/title>. Details are in the server log.");
                    }
                } catch (Exception error) {
                    if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                    Mysix.LOGGER.warn("Mysix setup failed", error);
                    feedback(source, "Mysix setup failed: " + shortError(error));
                } finally {
                    if (candidate != null && candidate != managed) candidate.close();
                    settingUp.set(false);
                }
            });
            source.sendSuccess(() -> Component.literal("Installing Mysix's pinned tools and anonymous token helper into mysix-tools. This may take several minutes; Google login is not requested."), false);
            return 1;
        } catch (RejectedExecutionException error) {
            settingUp.set(false);
            source.sendFailure(Component.literal("Download queue full; try setup again shortly.")); return 0;
        }
    }

    private static int check(net.minecraft.commands.CommandSourceStack source, String request) {
        try { YtDlpDownloader.searchTarget(request); }
        catch (IllegalArgumentException error) { source.sendFailure(Component.literal(error.getMessage())); return 0; }
        feedback(source, "Mysix check: testing actual audio download and conversion on the server ("
                + (managed != null ? "managed tools" : "system tools") + ").");
        downloadAsync(request, result -> feedback(source, "Mysix check passed: " + result.title() + " (" + result.bytes()
                + " bytes). Server retrieval works; client playback still needs an in-game check."),
                error -> {
                    Mysix.LOGGER.warn("Mysix download check failed", error);
                    feedback(source, "Mysix check failed: " + shortError(error) + ". See server log for details.");
                });
        return 1;
    }
    private static void feedback(net.minecraft.commands.CommandSourceStack source, String message) {
        source.getServer().execute(() -> source.sendSuccess(() -> Component.literal(message), false));
    }
    private static String shortError(Exception error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return message.substring(0, Math.min(message.length(), 220));
    }
}
