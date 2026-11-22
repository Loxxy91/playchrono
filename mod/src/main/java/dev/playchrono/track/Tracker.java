package dev.playchrono.track;

import dev.playchrono.probe.Snapshot;
import dev.playchrono.probe.State;
import dev.playchrono.store.EventLog;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Turns the stream of snapshots into the session journal. Single-threaded: only the sampler thread calls it.
 *
 * <p>Time accounting is sample-and-hold: the interval between two snapshots belongs to the state seen in the
 * first one. AFK is decided retroactively: time since the last input stays "pending" until either an input arrives
 * (-> active) or the AFK threshold is crossed (-> the whole idle stretch, threshold included, becomes AFK). This is
 * what WakaTime does with its heartbeat timeout, applied per state.
 */
public final class Tracker {
    public interface Listener {
        void chat(String key, Object... args);
        void openDashboard();
    }

    /** Pending (not yet AFK-decided) run of identical state. */
    private static final class Run {
        final long start; long ms; final String st, ctx, dim, screen; final boolean focus;
        Run(long start, long ms, String st, String ctx, String dim, String screen, boolean focus) {
            this.start = start; this.ms = ms; this.st = st; this.ctx = ctx; this.dim = dim; this.screen = screen; this.focus = focus;
        }
        boolean same(String st, String ctx, String dim, String screen, boolean focus) {
            return this.st.equals(st) && Objects.equals(this.ctx, ctx) && Objects.equals(this.dim, dim)
                    && Objects.equals(this.screen, screen) && this.focus == focus;
        }
    }

    private final EventLog log;
    private final Listener listener;
    private final long afkMs, jvmStart;
    private final boolean trackKeys, trackCommands;
    private final int breakMinutes;
    private final boolean chatWelcome, chatMilestones, chatAfkReturn;

    private Snapshot prev;
    private final ArrayDeque<Run> pending = new ArrayDeque<>();
    private long lastInputWall;
    private boolean afk;
    private long afkStart;

    private Run open;          // committed segment being extended
    private boolean openAfk;

    private Counters cnt = new Counters();
    private long cntStart;
    private String cntCtx, cntDim;
    private final ArrayDeque<double[]> speedWindow = new ArrayDeque<>();
    private double speedSum;

    private boolean bootLogged;
    private String curCtx, curDim, lastChatText;
    private long ctxSince, loadingSince = -1;
    private long settleUntil; // the server sends health/XP right after a join or dimension change: not real gains
    private String lastEnvSig;
    private long lastEnvWrite;

    // live totals for chat feedback
    private long sessionActive, todayActiveBefore, totalActiveBefore, continuousPlay, lastBreakNotice;
    private int streakBefore;
    private String today = LocalDate.now().toString();
    private boolean welcomed;
    private long nextMilestone = Long.MAX_VALUE;
    private final java.util.concurrent.atomic.AtomicInteger screenshots = new java.util.concurrent.atomic.AtomicInteger();

    public Tracker(EventLog log, Listener listener, long jvmStart, long afkMs, boolean trackKeys, boolean trackCommands,
                   boolean chatWelcome, boolean chatMilestones, boolean chatAfkReturn, int breakMinutes) {
        this.log = log;
        this.listener = listener;
        this.jvmStart = jvmStart;
        this.afkMs = afkMs;
        this.trackKeys = trackKeys;
        this.trackCommands = trackCommands;
        this.chatWelcome = chatWelcome;
        this.chatMilestones = chatMilestones;
        this.chatAfkReturn = chatAfkReturn;
        this.breakMinutes = breakMinutes;
    }

