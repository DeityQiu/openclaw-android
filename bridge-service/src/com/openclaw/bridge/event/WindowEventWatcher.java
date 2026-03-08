package com.openclaw.bridge.event;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import com.openclaw.bridge.CdpDispatcher;
import com.openclaw.bridge.WsServer;
import org.json.JSONObject;

/**
 * Listens for AccessibilityEvents to detect window state changes.
 * Emits Android.windowChanged CDP events.
 */
public class WindowEventWatcher {
    private static final String TAG = "OpenClaw.WindowWatcher";

    private final Context context;
    private final WsServer wsServer;
    private AccessibilityManager.AccessibilityStateChangeListener stateListener;

    public WindowEventWatcher(Context context, WsServer wsServer) {
        this.context = context;
        this.wsServer = wsServer;
    }

    public void start() {
        // Register for accessibility state changes
        AccessibilityManager am =
            (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        stateListener = enabled -> {
            try {
                JSONObject params = new JSONObject();
                params.put("accessibilityEnabled", enabled);
                params.put("timestamp", System.currentTimeMillis());
                wsServer.broadcast(CdpDispatcher.buildEvent("Android.accessibilityStateChanged", params));
            } catch (Exception ignored) {}
        };
        am.addAccessibilityStateChangeListener(stateListener);
        Log.i(TAG, "WindowEventWatcher started");
    }

    public void stop() {
        if (stateListener != null) {
            AccessibilityManager am =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
            am.removeAccessibilityStateChangeListener(stateListener);
        }
    }

    /** Emit a window-changed event (called externally when UI tree changes) */
    public void emitWindowChanged(String packageName) {
        try {
            JSONObject params = new JSONObject();
            params.put("pkg", packageName != null ? packageName : "");
            params.put("timestamp", System.currentTimeMillis());
            wsServer.broadcast(CdpDispatcher.buildEvent("Android.windowChanged", params));
        } catch (Exception e) {
            Log.e(TAG, "emitWindowChanged error", e);
        }
    }
}
