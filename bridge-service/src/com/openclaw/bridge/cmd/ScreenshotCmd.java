package com.openclaw.bridge.cmd;
import com.openclaw.bridge.DeviceController;
import org.json.JSONObject;
public class ScreenshotCmd {
    public static JSONObject execute(DeviceController ctrl, JSONObject p) throws Exception {
        String b64 = ctrl.screenshot();
        if (b64 == null) throw new Exception("screenshot failed");
        JSONObject r = new JSONObject();
        r.put("data", b64);
        r.put("mimeType", "image/jpeg");
        return r;
    }
}
