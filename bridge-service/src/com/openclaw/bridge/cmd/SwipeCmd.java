package com.openclaw.bridge.cmd;
import com.openclaw.bridge.DeviceController;
import org.json.JSONObject;
public class SwipeCmd {
    public static JSONObject execute(DeviceController ctrl, JSONObject p) throws Exception {
        float x1 = (float) p.getDouble("x1"), y1 = (float) p.getDouble("y1");
        float x2 = (float) p.getDouble("x2"), y2 = (float) p.getDouble("y2");
        long dur = p.optLong("duration", 300);
        ctrl.swipe(x1, y1, x2, y2, dur);
        JSONObject r = new JSONObject(); r.put("success", true); return r;
    }
}
