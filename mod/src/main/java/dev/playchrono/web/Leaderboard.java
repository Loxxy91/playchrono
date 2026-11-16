package dev.playchrono.web;

import dev.playchrono.reflect.R;
import dev.playchrono.store.AppState;
import dev.playchrono.util.Config;
import dev.playchrono.util.Json;
import dev.playchrono.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Online leaderboard client (OPT-IN, off by default).
 *
 * <p>Identity is proven the same way a Minecraft server does it: the leaderboard server hands out a one-time
 * {@code serverId}, the game tells Mojang's session server "this account joins serverId" with its access token,
 * and the leaderboard server asks Mojang {@code hasJoined}. The access token only ever goes to Mojang; the
 * leaderboard server receives the name, the UUID and the totals, nothing else. Nobody can post under someone
 * else's name, and offline (non-premium) accounts cannot take part.
 */
public final class Leaderboard {
    private static final String MOJANG_JOIN = "https://sessionserver.mojang.com/session/minecraft/join";
    private final Config cfg;
    private final AppState state;
    private final String mod;

    public Leaderboard(Config cfg, AppState state, String modVersion) {
        this.cfg = cfg;
        this.state = state;
        this.mod = modVersion;
    }

    public String url() {
        String u = cfg.str("leaderboard.url");
        if (u.isEmpty()) u = Config.builtIn("leaderboard.url");
        u = u.replaceAll("/+$", "");
        return safe(u) ? u : "";
    }

    /** HTTPS only, except for a server on this very machine (testing). */
    static boolean safe(String u) {
        if (u.isEmpty()) return false;
        try {
            URI x = URI.create(u);
            String host = x.getHost() == null ? "" : x.getHost();
            return "https".equals(x.getScheme()) || ("http".equals(x.getScheme()) && (host.equals("127.0.0.1") || host.equals("localhost")));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean enabled() { return cfg.bool("leaderboard.enabled") && !url().isEmpty(); }

    public void setEnabled(boolean on) {
        cfg.set("leaderboard.enabled", Boolean.toString(on));
        state.put("lbStatus", on ? "pending" : "off");
        if (on) state.remove("lbLeave");
        else if (state.str("lbLast") != null) state.put("lbLeave", "1"); // something was sent: ask the server to delete it
    }

    public boolean leavePending() { return state.str("lbLeave") != null && !url().isEmpty(); }

    /** Deletes this player's entry from the leaderboard server, with the same Mojang proof as a submission. */
    public boolean leave(int timeoutMs) {
        if (!leavePending()) return true;
        try {
            Map<String, Object> body = prove(url(), timeoutMs, false);
            if (body == null) return false;
            http("POST", url() + "/api/v1/leave", Json.write(body), timeoutMs);
            state.remove("lbLeave");
            state.remove("lbLast");
            return true;
        } catch (IOException e) {
            return false; // retried at the next refresh
        } catch (Throwable t) {
            Log.warn("leaderboard leave failed", t);
            return false;
        }
    }

    /**
     * Mojang proof: one-time challenge from our server, then "join" it on Mojang's session server with the game's
     * access token (which only ever goes to Mojang). Returns {name, uuid, serverId}, or null (status updated).
     */
    private Map<String, Object> prove(String base, int timeoutMs, boolean report) throws IOException {
        Object mc = R.callStatic("Minecraft.getInstance");
        Object user = R.call(mc, "Minecraft.getUser");
        String name = R.getStr(user, "User.getName");
        Object id = R.call(user, "User.getProfileId");
        String token = R.getStr(user, "User.getAccessToken");
        if (name == null || id == null || token == null || token.length() < 20) { if (report) status("offline-account"); return null; }
        String uuid = id.toString().replace("-", "");
        Map<String, Object> ch = Json.map(Json.tryParse(http("GET", base + "/api/v1/challenge", null, timeoutMs)));
        String serverId = ch == null ? null : Json.str(ch.get("serverId"), null);
        if (serverId == null || !serverId.matches("[0-9a-f]{16,64}")) { if (report) status("server-down"); return null; }
        Map<String, Object> join = new LinkedHashMap<>();
        join.put("accessToken", token);
        join.put("selectedProfile", uuid);
        join.put("serverId", serverId);
        try {
            http("POST", MOJANG_JOIN, Json.write(join), timeoutMs);
        } catch (HttpError e) {
            if (report) status(e.code == 403 ? "offline-account" : "mojang-down");
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("uuid", uuid);
        body.put("serverId", serverId);
        return body;
    }

    /** Info for the dashboard (no secrets). */
    public Map<String, Object> info(String name, String uuid) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("url", url());
        m.put("enabled", enabled());
        m.put("status", state.str("lbStatus") == null ? "off" : state.str("lbStatus"));
        if (state.str("lbLast") != null) m.put("last", Long.parseLong(state.str("lbLast")));
        if (name != null) m.put("name", name);
        if (uuid != null) m.put("uuid", uuid);
        return m;
    }

    /** Verifies the account with Mojang then submits the totals. Background thread only; never throws. */
    public boolean submit(Map<String, Object> me, String mcVersion, String loader, int timeoutMs) {
        if (!enabled() || me == null) return false;
        String base = url();
        try {
            Map<String, Object> body = prove(base, timeoutMs, true);
            if (body == null) return false;
            body.put("mod", mod);
            body.put("mc", mcVersion);
            body.put("loader", loader);
            Map<String, Object> stats = new LinkedHashMap<>(me);
            if (!cfg.bool("privacy.serverAddresses")) stats.remove("topServers");
            body.put("stats", stats);
            http("POST", base + "/api/v1/submit", Json.write(body), timeoutMs);
            state.put("lbLast", Long.toString(System.currentTimeMillis()));
            status("ok");
            return true;
        } catch (HttpError e) {
            status(e.code == 400 ? "rejected" : "server-down");
            Log.warn("leaderboard: HTTP " + e.code);
        } catch (IOException e) {
            status("server-down");
        } catch (Throwable t) {
            Log.warn("leaderboard submit failed", t);
        }
        return false;
    }

    private void status(String s) {
        if (!s.equals(state.str("lbStatus"))) state.put("lbStatus", s);
    }

    static final class HttpError extends IOException {
        final int code;
        HttpError(int code) { super("HTTP " + code); this.code = code; }
    }

    static String http(String method, String url, String body, int timeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setRequestMethod(method);
        c.setRequestProperty("User-Agent", "PlayChrono");
        c.setRequestProperty("Accept", "application/json");
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        int code = c.getResponseCode();
        if (code / 100 != 2) { c.disconnect(); throw new HttpError(code); }
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 64 * 1024) throw new IOException("response too large");
                b.write(buf, 0, n);
            }
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }
}
