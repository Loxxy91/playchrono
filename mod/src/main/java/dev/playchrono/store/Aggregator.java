package dev.playchrono.store;

import dev.playchrono.probe.State;
import dev.playchrono.util.Json;
import dev.playchrono.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Rebuilds the dashboard "rollup" from the raw journals: one entry per local calendar day plus sessions, servers,
 * versions, deaths... This JSON is what the local dashboard reads
 * (see docs/DATA_FORMAT.md). Everything is recomputed from scratch, so the format can evolve freely.
 */
public final class Aggregator {
    private static final String CLS = "pukmlb"; // p play, u in-world UI, k afk, m menu, l loading, b background
    private static final int NCLS = 6;

    public static final class Result {
        public Map<String, Object> rollup;
        /** Leaderboard totals measured by the mod since install (see Records). */
        public Map<String, Object> me;
        public long todayActive, totalActive;
        public int streak;
    }

    private final ZoneId zone = ZoneId.systemDefault();
    private final Map<String, Day> days = new TreeMap<>();
    private final List<Map<String, Object>> sessions = new ArrayList<>();
    private final Map<String, Map<String, Object>> ctxMeta = new HashMap<>();
    /** Per server / world: active time by hour of day and by weekday, distinct days, recent visits. */
    private final Map<String, long[]> ctxHours = new HashMap<>(), ctxWeekdays = new HashMap<>();
    private final Map<String, java.util.Set<String>> ctxDays = new HashMap<>();
    private final Map<String, List<long[]>> ctxVisits = new HashMap<>();
    /** A visit (= a session on one server) ends when the player stays away from it for more than this. */
    static final long VISIT_GAP = 3 * 60_000L;
    private final Map<String, Map<String, Object>> versions = new TreeMap<>();
    private final Map<String, Long> accounts = new HashMap<>();
    private final List<Map<String, Object>> deaths = new ArrayList<>();
    private final List<Map<String, Object>> disconnects = new ArrayList<>();
    private final List<Map<String, Object>> milestones = new ArrayList<>();
    private final List<long[]> tracked = new ArrayList<>();
    private final List<long[]> importedCovered = new ArrayList<>();
    private final Records records = new Records();
    private Map<String, Object> lastEnv = new LinkedHashMap<>();
    private Object lastMods;
    private long lastEnvTs;

    // ============================================================================================ day model
    private static final class Day {
        long o, a, k, bg;
        final Map<String, Object> st = new HashMap<>(), sk = new HashMap<>(), ver = new HashMap<>(), ldr = new HashMap<>(),
                dim = new HashMap<>(), sc = new HashMap<>(), n = new LinkedHashMap<>(), ev = new LinkedHashMap<>();
        final Map<String, long[]> ctx = new HashMap<>();
        final long[] h = new long[24], ho = new long[24], cls = new long[NCLS];
        byte[] tl = new byte[1440 * NCLS];
        int first = -1, last = -1;
        boolean imp;
        int sess;
    }

    private String curDate;
    private long curStart, curEnd;
    private int curWd;

    private Day dayAt(long t) {
        if (curDate == null || t < curStart || t >= curEnd) {
            LocalDate d = Instant.ofEpochMilli(t).atZone(zone).toLocalDate();
            curDate = d.toString();
            curStart = d.atStartOfDay(zone).toInstant().toEpochMilli();
            curEnd = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            curWd = d.getDayOfWeek().getValue() - 1; // Monday = 0
        }
        return days.computeIfAbsent(curDate, k -> new Day());
    }

    // ============================================================================================ entry point
    public static Result run(Path dataDir) {
        Aggregator ag = new Aggregator();
        ag.dataDir = dataDir;
        long t0 = System.currentTimeMillis();
        List<Path> live = list(dataDir.resolve("sessions"));
        List<Path> imported = list(dataDir.resolve("imported"));
        for (Path p : live) ag.readFile(p, false);
        for (Path p : imported) ag.readFile(p, true);
        Result r = ag.finish();
        Log.info("aggregated " + live.size() + " sessions + " + imported.size() + " import files in "
                + (System.currentTimeMillis() - t0) + " ms");
        return r;
    }

