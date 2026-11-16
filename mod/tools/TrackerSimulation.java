import dev.playchrono.probe.Snapshot;
import dev.playchrono.probe.State;
import dev.playchrono.store.Aggregator;
import dev.playchrono.store.EventLog;
import dev.playchrono.track.Tracker;
import dev.playchrono.util.Json;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deterministic end-to-end test of the time accounting, without Minecraft:
 * scripted snapshots -> Tracker -> session journal -> Aggregator -> assertions.
 *
 *   cd mod && java Build.java && java -cp build/classes tools/TrackerSimulation.java
 */
public class TrackerSimulation {
    static long t0, wall, nanos;
    static Tracker tr;
    static double x;
    static final List<String> chat = new ArrayList<>();
    static int fails;

    public static void main(String[] a) throws Exception {
        Path dir = Files.createTempDirectory("playchrono-sim");
        t0 = LocalDate.now().atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        wall = t0;
        EventLog log = new EventLog(dir.resolve("sessions"), t0 - 30_000, dev.playchrono.store.Signer.of(dir));
        Map<String, Object> start = EventLog.rec("start");
        start.put("ts", t0 - 30_000);
        start.put("mc", "1.21.4");
        start.put("ldr", "fabric");
        log.write(start);
        tr = new Tracker(log, new Tracker.Listener() {
            public void chat(String key, Object... args) { chat.add(key); }
            public void openDashboard() { chat.add("dashboard"); }
        }, t0 - 30_000, 300_000, true, true, true, true, true, 0);
        tr.setBaseline(0, 9L * 3600_000 + 59 * 60_000, 3, false); // 1 min away from the 10 h milestone

        run(60_000, State.TITLE, false, true);          // menu, active input
        run(10_000, State.LOADING, false, false);       // joining
        run(120_000, State.PLAY, true, true);           // playing, sprinting
        run(400_000, State.PLAY, true, false);          // hands off the keyboard: AFK after 300 s
        run(60_000, State.PLAY, true, true);            // back
        Snapshot c = snap(State.CHAT, true, true);
        c.chatText = "/home base";
        step(c);
        Snapshot sent = snap(State.PLAY, true, true);
        sent.enterPressed = true;
        step(sent);
        wall += 3000; nanos += 3_000_000_000L;           // game frozen 3 s
        step(snap(State.PLAY, true, true));
        run(5_000, State.DEAD, true, true);
        run(2_000, State.PLAY, true, true);
        tr.close(wall);
        log.close();

        Aggregator.Result r = Aggregator.run(dir);
        Map<String, Object> day = Json.map(Json.map(r.rollup.get("days")).get(LocalDate.now().toString()));
        Map<String, Object> st = Json.map(day.get("st")), sk = Json.map(day.get("sk")), n = Json.map(day.get("n"));
        System.out.println("day = " + Json.write(day).substring(0, 400) + "...");
        near("BOOT", st.get("BOOT"), 30_000, 600);
        near("TITLE", st.get("TITLE"), 60_000, 1000);
        near("LOADING", st.get("LOADING"), 10_000, 1000);
        near("AFK (retroactive, 400 s idle)", day.get("k"), 400_000, 2000);
        near("AFK while PLAY", sk.get("PLAY"), 400_000, 2000);
        // 120 s + 60 s of active play, + 0.5 s after the chat, + 2 s at the end
        near("PLAY not AFK", st.get("PLAY"), 182_500, 1000);
        near("LAG", st.get("LAG"), 3_000, 600);
        // active = in a world, focused, not AFK, not loading: PLAY + CHAT 0.5 s + DEAD 5 s (LAG excluded)
        near("active", day.get("a"), 182_500 + 500 + 5_000, 1000);
        check("command /home counted", Json.num(Json.map(n.get("cmd")).get("home"), 0) == 1);
        check("death counted", Json.num(Json.map(day.get("ev")).get("death"), 0) == 1);
        check("freeze counted", Json.num(n.get("hitch"), 0) == 1);
        // 1166 PLAY probes * 0.25 block, minus the steps that follow a non-world or dead snapshot
        double sprint = Json.dbl(Json.map(n.get("mv")).get("sprint"), 0);
        check("sprint distance = " + sprint + " blocks (expected ~291)", Math.abs(sprint - 291) < 2);
        check("server context recorded", Json.map(day.get("ctx")).containsKey("server:test.example"));
        check("welcome sent once", chat.stream().filter("welcome"::equals).count() == 1);
        check("milestone 10 h reached", chat.contains("milestone"));
        check("timeline has an AFK block", String.valueOf(day.get("tl")).contains("k"));
        Map<String, Object> srv = Json.map(Json.map(r.rollup.get("ctx")).get("server:test.example"));
        check("one session on the server", Json.num(srv.get("v"), 0) == 1);
        near("longest session on the server", srv.get("vMax"), 120_000 + 400_000 + 60_000 + 6_000 + 5_000 + 2_000 + 500, 2000);
        check("first connection recorded", Json.num(srv.get("first"), 0) >= t0 && Json.num(srv.get("first"), 0) < t0 + 120_000);
        check("1 day played on the server", Json.num(srv.get("days"), 0) == 1);
        check("loading time recorded for the server", Json.num(srv.get("ld"), 0) == 1 && Json.num(srv.get("ldMs"), 0) >= 9_000);
        long hSum = 0;
        for (long v : (long[]) srv.get("h")) hSum += v;
        check("hours of the day add up to the active time on the server", Math.abs(hSum - Json.num(day.get("a"), 0)) < 1000);
        check("recent sessions list", srv.get("vl") instanceof java.util.List && ((java.util.List<?>) srv.get("vl")).size() == 1);
        // ---- anti-cheat: signed journal, hand edits, compaction
        long active = Json.num(r.me.get("active"), 0);
        check("signed session counts for the leaderboard (" + active / 1000 + " s)", active > 150_000);
        Path f = log.file();
        java.util.List<String> lines = Files.readAllLines(f);
        Path copy = dir.resolve("sessions").resolve("cheat.jsonl");
        java.util.List<String> forged = new java.util.ArrayList<>();
        for (String l : lines) forged.add(l.contains("\"t\":\"seg\"") && l.contains("\"st\":\"PLAY\"") ? l.replace("\"d\":", "\"d\":9") : l);
        Files.write(copy, forged);
        Files.delete(f);
        Aggregator.Result r2 = Aggregator.run(dir);
        check("hand-edited session excluded from the leaderboard", Json.num(r2.me.get("active"), -1) == 0);
        check("...but still shown in the player's own dashboard", Json.num(Json.map(Json.map(r2.rollup.get("days")).get(LocalDate.now().toString())).get("o"), 0) > 0);
        dev.playchrono.store.Compactor.compactAll(dir, null);
        check("compaction does not launder the edit", Json.num(Aggregator.run(dir).me.get("active"), -1) == 0);
        Files.delete(copy);
        Files.write(f, lines);
        dev.playchrono.store.Compactor.compactAll(dir, null);
        Aggregator.Result r3 = Aggregator.run(dir);
        check("intact session still counts after compaction", Json.num(r3.me.get("active"), 0) == active);
        Map<String, Object> srv3 = Json.map(Json.map(r3.rollup.get("ctx")).get("server:test.example"));
        check("server details survive compaction", Json.num(srv3.get("v"), 0) == 1 && Json.num(srv3.get("vMax"), 0) == Json.num(srv.get("vMax"), -1)
                && Json.num(srv3.get("ld"), 0) == 1 && Json.num(srv3.get("first"), 0) == Json.num(srv.get("first"), -1));
        // ---- the dashboard refreshes the baseline every 5 minutes WITHOUT the live session: the milestone crossed
        // during the session must not be announced again at each refresh (it used to repeat every 5 minutes)
        Path dir2 = Files.createTempDirectory("playchrono-sim");
        EventLog log2 = new EventLog(dir2.resolve("sessions"), wall, dev.playchrono.store.Signer.of(dir2));
        chat.clear();
        tr = new Tracker(log2, new Tracker.Listener() {
            public void chat(String key, Object... args) { chat.add(key); }
            public void openDashboard() { }
        }, wall, 300_000, true, true, false, true, true, 0);
        long base = 9L * 3600_000 + 59 * 60_000;
        tr.setBaseline(0, base, 3, true);
        run(120_000, State.PLAY, true, true);                 // crosses 10 h
        for (int i = 0; i < 6; i++) {                          // six refreshes later, still the same session
            tr.setBaseline(0, base, 3, true);
            run(60_000, State.PLAY, true, true);
        }
        check("milestone announced once, not repeated after refreshes (" + chat.stream().filter("milestone"::equals).count() + ")",
                chat.stream().filter("milestone"::equals).count() == 1);
        tr.setBaseline(0, 19L * 3600_000 + 59 * 60_000, 3, true);  // next game: 19 h 59 played
        run(120_000, State.PLAY, true, true);
        check("next milestone (20 h) still announced", chat.stream().filter("milestone"::equals).count() == 2);
        tr.close(wall);
        log2.close();
        // ---- seasonal events, every year
        int[][] easter = {{2024, 3, 31}, {2025, 4, 20}, {2026, 4, 5}, {2027, 3, 28}, {2028, 4, 16}, {2035, 3, 25}, {2038, 4, 25}};
        boolean eOk = true;
        for (int[] e : easter) eOk &= dev.playchrono.util.Season.easter(e[0]).equals(LocalDate.of(e[0], e[1], e[2]));
        check("Easter dates 2024-2038", eOk);
        String[][] sc = {{"2026-12-09", null}, {"2026-12-10", "xmas"}, {"2026-12-29", "xmas"}, {"2026-12-30", "newyear"}, {"2027-01-02", "newyear"},
                {"2027-01-03", null}, {"2026-10-09", null}, {"2026-10-10", "halloween"}, {"2026-11-02", "halloween"}, {"2026-11-03", null},
                {"2026-04-05", "easter"}, {"2026-04-06", null}};
        boolean sOk = true;
        for (String[] sd : sc) sOk &= java.util.Objects.equals(dev.playchrono.util.Season.of(LocalDate.parse(sd[0])), sd[1]);
        check("season boundaries (Christmas, New Year, Halloween, Easter)", sOk);
        check("New Year 30 Dec 2026 and 1 Jan 2027 are the same event, wishing 2027",
                dev.playchrono.util.Season.eventId("newyear", LocalDate.parse("2026-12-30")).equals(dev.playchrono.util.Season.eventId("newyear", LocalDate.parse("2027-01-01")))
                        && dev.playchrono.util.Season.greetingYear(LocalDate.parse("2026-12-31")) == 2027 && dev.playchrono.util.Season.greetingYear(LocalDate.parse("2027-01-01")) == 2027);
        boolean lOk = true;
        for (String l : dev.playchrono.ui.Lang.SUPPORTED) for (String k : new String[]{"xmas", "newyear", "easter", "halloween"}) {
            String m = dev.playchrono.ui.Lang.get(l, "season." + k, "2027");
            lOk &= !m.contains("{") && !m.contains("''") && (!k.equals("newyear") || m.contains("2027"));
        }
        check("seasonal chat messages in 10 languages (year inserted, apostrophes ok): " + dev.playchrono.ui.Lang.get("fr", "season.easter"), lOk);
        System.out.println(fails == 0 ? "ALL CHECKS PASSED" : fails + " CHECK(S) FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }

    static Snapshot snap(String state, boolean inWorld, boolean input) {
        Snapshot s = new Snapshot();
        s.nanos = nanos;
        s.wall = wall;
        s.mcReady = true;
        s.state = state;
        s.inWorld = inWorld;
        s.anyInput = input;
        s.focused = true;
        s.fps = 90;
        if (inWorld) {
            s.ctx = "server:test.example";
            s.ctxName = "Test";
            s.dim = "minecraft:overworld";
            s.biome = "minecraft:plains";
            s.hasPos = true;
            if (State.PLAY.equals(state)) x += 0.25; // 0.5 block/s at 2 Hz
            s.x = x;
            s.y = 64;
            s.sprint = true;
            s.onGround = true;
            s.health = 20;
            s.maxHealth = 20;
        }
        return s;
    }

    static void step(Snapshot s) {
        tr.process(s, 0);
        wall += 500;
        nanos += 500_000_000L;
    }

    static void run(long ms, String state, boolean inWorld, boolean input) {
        for (long t = 0; t < ms; t += 500) step(snap(state, inWorld, input));
    }

    static void near(String what, Object v, long expected, long tol) {
        long got = Json.num(v, -1);
        check(what + " = " + got / 1000.0 + " s (expected " + expected / 1000.0 + " s)", Math.abs(got - expected) <= tol);
    }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + what);
        if (!ok) fails++;
    }
}
