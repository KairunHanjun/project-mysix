package me.simonhamour.mysix.network;

import java.util.UUID;
import me.simonhamour.mysix.Mysix;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Small ordered packets, rather than a whole song in one network packet. */
public record JukeboxAudioPayload(UUID session, int kind, Identifier dimension, BlockPos pos,
        String videoId, String title, int totalBytes, long startTick, int offset, byte[] data)
        implements CustomPacketPayload {
    public static final int BEGIN = 0, CHUNK = 1, PLAY = 2, STOP = 3;
    public static final int CHUNK_BYTES = 24 * 1024;
    public static final int MAX_BYTES = 30 * 1024 * 1024;
    public static final Type<JukeboxAudioPayload> TYPE = new Type<>(Mysix.id("jukebox_audio"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JukeboxAudioPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUUID(value.session());
                buf.writeVarInt(value.kind());
                buf.writeIdentifier(value.dimension());
                buf.writeBlockPos(value.pos());
                buf.writeUtf(value.videoId(), 11);
                buf.writeUtf(value.title(), 200);
                buf.writeVarInt(value.totalBytes());
                buf.writeLong(value.startTick());
                buf.writeVarInt(value.offset());
                buf.writeByteArray(value.data());
            }, buf -> new JukeboxAudioPayload(buf.readUUID(), buf.readVarInt(), buf.readIdentifier(),
                    buf.readBlockPos(), buf.readUtf(11), buf.readUtf(200), buf.readVarInt(),
                    buf.readLong(), buf.readVarInt(), buf.readByteArray(CHUNK_BYTES)));

    public JukeboxAudioPayload {
        if (kind < BEGIN || kind > STOP || totalBytes < 0 || totalBytes > MAX_BYTES
                || offset < 0 || offset > totalBytes || data.length > CHUNK_BYTES
                || data.length > totalBytes - offset || title.length() > 200
                || !videoId.matches("[A-Za-z0-9_-]{11}")) {
            throw new IllegalArgumentException("Invalid Mysix audio packet");
        }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
    }

    @Override public Type<JukeboxAudioPayload> type() { return TYPE; }
}
