package me.simonhamour.mysix.music;

import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import me.simonhamour.mysix.Mysix;
import me.simonhamour.mysix.platform.MysixPlatform;
import me.simonhamour.mysix.download.DownloadCommands;
import me.simonhamour.mysix.download.YtDlpDownloader.DownloadResult;
import me.simonhamour.mysix.network.JukeboxAudioPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
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
    public record Output(ServerLevel level, BlockPos pos, double range) {}
    public interface Route {
        boolean active();
        List<Output> outputs();
        void close();
    }
    @FunctionalInterface public interface Routes { Route open(ServerLevel level, BlockPos pos); }
    public interface PersonalSource {
        ItemStack disc();
        boolean isActive();
        void save();
        void stop();
        default boolean personal() { return true; }
        default ServerLevel level(ServerPlayer player) { return (ServerLevel) player.level(); }
        default BlockPos position(ServerPlayer player) { return player.blockPosition(); }
        default void finished() { stop(); }
        default void failed() { stop(); }
        default boolean needsDuration() { return false; }
    }
    private static OnlineJukebox active;
    private final Downloads downloads;
    private final Transport transport;
    private final Routes routes;
    private final Map<UUID, Session> sessions = new LinkedHashMap<>();

    public OnlineJukebox(Downloads downloads, Transport transport) {
        this(downloads, transport, MysixPlatform::openRoute);
    }

    public OnlineJukebox(Downloads downloads, Transport transport, Routes routes) {
        this.downloads = downloads;
        this.transport = transport;
        this.routes = routes;
    }

    public static void initialize() {
        MysixPlatform.onServerStarted(server -> active = new OnlineJukebox(
                DownloadCommands::downloadAsync, new Transport() {
                    public boolean supports(ServerPlayer player) {
                        return MysixPlatform.canSend(player);
                    }
                    public void send(ServerPlayer player, JukeboxAudioPayload packet) {
                        MysixPlatform.send(player, packet);
                    }
                }));
        MysixPlatform.onServerTick(server -> {
            if (active != null) active.tick(server);
        });
        MysixPlatform.onServerStopping(server -> {
            if (active != null) active.clear();
            active = null;
        });
    }

    public static boolean tryInsert(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack held) {
        if (active == null) return false;
        active.insert(player, level, pos, held);
        return true;
    }

    public static boolean hasSessionAt(JukeboxBlockEntity box) {
        return active != null && active.hasSession(box);
    }

    public static boolean tryPlayPlaced(ServerPlayer player, JukeboxBlockEntity box) {
        return active != null && active.playPlaced(player, box, true);
    }

    public static void requireDurationAt(JukeboxBlockEntity box) {
        if (active != null) active.requireDuration(box);
    }

    /** Includes loading sessions, so automation must not advance while a download is pending. */
    public boolean hasSession(JukeboxBlockEntity box) {
        return sessions.values().stream().anyMatch(s -> s.personal == null && s.level == box.getLevel()
                && s.pos.equals(box.getBlockPos()) && s.disc == box.getTheItem());
    }

    public void requireDuration(JukeboxBlockEntity box) {
        sessions.values().stream().filter(s -> s.personal == null && s.level == box.getLevel()
                && s.pos.equals(box.getBlockPos()) && s.disc == box.getTheItem())
                .toList().forEach(s -> {
                    s.needsDuration = true;
                    if (s.result != null && (!Double.isFinite(s.result.durationSeconds()) || s.result.durationSeconds() <= 0)) {
                        fail(s, new java.io.IOException("This recording has no duration and cannot be used in a playlist"));
                    }
                });
    }

    /** Starts a disc already inserted by server automation; never copies or consumes another item. */
    public boolean playPlaced(ServerPlayer player, JukeboxBlockEntity box, boolean needsDuration) {
        if (!(box.getLevel() instanceof ServerLevel level) || box.getTheItem().isEmpty()) return false;
        if (hasSession(box)) return true;
        var disc = box.getTheItem();
        if (sessions.size() >= 8 || disc.getCustomName() == null || !transport.supports(player)) {
            box.popOutTheItem();
            player.sendOverlayMessage(Component.literal("Cannot play CD: Mysix is required on the client, or too many jukeboxes are active."));
            return false;
        }
        var session = new Session(level, box.getBlockPos().immutable(), disc, player.getUUID());
        session.needsDuration = needsDuration;
        sessions.put(session.id, session);
        load(session, player, disc.getCustomName().getString());
        return true;
    }

    /** Server-side presentation state; never starts a vanilla song to animate an external CD. */
    public static boolean isPlayingAt(JukeboxBlockEntity box) {
        return active != null && active.playingAt(box);
    }

    public boolean playingAt(JukeboxBlockEntity box) {
        if (!(box.getLevel() instanceof ServerLevel level)) return false;
        long now = level.getGameTime();
        return sessions.values().stream().anyMatch(session -> session.level == level
                && session.pos.equals(box.getBlockPos())
                && (session.personal == null || !session.personal.personal())
                && session.audio != null && now >= session.startTick
                && (session.result.durationSeconds() <= 0
                    || now <= session.startTick + Math.ceil(session.result.durationSeconds() * 20))
                && stillInserted(session));
    }

    public static boolean tryPlayPersonal(ServerPlayer player, PersonalSource source) {
        return active != null && active.playPersonal(player, source);
    }

    public static boolean tryPlayPersonalSong(ServerPlayer player, PersonalSource source, JukeboxSong song) {
        return active != null && active.playPersonalSong(player, source, song);
    }

    public boolean playPersonal(ServerPlayer player, PersonalSource source) {
        if (source.disc().getCustomName() == null) { source.stop(); return false; }
        var session = allocate(player, source);
        if (session == null) return false;
        load(session, player, source.disc().getCustomName().getString());
        return true;
    }

    public boolean playPersonalSong(ServerPlayer player, PersonalSource source, JukeboxSong song) {
        var session = allocate(player, source);
        if (session == null) return false;
        session.nativeSong = song;
        session.startTick = session.level.getGameTime() + 1;
        return true;
    }

    private Session allocate(ServerPlayer player, PersonalSource source) {
        var old = sessions.values().iterator();
        while (old.hasNext()) {
            var session = old.next();
            if (source.personal() && session.personal != null && session.personal.personal()
                    && session.owner.equals(player.getUUID())) {
                stopListeners(session);
                session.personal.stop();
                old.remove();
            }
        }
        if (sessions.size() >= 8 || !transport.supports(player)) {
            source.failed();
            player.sendOverlayMessage(Component.literal("Cannot start music: session limit or incompatible Mysix client."));
            return null;
        }
        var session = new Session(source.level(player), source.position(player), source.disc(), player.getUUID());
        session.personal = source;
        session.personalPlayer = player;
        sessions.put(session.id, session);
        return session;
    }

    public void insert(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack held) {
        if (!(level.getBlockEntity(pos) instanceof JukeboxBlockEntity box)
                || !box.getTheItem().isEmpty() || held.isEmpty()) return;
        var disc = held.split(1);
        box.setTheItem(disc);
        playPlaced(player, box, false);
    }

    private void load(Session session, ServerPlayer player, String query) {
        player.sendOverlayMessage(Component.literal("Finding music: " + query));
        var server = session.level.getServer();
        String request = CdRecording.pinnedId(session.disc)
                .map(id -> "https://www.youtube.com/watch?v=" + id).orElse(query);
        downloads.load(request, result -> {
            try {
                if (session.personal != null && session.personal.needsDuration() && result.durationSeconds() <= 0) {
                    throw new java.io.IOException("This recording has no duration and cannot be used in a playlist");
                }
                long size = Files.size(result.file());
                if (size <= 0 || size > JukeboxAudioPayload.MAX_BYTES) {
                    throw new java.io.IOException("Audio exceeds the 30 MiB multiplayer limit");
                }
                byte[] audio = Files.readAllBytes(result.file());
                server.execute(() -> {
                    if (sessions.get(session.id) != session || !stillInserted(session)) return;
                    if (session.needsDuration && (!Double.isFinite(result.durationSeconds()) || result.durationSeconds() <= 0)) {
                        fail(session, new java.io.IOException("This recording has no duration and cannot be used in a playlist"));
                        return;
                    }
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
        if (session.personal != null) return (!session.personal.personal()
                || session.level.getServer().getPlayerList().getPlayer(session.owner) == session.personalPlayer)
                && session.personal.isActive();
        return session.level.hasChunkAt(session.pos)
                && session.level.getBlockEntity(session.pos) instanceof JukeboxBlockEntity box
                && box.getTheItem() == session.disc;
    }

    private void fail(Session session, Exception error) {
        if (sessions.remove(session.id) != session) return;
        stopListeners(session);
        if (stillInserted(session)) {
            if (session.personal != null) session.personal.failed();
            else ((JukeboxBlockEntity) session.level.getBlockEntity(session.pos)).popOutTheItem();
            var player = session.level.getServer().getPlayerList().getPlayer(session.owner);
            if (player != null) player.sendOverlayMessage(Component.literal(
                    "Could not play CD. " + error.getMessage()));
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
            if (session.audio == null && session.nativeSong == null) continue;
            double duration = session.nativeSong != null ? session.nativeSong.lengthInSeconds()
                    : session.result.durationSeconds();
            if (duration > 0 && session.level.getGameTime() > session.startTick + Math.ceil(duration * 20)) {
                stopListeners(session);
                if (session.personal != null) session.personal.finished();
                iterator.remove();
                continue;
            }
            boolean isPersonal = session.personal != null && session.personal.personal();
            if (!isPersonal) {
                if (session.route != null && !session.route.active()) {
                    session.route.close(); session.route = null;
                }
                if (session.route == null) session.route = routes.open(session.level, session.pos);
            }
            var outputs = session.route == null ? List.of(new Output(session.level, session.pos, 64))
                    : session.route.outputs();
            var nearby = new HashSet<UUID>();
            for (var player : server.getPlayerList().getPlayers()) {
                var output = isPersonal ? new Output(session.level, session.pos, 64) : outputs.stream()
                        .filter(target -> player.level() == target.level()
                                && target.pos().closerToCenterThan(player.position(), target.range()))
                        .min(Comparator.comparingDouble(target -> target.pos().distToCenterSqr(player.position())))
                        .orElse(null);
                boolean audible = isPersonal ? player.getUUID().equals(session.owner) : output != null;
                if (!audible || !transport.supports(player)) continue;
                nearby.add(player.getUUID());
                var listener = session.listeners.get(player.getUUID());
                if (listener == null || listener.player != player || !listener.output.equals(output)) {
                    if (listener != null) transport.send(listener.player, packet(session, JukeboxAudioPayload.STOP, 0, new byte[0]));
                    listener = new Listener(player, output);
                    session.listeners.put(player.getUUID(), listener);
                    if (session.nativeSong == null) transport.send(player, packet(session, isPersonal
                            ? JukeboxAudioPayload.PERSONAL_BEGIN : JukeboxAudioPayload.BEGIN, 0, new byte[0], output));
                }
                if (session.nativeSong != null) {
                    if (!listener.started && session.level.getGameTime() >= session.startTick) {
                        transport.send(player, packet(session, JukeboxAudioPayload.PERSONAL_NATIVE, 0, new byte[0]));
                        listener.started = true;
                    }
                    continue;
                }
                // At most 96 KiB/player/jukebox each tick; never one huge packet.
                for (int count = 0; count < 4 && transferChunksLeft > 0
                        && listener.offset < session.audio.length; count++) {
                    int end = Math.min(session.audio.length, listener.offset + JukeboxAudioPayload.CHUNK_BYTES);
                    transport.send(player, packet(session, JukeboxAudioPayload.CHUNK, listener.offset,
                            Arrays.copyOfRange(session.audio, listener.offset, end), output));
                    listener.offset = end;
                    transferChunksLeft--;
                }
                if (listener.offset == session.audio.length && !listener.started
                        && session.level.getGameTime() >= session.startTick) {
                    transport.send(player, packet(session, JukeboxAudioPayload.PLAY, 0, new byte[0], output));
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
        return packet(s, kind, offset, bytes, new Output(s.level, s.pos, 64));
    }

    private JukeboxAudioPayload packet(Session s, int kind, int offset, byte[] bytes, Output output) {
        String title = s.nativeSong != null ? s.nativeSong.description().getString() : s.result.title();
        return new JukeboxAudioPayload(s.id, kind, output.level().dimension().identifier(), output.pos(),
                s.nativeSong != null ? "aaaaaaaaaaa" : s.result.videoId(), title.substring(0, Math.min(200, title.length())),
                s.audio == null ? 0 : s.audio.length, s.startTick, offset, bytes,
                s.nativeSong == null ? "" : s.nativeSong.soundEvent().value().location().toString());
    }

    private void stopListeners(Session session) {
        for (var listener : session.listeners.values()) {
            transport.send(listener.player, packet(session, JukeboxAudioPayload.STOP, 0, new byte[0]));
        }
        session.listeners.clear();
        if (session.route != null) { session.route.close(); session.route = null; }
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
        final Output output;
        int offset;
        boolean started;
        Listener(ServerPlayer player, Output output) { this.player = player; this.output = output; }
    }
    private static final class Session {
        final UUID id = UUID.randomUUID();
        final ServerLevel level;
        final BlockPos pos;
        final ItemStack disc;
        final UUID owner;
        final Map<UUID, Listener> listeners = new HashMap<>();
        DownloadResult result;
        JukeboxSong nativeSong;
        byte[] audio;
        long startTick;
        PersonalSource personal;
        Route route;
        ServerPlayer personalPlayer;
        boolean needsDuration;
        Session(ServerLevel level, BlockPos pos, ItemStack disc, UUID owner) {
            this.level = level; this.pos = pos; this.disc = disc; this.owner = owner;
        }
    }
}
