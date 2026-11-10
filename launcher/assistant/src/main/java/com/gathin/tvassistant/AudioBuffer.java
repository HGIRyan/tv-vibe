package com.gathin.tvassistant;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Single audio-thread owner. Snapshot includes the trigger frame exactly once. */
final class AudioBuffer {
    static final int RATE = 16000;
    private final byte[] ring;
    private int write, size;

    AudioBuffer(int milliseconds) { ring = new byte[RATE * 2 * milliseconds / 1000]; }

    void append(byte[] bytes, int length) {
        for (int i = 0; i < length; i++) {
            ring[write] = bytes[i];
            write = (write + 1) % ring.length;
            size = Math.min(size + 1, ring.length);
        }
    }

    byte[] snapshot() {
        byte[] result = new byte[size];
        int start = (write - size + ring.length) % ring.length;
        for (int i = 0; i < size; i++) result[i] = ring[(start + i) % ring.length];
        return result;
    }

    void clear() { Arrays.fill(ring, (byte) 0); write = size = 0; }

    static double rms(byte[] pcm, int length) {
        double squares = 0;
        for (int i = 0; i + 1 < length; i += 2) {
            short sample = (short) ((pcm[i] & 255) | (pcm[i + 1] << 8));
            squares += (double) sample * sample;
        }
        return Math.sqrt(squares / Math.max(1, length / 2));
    }

    static byte[] wav(byte[] pcm) {
        ByteBuffer out = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        out.put(new byte[]{'R','I','F','F'}).putInt(36 + pcm.length);
        out.put(new byte[]{'W','A','V','E','f','m','t',' '}).putInt(16);
        out.putShort((short) 1).putShort((short) 1).putInt(RATE).putInt(RATE * 2);
        out.putShort((short) 2).putShort((short) 16);
        out.put(new byte[]{'d','a','t','a'}).putInt(pcm.length).put(pcm);
        return out.array();
    }

    /** Bounded post-wake endpointing, using sample counts instead of wall time. */
    static final class Capture {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private int afterWakeBytes, quietBytes;
        private final double threshold;

        Capture(byte[] preRoll, double noiseFloor) {
            bytes.write(preRoll, 0, preRoll.length);
            threshold = Math.max(250, noiseFloor * 2.0);
        }

        boolean append(byte[] frame, int length) {
            bytes.write(frame, 0, length);
            afterWakeBytes += length;
            quietBytes = rms(frame, length) < threshold ? quietBytes + length : 0;
            return (afterWakeBytes >= RATE * 2 && quietBytes >= RATE * 2 * 800 / 1000)
                || afterWakeBytes >= RATE * 2 * 8;
        }

        byte[] finish() { return bytes.toByteArray(); }
    }
}
