package com.openclaw.settings;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VoiceListenerService extends Service {
    private static final String TAG = "OpenClawVoice";
    private static final String CHANNEL_ID = "openclaw_voice";
    private static final int NOTIFICATION_ID = 1002;

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
            whisperASR = new WhisperASR("/system/etc/openclaw/ggml-base.bin");
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
                if (wakeWordDetector.detect()) {
                    Log.i(TAG, "Wake word detected, starting ASR...");
                    String text = whisperASR.transcribe();
                    if (text != null && !text.trim().isEmpty()) {
                        Log.i(TAG, "Recognized: " + text);
                        sendToGateway(text.trim());
                    }
                }
                Thread.sleep(100);
            } catch (Exception e) {
                Log.e(TAG, "Voice loop error", e);
                try { Thread.sleep(1000); } catch (InterruptedException ie) { break; }
            }
        }
    }

    private void sendToGateway(String text) {
        new Thread(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences("openclaw_config", MODE_PRIVATE);
                int gatewayPort = prefs.getInt("gateway_port", 18789);
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