    /** Totals from earlier sessions (computed in the background at startup) used for chat summaries. */
    public void setBaseline(long todayActive, long totalActive, int streak, boolean welcomedToday) {
        this.todayActiveBefore = todayActive;
        this.totalActiveBefore = totalActive;
        this.streakBefore = streak;
        this.welcomed = welcomed || welcomedToday;
        long step = 10L * 3600_000L;
        // Only ever moves forward: the baseline excludes the live session, so after crossing a milestone mid-session
        // it is still below it, and recomputing from it (every refresh) used to announce the same milestone again.
        long next = (totalActive / step + 1) * step;
        this.nextMilestone = nextMilestone == Long.MAX_VALUE ? next : Math.max(nextMilestone, next); // MAX = no baseline yet
    }

    public boolean welcomedToday() { return welcomed; }

    public void screenshotTaken() { screenshots.incrementAndGet(); }

    // ======================================================================================== main entry
    public void process(Snapshot s, long suspendedMs) {
        if (prev == null) {
            first(s);
            return;
        }
        long dt = (s.nanos - prev.nanos) / 1_000_000L - suspendedMs;
        if (dt < 0) dt = 0;
        String held = prev.state;
        if (dt > 1000) { // main thread blocked between the two probes
            held = prev.inWorld ? State.LAG : (State.BOOT.equals(prev.state) ? State.BOOT : State.LOADING);
            Map<String, Object> e = ev("hitch");
            e.put("d", dt);
            e.put("st", prev.state);
            log.write(e);
            cnt.add("hitch", 1);
            cnt.add("hitchMs", dt);
        }
        account(s.wall - dt, dt, held, prev);
        weighted(prev, dt);          // belongs to the bag of the previous context
        rotateCounters(s, false);    // then switch bag if context/dimension changed or a minute elapsed
        events(s);
        counters(s);
        afkLogic(s);
        feedback(s);
        prev = s;
    }

    private void first(Snapshot s) {
        prev = s;
        lastInputWall = s.wall;
        cntStart = s.wall;
        long boot = s.wall - jvmStart;
        if (boot > 0 && boot < 30 * 60_000L) commit(new Run(jvmStart, boot, State.BOOT, null, null, null, true), false);
    }

    // ======================================================================================== time accounting
    private void account(long start, long ms, String st, Snapshot p) {
        if (ms <= 0) return;
        String ctx = State.inWorld(st) ? p.ctx : null;
        String dim = State.inWorld(st) ? p.dim : null;
        String screen = State.PLAY.equals(st) ? null : p.screen;
        boolean focus = p.focused && !p.iconified;
        if (State.group(st) == 'l') {           // waiting on the game is never AFK and never idle time
            flushPending(afk);
            commit(new Run(start, ms, st, ctx, dim, screen, focus), false);
            lastInputWall = Math.max(lastInputWall, start + ms);
            return;
        }
        if (afk) {
            commit(new Run(start, ms, st, ctx, dim, screen, focus), true);
            return;
        }
        Run last = pending.peekLast();
        if (last != null && last.same(st, ctx, dim, screen, focus)) last.ms += ms;
        else pending.add(new Run(start, ms, st, ctx, dim, screen, focus));
    }

    private void afkLogic(Snapshot s) {
        if (s.anyInput) {
            if (afk) {
                long d = s.wall - afkStart;
                Map<String, Object> e = ev("afk");
                e.put("s", afkStart);
                e.put("d", d);
                e.put("st", s.state);
                log.write(e);
                afk = false;
                if (chatAfkReturn && d >= 10 * 60_000L && s.inWorld) listener.chat("afkReturn", fmt(d));
            }
            flushPending(false);
            lastInputWall = s.wall;
        } else if (!afk && s.wall - lastInputWall >= afkMs) {
            afk = true;
            afkStart = lastInputWall;
            flushPending(true);
            continuousPlay = 0;
        }
    }

    private void flushPending(boolean asAfk) {
        while (!pending.isEmpty()) commit(pending.poll(), asAfk);
    }

