package com.openclaw.bridge.cmd;
import com.openclaw.bridge.DeviceController;
import org.json.JSONObject;
public class LaunchCmd {
    public static JSONObject execute(DeviceController ctrl, JSONObject p) throws Exception {
        String pkg = p.getString("pkg");
        String act = p.optString("activity", "");
        ctrl.launchApp(pkg, act);
        JSONObject r = new JSONObject(); r.put("success", true); return r;
    }
}
