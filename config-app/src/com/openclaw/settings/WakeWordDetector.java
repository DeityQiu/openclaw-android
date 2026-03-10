package com.openclaw.settings;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

/**
 * Wake-word detector using energy-based RMS threshold (no TFLite).
 * Listens for sustained loud audio as a simple trigger.
 */
public class WakeWordDetector {
    private static final String TAG = "OpenClawWake";
    private static final int SAMPLE_RATE = 16000;
    private static final int FRAME_SIZE = 1600; // 100ms at 16kHz
    private static final float RMS_THRESHOLD = 0.08f;
    private static final int TRIGGER_FRAMES = 3; // 3 consecutive loud frames

    private final String wakeWord;
    private AudioRecord recorder;
    private volatile boolean released = false;

    public WakeWordDetector(String wakeWord) {
        this.wakeWord = wakeWord;
        Log.i(TAG, "WakeWordDetector init (RMS stub), word: " + wakeWord);
        try {
            int bufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
            recorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                Math.max(bufSize, FRAME_SIZE * 2));
            recorder.startRecording();
        } catch (Exception e) {
            Log.e(TAG, "Failed to init AudioRecord: " + e.getMessage());
        }
    }

    /** Blocking call — returns true when wake word is detected. */
    public boolean detect() {
        if (recorder == null || released) {
            try { Thread.sleep(500); } catch (InterruptedException e) {}
            return false;
        }
        short[] buf = new short[FRAME_SIZE];
        int loudCount = 0;
        while (!released) {
            int read = recorder.read(buf, 0, FRAME_SIZE);
            if (read <= 0) continue;
            float rms = computeRms(buf, read);
            if (rms >= RMS_THRESHOLD) {
                loudCount++;
                if (loudCount >= TRIGGER_FRAMES) {
                    loudCount = 0;
                    return true;
                }
            } else {
                loudCount = 0;
            }
        }
        return false;
    }

    private float computeRms(short[] buf, int len) {
        long sum = 0;
        for (int i = 0; i < len; i++) sum += (long) buf[i] * buf[i];
        return (float) Math.sqrt((double) sum / len) / 32768f;
    }

    public void release() {
        released = true;
        try {
            if (recorder != null) {
                recorder.stop();
                recorder.release();
                recorder = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error releasing recorder", e);
        }
    }
}