    private void commit(Run r, boolean isAfk) {
        if (!isAfk && r.focus && State.inWorld(r.st) && State.group(r.st) != 'l') {
            sessionActive += r.ms;
            continuousPlay += r.ms;
        }
        if (open != null && openAfk == isAfk && open.same(r.st, r.ctx, r.dim, r.screen, r.focus)
                && Math.abs(open.start + open.ms - r.start) < 3000 && open.ms < 60_000) {
            open.ms += r.ms;
            return;
        }
        writeOpen();
        open = new Run(r.start, r.ms, r.st, r.ctx, r.dim, r.screen, r.focus);
        openAfk = isAfk;
    }

    private void writeOpen() {
        if (open == null || open.ms <= 0) return;
        Map<String, Object> m = EventLog.rec("seg");
        m.put("s", open.start);
        m.put("d", open.ms);
        m.put("st", open.st);
        if (open.ctx != null) m.put("c", open.ctx);
        if (open.dim != null) m.put("dm", open.dim);
        if (open.screen != null) m.put("sc", open.screen);
        if (!open.focus) m.put("f", 0);
        if (openAfk) m.put("k", 1);
        log.write(m);
        open = null;
    }

    // ======================================================================================== counters
    private void rotateCounters(Snapshot s, boolean force) {
        boolean ctxChanged = !Objects.equals(cntCtx, s.inWorld ? s.ctx : null) || !Objects.equals(cntDim, s.inWorld ? s.dim : null);
        if (!force && !ctxChanged && s.wall - cntStart < 60_000) return;
        if (!cnt.isEmpty()) {
            Map<String, Object> m = EventLog.rec("m");
            m.put("s", cntStart);
            m.put("e", s.wall);
            if (cntCtx != null) m.put("c", cntCtx);
            if (cntDim != null) m.put("dm", cntDim);
            m.put("n", cnt.map());
            log.write(m);
        }
        cnt = new Counters();
        cntStart = s.wall;
        cntCtx = s.inWorld ? s.ctx : null;
        cntDim = s.inWorld ? s.dim : null;
    }

    /** Time-weighted values over the interval that just ended (state of the previous snapshot). */
    private void weighted(Snapshot p, long dt) {
        if (dt <= 0 || dt > 5000) return;
        if (p.inWorld) {
            if (p.biome != null) cnt.add("bio", p.biome, dt);
            if (p.gameMode != null) cnt.add("gm", p.gameMode, dt);
            cnt.add("wx", p.thunder ? "thunder" : p.rain ? "rain" : "clear", dt);
            if (p.dayTime >= 0) {
                long t = p.dayTime % 24000L;
                cnt.add("tod", t < 12000 ? "day" : t < 13000 ? "dusk" : t < 23000 ? "night" : "dawn", dt);
            }
            if (p.underwater) cnt.add("underwaterMs", dt);
            if (p.onFire) cnt.add("fireMs", dt);
            if (p.sleeping) cnt.add("sleepMs", dt);
            if (p.fps > 0 && State.PLAY.equals(p.state)) {
                cnt.add("fpsSum", p.fps * (double) dt);
                cnt.add("fpsMs", dt);
                cnt.min("fpsMin", p.fps);
                cnt.max("fpsMax", p.fps);
                cnt.add("fpsB", p.fps < 30 ? "lt30" : p.fps < 60 ? "30_60" : p.fps < 120 ? "60_120" : p.fps < 240 ? "120_240" : "240p", dt);
            }
            if (p.ping >= 0) {
                cnt.add("pingSum", p.ping * (double) dt);
                cnt.add("pingMs", dt);
                cnt.max("pingMax", p.ping);
            }
            if (p.players >= 0) cnt.max("playersMax", p.players);
            if (p.food >= 0 && p.food <= 6) cnt.add("hungryMs", dt);
        }
    }

