package com.openclaw.settings;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.util.Log;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VoiceListenerService extends Service {
    private static final String TAG = "OpenClawVoice";
    private static final String CHANNEL_ID = "openclaw_voice";
    private static final int NOTIFICATION_ID = 1002;

    private static final int SAMPLE_RATE = 16000;
    private static final int RECORD_SECONDS = 5;
    private static final int RECORD_SAMPLES = SAMPLE_RATE * RECORD_SECONDS;

    private WakeWordDetector wakeWordDetector;
    private WhisperASR whisperASR;
    private volatile boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundNotification();
        SharedPreferences prefs = getSharedPreferences("openclaw_config", MODE_PRIVATE);
        String wakeWord = prefs.getString("wake_word", "hey openclaw");
        try {
            wakeWordDetector = new WakeWordDetector(wakeWord);
            whisperASR = new WhisperASR(this);
            running = true;
            new Thread(this::listenLoop).start();
            Log.i(TAG, "Voice listener started, wake word: " + wakeWord);
        } catch (Exception e) {
            Log.e(TAG, "Failed to init voice listener", e);
        }
    }

    private void listenLoop() {
        while (running) {
            try {
                // 阻塞等待唤醒词
                if (wakeWordDetector.detect()) {
                    Log.i(TAG, "Wake word detected, recording command...");
                    // 唤醒词检测器先暂停，再录音避免冲突
                    wakeWordDetector.release();

                    short[] audio = recordCommand();
                    if (audio != null) {
                        String text = whisperASR.transcribe(audio);
                        if (text != null && !text.trim().isEmpty()) {
                            Log.i(TAG, "Recognized: " + text);
                            sendToGateway(text.trim());
                        }
                    }

                    // 重新初始化 wake word 检测
                    SharedPreferences prefs = getSharedPreferences("openclaw_config", MODE_PRIVATE);
                    String wakeWord = prefs.getString("wake_word", "hey openclaw");
                    wakeWordDetector = new WakeWordDetector(wakeWord);
                }
            } catch (Exception e) {
                Log.e(TAG, "Voice loop error", e);
                try { Thread.sleep(1000); } catch (InterruptedException ie) { break; }
            }
        }
    }

    private short[] recordCommand() {
        try {
            int bufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            AudioRecord recorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                Math.max(bufSize, RECORD_SAMPLES * 2));
            recorder.startRecording();
            short[] audio = new short[RECORD_SAMPLES];
            int read = 0;
            while (read < RECORD_SAMPLES) {
                int n = recorder.read(audio, read, RECORD_SAMPLES - read);
                if (n <= 0) break;
                read += n;
            }
            recorder.stop();
            recorder.release();
            return audio;
        } catch (Exception e) {
            Log.e(TAG, "recordCommand failed", e);
            return null;
        }
    }

    private void sendToGateway(String text) {
        new Thread(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences("openclaw_config", MODE_PRIVATE);
                int gatewayPort = prefs.getInt("server_port", 18789);
                String gatewayToken = prefs.getString("gateway_token", "");
                URL url = new URL("http://127.0.0.1:" + gatewayPort + "/voice");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + gatewayToken);
                conn.setDoOutput(true);
                conn.setConnectTimeout(3000);
                String body = "{\"text\":\"" + text.replace("\"", "\\\"") + "\",\"source\":\"voice\"}";
                OutputStream os = conn.getOutputStream();
                os.write(body.getBytes("UTF-8"));
                os.close();
                int code = conn.getResponseCode();
                Log.i(TAG, "Sent to gateway, response: " + code);
            } catch (Exception e) {
                Log.e(TAG, "Failed to send to gateway: " + e.getMessage());
            }
        }).start();
    }

    private void startForegroundNotification() {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "OpenClaw Voice", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
            Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("OpenClaw Voice")
                .setContentText("Listening for wake word...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build();
            startForeground(NOTIFICATION_ID, notification);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        running = false;
        try { if (wakeWordDetector != null) wakeWordDetector.release(); } catch (Exception e) {}
        super.onDestroy();
    }
}
