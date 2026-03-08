package com.openclaw.settings;
import android.util.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;
public class BridgeClient {
    private static final String TAG = "OpenClaw.BridgeClient";
    public static void sendOnce(String host, int port, String json) {
        new Thread(() -> {
            try (Socket s = new Socket(host, port)) {
                s.setSoTimeout(3000);
                InputStream in = s.getInputStream();
                OutputStream out = s.getOutputStream();
                byte[] keyBytes = new byte[16]; new Random().nextBytes(keyBytes);
                String key = Base64.encodeToString(keyBytes, Base64.NO_WRAP);
                String req = "GET / HTTP/1.1\r\nHost: " + host + "\r\nUpgrade: websocket\r\n"
                    + "Connection: Upgrade\r\nSec-WebSocket-Key: " + key + "\r\nSec-WebSocket-Version: 13\r\n\r\n";
                out.write(req.getBytes()); out.flush();
                StringBuilder hdr = new StringBuilder();
                while (true) { hdr.append((char)in.read()); if (hdr.length()>=4 && hdr.substring(hdr.length()-4).equals("\r\n\r\n")) break; }
                if (!hdr.toString().contains("101")) return;
                byte[] payload = json.getBytes(StandardCharsets.UTF_8);
                byte[] mask = new byte[4]; new Random().nextBytes(mask);
                ByteArrayOutputStream frame = new ByteArrayOutputStream();
                frame.write(0x81);
                if (payload.length<=125) frame.write(0x80|payload.length);
                else { frame.write(0x80|126); frame.write((payload.length>>8)&0xFF); frame.write(payload.length&0xFF); }
                frame.write(mask);
                for (int i=0;i<payload.length;i++) frame.write(payload[i]^mask[i%4]);
                out.write(frame.toByteArray()); out.flush();
                out.write(new byte[]{(byte)0x88,(byte)0x80,0,0,0,0}); out.flush();
            } catch (Exception e) { Log.e(TAG, "error", e); }
        }).start();
    }
}