    private void counters(Snapshot s) {
        // input
        if (s.keyPresses > 0) {
            cnt.add("keys", s.keyPresses);
            if (trackKeys) for (int i = 0; i < s.keyCount; i++) cnt.add("kc", Integer.toString(s.keys[i]), 1);
        }
        cnt.add("clickL", s.clicksL);
        cnt.add("clickR", s.clicksR);
        cnt.add("clickM", s.clicksM + s.clicksX);
        cnt.add("mousePx", Math.round(s.mousePx));
        if (s.spacePressed && State.PLAY.equals(s.state)) cnt.add("jump", 1);
        int shots = screenshots.getAndSet(0);
        if (shots > 0) cnt.add("shot", shots);
        Runtime rt = Runtime.getRuntime();
        cnt.max("ramMax", (rt.totalMemory() - rt.freeMemory()) / 1048576L);

        if (!s.inWorld || !prev.inWorld || !Objects.equals(s.ctx, prev.ctx)) {
            speedWindow.clear();
            speedSum = 0;
            return;
        }
        // movement
        if (s.hasPos && prev.hasPos && Objects.equals(s.dim, prev.dim) && !State.DEAD.equals(s.state) && !State.DEAD.equals(prev.state)) {
            double dx = s.x - prev.x, dy = s.y - prev.y, dz = s.z - prev.z;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > 100) {
                cnt.add("tp", 1);
            } else if (d > 0.001) {
                String mode = s.ride ? "ride" : s.elytra ? "elytra" : s.flying ? "fly" : s.swim ? "swim"
                        : s.inLava ? "lava" : s.inWater ? "water" : s.sprint ? "sprint" : s.sneak ? "sneak"
                        : s.onGround ? "walk" : dy < 0 ? "fall" : "air";
                cnt.add("mv", mode, d);
                if (s.ride && s.vehicle != null) cnt.add("ridev", s.vehicle, d);
                if (s.dim != null) cnt.add("mvDim", s.dim, d);
                cnt.add("climb", dy > 0 && !s.flying && !s.elytra && !s.ride ? dy : 0);
                speedWindow.add(new double[]{s.wall, d});
                speedSum += d;
                while (!speedWindow.isEmpty() && s.wall - speedWindow.peekFirst()[0] > 1000) speedSum -= speedWindow.pollFirst()[1];
                if (speedWindow.size() > 3) {
                    double span = s.wall - speedWindow.peekFirst()[0];
                    if (span >= 500) cnt.max("speedMax", speedSum / span * 1000.0);
                }
            }
            cnt.max("yMax", s.y);
            cnt.min("yMin", s.y);
            cnt.max("farMax", Math.sqrt(s.x * s.x + s.z * s.z));
        }
        // vitals (skipped while the server is still syncing the player after a join / respawn / dimension change)
        if (s.wall < settleUntil) return;
        if (s.health >= 0 && prev.health >= 0 && !State.DEAD.equals(prev.state) && !State.DEAD.equals(s.state)) {
            double dh = s.health - prev.health;
            if (dh < 0) cnt.add("dmg", -dh);
            else cnt.add("heal", dh);
        }
        if (s.xpLevel > prev.xpLevel && prev.xpLevel >= 0) cnt.add("xpLevels", s.xpLevel - prev.xpLevel);
        if (s.slot >= 0 && prev.slot >= 0 && s.slot != prev.slot) cnt.add("hotbar", 1);
    }

    // ======================================================================================== events
    private Map<String, Object> ev(String kind) {
        Map<String, Object> m = EventLog.rec("ev");
        m.put("ts", System.currentTimeMillis());
        m.put("k", kind);
        return m;
    }

    private void events(Snapshot s) {
        String st = s.state, pst = prev.state;
        if (!bootLogged && !State.BOOT.equals(st)) {
            bootLogged = true;
            Map<String, Object> e = ev("boot");
            e.put("d", s.wall - jvmStart);
            log.write(e);
        }
        // context (server / world) changes
        String ctx = s.inWorld ? s.ctx : null;
        if (!Objects.equals(ctx, curCtx)) {
            if (curCtx != null) {
                Map<String, Object> e = ev("leave");
                e.put("c", curCtx);
                e.put("d", s.wall - ctxSince);
                log.write(e);
            }
            if (ctx != null) {
                Map<String, Object> e = ev("join");
                e.put("c", ctx);
                put(e, "n", s.ctxName);
                put(e, "b", s.serverBrand);
                put(e, "v", s.serverVersion);
                if (s.lanOpen) e.put("lan", 1);
                if (loadingSince > 0) e.put("load", s.wall - loadingSince);
                log.write(e);
                cnt.add("joins", 1);
                if (chatWelcome && !welcomed) {
                    welcomed = true;
                    listener.chat("welcome", fmt(todayActiveBefore + sessionActive), streakBefore + (todayActiveBefore > 0 ? 0 : 1));
                }
            }
            curCtx = ctx;
            ctxSince = s.wall;
            settleUntil = s.wall + 10_000;
            curDim = s.inWorld ? s.dim : null;
            loadingSince = -1;
        } else if (s.inWorld && !Objects.equals(s.dim, curDim)) {
            Map<String, Object> e = ev("dim");
            put(e, "from", curDim);
            put(e, "to", s.dim);
            put(e, "c", ctx);
            log.write(e);
            curDim = s.dim;
            settleUntil = s.wall + 10_000;
        }
        if (State.LOADING.equals(st) && loadingSince < 0 && !State.LOADING.equals(pst)) loadingSince = s.wall;
        if (State.group(st) == 'm') loadingSince = -1;

        if (State.DEAD.equals(st) && !State.DEAD.equals(pst)) {
            Map<String, Object> e = ev("death");
            put(e, "c", ctx);
            put(e, "dm", s.dim);
            if (prev.hasPos) {
                e.put("x", Math.round(prev.x));
                e.put("y", Math.round(prev.y));
                e.put("z", Math.round(prev.z));
            }
            log.write(e);
            cnt.add("deaths", 1);
        }
        if (s.sleeping && !prev.sleeping && s.inWorld) cnt.add("sleeps", 1);
        // chat lines received + death messages seen (only the cause key, e.g. "attack.lava", is kept)
        cnt.add("chatSeen", s.chatIn);
        if (s.deathMsgs != null) {
            for (String d : s.deathMsgs) {
                int bar = d.indexOf('|');
                String cause = d.substring(bar + 1);
                cnt.add("dm", 1);
                cnt.add("dmk", cause, 1);
                if (bar == 2) { // "me|"
                    cnt.add("dmMe", 1);
                    cnt.add("dmkMe", cause, 1);
                }
            }
        }
        if (s.disconnectReason != null) {
            Map<String, Object> e = ev("disc");
            put(e, "c", prev.ctx);
            String r = s.disconnectReason.replaceAll("§.", "").trim();
            e.put("r", r.length() > 140 ? r.substring(0, 140) : r);
            log.write(e);
        }
        // chat messages / commands sent (content is never stored, only the command name)
        if (State.CHAT.equals(st) && s.chatText != null) lastChatText = s.chatText;
        if (State.CHAT.equals(pst) && !State.CHAT.equals(st)) {
            boolean sent = s.enterDown || s.enterPressed || prev.enterPressed;
            String txt = lastChatText == null ? "" : lastChatText.trim();
            if (sent && !txt.isEmpty()) {
                if (txt.startsWith("/")) {
                    cnt.add("cmds", 1);
                    if (trackCommands) {
                        String name = txt.substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
                        if (name.length() > 32) name = name.substring(0, 32);
                        if (!name.isEmpty()) cnt.add("cmd", name, 1);
                    }
                } else {
                    cnt.add("chat", 1);
                    cnt.add("chatChars", txt.length());
                }
            }
            lastChatText = null;
        }
        envUpdate(s);
    }

    private static void put(Map<String, Object> m, String k, Object v) { if (v != null) m.put(k, v); }

    private void envUpdate(Snapshot s) {
        if (s.wall - lastEnvWrite < 10_000) return;
        String sig = s.username + "|" + s.lang + "|" + s.gpu + "|" + s.renderDistance + "|" + s.fov + "|" + s.resourcePacks
                + "|" + s.winW + "x" + s.winH + "|" + s.fullscreen + "|" + s.launchedVersion;
        if (sig.equals(lastEnvSig) || s.username == null) return;
        lastEnvSig = sig;
        lastEnvWrite = s.wall;
        Map<String, Object> e = EventLog.rec("env");
        e.put("ts", s.wall);
        put(e, "acct", s.username);
        put(e, "uuid", s.uuid);
        put(e, "lang", s.lang);
        put(e, "gpu", s.gpu);
        put(e, "launched", s.launchedVersion);
        if (s.renderDistance > 0) e.put("rd", s.renderDistance);
        if (s.fov > 0) e.put("fov", s.fov);
        put(e, "packs", s.resourcePacks);
        if (s.winW > 0) e.put("win", s.winW + "x" + s.winH);
        e.put("fs", s.fullscreen ? 1 : 0);
        log.write(e);
    }

    // ======================================================================================== chat feedback
    private void feedback(Snapshot s) {
        if (s.hotkeyPressed && !State.CHAT.equals(s.state) && !State.SIGN.equals(s.state) && !State.BOOK.equals(s.state)) {
            listener.openDashboard();
        }
        String d = LocalDate.now().toString();
        if (!d.equals(today)) { // midnight while playing
            today = d;
            todayActiveBefore = -sessionActive; // so that today's total restarts from zero
            welcomed = false;
        }
        long total = totalActiveBefore + sessionActive;
        if (chatMilestones && total >= nextMilestone && s.inWorld) {
            long hours = nextMilestone / 3600_000L;
            nextMilestone += 10L * 3600_000L;
            Map<String, Object> e = ev("milestone");
            e.put("h", hours);
            log.write(e);
            listener.chat("milestone", hours);
        }
        if (breakMinutes > 0 && s.inWorld && continuousPlay >= breakMinutes * 60_000L
                && continuousPlay - lastBreakNotice >= breakMinutes * 60_000L) {
            lastBreakNotice = continuousPlay;
            listener.chat("break", fmt(continuousPlay));
        }
        if (State.group(s.state) == 'm' && continuousPlay > 0 && s.wall - lastInputWall > 5 * 60_000L) {
            continuousPlay = 0;
            lastBreakNotice = 0;
        }
    }

    public static String fmt(long ms) {
        long m = ms / 60_000L;
        return m >= 60 ? (m / 60) + "h" + String.format("%02d", m % 60) : m + " min";
    }

    // ======================================================================================== lifecycle
    /** Periodic durability point (every 10 s): write what is settled, keep the open segment going. */
    public void checkpoint() {
        if (open != null && open.ms >= 60_000) writeOpen();
        log.flush();
    }

    /** Writes everything already decided (open segment + current counters) so the dashboard is up to date. */
    public void flushNow() {
        writeOpen();
        if (prev != null) {
            Snapshot cut = new Snapshot();
            cut.wall = System.currentTimeMillis();
            cut.inWorld = prev.inWorld;
            cut.ctx = prev.ctx;
            cut.dim = prev.dim;
            rotateCounters(cut, true);
        }
        log.flush();
    }

    public void close(long nowWall) {
        if (prev != null) {
            long tail = Math.min(5000, Math.max(0, nowWall - prev.wall));
            account(prev.wall, tail, prev.state, prev);
            if (curCtx != null) {
                Map<String, Object> e = ev("leave");
                e.put("c", curCtx);
                e.put("d", nowWall - ctxSince);
                log.write(e);
            }
            flushPending(afk || nowWall - lastInputWall >= afkMs);
            Snapshot end = new Snapshot();
            end.wall = nowWall;
            rotateCounters(end, true);
        }
        writeOpen();
        Map<String, Object> m = EventLog.rec("end");
        m.put("ts", nowWall);
        m.put("active", sessionActive);
        log.write(m);
        log.flush();
    }

    public long sessionActive() { return sessionActive; }
}
