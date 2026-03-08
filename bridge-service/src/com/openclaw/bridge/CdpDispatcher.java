package com.openclaw.bridge;

import android.util.Log;
import com.openclaw.bridge.cmd.*;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Routes incoming CDP-style messages to the correct command handler.
 *
 * Request:  {"id": 1, "method": "Android.tap", "params": {"x": 540, "y": 960}}
 * Response: {"id": 1, "result": {...}}
 * Error:    {"id": 1, "error": {"code": -32601, "message": "..."}}
 * Event:    {"method": "Android.activityResumed", "params": {...}}   (no id)
 */
public class CdpDispatcher {
    private static final String TAG = "OpenClaw.CdpDispatcher";

    private final DeviceController controller;

    public CdpDispatcher(DeviceController controller) {
        this.controller = controller;
    }

    /** Dispatch a raw JSON string, return a response JSON string (or null for no reply) */
    public String dispatch(String raw) {
        int id = -1;
        try {
            JSONObject req = new JSONObject(raw);
            id = req.optInt("id", -1);
            String method = req.optString("method", "");
            JSONObject params = req.optJSONObject("params");
            if (params == null) params = new JSONObject();

            Log.d(TAG, "dispatch: " + method + " id=" + id);

            JSONObject result = route(method, params);
            return buildResult(id, result);

        } catch (Exception e) {
            Log.e(TAG, "dispatch error", e);
            return buildError(id, -32603, "Internal error: " + e.getMessage());
        }
    }

    private JSONObject route(String method, JSONObject params) throws Exception {
        switch (method) {
            case "Android.tap":
                return TapCmd.execute(controller, params);
            case "Android.type":
                return TypeCmd.execute(controller, params);
            case "Android.screenshot":
                return ScreenshotCmd.execute(controller, params);
            case "Android.dumpUiTree":
                return DumpUiTreeCmd.execute(controller, params);
            case "Android.launch":
                return LaunchCmd.execute(controller, params);
            case "Android.shell":
                return ShellCmd.execute(controller, params);
            case "Android.swipe":
                return SwipeCmd.execute(controller, params);
            case "Android.keyEvent":
                return KeyEventCmd.execute(controller, params);
            case "Android.ping":
                JSONObject pong = new JSONObject();
                pong.put("pong", true);
                return pong;
            default:
                throw new Exception("Method not found: " + method);
        }
    }

    private String buildResult(int id, JSONObject result) {
        try {
            JSONObject resp = new JSONObject();
            if (id >= 0) resp.put("id", id);
            resp.put("result", result != null ? result : new JSONObject());
            return resp.toString();
        } catch (JSONException e) {
            return "{\"error\":{\"code\":-32603,\"message\":\"json error\"}}";
        }
    }

    private String buildError(int id, int code, String message) {
        try {
            JSONObject resp = new JSONObject();
            if (id >= 0) resp.put("id", id);
            JSONObject err = new JSONObject();
            err.put("code", code);
            err.put("message", message);
            resp.put("error", err);
            return resp.toString();
        } catch (JSONException e) {
            return "{\"error\":{\"code\":-32603,\"message\":\"json error\"}}";
        }
    }

    /** Build a server-initiated event JSON (no id) */
    public static String buildEvent(String method, JSONObject params) {
        try {
            JSONObject event = new JSONObject();
            event.put("method", method);
            event.put("params", params != null ? params : new JSONObject());
            return event.toString();
        } catch (JSONException e) {
            return "{\"method\":\"Android.error\",\"params\":{}}";
        }
    }
}
