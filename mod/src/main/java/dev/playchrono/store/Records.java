package dev.playchrono.store;

import dev.playchrono.util.Json;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Leaderboard-ready totals. Fed by the Aggregator with data the mod MEASURED itself (sessions from the install
 * onwards): imported log estimates never count, so every player starts from zero on the day they install.
 * The same map is shown in the dashboard ("You") and, when the player opted in, sent to the leaderboard server.
 */
final class Records {
    /** Metric keys, in display order. Shared with web/app.js and the leaderboard server. */
    static final String[] METRICS = {"active", "last7", "last30", "streak", "bestStreak", "longest", "days", "distance",
            "elytra", "biomes", "servers", "versions", "jumps", "apm", "deaths", "maxY", "farthest", "speed", "levels", "chat"};

    private long active, keys, clicks, jumps, chat, deaths, longest;
    private double distance, elytra, levels, maxY = Double.NaN, farthest, speed;
    private final Map<String, Long> perDay = new HashMap<>(), perServer = new HashMap<>();
    /** Public servers only: never LAN, localhost or private addresses (mirrors the server-side filter). */
    private static final java.util.regex.Pattern PRIVATE = java.util.regex.Pattern.compile(
            "^(localhost|127[.]|10[.]|192[.]168[.]|172[.](1[6-9]|2[0-9]|3[01])[.]|0[.]|169[.]254[.])|[.]local(:|$)|[.]lan(:|$)|unknown");
    private final Set<String> biomes = new HashSet<>(), servers = new HashSet<>(), versions = new HashSet<>();
    private long since = Long.MAX_VALUE;

    void session(long start, long end) {
        since = Math.min(since, start);
        longest = Math.max(longest, end - start);
    }

    void active(String date, long ms, String version, String ctx) {
        active += ms;
        perDay.merge(date, ms, Long::sum);
        if (version != null && !"?".equals(version)) versions.add(version);
        if (ctx != null && ctx.startsWith("server:")) {
            servers.add(ctx);
            String host = ctx.substring(7);
            if (!PRIVATE.matcher(host).find() && host.length() <= 80) perServer.merge(host, ms, Long::sum);
        }
    }

    void death() { deaths++; }

    void counters(Map<String, Object> n) {
        keys += Json.num(n.get("keys"), 0);
        clicks += Json.num(n.get("clickL"), 0) + Json.num(n.get("clickR"), 0) + Json.num(n.get("clickM"), 0);
        jumps += Json.num(n.get("jump"), 0);
        chat += Json.num(n.get("chat"), 0);
        levels += Json.dbl(n.get("xpLevels"), 0);
        Map<String, Object> mv = Json.map(n.get("mv"));
        if (mv != null) {
            for (Map.Entry<String, Object> e : mv.entrySet()) {
                double d = Json.dbl(e.getValue(), 0);
                distance += d;
                if ("elytra".equals(e.getKey())) elytra += d;
            }
        }
        Map<String, Object> bio = Json.map(n.get("bio"));
        if (bio != null) biomes.addAll(bio.keySet());
        if (n.get("yMax") instanceof Number) {
            double y = Json.dbl(n.get("yMax"), 0);
            maxY = Double.isNaN(maxY) ? y : Math.max(maxY, y);
        }
        farthest = Math.max(farthest, Json.dbl(n.get("farMax"), 0));
        speed = Math.max(speed, Math.min(400, Json.dbl(n.get("speedMax"), 0)));
    }

    Map<String, Object> stats(LocalDate today) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("active", active);
        m.put("last7", window(today, 7));
        m.put("last30", window(today, 30));
        int cur = 0, best = 0, run = 0;
        LocalDate c = today;
        if (!played(c)) c = c.minusDays(1);
        while (played(c)) { cur++; c = c.minusDays(1); }
        LocalDate prev = null;
        for (String k : new java.util.TreeSet<>(perDay.keySet())) {
            if (!played(LocalDate.parse(k))) continue;
            LocalDate d = LocalDate.parse(k);
            run = prev != null && prev.plusDays(1).equals(d) ? run + 1 : 1;
            best = Math.max(best, run);
            prev = d;
        }
        m.put("streak", cur);
        m.put("bestStreak", best);
        m.put("longest", longest);
        m.put("days", perDay.values().stream().filter(v -> v >= 60_000).count());
        m.put("distance", Math.round(distance));
        m.put("elytra", Math.round(elytra));
        m.put("biomes", biomes.size());
        m.put("servers", servers.size());
        m.put("versions", versions.size());
        m.put("jumps", jumps);
        m.put("apm", active >= 600_000 ? Math.round((keys + clicks) / (active / 60_000.0) * 10) / 10.0 : 0);
        m.put("deaths", deaths);
        m.put("maxY", Double.isNaN(maxY) ? 0 : Math.round(maxY));
        m.put("farthest", Math.round(farthest));
        m.put("speed", Math.round(speed * 10) / 10.0);
        m.put("levels", Math.round(levels));
        m.put("chat", chat);
        m.put("since", since == Long.MAX_VALUE ? 0 : since);
        java.util.List<Object> top = new java.util.ArrayList<>();
        perServer.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(20)
                .forEach(e -> top.add(java.util.Arrays.asList(e.getKey(), e.getValue())));
        m.put("topServers", top);
        return m;
    }

    private boolean played(LocalDate d) { return perDay.getOrDefault(d.toString(), 0L) >= 60_000; }

    private long window(LocalDate today, int days) {
        long s = 0;
        for (int i = 0; i < days; i++) s += perDay.getOrDefault(today.minusDays(i).toString(), 0L);
        return s;
    }
}
