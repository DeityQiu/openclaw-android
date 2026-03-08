package com.openclaw.bridge;

import android.util.Log;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/**
 * RFC 6455 WebSocket server, listens on port 7788.
 * Each connected client gets a WsSession; messages are dispatched to CdpDispatcher.
 */
public class WsServer {
    private static final String TAG = "OpenClaw.WsServer";
    public static final int PORT = 7788;

    private final CdpDispatcher dispatcher;
    private ServerSocket serverSocket;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final Set<WsSession> sessions = Collections.synchronizedSet(new HashSet<>());
    private volatile boolean running = false;

    public WsServer(CdpDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress("0.0.0.0", PORT));
        running = true;
        Log.i(TAG, "WebSocket server listening on port " + PORT);

        pool.execute(() -> {
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    pool.execute(() -> handleClient(client));
                } catch (IOException e) {
                    if (running) Log.e(TAG, "accept error", e);
                }
            }
        });
    }

    public void stop() {
        running = false;
        try { serverSocket.close(); } catch (Exception ignored) {}
        synchronized (sessions) {
            for (WsSession s : sessions) s.close();
        }
        pool.shutdownNow();
    }

    /** Broadcast an event to all connected clients (no id = server-initiated event) */
    public void broadcast(String json) {
        synchronized (sessions) {
            for (WsSession s : sessions) s.send(json);
        }
    }

    private void handleClient(Socket socket) {
        try {
            socket.setTcpNoDelay(true);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // HTTP Upgrade handshake
            if (!doHandshake(in, out)) {
                socket.close();
                return;
            }

            WsSession session = new WsSession(socket, out, dispatcher);
            sessions.add(session);
            Log.i(TAG, "client connected: " + socket.getRemoteSocketAddress());
            try {
                session.run(in);
            } finally {
                sessions.remove(session);
                Log.i(TAG, "client disconnected: " + socket.getRemoteSocketAddress());
            }
        } catch (Exception e) {
            Log.e(TAG, "client handler error", e);
        }
    }

    /** Perform WebSocket HTTP upgrade handshake */
    private boolean doHandshake(InputStream in, OutputStream out) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim().toLowerCase(),
                            line.substring(colon + 1).trim());
            }
        }

        String key = headers.get("sec-websocket-key");
        if (key == null) return false;

        String accept = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1")
                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                    .getBytes(StandardCharsets.UTF_8))
        );

        String response = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();
        return true;
    }
}
