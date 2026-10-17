package me.simonhamour.mysix.network;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class AudioTransferTest {
    @Test
    void reassemblesExactBytesAndCannotPlayTwice() {
        var transfer = new AudioTransfer(5);
        transfer.append(0, new byte[]{1, 2});
        assertThrows(IllegalStateException.class, transfer::finish);
        assertThrows(IllegalArgumentException.class, () -> transfer.append(0, new byte[]{3}));
        assertThrows(IllegalArgumentException.class, () -> transfer.append(3, new byte[]{3}));
        assertThrows(IllegalArgumentException.class, () -> transfer.append(2, new byte[]{3, 4, 5, 6}));
        transfer.append(2, new byte[]{3, 4, 5});
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, transfer.finish());
        assertThrows(IllegalStateException.class, transfer::finish);
        assertThrows(IllegalArgumentException.class, () -> transfer.append(5, new byte[]{6}));
    }

    @Test
    void boundsMemoryAndPacketSizes() {
        assertThrows(IllegalArgumentException.class, () -> new AudioTransfer(0));
        assertThrows(IllegalArgumentException.class, () -> new AudioTransfer(JukeboxAudioPayload.MAX_BYTES + 1));
        var transfer = new AudioTransfer(JukeboxAudioPayload.MAX_BYTES);
        assertThrows(IllegalArgumentException.class,
                () -> transfer.append(0, new byte[JukeboxAudioPayload.CHUNK_BYTES + 1]));
    }

    @Test
    void actualNetworkCodecRoundTripsAllSessionFieldsAndAudio() {
        var packet = new JukeboxAudioPayload(UUID.randomUUID(), JukeboxAudioPayload.CHUNK,
                Identifier.withDefaultNamespace("overworld"), new BlockPos(12, 64, -9),
                "Yikl044w8d0", "Dhyo Haw - Ada Aku Disini", 3, 20L, 0, new byte[]{1, 2, 3});
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            JukeboxAudioPayload.CODEC.encode(buffer, packet);
            var decoded = JukeboxAudioPayload.CODEC.decode(buffer);
            assertEquals(packet.session(), decoded.session());
            assertEquals(packet.kind(), decoded.kind());
            assertEquals(packet.dimension(), decoded.dimension());
            assertEquals(packet.pos(), decoded.pos());
            assertEquals(packet.videoId(), decoded.videoId());
            assertEquals(packet.title(), decoded.title());
            assertEquals(packet.totalBytes(), decoded.totalBytes());
            assertEquals(packet.startTick(), decoded.startTick());
            assertEquals(packet.offset(), decoded.offset());
            assertArrayEquals(packet.data(), decoded.data());
        } finally { buffer.release(); }
    }
}
