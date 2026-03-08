package com.openclaw.bridge;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.SystemClock;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;

import java.io.*;
import java.lang.reflect.Method;

/**
 * Performs device actions. Requires system UID for INJECT_EVENTS and READ_FRAME_BUFFER.
 */
public class DeviceController {
    private static final String TAG = "OpenClaw.DeviceCtrl";
    private final Context context;

    public DeviceController(Context context) {
        this.context = context;
    }

    // ─── Input ───────────────────────────────────────────────────────────────

    /** Inject a tap (down + up) at screen coordinates */
    public void tap(float x, float y) {
        long now = SystemClock.uptimeMillis();
        injectMotion(MotionEvent.ACTION_DOWN, x, y, now, now);
        injectMotion(MotionEvent.ACTION_UP, x, y, now, now + 50);
    }

    /** Inject a swipe gesture */
    public void swipe(float x1, float y1, float x2, float y2, long durationMs) {
        long now = SystemClock.uptimeMillis();
        int steps = (int) Math.max(durationMs / 16, 2);
        injectMotion(MotionEvent.ACTION_DOWN, x1, y1, now, now);
        for (int i = 1; i <= steps; i++) {
            float t = (float) i / steps;
            float mx = x1 + (x2 - x1) * t;
            float my = y1 + (y2 - y1) * t;
            injectMotion(MotionEvent.ACTION_MOVE, mx, my, now, now + i * (durationMs / steps));
        }
        injectMotion(MotionEvent.ACTION_UP, x2, y2, now, now + durationMs);
    }

    /** Type text by injecting key events for each character */
    public void typeText(String text) {
        // Use clipboard + paste for reliable Unicode support
        try {
            android.content.ClipboardManager cm =
                (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("octype", text));
            // Inject Ctrl+V
            injectKey(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON);
        } catch (Exception e) {
            Log.e(TAG, "typeText error", e);
        }
    }

    /** Inject a key event with optional meta keys */
    public void keyEvent(int keyCode, int metaState) {
        injectKey(keyCode, metaState);
    }

    // ─── Screenshot ──────────────────────────────────────────────────────────

    /**
     * Capture screenshot via SurfaceControl.screenshot (AOSP internal API).
     * Returns base64-encoded JPEG string.
     */
    public String screenshot() {
        try {
            // Use SurfaceControl.screenshot via reflection (available in AOSP system process)
            DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
            DisplayMetrics metrics = new DisplayMetrics();
            display.getRealMetrics(metrics);

            Class<?> scClass = Class.forName("android.view.SurfaceControl");
            Method screenshotMethod = scClass.getMethod("screenshot",
                    Rect.class, int.class, int.class);
            Bitmap bmp = (Bitmap) screenshotMethod.invoke(null,
                    new Rect(0, 0, metrics.widthPixels, metrics.heightPixels),
                    metrics.widthPixels, metrics.heightPixels);

            if (bmp == null) throw new RuntimeException("SurfaceControl.screenshot returned null");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos);
            bmp.recycle();
            return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

        } catch (Exception e) {
            Log.e(TAG, "screenshot error", e);
            return null;
        }
    }

    // ─── UI Tree ─────────────────────────────────────────────────────────────

    /**
     * Dump the UI hierarchy using uiautomator dump (runs as system, always succeeds).
     * Returns XML string of the current UI tree.
     */
    public String dumpUiTree() {
        try {
            File tmpFile = new File(context.getCacheDir(), "uidump.xml");
            Process proc = Runtime.getRuntime().exec(
                new String[]{"uiautomator", "dump", tmpFile.getAbsolutePath()});
            proc.waitFor();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(tmpFile)))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            tmpFile.delete();
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "dumpUiTree error", e);
            return null;
        }
    }

    // ─── App Launch ──────────────────────────────────────────────────────────

    /** Launch an app by package name (opens main activity) */
    public void launchApp(String packageName, String activityName) {
        try {
            Intent intent;
            if (activityName != null && !activityName.isEmpty()) {
                intent = new Intent(Intent.ACTION_MAIN);
                intent.setComponent(new ComponentName(packageName, activityName));
            } else {
                intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
                if (intent == null) {
                    Log.e(TAG, "no launch intent for: " + packageName);
                    return;
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "launchApp error", e);
        }
    }

    // ─── Shell ───────────────────────────────────────────────────────────────

    /** Execute a shell command and return stdout (max 64KB) */
    public String shell(String command) throws IOException, InterruptedException {
        Process proc = Runtime.getRuntime().exec(
            new String[]{"/system/bin/sh", "-c", command});
        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();

        // Read stdout
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(proc.getInputStream()))) {
            String line;
            int chars = 0;
            while ((line = r.readLine()) != null && chars < 65536) {
                out.append(line).append('\n');
                chars += line.length();
            }
        }
        // Read stderr
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(proc.getErrorStream()))) {
            String line;
            while ((line = r.readLine()) != null) err.append(line).append('\n');
        }

        int exitCode = proc.waitFor();
        return "exit=" + exitCode + "\n" + out.toString()
            + (err.length() > 0 ? "[stderr]\n" + err.toString() : "");
    }

    // ─── Internal helpers ────────────────────────────────────────────────────

    private void injectMotion(int action, float x, float y, long downTime, long eventTime) {
        try {
            MotionEvent ev = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
            ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            injectInputEvent(ev);
            ev.recycle();
        } catch (Exception e) {
            Log.e(TAG, "injectMotion error", e);
        }
    }

    private void injectKey(int keyCode, int metaState) {
        try {
            long now = SystemClock.uptimeMillis();
            KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState);
            KeyEvent up   = new KeyEvent(now, now + 50, KeyEvent.ACTION_UP, keyCode, 0, metaState);
            injectInputEvent(down);
            injectInputEvent(up);
        } catch (Exception e) {
            Log.e(TAG, "injectKey error", e);
        }
    }

    private void injectInputEvent(InputEvent event) throws Exception {
        // Use InputManager.injectInputEvent via reflection (requires INJECT_EVENTS permission)
        android.hardware.input.InputManager im =
            (android.hardware.input.InputManager) context.getSystemService(Context.INPUT_SERVICE);
        Method inject = android.hardware.input.InputManager.class.getMethod(
            "injectInputEvent", InputEvent.class, int.class);
        inject.setAccessible(true);
        inject.invoke(im, event, 0 /* INJECT_INPUT_EVENT_MODE_ASYNC */);
    }
}
