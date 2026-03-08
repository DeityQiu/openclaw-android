package com.openclaw.settings;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
    public void onReceive(Context ctx, Intent i) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) {
            SharedPreferences p = ctx.getSharedPreferences("openclaw_config", 0);
            if (p.getBoolean("voice_enabled", false))
                ctx.startForegroundService(new Intent(ctx, VoiceListenerService.class));
        }
    }
}
