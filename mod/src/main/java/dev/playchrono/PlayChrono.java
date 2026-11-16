package dev.playchrono;

import dev.playchrono.probe.GameProbe;
import dev.playchrono.probe.Snapshot;
import dev.playchrono.reflect.R;
import dev.playchrono.store.AppState;
import dev.playchrono.store.Aggregator;
import dev.playchrono.store.Compactor;
import dev.playchrono.store.EventLog;
import dev.playchrono.store.Signer;
import dev.playchrono.util.Secure;
import dev.playchrono.store.LogImporter;
import dev.playchrono.track.Tracker;
import dev.playchrono.ui.ChatOut;
import dev.playchrono.ui.Lang;
import dev.playchrono.util.Config;
import dev.playchrono.util.Log;
import dev.playchrono.web.Dashboard;
import dev.playchrono.web.Leaderboard;
import dev.playchrono.web.MiniHttp;
import dev.playchrono.web.Skin;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wiring. The mod never hooks into Minecraft's code: a daemon "sampler" thread asks Minecraft's own main-thread
 * executor (Minecraft implements java.util.concurrent.Executor in every version) to run a read-only probe ~40 times
 * per second, then does all the bookkeeping off the main thread. Zero mixins, zero loader API => one jar for
 * Fabric, Quilt, Forge and NeoForge on every version from 1.20 to 26.x.
 */
public final class PlayChrono implements Tracker.Listener {
    public static final String VERSION = readVersion();
    private static boolean started;

    private final Path dataDir;
    private final Config cfg;
    private final AppState state;
    private final Env env;
    private final EventLog log;
    private final Tracker tracker;
    private final GameProbe probe;
    private final Dashboard dashboard;
    private final Leaderboard leaderboard;
    private long lastSubmit;
    private MiniHttp http;
    private String lang = "en";

    private final ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor(daemon("PlayChrono-Sampler"));
    private final ExecutorService background = Executors.newSingleThreadExecutor(daemon("PlayChrono-Background"));
    private final ConcurrentLinkedQueue<Snapshot> done = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> chatQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean pending = new AtomicBoolean();
    private Object mc;
    private long lastTick, suspendedMs, lastCheckpoint, lastRefresh, lastOpen;
    private boolean gameDirKnown, probeErrorLogged;

    // ============================================================================================ boot
    /** Called by every loader entry point. Idempotent, never throws into the loader. */
    public static synchronized void boot(String loader) {
        if (started) return;
        started = true;
        try {
            R.init(PlayChrono.class.getClassLoader());
            Class<?> mcClass = R.cls("Minecraft");
            if (mcClass == null) {
                Log.info("no Minecraft client classes found (dedicated server?): PlayChrono stays idle");
                return;
            }
            new PlayChrono(loader, mcClass).start();
        } catch (Throwable t) {
            Log.warn("PlayChrono disabled after an unexpected error - the game is not affected", t);
        }
    }

    private PlayChrono(String loader, Class<?> mcClass) throws Exception {
        dataDir = Config.dataDir();
        cfg = new Config(dataDir);
        state = new AppState(dataDir);
        env = Env.detect(loader, mcClass);
        env.readMods();
        Secure.ownerOnlyTree(dataDir); // other OS accounts can neither read nor edit the stats
        log = new EventLog(dataDir.resolve("sessions"), env.jvmStart, Signer.of(dataDir));
        Map<String, Object> start = EventLog.rec("start");
        start.put("ts", env.jvmStart);
        start.put("sid", log.sessionId());
        start.put("mc", env.mcVersion);
        start.put("mcType", env.mcVersionType);
        start.put("ldr", env.loader);
        start.put("ldrv", env.loaderVersion);
        start.put("mod", VERSION);
        start.put("ns", R.namespace().name());
        start.put("env", env.toMap());
        start.put("mods", env.modsList());
        log.write(start);
        log.flush();
        tracker = new Tracker(log, this, env.jvmStart, cfg.integer("afk.seconds") * 1000L, cfg.bool("privacy.keys"),
                cfg.bool("privacy.commands"), cfg.bool("chat.welcome"), cfg.bool("chat.milestones"),
                cfg.bool("chat.afkReturn"), cfg.integer("chat.breakReminderMinutes"));
        probe = new GameProbe(hotkey(cfg.str("dashboard.hotkey")));
        dashboard = new Dashboard(dataDir, VERSION);
        leaderboard = new Leaderboard(cfg, state, VERSION);
        dashboard.extra("leaderboard", () -> leaderboard.info(null, null));
        dashboard.extra("installedAt", () -> state.num("created"));
        dashboard.extra("prefs", cfg::prefs);
        dashboard.extra("face", () -> cfg.bool("dashboard.skin") ? Skin.dataUri(dataDir) : null);
        Log.info("PlayChrono " + VERSION + " tracking Minecraft " + env.mcVersion + " on " + env.loader + " " + env.loaderVersion
                + " -> " + log.file());
    }

