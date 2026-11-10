package com.gathin.tvassistant;

import org.junit.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import static org.junit.Assert.*;

public class AudioBufferTest {
    @Test public void wrappedPreRollKeepsNewestSamplesInOrder() {
        AudioBuffer buffer = new AudioBuffer(1); // 32 bytes
        byte[] input = new byte[80];
        for (int i = 0; i < input.length; i++) input[i] = (byte) i;
        buffer.append(input, input.length);
        assertArrayEquals(Arrays.copyOfRange(input, 48, 80), buffer.snapshot());
        buffer.clear(); assertEquals(0, buffer.snapshot().length);
    }

    @Test public void quickCommandInTriggerFrameIsIncludedExactlyOnce() {
        AudioBuffer buffer = new AudioBuffer(4000);
        byte[] wakeAndPause = new byte[]{1,2,3,4,5,6};
        buffer.append(wakeAndPause, wakeAndPause.length);
        AudioBuffer.Capture capture = new AudioBuffer.Capture(buffer.snapshot(), 100);
        capture.append(new byte[]{7,8}, 2);
        assertArrayEquals(new byte[]{1,2,3,4,5,6,7,8}, capture.finish());
    }

    @Test public void recordingWaitsForOneSecondAndEndsAfterQuiet() {
        AudioBuffer.Capture capture = new AudioBuffer.Capture(new byte[128000], 100);
        byte[] quiet = new byte[640];
        for (int i = 0; i < 49; i++) assertFalse(capture.append(quiet, quiet.length));
        assertTrue(capture.append(quiet, quiet.length));
    }

    @Test public void loudTvCannotMakeCaptureUnbounded() {
        AudioBuffer.Capture capture = new AudioBuffer.Capture(new byte[0], 100);
        byte[] loud = new byte[640]; Arrays.fill(loud, (byte) 127);
        for (int i = 0; i < 399; i++) assertFalse(capture.append(loud, loud.length));
        assertTrue(capture.append(loud, loud.length));
        assertEquals(256000, capture.finish().length);
    }

    @Test public void wavHeaderMatchesAudioActuallySent() {
        byte[] wav = AudioBuffer.wav(new byte[]{1,2,3,4});
        ByteBuffer bytes = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(48, wav.length); assertEquals(40, bytes.getInt(4));
        assertEquals(16000, bytes.getInt(24)); assertEquals(4, bytes.getInt(40));
        assertArrayEquals(new byte[]{1,2,3,4}, Arrays.copyOfRange(wav, 44, 48));
    }
}
