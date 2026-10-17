package me.simonhamour.mysix.music;

import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import me.simonhamour.mysix.Mysix;
import me.simonhamour.mysix.download.DownloadCommands;
import me.simonhamour.mysix.download.YtDlpDownloader.DownloadResult;
import me.simonhamour.mysix.network.JukeboxAudioPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;

/** All mutable session state belongs to the server thread. Downloads alone run off-thread. */
public final class OnlineJukebox {
    public interface Downloads {
        void load(String query, Consumer<DownloadResult> success, Consumer<Exception> failure);
    }
    public interface Transport {
        boolean supports(ServerPlayer player);
        void send(ServerPlayer player, JukeboxAudioPayload packet);
    }
    public interface PersonalSource {
        ItemStack disc();
        boolean isActive();
        void save();
        void stop();
    }
    private static OnlineJukebox active;
    private final Downloads downloads;
    private final Transport transport;
    private final Map<UUID, Session> sessions = new LinkedHashMap<>();

    public OnlineJukebox(Downloads downloads, Transport transport) {
        this.downloads = downloads;
        this.transport = transport;
    }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> active = new OnlineJukebox(
                DownloadCommands::downloadAsync, new Transport() {
                    public boolean supports(ServerPlayer player) {
                        return ServerPlayNetworking.canSend(player, JukeboxAudioPayload.TYPE);
                    }
                    public void send(ServerPlayer player, JukeboxAudioPayload packet) {
                        ServerPlayNetworking.send(player, packet);
                    }
                }));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (active != null) active.tick(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (active != null) active.clear();
            active = null;
        });
    }

    public static boolean tryInsert(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack held) {
        if (active == null) return false;
        active.insert(player, level, pos, held);
        return true;
    }

    public static boolean tryPlayPersonal(ServerPlayer player, PersonalSource source) {
        return active != null && active.playPersonal(player, source);
    }

    public boolean playPersonal(ServerPlayer player, PersonalSource source) {
        var old = sessions.values().iterator();
        while (old.hasNext()) {
            var session = old.next();
            if (session.personal != null && session.owner.equals(player.getUUID())) {
                stopListeners(session);
                old.remove();
            }
        }
        if (sessions.size() >= 8 || !transport.supports(player) || source.disc().getCustomName() == null) {
            source.stop();
            player.sendOverlayMessage(Component.literal("Cannot start backpack music: session limit or incompatible Mysix client."));
            return false;
        }
        var session = new Session((ServerLevel) player.level(), player.blockPosition(), source.disc(), player.getUUID());
        session.personal = source;
        session.personalPlayer = player;
        sessions.put(session.id, session);
        load(session, player, source.disc().getCustomName().getString());
        return true;
    }

    public void insert(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack held) {
        if (!(level.getBlockEntity(pos) instanceof JukeboxBlockEntity box)
                || !box.getTheItem().isEmpty() || held.isEmpty()) return;
        var disc = held.split(1);
        box.setTheItem(disc);
        if (sessions.size() >= 8 || disc.getCustomName() == null || !transport.supports(player)) {
            box.popOutTheItem();
            player.sendOverlayMessage(Component.literal("Cannot play CD: Mysix is required on the client, or too many jukeboxes are active."));
            return;
        }
        String query = disc.getCustomName().getString();
        var session = new Session(level, pos.immutable(), disc, player.getUUID());
        sessions.put(session.id, session);
        load(session, player, query);
    }

    private void load(Session session, ServerPlayer player, String query) {
        player.sendOverlayMessage(Component.literal("Finding music: " + query));
        var server = session.level.getServer();
        String request = CdRecording.pinnedId(session.disc)
                .map(id -> "https://www.youtube.com/watch?v=" + id).orElse(query);
        downloads.load(request, result -> {
            try {
                long size = Files.size(result.file());
                if (size <= 0 || size > JukeboxAudioPayload.MAX_BYTES) {
                    throw new java.io.IOException("Audio exceeds the 30 MiB multiplayer limit");
                }
                byte[] audio = Files.readAllBytes(result.file());
                server.execute(() -> {
                    if (sessions.get(session.id) != session || !stillInserted(session)) return;
                    session.result = result;
                    session.audio = audio;
                    CdRecording.remember(session.disc, query, result.videoId());
                    if (session.personal != null) session.personal.save();
                    else session.level.getBlockEntity(session.pos).setChanged();
                    session.startTick = session.level.getGameTime() + 20;
                    player.sendOverlayMessage(Component.literal("Playing: " + result.title()
                            + ". Wrong recording? Rename the CD to a YouTube link."));
                });
            } catch (Exception error) {
                server.execute(() -> fail(session, error));
            }
        }, error -> server.execute(() -> fail(session, error)));
    }

    private boolean stillInserted(Session session) {
        if (session.personal != null) return session.level.getServer().getPlayerList().getPlayer(session.owner)
                == session.personalPlayer && session.personal.isActive();
        return session.level.hasChunkAt(session.pos)
                && session.level.getBlockEntity(session.pos) instanceof JukeboxBlockEntity box
                && box.getTheItem() == session.disc;
    }

    private void fail(Session session, Exception error) {
        if (sessions.remove(session.id) != session) return;
        stopListeners(session);
        if (stillInserted(session)) {
            if (session.personal != null) session.personal.stop();
            else ((JukeboxBlockEntity) session.level.getBlockEntity(session.pos)).popOutTheItem();
            var player = session.level.getServer().getPlayerList().getPlayer(session.owner);
            if (player != null) player.sendOverlayMessage(Component.literal(
                    "Could not play CD. Try a YouTube link. " + error.getMessage()));
        }
        Mysix.LOGGER.warn("CD download failed: {}", error.toString());
    }

    public void tick(MinecraftServer server) {
        int transferChunksLeft = 8; // Bound total song traffic across all jukeboxes per tick.
        var iterator = sessions.values().iterator();
        while (iterator.hasNext()) {
            var session = iterator.next();
            if (!stillInserted(session)) {
                stopListeners(session);
                if (session.personal != null) session.personal.stop();
                iterator.remove();
                continue;
            }
            if (session.audio == null) continue;
            if (session.result.durationSeconds() > 0 && session.level.getGameTime() > session.startTick
                    + Math.ceil(session.result.durationSeconds() * 20)) {
                stopListeners(session);
                if (session.personal != null) session.personal.stop();
                iterator.remove();
                continue;
            }
            var nearby = new HashSet<UUID>();
            for (var player : server.getPlayerList().getPlayers()) {
                boolean audible = session.personal != null ? player.getUUID().equals(session.owner)
                        : player.level() == session.level && session.pos.closerToCenterThan(player.position(), 64);
                if (!audible || !transport.supports(player)) continue;
                nearby.add(player.getUUID());
                var listener = session.listeners.get(player.getUUID());
                if (listener == null || listener.player != player) {
                    listener = new Listener(player);
                    session.listeners.put(player.getUUID(), listener);
                    transport.send(player, packet(session, session.personal != null
                            ? JukeboxAudioPayload.PERSONAL_BEGIN : JukeboxAudioPayload.BEGIN, 0, new byte[0]));
                }
                // At most 96 KiB/player/jukebox each tick; never one huge packet.
                for (int count = 0; count < 4 && transferChunksLeft > 0
                        && listener.offset < session.audio.length; count++) {
                    int end = Math.min(session.audio.length, listener.offset + JukeboxAudioPayload.CHUNK_BYTES);
                    transport.send(player, packet(session, JukeboxAudioPayload.CHUNK, listener.offset,
                            Arrays.copyOfRange(session.audio, listener.offset, end)));
                    listener.offset = end;
                    transferChunksLeft--;
                }
                if (listener.offset == session.audio.length && !listener.started
                        && session.level.getGameTime() >= session.startTick) {
                    transport.send(player, packet(session, JukeboxAudioPayload.PLAY, 0, new byte[0]));
                    listener.started = true;
                }
            }
            var listeners = session.listeners.entrySet().iterator();
            while (listeners.hasNext()) {
                var entry = listeners.next();
                if (!nearby.contains(entry.getKey())) {
                    transport.send(entry.getValue().player, packet(session, JukeboxAudioPayload.STOP, 0, new byte[0]));
                    listeners.remove();
                }
            }
        }
    }

    private JukeboxAudioPayload packet(Session s, int kind, int offset, byte[] bytes) {
        return new JukeboxAudioPayload(s.id, kind, s.level.dimension().identifier(), s.pos,
                s.result.videoId(), s.result.title().substring(0, Math.min(200, s.result.title().length())),
                s.audio.length, s.startTick, offset, bytes);
    }

    private void stopListeners(Session session) {
        for (var listener : session.listeners.values()) {
            transport.send(listener.player, packet(session, JukeboxAudioPayload.STOP, 0, new byte[0]));
        }
        session.listeners.clear();
    }

    public void clear() {
        for (var session : sessions.values()) {
            stopListeners(session);
            if (session.personal != null) session.personal.stop();
        }
        sessions.clear();
    }

    private static final class Listener {
        final ServerPlayer player;
        int offset;
        boolean started;
        Listener(ServerPlayer player) { this.player = player; }
    }
    private static final class Session {
        final UUID id = UUID.randomUUID();
        final ServerLevel level;
        final BlockPos pos;
        final ItemStack disc;
        final UUID owner;
        final Map<UUID, Listener> listeners = new HashMap<>();
        DownloadResult result;
        byte[] audio;
        long startTick;
        PersonalSource personal;
        ServerPlayer personalPlayer;
        Session(ServerLevel level, BlockPos pos, ItemStack disc, UUID owner) {
            this.level = level; this.pos = pos; this.disc = disc; this.owner = owner;
        }
    }
}
