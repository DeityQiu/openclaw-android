package com.openclaw.bridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "OpenClawBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i(TAG, "Boot completed, starting OpenClawBridgeService");
            try {
                Intent serviceIntent = new Intent(context, OpenClawBridgeService.class);
                context.startForegroundService(serviceIntent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start service", e);
            }
        }
    }
}