    private static List<Path> list(Path dir) {
        if (!Files.isDirectory(dir)) return new ArrayList<>();
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".jsonl")).sorted().collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    // ============================================================================================ reading
    private final class Session {
        final Map<String, Object> start;
        final List<Map<String, Object>> buffered = new ArrayList<>();
        long s, e, o, a, k;
        String ver = "?", ldr = "?", acct;
        boolean imp, valid = true;
        long boot = -1;
        final Map<String, long[]> ctx = new HashMap<>();
        String visCtx;
        long visStart, visEnd, visActive;
        Session(Map<String, Object> start, boolean imp) {
            this.start = start;
            this.imp = imp;
            this.s = Json.num(start.get("ts"), 0);
            this.e = s;
            this.ver = Json.str(start.get("mc"), "?");
            this.ldr = Json.str(start.get("ldr"), "?");
            this.acct = Json.str(start.get("acct"), null);
        }
    }

    private Path dataDir;

    private void readFile(Path p, boolean importFile) {
        Session cur = null;
        List<String> lines;
        try {
            lines = Files.readAllLines(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.warn("cannot read " + p, e);
            return;
        }
        boolean valid = true;
        if (!importFile) {
            Map<String, Object> first = lines.isEmpty() ? null : Json.map(Json.tryParse(Signer.split(lines.get(0))[0]));
            long start = first == null ? 0 : Json.num(first.get("ts"), 0);
            valid = first != null && first.get("tp") == null && Signer.of(dataDir).verify(lines, start);
        }
        {
            for (String raw : lines) {
                String line = importFile ? raw : Signer.split(raw)[0];
                Map<String, Object> rec = Json.map(Json.tryParse(line));
                if (rec == null) continue; // truncated line after a crash
                String t = Json.str(rec.get("t"), "");
                if ("start".equals(t)) {
                    if (cur != null) closeSession(cur);
                    cur = new Session(rec, importFile || rec.get("imp") != null);
                    cur.valid = valid;
                    if (!cur.imp) {
                        Object env = rec.get("env");
                        if (env instanceof Map && cur.s >= lastEnvTs) {
                            lastEnv = new LinkedHashMap<>(Json.map(env));
                            lastMods = rec.get("mods");
                            lastEnvTs = cur.s;
                        }
                    }
                    continue;
                }
                if (cur == null) continue;
                if (cur.imp) cur.buffered.add(rec); // decided at session end (dedup against tracked sessions)
                else apply(cur, rec);
                if ("end".equals(t)) {
                    closeSession(cur);
                    cur = null;
                }
            }
        }
        if (cur != null) closeSession(cur);
    }

    /** Ends the current visit of this session (if any) and files it under its server. */
    private void closeVisit(Session s) {
        if (s.visCtx == null) return;
        long len = s.visEnd - s.visStart;
        Map<String, Object> cm = ctxMeta.computeIfAbsent(s.visCtx, x -> new LinkedHashMap<>());
        addNum(cm, "v", 1);
        maxNum(cm, "vMax", len);
        List<long[]> vl = ctxVisits.computeIfAbsent(s.visCtx, x -> new ArrayList<>());
        vl.add(new long[]{s.visStart, len, s.visActive});
        if (vl.size() > 400) { // keep the most recent ones only
            vl.sort((x, y) -> Long.compare(y[0], x[0]));
            vl.subList(200, vl.size()).clear();
        }
        s.visCtx = null;
    }

    private void closeSession(Session s) {
        closeVisit(s);
        if (s.imp) {
            long end = s.s;
            for (Map<String, Object> r : s.buffered) {
                end = Math.max(end, Json.num(r.get("ts"), Json.num(r.get("s"), 0) + Json.num(r.get("d"), 0)));
            }
            for (long[] iv : tracked) if (s.s < iv[1] && end > iv[0]) return; // the mod tracked this one precisely
            // Two instances open at once (or the same logs seen through two launchers) must not double a person's
            // time: an imported session mostly covered by an already accepted one is dropped.
            long overlap = 0;
            for (long[] iv : importedCovered) overlap += Math.max(0, Math.min(end, iv[1]) - Math.max(s.s, iv[0]));
            if (overlap > (end - s.s) / 2) return;
            importedCovered.add(new long[]{s.s, end});
            for (Map<String, Object> r : s.buffered) apply(s, r);
        } else {
            tracked.add(new long[]{s.s, s.e});
            if (s.valid) records.session(s.s, s.e);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("s", s.s);
        m.put("e", s.e);
        m.put("o", s.o);
        m.put("a", s.a);
        m.put("k", s.k);
        m.put("ver", s.ver);
        m.put("ldr", s.ldr);
        if (s.acct != null) m.put("acct", s.acct);
        if (s.boot >= 0) m.put("boot", s.boot);
        if (s.imp) m.put("imp", 1);
        List<Object> top = s.ctx.entrySet().stream()
                .sorted((x, y) -> Long.compare(y.getValue()[2], x.getValue()[2])).limit(4)
                .map(Map.Entry::getKey).collect(Collectors.toList());
        m.put("ctx", top);
        if (s.o > 0 || s.e - s.s > 0) sessions.add(m);
        Day d = dayAt(s.s);
        d.sess++;
        if (s.imp) d.imp = true;
        if (s.acct != null) accounts.merge(s.acct, s.o, Long::sum);
    }

    private void apply(Session s, Map<String, Object> r) {
        String t = Json.str(r.get("t"), "");
        switch (t) {
            case "seg": segment(s, r); break;
            case "m":
                counters(r);
                if (!s.imp && s.valid && r.get("n") instanceof Map) records.counters(Json.map(r.get("n")));
                break;
            case "ev": event(s, r); break;
            case "env": {
                String acct = Json.str(r.get("acct"), null);
                if (acct != null) s.acct = acct;
                long ts = Json.num(r.get("ts"), 0);
                if (!s.imp && ts >= lastEnvTs) {
                    for (Map.Entry<String, Object> e : r.entrySet()) if (!"t".equals(e.getKey())) lastEnv.put(e.getKey(), e.getValue());
                    lastEnvTs = ts;
                }
                break;
            }
            case "end": s.e = Math.max(s.e, Json.num(r.get("ts"), s.e)); break;
            default:
        }
    }

    private void segment(Session s, Map<String, Object> r) {
        long start = Json.num(r.get("s"), 0), dur = Json.num(r.get("d"), 0);
        if (dur <= 0 || start <= 0) return;
        String st = Json.str(r.get("st"), State.MENU);
        String ctx = Json.str(r.get("c"), null);
        String dim = Json.str(r.get("dm"), null);
        String screen = Json.str(r.get("sc"), null);
        boolean focus = Json.num(r.get("f"), 1) != 0;
        boolean afk = Json.num(r.get("k"), 0) != 0;
        char g = State.group(st);
        boolean inWorld = State.inWorld(st);
        boolean active = !afk && focus && inWorld && g != 'l';
        int cls = afk ? 2 : !focus ? 5 : g == 'p' ? 0 : g == 'u' ? 1 : g == 'l' ? 4 : 3;
        if (State.IG_LOADING.equals(st) || State.LAG.equals(st)) cls = 4;

        s.e = Math.max(s.e, start + dur);
        s.o += dur;
        if (active) s.a += dur;
        if (afk) s.k += dur;
        if (ctx != null) {
            long[] c = s.ctx.computeIfAbsent(ctx, x -> new long[3]);
            c[2] += dur;
            if (!ctx.equals(s.visCtx) || start - s.visEnd > VISIT_GAP) {
                closeVisit(s);
                s.visCtx = ctx;
                s.visStart = start;
                s.visActive = 0;
            }
            s.visEnd = Math.max(s.visEnd, start + dur);
            if (active) s.visActive += dur;
        }
        Map<String, Object> vm = versions.computeIfAbsent(s.ver, x -> new LinkedHashMap<>());
        addNum(vm, "o", dur);
        if (active) addNum(vm, "a", dur);
        minNum(vm, "first", start);
        maxNum(vm, "last", start + dur);
        addMap(vm, "ldr", s.ldr, dur);
        if (s.imp) vm.put("imp", 1);
        if (ctx != null) {
            Map<String, Object> cm = ctxMeta.computeIfAbsent(ctx, x -> new LinkedHashMap<>());
            minNum(cm, "first", start);
            maxNum(cm, "last", start + dur);
            addMap(cm, "mcT", s.ver, dur);
            if (s.acct != null) addMap(cm, "acT", s.acct, dur);
            if (active && dim != null) addMap(cm, "dim", dim, dur);
            if (s.imp) cm.put("imp", 1);
        }

        long t = start, end = start + dur;
        while (t < end) {
            Day d = dayAt(t);
            long minuteStart = curStart + ((t - curStart) / 60_000L) * 60_000L;
            long next = Math.min(end, Math.min(minuteStart + 60_000L, curEnd));
            long piece = next - t;
            int minute = (int) Math.min(1439, Math.max(0, (minuteStart - curStart) / 60_000L));
            int hour = Math.min(23, minute / 60);
            d.o += piece;
            d.ho[hour] += piece;
            if (s.imp) d.imp = true;
            if (afk) {
                d.k += piece;
                addNum(d.sk, st, piece);
            } else {
                addNum(d.st, st, piece);
                if (!focus) d.bg += piece;
                if (screen != null) addNum(d.sc, screen, piece);
            }
            if (active) {
                if (!s.imp && s.valid) records.active(curDate, piece, s.ver, ctx);
                d.a += piece;
                d.h[hour] += piece;
                if (dim != null) addNum(d.dim, dim, piece);
            }
            addNum(d.ver, s.ver, piece);
            addNum(d.ldr, s.ldr, piece);
            if (ctx != null) {
                long[] c = d.ctx.computeIfAbsent(ctx, x -> new long[3]);
                if (active) c[0] += piece;
                if (afk) c[1] += piece;
                c[2] += piece;
                if (active) {
                    s.ctx.get(ctx)[0] += piece;
                    ctxHours.computeIfAbsent(ctx, x -> new long[24])[hour] += piece;
                    ctxWeekdays.computeIfAbsent(ctx, x -> new long[7])[curWd] += piece;
                    ctxDays.computeIfAbsent(ctx, x -> new java.util.HashSet<>()).add(curDate);
                }
            }
            d.cls[cls] += piece;
            int idx = minute * NCLS + cls;
            d.tl[idx] = (byte) Math.min(255, (d.tl[idx] & 0xFF) + Math.max(1, piece / 1000));
            if (d.first < 0 || minute < d.first) d.first = minute;
            if (minute > d.last) d.last = minute;
            t = next;
        }
        if (ctx != null) {
            Map<String, Object> cm = ctxMeta.get(ctx);
            addNum(cm, "o", dur);
            if (active) addNum(cm, "a", dur);
            if (afk) addNum(cm, "k", dur);
        }
    }

    private void counters(Map<String, Object> r) {
        long s = Json.num(r.get("s"), 0);
        Map<String, Object> n = Json.map(r.get("n"));
        if (s <= 0 || n == null) return;
        Day d = dayAt(s);
        merge(d.n, n);
        String ctx = Json.str(r.get("c"), null);
        if (ctx != null) {
            Map<String, Object> cm = ctxMeta.computeIfAbsent(ctx, x -> new LinkedHashMap<>());
            Map<String, Object> cn = Json.map(cm.computeIfAbsent("n", x -> new LinkedHashMap<String, Object>()));
            merge(cn, n);
        }
    }

    /** Counter merge rule shared with the server: *Max -> max, *Min -> min, everything else sums. */
    @SuppressWarnings("unchecked")
    public static void merge(Map<String, Object> dst, Map<String, Object> src) {
        for (Map.Entry<String, Object> e : src.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (v instanceof Map) {
                Map<String, Object> sub = (Map<String, Object>) dst.computeIfAbsent(k, x -> new LinkedHashMap<String, Object>());
                merge(sub, (Map<String, Object>) v);
            } else if (v instanceof Number) {
                double x = ((Number) v).doubleValue();
                Object cur = dst.get(k);
                if (!(cur instanceof Number)) dst.put(k, x);
                else if (k.endsWith("Max")) dst.put(k, Math.max(((Number) cur).doubleValue(), x));
                else if (k.endsWith("Min")) dst.put(k, Math.min(((Number) cur).doubleValue(), x));
                else dst.put(k, ((Number) cur).doubleValue() + x);
            }
        }
    }

    private void event(Session s, Map<String, Object> r) {
        long ts = Json.num(r.get("ts"), 0);
        if (ts <= 0) return;
        s.e = Math.max(s.e, ts);
        String k = Json.str(r.get("k"), "");
        Day d = dayAt(ts);
        String ctx = Json.str(r.get("c"), null);
        switch (k) {
            case "boot": {
                long dur = Json.num(r.get("d"), 0);
                if (dur > 0 && dur < 30 * 60_000L) {
                    addNum(d.ev, "boot", 1);
                    addNum(d.ev, "bootMs", dur);
                    s.boot = dur;
                }
                break;
            }
            case "join": {
                addNum(d.ev, "join", 1);
                long load = Json.num(r.get("load"), -1);
                if (load > 0 && load < 30 * 60_000L) {
                    addNum(d.ev, "load", 1);
                    addNum(d.ev, "loadMs", load);
                }
                if (ctx != null) {
                    Map<String, Object> cm = ctxMeta.computeIfAbsent(ctx, x -> new LinkedHashMap<>());
                    addNum(cm, "j", 1);
                    if (load > 0 && load < 30 * 60_000L) {
                        addNum(cm, "ld", 1);
                        addNum(cm, "ldMs", load);
                    }
                    if (r.get("n") != null) cm.put("name", r.get("n"));
                    if (r.get("b") != null) cm.put("brand", r.get("b"));
                    if (r.get("v") != null) cm.put("ver", r.get("v"));
                    if (r.get("lan") != null) cm.put("lan", 1);
                    minNum(cm, "first", ts);
                    maxNum(cm, "last", ts);
                    addMap(cm, "acct", s.acct == null ? "?" : s.acct, 1);
                    addMap(cm, "mc", s.ver, 1);
                }
                break;
            }
            case "death": {
                addNum(d.ev, "death", 1);
                if (!s.imp && s.valid) records.death();
                if (ctx != null) addNum(ctxMeta.computeIfAbsent(ctx, x -> new LinkedHashMap<>()), "d", 1);
                Map<String, Object> m = new LinkedHashMap<>(r);
                m.remove("t");
                m.remove("k");
                deaths.add(m);
                break;
            }
            case "disc": {
                addNum(d.ev, "disc", 1);
                if (ctx != null) {
                    Map<String, Object> cm = ctxMeta.computeIfAbsent(ctx, x -> new LinkedHashMap<>());
                    addNum(cm, "dc", 1);
                    if (ts >= Json.num(cm.get("dcTs"), 0)) {
                        cm.put("dcTs", ts);
                        cm.put("dcR", Json.str(r.get("r"), "?"));
                    }
                }
                Map<String, Object> m = new LinkedHashMap<>(r);
                m.remove("t");
                m.remove("k");
                disconnects.add(m);
                break;
            }
            case "afk": {
                long dur = Json.num(r.get("d"), 0);
                addNum(d.ev, "afk", 1);
                maxNum(d.ev, "afkMax", dur);
                break;
            }
            case "dim": addNum(d.ev, "dim", 1); break;
            case "milestone": {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ts", ts);
                m.put("h", r.get("h"));
                milestones.add(m);
                break;
            }
            default:
        }
    }

    // ============================================================================================ output
    private Result finish() {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> dayOut = new LinkedHashMap<>();
        long total = 0;
        for (Map.Entry<String, Day> e : days.entrySet()) {
            Day d = e.getValue();
            if (d.o <= 0 && d.n.isEmpty()) continue;
            dayOut.put(e.getKey(), dayJson(d));
            total += d.a;
        }
        String today = LocalDate.now(zone).toString();
        Day td = days.get(today);
        int streak = 0;
        LocalDate cursor = LocalDate.now(zone);
        if (!played(days.get(cursor.toString()))) cursor = cursor.minusDays(1);
        while (played(days.get(cursor.toString()))) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        sessions.sort(Comparator.comparingLong(m -> -Json.num(m.get("s"), 0)));
        deaths.sort(Comparator.comparingLong(m -> -Json.num(m.get("ts"), 0)));
        disconnects.sort(Comparator.comparingLong(m -> -Json.num(m.get("ts"), 0)));

        out.put("schema", 1);
        out.put("generated", System.currentTimeMillis());
        out.put("tz", zone.getId());
        out.put("env", lastEnv);
        out.put("mods", lastMods);
        out.put("accounts", accounts);
        out.put("days", dayOut);
        out.put("sessions", sessions);
        for (Map.Entry<String, Map<String, Object>> e : ctxMeta.entrySet()) {
            String k = e.getKey();
            Map<String, Object> cm = e.getValue();
            if (ctxHours.containsKey(k)) cm.put("h", ctxHours.get(k));
            if (ctxWeekdays.containsKey(k)) cm.put("wd", ctxWeekdays.get(k));
            if (ctxDays.containsKey(k)) cm.put("days", ctxDays.get(k).size());
            List<long[]> vl = ctxVisits.get(k);
            if (vl != null) {
                vl.sort((x, y) -> Long.compare(y[0], x[0]));
                List<Object> recent = new ArrayList<>();
                for (long[] v : vl.subList(0, Math.min(30, vl.size()))) recent.add(v);
                cm.put("vl", recent);
            }
        }
        out.put("ctx", ctxMeta);
        out.put("versions", versions);
        out.put("deaths", deaths.size() > 1000 ? deaths.subList(0, 1000) : deaths);
        out.put("disc", disconnects.size() > 300 ? disconnects.subList(0, 300) : disconnects);
        out.put("milestones", milestones);
        Map<String, Object> me = records.stats(LocalDate.now(zone));
        out.put("me", me);
        Result r = new Result();
        r.rollup = out;
        r.me = me;
        r.todayActive = td == null ? 0 : td.a;
        r.totalActive = total;
        r.streak = streak;
        return r;
    }

    private static boolean played(Day d) { return d != null && (d.a >= 60_000 || (d.imp && d.o >= 60_000)); }

    private Map<String, Object> dayJson(Day d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("o", d.o);
        m.put("a", d.a);
        m.put("k", d.k);
        m.put("bg", d.bg);
        m.put("st", d.st);
        if (!d.sk.isEmpty()) m.put("sk", d.sk);
        m.put("h", d.h);
        m.put("ho", d.ho);
        Map<String, Object> cls = new LinkedHashMap<>();
        for (int i = 0; i < NCLS; i++) if (d.cls[i] > 0) cls.put(String.valueOf(CLS.charAt(i)), d.cls[i]);
        m.put("cls", cls);
        Map<String, Object> ctx = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> e : d.ctx.entrySet()) ctx.put(e.getKey(), e.getValue());
        m.put("ctx", ctx);
        m.put("ver", d.ver);
        m.put("ldr", d.ldr);
        if (!d.dim.isEmpty()) m.put("dim", d.dim);
        if (!d.sc.isEmpty()) m.put("sc", d.sc);
        if (!d.n.isEmpty()) m.put("n", round(d.n));
        if (!d.ev.isEmpty()) m.put("ev", d.ev);
        m.put("first", d.first);
        m.put("last", d.last);
        m.put("sess", d.sess);
        if (d.imp) m.put("imp", 1);
        m.put("tl", timeline(d.tl));
        d.tl = null;
        return m;
    }

    /** Dominant activity class per minute, run-length encoded: [[startMinute, endMinute, "p"], ...]. */
    private static List<Object> timeline(byte[] tl) {
        List<Object> out = new ArrayList<>();
        int runStart = -1;
        char runCls = 0;
        for (int minute = 0; minute <= 1440; minute++) {
            char c = 0;
            if (minute < 1440) {
                int best = 0;
                for (int k = 0; k < NCLS; k++) {
                    int v = tl[minute * NCLS + k] & 0xFF;
                    if (v > best) { best = v; c = CLS.charAt(k); }
                }
                if (best < 5) c = 0;
            }
            if (c != runCls) {
                if (runCls != 0) {
                    List<Object> seg = new ArrayList<>();
                    seg.add(runStart);
                    seg.add(minute);
                    seg.add(String.valueOf(runCls));
                    out.add(seg);
                }
                runStart = minute;
                runCls = c;
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> round(Map<String, Object> n) {
        for (Map.Entry<String, Object> e : n.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Double) e.setValue(Math.round((Double) v * 10.0) / 10.0);
            else if (v instanceof Map) round((Map<String, Object>) v);
        }
        return n;
    }

    // ============================================================================================ map helpers
    static void addNum(Map<String, Object> m, String k, long v) {
        m.merge(k, v, (a, b) -> ((Number) a).longValue() + ((Number) b).longValue());
    }

    static void maxNum(Map<String, Object> m, String k, long v) {
        Object o = m.get(k);
        if (!(o instanceof Number) || ((Number) o).longValue() < v) m.put(k, v);
    }

    static void minNum(Map<String, Object> m, String k, long v) {
        Object o = m.get(k);
        if (!(o instanceof Number) || ((Number) o).longValue() > v) m.put(k, v);
    }

    @SuppressWarnings("unchecked")
    static void addMap(Map<String, Object> m, String group, String k, long v) {
        Map<String, Object> sub = (Map<String, Object>) m.computeIfAbsent(group, x -> new LinkedHashMap<String, Object>());
        addNum(sub, k, v);
    }
}
