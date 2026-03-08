package com.openclaw.bridge.cmd;
import com.openclaw.bridge.DeviceController;
import org.json.JSONObject;
public class DumpUiTreeCmd {
    public static JSONObject execute(DeviceController ctrl, JSONObject p) throws Exception {
        String xml = ctrl.dumpUiTree();
        JSONObject r = new JSONObject();
        if (xml != null && !xml.isEmpty()) {
            r.put("mode", "ui_tree");
            r.put("xml", xml);
        } else {
            r.put("mode", "screenshot_fallback");
            r.put("reason", "no_accessible_nodes");
            String ss = ctrl.screenshot();
            if (ss != null) r.put("screenshot", ss);
        }
        return r;
    }
}
