package com.openclaw.bridge.cmd;
import com.openclaw.bridge.DeviceController;
import org.json.JSONObject;
public class ShellCmd {
    public static JSONObject execute(DeviceController ctrl, JSONObject p) throws Exception {
        String cmd = p.getString("command");
        String output = ctrl.shell(cmd);
        JSONObject r = new JSONObject();
        r.put("output", output);
        return r;
    }
}
