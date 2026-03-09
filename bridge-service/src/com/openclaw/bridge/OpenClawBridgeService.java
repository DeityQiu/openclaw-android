package com.openclaw.bridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class OpenClawBridgeService extends Service {
    private static final String TAG = "OpenClawBridge";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "openclaw_bridge";
    private static final int BRIDGE_PORT = 7788;

    private WsServer wsServer;
    private DeviceController deviceController;
    private ActivityEventWatcher activityWatcher;
    private WindowEventWatcher windowWatcher;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundNotification();
        startBridge();
    }

    private void startBridge() {
        try {
            deviceController = new DeviceController(this);
            CdpDispatcher dispatcher = new CdpDispatcher(deviceController);
            wsServer = new WsServer(BRIDGE_PORT, dispatcher);
            wsServer.start();
            Log.i(TAG, "OpenClaw Bridge started on port " + BRIDGE_PORT);
        } catch (Exception e) {
            // DO NOT rethrow — crashing here with persistent=true causes boot loop
            Log.e(TAG, "Bridge failed to start, service will idle", e);
        }

        try {
            activityWatcher = new ActivityEventWatcher(this);
            activityWatcher.start();
            windowWatcher = new WindowEventWatcher(this);
            windowWatcher.start();
        } catch (Exception e) {
            Log.e(TAG, "Event watchers failed to start", e);
        }
    }

    private void startForegroundNotification() {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "OpenClaw Bridge", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
            Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("OpenClaw Bridge")
                .setContentText("Running on port " + BRIDGE_PORT)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
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
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        try {
            if (wsServer != null) wsServer.stop();
            if (activityWatcher != null) activityWatcher.stop();
            if (windowWatcher != null) windowWatcher.stop();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping bridge", e);
        }
        super.onDestroy();
    }
}
