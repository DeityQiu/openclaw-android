package com.openclaw.bridge.event;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import com.openclaw.bridge.CdpDispatcher;
import com.openclaw.bridge.WsServer;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Polls ActivityManager for foreground app changes and emits CDP events.
 * A proper hook would use IActivityManager.registerTaskStackListener,
 * but polling is reliable and simpler for system-uid context.
 */
public class ActivityEventWatcher {
    private static final String TAG = "OpenClaw.ActivityWatcher";
    private static final int POLL_INTERVAL_MS = 500;

    private final Context context;
    private final WsServer wsServer;
    private HandlerThread thread;
    private Handler handler;
    private String lastActivity = "";
    private volatile boolean running = false;

    public ActivityEventWatcher(Context context, WsServer wsServer) {
        this.context = context;
        this.wsServer = wsServer;
    }

    public void start() {
        running = true;
        thread = new HandlerThread("openclaw-activity-watcher");
        thread.start();
        handler = new Handler(thread.getLooper());
        handler.post(pollRunnable);
        Log.i(TAG, "ActivityEventWatcher started");
    }

    public void stop() {
        running = false;
        if (thread != null) thread.quitSafely();
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            try {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
                if (tasks != null && !tasks.isEmpty()) {
                    ActivityManager.RunningTaskInfo top = tasks.get(0);
                    if (top.topActivity != null) {
                        String current = top.topActivity.flattenToShortString();
                        if (!current.equals(lastActivity)) {
                            lastActivity = current;
                            emitActivityResumed(
                                top.topActivity.getPackageName(),
                                top.topActivity.getClassName()
                            );
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "poll error", e);
            }
            if (running) handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private void emitActivityResumed(String pkg, String activity) {
        try {
            JSONObject params = new JSONObject();
            params.put("pkg", pkg);
            params.put("activity", activity);
            params.put("timestamp", System.currentTimeMillis());
            wsServer.broadcast(CdpDispatcher.buildEvent("Android.activityResumed", params));
            Log.d(TAG, "activityResumed: " + pkg + "/" + activity);
        } catch (Exception e) {
            Log.e(TAG, "emitActivityResumed error", e);
        }
    }
}