    private void start() {
        sampler.scheduleAtFixedRate(this::tick, 100, 25, TimeUnit.MILLISECONDS);
        background.submit(this::startup);
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "PlayChrono-Shutdown"));
    }

    private void startup() {
        try {
            dashboard.installAssets();
            if (cfg.bool("import.logs")) {
                java.util.Set<Path> dirs = new java.util.LinkedHashSet<>();
                dirs.add(env.gameDir.toAbsolutePath().normalize());
                for (Path d : LogImporter.discoverGameDirs()) dirs.add(d.toAbsolutePath().normalize());
                for (Path d : dirs) LogImporter.importGameDir(d, dataDir);
            }
            Compactor.compactAll(dataDir, log.file()); // finished sessions only: keeps aggregation fast forever
            if (cfg.bool("dashboard.skin")) Skin.refresh(dataDir, 8000);
            int port = cfg.integer("dashboard.port");
            if (port > 0) {
                MiniHttp h = new MiniHttp(dashboard, on -> {
                    leaderboard.setEnabled(on);
                    background.submit(() -> {
                        Aggregator.Result r = dashboard.refresh();
                        if (on && leaderboard.submit(r.me, env.mcVersion, env.loader, 8000)) lastSubmit = System.currentTimeMillis();
                        if (!on) leaderboard.leave(8000); // your entry is deleted from the server
                        dashboard.refresh();
                    });
                });
                h.prefs(cfg::savePrefs);
                if (h.start(port) > 0) {
                    http = h;
                    Log.info("live dashboard: " + h.url());
                }
            }
            refreshAndBaseline();
        } catch (Throwable t) {
            Log.warn("startup tasks failed", t);
        }
    }

    private void refreshAndBaseline() {
        Aggregator.Result r = dashboard.refresh();
        if (!leaderboard.enabled() && leaderboard.leavePending()) leaderboard.leave(8000); // retry a failed removal
        if (leaderboard.enabled() && System.currentTimeMillis() - lastSubmit >= 15 * 60_000L) {
            lastSubmit = System.currentTimeMillis();
            leaderboard.submit(r.me, env.mcVersion, env.loader, 8000);
        }
        boolean welcomed = LocalDate.now().toString().equals(state.str("welcomeDay"));
        sampler.execute(() -> {
            long live = tracker.sessionActive();
            tracker.setBaseline(Math.max(0, r.todayActive - live), Math.max(0, r.totalActive - live), r.streak, welcomed);
        });
        lastRefresh = System.currentTimeMillis();
    }

    // ============================================================================================ sampler thread
    private void tick() {
        try {
            long now = System.nanoTime();
            if (lastTick != 0 && now - lastTick > 10_000_000_000L) suspendedMs += (now - lastTick) / 1_000_000L; // PC asleep
            lastTick = now;
            Snapshot s;
            while ((s = done.poll()) != null) {
                tracker.process(s, suspendedMs);
                suspendedMs = 0;
            }
            if (mc == null) mc = R.callStatic("Minecraft.getInstance");
            if (mc instanceof Executor && pending.compareAndSet(false, true)) {
                try {
                    ((Executor) mc).execute(this::probeOnMainThread);
                } catch (Throwable t) {
                    pending.set(false); // game is shutting down
                }
            }
            long wall = System.currentTimeMillis();
            if (!gameDirKnown && mc != null) {
                Object dir = R.get(mc, "Minecraft.gameDirectory");
                if (dir instanceof File) {
                    gameDirKnown = true;
                    env.gameDir = ((File) dir).toPath().toAbsolutePath();
                    startScreenshotWatcher(env.gameDir.resolve("screenshots"));
                }
            }
            if (wall - lastCheckpoint >= 10_000) {
                lastCheckpoint = wall;
                tracker.checkpoint();
            }
            if (wall - lastRefresh >= 5 * 60_000L) {
                lastRefresh = wall;
                background.submit(this::refreshAndBaseline);
            }
        } catch (Throwable t) {
            Log.warn("sampler error", t);
        }
    }

    /** Runs on Minecraft's main thread: read-only probe + pending chat lines. Must stay cheap and never throw. */
    private void probeOnMainThread() {
        try {
            Snapshot s = probe.probe();
            if (s.lang != null) lang = Lang.pick(cfg.str("language"), s.lang);
            done.add(s);
            String msg;
            while ((msg = chatQueue.poll()) != null) ChatOut.send(msg);
        } catch (Throwable t) {
            if (!probeErrorLogged) {
                probeErrorLogged = true;
                Log.warn("probe error (will keep trying silently)", t);
            }
        } finally {
            pending.set(false);
        }
    }

    // ============================================================================================ tracker callbacks
    @Override
    public void chat(String key, Object... args) {
        Object[] a = args;
        if ("welcome".equals(key)) {
            String hk = cfg.str("dashboard.hotkey");
            a = new Object[]{args[0], args[1], "none".equalsIgnoreCase(hk) ? "/" : hk};
            state.put("welcomeDay", LocalDate.now().toString());
            if (!leaderboard.enabled() && !leaderboard.url().isEmpty() && state.str("lbInvited") == null) {
                state.put("lbInvited", "1");
                chatQueue.add(Lang.get(lang, key, a)); // welcome first, then the one-time invite
                chatQueue.add(Lang.get(lang, "lbInvite", a[2]));
                return;
            }
        }
        chatQueue.add(Lang.get(lang, key, a));
    }

    @Override
    public void openDashboard() {
        long now = System.currentTimeMillis();
        if (now - lastOpen < 3000) return;
        lastOpen = now;
        chatQueue.add(Lang.get(lang, "dashboard"));
        background.submit(() -> {
            try { sampler.submit(tracker::flushNow).get(2, TimeUnit.SECONDS); } catch (Exception ignored) { /* show what we have */ }
            dashboard.installAssets();
            dashboard.refresh();
            String target = http != null ? http.url() : dashboard.index().toUri().toString();
            if (!Dashboard.open(target)) chatQueue.add(Lang.get(lang, "dashboardFail", dashboard.index().toString()));
        });
    }

    // ============================================================================================ misc
    private void startScreenshotWatcher(Path dir) {
        Thread t = new Thread(() -> {
            try {
                Files.createDirectories(dir);
                WatchService ws = FileSystems.getDefault().newWatchService();
                dir.register(ws, StandardWatchEventKinds.ENTRY_CREATE);
                while (true) {
                    WatchKey key = ws.take();
                    for (WatchEvent<?> e : key.pollEvents()) {
                        if (String.valueOf(e.context()).toLowerCase().endsWith(".png")) tracker.screenshotTaken();
                    }
                    if (!key.reset()) return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable t2) {
                Log.info("screenshot counter disabled: " + t2.getMessage());
            }
        }, "PlayChrono-Screenshots");
        t.setDaemon(true);
        t.start();
    }

    private void shutdown() {
        try {
            sampler.shutdown();
            sampler.awaitTermination(1, TimeUnit.SECONDS);
            Snapshot s;
            while ((s = done.poll()) != null) tracker.process(s, 0);
            tracker.close(System.currentTimeMillis());
            log.close();
            Future<?> f = background.submit(() -> {
                Aggregator.Result r = dashboard.refresh();
                if (leaderboard.enabled()) leaderboard.submit(r.me, env.mcVersion, env.loader, 2500);
            });
            f.get(4, TimeUnit.SECONDS);
        } catch (Throwable t) {
            // never block or crash the game exit
        } finally {
            if (http != null) http.stop();
        }
    }

    /** "F1".."F12" -> GLFW key code; anything else -> disabled. */
    static int hotkey(String s) {
        if (s == null) return -1;
        s = s.trim().toUpperCase();
        if (s.matches("F([1-9]|1[0-2])")) return 290 + Integer.parseInt(s.substring(1)) - 1;
        return -1;
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        };
    }

    private static String readVersion() {
        try (InputStream in = PlayChrono.class.getClassLoader().getResourceAsStream("playchrono/version.txt")) {
            if (in == null) return "dev";
            byte[] b = new byte[64];
            int n = in.read(b);
            return new String(b, 0, Math.max(0, n), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "dev";
        }
    }
}
