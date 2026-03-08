package com.openclaw.bridge.cmd;
import com.openclaw.bridge.DeviceController;
import org.json.JSONObject;
public class KeyEventCmd {
    public static JSONObject execute(DeviceController ctrl, JSONObject p) throws Exception {
        int keyCode = p.getInt("keyCode");
        int meta = p.optInt("metaState", 0);
        ctrl.keyEvent(keyCode, meta);
        JSONObject r = new JSONObject(); r.put("success", true); return r;
    }
}
