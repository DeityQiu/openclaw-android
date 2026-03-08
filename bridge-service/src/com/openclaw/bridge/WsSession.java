package com.openclaw.bridge;

import android.util.Log;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Represents a single WebSocket connection.
 * Reads frames, decodes text, passes to CdpDispatcher; sends frames back.
 */
public class WsSession {
    private static final String TAG = "OpenClaw.WsSession";
    private final Socket socket;
    private final OutputStream out;
    private final CdpDispatcher dispatcher;
    private volatile boolean open = true;

    public WsSession(Socket socket, OutputStream out, CdpDispatcher dispatcher) {
        this.socket = socket;
        this.out = out;
        this.dispatcher = dispatcher;
    }

    /** Blocking read loop — returns when connection closes */
    public void run(InputStream in) {
        try {
            while (open) {
                String msg = readFrame(in);
                if (msg == null) break;
                String response = dispatcher.dispatch(msg);
                if (response != null) send(response);
            }
        } catch (IOException e) {
            if (open) Log.d(TAG, "session read error: " + e.getMessage());
        } finally {
            close();
        }
    }

    /** Send a text frame to the client */
    public synchronized void send(String json) {
        if (!open) return;
        try {
            byte[] payload = json.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            frame.write(0x81); // FIN + opcode TEXT
            int len = payload.length;
            if (len <= 125) {
                frame.write(len);
            } else if (len <= 65535) {
                frame.write(126);
                frame.write((len >> 8) & 0xFF);
                frame.write(len & 0xFF);
            } else {
                frame.write(127);
                for (int i = 7; i >= 0; i--) frame.write((int)(((long)len >> (i * 8)) & 0xFF));
            }
            frame.write(payload);
            out.write(frame.toByteArray());
            out.flush();
        } catch (IOException e) {
            Log.e(TAG, "send error", e);
            close();
        }
    }

    public void close() {
        open = false;
        try { socket.close(); } catch (Exception ignored) {}
    }

    /** Read one complete WebSocket frame, return text payload (null = closed) */
    private String readFrame(InputStream in) throws IOException {
        int b0 = in.read();
        if (b0 < 0) return null;
        int b1 = in.read();
        if (b1 < 0) return null;

        int opcode = b0 & 0x0F;
        boolean masked = (b1 & 0x80) != 0;
        long payloadLen = b1 & 0x7F;

        if (payloadLen == 126) {
            payloadLen = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
        } else if (payloadLen == 127) {
            payloadLen = 0;
            for (int i = 0; i < 8; i++) payloadLen = (payloadLen << 8) | (in.read() & 0xFF);
        }

        byte[] mask = new byte[4];
        if (masked) readFully(in, mask);

        byte[] payload = new byte[(int) payloadLen];
        readFully(in, payload);
        if (masked) {
            for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
        }

        if (opcode == 0x8) { close(); return null; }  // close frame
        if (opcode == 0x9) { sendPong(payload); return readFrame(in); } // ping → pong
        if (opcode == 0x1 || opcode == 0x2) {
            return new String(payload, StandardCharsets.UTF_8);
        }
        return readFrame(in); // skip control frames
    }

    private void sendPong(byte[] payload) throws IOException {
        out.write(0x8A); out.write(payload.length);
        if (payload.length > 0) out.write(payload);
        out.flush();
    }

    private void readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) throw new IOException("stream closed");
            off += n;
        }
    }
}
