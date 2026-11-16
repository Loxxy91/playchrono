package dev.playchrono.web;

import dev.playchrono.util.Json;
import dev.playchrono.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Locale;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tiny HTTP server for the live dashboard. Deliberately minimal and locked down:
 * <ul>
 *   <li>binds 127.0.0.1 only;</li>
 *   <li>rejects any Host header other than 127.0.0.1/localhost (DNS-rebinding protection);</li>
 *   <li>serves data as JSON without CORS headers, never as a script, and refuses cross-site fetches;</li>
 *   <li>bounded request lines / headers, 5 s socket timeout;</li>
 *   <li>a single CSRF-protected write endpoint (leaderboard opt-in);</li>
 *   <li>only a fixed whitelist of files, served with a strict Content-Security-Policy.</li>
 * </ul>
 */
public final class MiniHttp {
    private final Dashboard dashboard;
    private ServerSocket socket;
    private int port;
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "PlayChrono-HTTP");
        t.setDaemon(true);
        return t;
    });

    private final java.util.function.Consumer<Boolean> onLeaderboard;
    private java.util.function.Consumer<Map<String, Object>> onPrefs;

    /** Receiver of POST /api/prefs ({"theme": ..., "keyboard": ...}); validates the values itself. */
    public MiniHttp prefs(java.util.function.Consumer<Map<String, Object>> c) { this.onPrefs = c; return this; }

    public MiniHttp(Dashboard dashboard) { this(dashboard, null); }

    public MiniHttp(Dashboard dashboard, java.util.function.Consumer<Boolean> onLeaderboard) {
        this.dashboard = dashboard;
        this.onLeaderboard = onLeaderboard;
    }

    /** Tries the configured port then the next few. Returns the bound port, or -1. */
    public int start(int wanted) {
        for (int p = wanted; p < wanted + 10; p++) {
            try {
                socket = new ServerSocket(p, 16, InetAddress.getByName("127.0.0.1"));
                port = p;
                Thread t = new Thread(this::loop, "PlayChrono-HTTP-accept");
                t.setDaemon(true);
                t.start();
                return p;
            } catch (IOException ignored) { /* port busy: another game instance may own it */ }
        }
        return -1;
    }

    public int port() { return port; }

    public String url() { return "http://127.0.0.1:" + port + "/"; }

    public void stop() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) { /* */ }
        pool.shutdownNow();
    }

    private void loop() {
        while (!socket.isClosed()) {
            try {
                Socket s = socket.accept();
                pool.execute(() -> handle(s));
            } catch (IOException e) {
                if (!socket.isClosed()) Log.warn("http accept failed", e);
            }
        }
    }

    private static final int MAX_LINE = 8192, MAX_HEADERS = 60;

    /** Reads one CRLF line with a hard length limit (no unbounded buffering from a local client). */
    private static String line(java.io.InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder(128);
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
            if (sb.length() > MAX_LINE) throw new IOException("line too long");
        }
        return c == -1 && sb.length() == 0 ? null : sb.toString();
    }

    private void handle(Socket s) {
        try (Socket sock = s) {
            sock.setSoTimeout(5000);
            java.io.InputStream in = new java.io.BufferedInputStream(sock.getInputStream(), 4096);
            String request = line(in);
            if (request == null) return;
            Map<String, String> h = new HashMap<>();
            String l;
            int count = 0;
            while ((l = line(in)) != null && !l.isEmpty()) {
                if (++count > MAX_HEADERS) return;
                int c = l.indexOf(':');
                if (c > 0) h.put(l.substring(0, c).trim().toLowerCase(Locale.ROOT), l.substring(c + 1).trim());
            }
            this.gzip.set(h.getOrDefault("accept-encoding", "").contains("gzip"));
            OutputStream out = sock.getOutputStream();
            String[] parts = request.split(" ");
            String host = h.get("host");
            if (parts.length < 2 || host == null || !(host.equals("127.0.0.1:" + port) || host.equals("localhost:" + port))) {
                send(out, 403, "text/plain", "forbidden".getBytes());
                return;
            }
            String path = parts[1];
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
            if (parts[0].equals("POST") && (path.equals("/api/leaderboard") || path.equals("/api/prefs"))) {
                postJson(path, in, h, out);
                return;
            }
            if (!(parts[0].equals("GET") || parts[0].equals("HEAD"))) { send(out, 405, "text/plain", "method not allowed".getBytes()); return; }
            // Fetch metadata: refuse reads initiated by another website (the page itself is same-origin).
            String site = h.get("sec-fetch-site");
            if (site != null && !(site.equals("same-origin") || site.equals("none"))) { send(out, 403, "text/plain", "forbidden".getBytes()); return; }
            if (path.equals("/")) path = "/index.html";
            if (path.equals("/api/data.json")) {
                send(out, 200, "application/json; charset=utf-8", dashboard.json(15_000).getBytes(StandardCharsets.UTF_8));
                lingeringClose(sock);
                return;
            }
            String file = path.substring(1);
            if (!Arrays.asList(Dashboard.ASSETS).contains(file)) { send(out, 404, "text/plain", "not found".getBytes()); return; }
            Path p = dashboard.dir().resolve(file);
            if (!Files.exists(p)) { send(out, 404, "text/plain", "not found".getBytes()); return; }
            send(out, 200, type(file), Files.readAllBytes(p));
            lingeringClose(sock);
        } catch (Exception e) {
            // client went away or sent garbage: nothing to do
        }
    }

    /**
     * The two write endpoints: the "join / leave the leaderboard" switch and the display preferences (theme,
     * keyboard layout). Protected against other websites (CSRF): exact Origin, a custom header (forces a CORS
     * preflight we never answer), tiny JSON body, values validated by the receiver.
     */
    private void postJson(String path, java.io.InputStream in, Map<String, String> h, OutputStream out) throws IOException {
        boolean lb = path.equals("/api/leaderboard");
        String origin = h.get("origin");
        boolean sameOrigin = ("http://127.0.0.1:" + port).equals(origin) || ("http://localhost:" + port).equals(origin);
        int len;
        try { len = Integer.parseInt(h.getOrDefault("content-length", "-1")); } catch (NumberFormatException e) { len = -1; }
        if (!sameOrigin || !"1".equals(h.get("x-playchrono")) || !h.getOrDefault("content-type", "").startsWith("application/json")
                || len < 2 || len > 256 || (lb ? onLeaderboard == null : onPrefs == null)) {
            send(out, 403, "text/plain", "forbidden".getBytes());
            return;
        }
        byte[] body = new byte[len];
        int off = 0;
        while (off < len) {
            int n = in.read(body, off, len - off);
            if (n < 0) return;
            off += n;
        }
        Map<String, Object> m = Json.map(Json.tryParse(new String(body, StandardCharsets.UTF_8)));
        if (m == null || (lb && !(m.get("enabled") instanceof Boolean))) { send(out, 400, "text/plain", "bad request".getBytes()); return; }
        if (lb) onLeaderboard.accept((Boolean) m.get("enabled"));
        else onPrefs.accept(m);
        send(out, 200, "application/json; charset=utf-8", "{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
    }

    private static String type(String f) {
        if (f.endsWith(".html")) return "text/html; charset=utf-8";
        if (f.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (f.endsWith(".css")) return "text/css; charset=utf-8";
        if (f.endsWith(".svg")) return "image/svg+xml";
        if (f.endsWith(".woff2")) return "font/woff2";
        if (f.endsWith(".txt")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }

    private final ThreadLocal<Boolean> gzip = ThreadLocal.withInitial(() -> false);

    private void send(OutputStream out, int code, String type, byte[] body) throws IOException {
        String enc = "";
        if (gzip.get() && body.length > 1024) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream(body.length / 4);
            try (java.util.zip.GZIPOutputStream g = new java.util.zip.GZIPOutputStream(b)) { g.write(body); }
            body = b.toByteArray();
            enc = "\r\nContent-Encoding: gzip\r\nVary: Accept-Encoding";
        }
        String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : " Error") + "\r\nContent-Type: " + type + enc
                + "\r\nContent-Length: " + body.length + "\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff"
                + "\r\nReferrer-Policy: no-referrer\r\nX-Frame-Options: DENY"
                + (type.startsWith("text/html") ? "\r\nContent-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                        + "img-src 'self' data:; font-src 'self'; connect-src 'self' https: http://127.0.0.1:* http://localhost:*; frame-ancestors 'none'; base-uri 'none'; form-action 'none'" : "")
                + "\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    /** Half-close then drain before closing: avoids the RST that truncates large responses on Windows. */
    private static void lingeringClose(Socket s) {
        try {
            s.shutdownOutput();
            s.setSoTimeout(2000);
            byte[] b = new byte[1024];
            while (s.getInputStream().read(b) > 0) { /* drain */ }
        } catch (IOException ignored) { /* already closed */ }
    }
}
