package com.openclaw.bridge.cmd;
import com.openclaw.bridge.DeviceController;
import org.json.JSONObject;
public class TapCmd {
    public static JSONObject execute(DeviceController ctrl, JSONObject p) throws Exception {
        float x = (float) p.getDouble("x");
        float y = (float) p.getDouble("y");
        ctrl.tap(x, y);
        JSONObject r = new JSONObject(); r.put("success", true); return r;
    }
}
