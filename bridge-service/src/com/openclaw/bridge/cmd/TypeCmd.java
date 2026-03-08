package com.openclaw.bridge.cmd;
import com.openclaw.bridge.DeviceController;
import org.json.JSONObject;
public class TypeCmd {
    public static JSONObject execute(DeviceController ctrl, JSONObject p) throws Exception {
        String text = p.getString("text");
        ctrl.typeText(text);
        JSONObject r = new JSONObject(); r.put("success", true); return r;
    }
}
