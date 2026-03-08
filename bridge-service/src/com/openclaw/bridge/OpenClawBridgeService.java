package com.openclaw.bridge;

import android.content.Context;
import android.util.Log;
import com.android.server.SystemService;
import com.openclaw.bridge.event.ActivityEventWatcher;
import com.openclaw.bridge.event.WindowEventWatcher;

/**
 * OpenClaw Android Bridge Service.
 *
 * Registered in AOSP SystemServer:
 *   mSystemServiceManager.startService(OpenClawBridgeService.class);
 *
 * Runs with system UID. Starts WebSocket server on port 7788.
 * Implements CDP-style protocol for OpenClaw Android Node.
 */
public class OpenClawBridgeService extends SystemService {
    private static final String TAG = "OpenClaw.BridgeService";

    private WsServer wsServer;
    private ActivityEventWatcher activityWatcher;
    private WindowEventWatcher windowWatcher;

    public OpenClawBridgeService(Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        Log.i(TAG, "OpenClawBridgeService starting...");
        try {
            Context ctx = getContext();

            // Build the device controller (requires system UID permissions)
            DeviceController controller = new DeviceController(ctx);

            // Build the CDP dispatcher (routes commands to controller)
            CdpDispatcher dispatcher = new CdpDispatcher(controller);

            // Start WebSocket server
            wsServer = new WsServer(dispatcher);
            wsServer.start();

            // Start event watchers
            activityWatcher = new ActivityEventWatcher(ctx, wsServer);
            activityWatcher.start();

            windowWatcher = new WindowEventWatcher(ctx, wsServer);
            windowWatcher.start();

            Log.i(TAG, "OpenClawBridgeService started on ws://0.0.0.0:7788");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start OpenClawBridgeService", e);
        }
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            Log.i(TAG, "System services ready — OpenClaw Bridge fully operational");
        }
    }
}
