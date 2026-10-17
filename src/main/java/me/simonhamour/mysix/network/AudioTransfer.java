package me.simonhamour.mysix.network;

import java.io.ByteArrayOutputStream;

/** Bounded, ordered assembly. No paths or filenames are accepted from a server. */
public final class AudioTransfer {
    private final int expected;
    private ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    public AudioTransfer(int expected) {
        if (expected <= 0 || expected > JukeboxAudioPayload.MAX_BYTES) {
            throw new IllegalArgumentException("Invalid audio size");
        }
        this.expected = expected;
    }

    public void append(int offset, byte[] chunk) {
        if (bytes == null || offset != bytes.size() || chunk.length == 0
                || chunk.length > JukeboxAudioPayload.CHUNK_BYTES || chunk.length > expected - offset) {
            throw new IllegalArgumentException("Out-of-order or oversized audio transfer");
        }
        bytes.writeBytes(chunk);
    }

    public byte[] finish() {
        if (bytes == null || bytes.size() != expected) throw new IllegalStateException("Incomplete audio transfer");
        var result = bytes.toByteArray();
        bytes = null;
        return result;
    }
}
