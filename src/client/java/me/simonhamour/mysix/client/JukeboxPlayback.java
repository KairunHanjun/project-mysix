package me.simonhamour.mysix.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.simonhamour.mysix.network.AudioTransfer;
import me.simonhamour.mysix.network.JukeboxAudioPayload;
import net.minecraft.client.Minecraft;
import me.simonhamour.mysix.platform.MysixClientPlatform;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

public final class JukeboxPlayback {
    private static final Map<UUID, Playback> sessions = new HashMap<>();
    private JukeboxPlayback() {}

    public static void initialize() {
        MysixClientPlatform.initializeAudio(JukeboxPlayback::receive, client -> {
            for (var session : sessions.values()) stop(client, session);
            sessions.clear();
        }, client -> {
            var iterator = sessions.values().iterator();
            while (iterator.hasNext()) {
                var session = iterator.next();
                if (client.level == null || client.player == null
                        || (!session.personal() && (!client.level.dimension().identifier().equals(session.begin.dimension())
                        || !session.begin.pos().closerToCenterThan(client.player.position(), 64)))) {
                    stop(client, session);
                    iterator.remove();
                }
            }
        });
    }

    private static void receive(Minecraft client, JukeboxAudioPayload packet) {
        if (packet.kind() == JukeboxAudioPayload.STOP) {
            var session = sessions.remove(packet.session());
            if (session != null) stop(client, session);
            return;
        }
        if (client.level == null) return;
        var existing = sessions.get(packet.session());
        if (packet.kind() != JukeboxAudioPayload.PERSONAL_BEGIN && packet.kind() != JukeboxAudioPayload.PERSONAL_NATIVE
                && (existing == null || !existing.personal())
                && !client.level.dimension().identifier().equals(packet.dimension())) return;
        try {
            if (packet.kind() == JukeboxAudioPayload.PERSONAL_NATIVE) {
                var previous = sessions.remove(packet.session());
                if (previous != null) stop(client, previous);
                if (sessions.size() >= 8) throw new IllegalStateException("Too many active jukeboxes");
                var playback = new Playback(packet, null);
                playback.sound = personalNativeSound(packet.nativeSound());
                sessions.put(packet.session(), playback);
                client.getSoundManager().play(playback.sound);
                return;
            }
            if (packet.kind() == JukeboxAudioPayload.BEGIN || packet.kind() == JukeboxAudioPayload.PERSONAL_BEGIN) {
                var previous = sessions.remove(packet.session());
                if (previous != null) stop(client, previous);
                if (sessions.size() >= 8) throw new IllegalStateException("Too many active jukeboxes");
                sessions.put(packet.session(), new Playback(packet, new AudioTransfer(packet.totalBytes())));
                return;
            }
            var session = sessions.get(packet.session());
            if (session == null || session.sound != null) return;
            if (packet.kind() == JukeboxAudioPayload.CHUNK) {
                session.transfer.append(packet.offset(), packet.data());
            } else if (packet.kind() == JukeboxAudioPayload.PLAY) {
                var audio = session.transfer.finish();
                double elapsed = Math.max(0, client.level.getGameTime() - packet.startTick()) / 20.0;
                java.util.function.Consumer<String> onError = message -> client.execute(() -> error(client, "Cannot decode CD: " + message));
                var sound = session.personal() ? DownloadedSound.personal(audio, elapsed, onError)
                        : DownloadedSound.jukebox(audio, packet.pos(), elapsed, onError);
                session.sound = sound;
                if (client.getSoundManager().play(sound) == SoundEngine.PlayResult.NOT_STARTED) {
                    throw new IllegalStateException("CD audio could not start; check Jukebox/Note Blocks volume");
                }
            }
        } catch (Exception exception) {
            var session = sessions.remove(packet.session());
            if (session != null) stop(client, session);
            error(client, exception.getMessage());
        }
    }

    private static void stop(Minecraft client, Playback playback) {
        if (playback.sound != null) client.getSoundManager().stop(playback.sound);
    }

    private static void error(Minecraft client, String message) {
        if (client.player != null) client.player.sendOverlayMessage(Component.literal(message));
    }

    public static SimpleSoundInstance personalNativeSound(String id) {
        return new SimpleSoundInstance(Identifier.parse(id), SoundSource.RECORDS, 1, 1,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
    }

    private static final class Playback {
        final JukeboxAudioPayload begin;
        final AudioTransfer transfer;
        SoundInstance sound;
        Playback(JukeboxAudioPayload begin, AudioTransfer transfer) {
            this.begin = begin; this.transfer = transfer;
        }
        boolean personal() { return begin.kind() == JukeboxAudioPayload.PERSONAL_BEGIN
                || begin.kind() == JukeboxAudioPayload.PERSONAL_NATIVE; }
    }
}
