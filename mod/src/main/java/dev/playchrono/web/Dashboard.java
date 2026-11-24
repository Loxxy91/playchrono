package dev.playchrono.web;

import dev.playchrono.store.Aggregator;
import dev.playchrono.util.Json;
import dev.playchrono.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;

/**
 * Local dashboard: static files extracted from the jar into ~/.playchrono/dashboard plus a generated data.js, so
 * index.html works by double-click (file://) even when the game is closed. The optional MiniHttp server serves the
 * same files with live data.
 */
public final class Dashboard {
    public static final String[] ASSETS = {"index.html", "app.js", "charts.js", "i18n.js", "icons.js", "theme.js", "style.css", "favicon.svg",
            "pixel-latin.woff2", "pixel-latin-ext.woff2", "pixel-cyrillic.woff2", "pixel-digits.woff2", "OFL-PixelifySans.txt", "OFL-Silkscreen.txt"};
    private final Path dataDir, dir;
    private final String modVersion;
    private volatile String json;
    private volatile long jsonAt;
    private volatile Aggregator.Result last;

    private final Map<String, java.util.function.Supplier<Object>> extras = new java.util.LinkedHashMap<>();

    /** Extra live values merged into every rollup (leaderboard state, install date...). */
    public void extra(String key, java.util.function.Supplier<Object> value) { extras.put(key, value); }

    public Dashboard(Path dataDir, String modVersion) {
        this.dataDir = dataDir;
        this.dir = dataDir.resolve("dashboard");
        this.modVersion = modVersion;
    }

    public Path dir() { return dir; }

    public Path index() { return dir.resolve("index.html"); }

    public Aggregator.Result last() { return last; }

    /** Copies the web assets out of the jar (always: they are tiny and must match the running mod version). */
    public void installAssets() {
        try {
            Files.createDirectories(dir);
            for (String a : ASSETS) {
                try (InputStream in = Dashboard.class.getClassLoader().getResourceAsStream("playchrono/web/" + a)) {
                    if (in != null) Files.copy(in, dir.resolve(a), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.write(dir.resolve(".version"), modVersion.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.warn("cannot install dashboard files", e);
        }
    }

    /** Re-aggregates everything and rewrites data.js. Safe to call from any background thread. */
    public synchronized Aggregator.Result refresh() {
        Aggregator.Result r = Aggregator.run(dataDir);
        r.rollup.put("mod", modVersion);
        r.rollup.put("mode", "local");
        for (Map.Entry<String, java.util.function.Supplier<Object>> e : extras.entrySet()) {
            try { r.rollup.put(e.getKey(), e.getValue().get()); } catch (RuntimeException ignored) { /* optional */ }
        }
        Map<String, Object> env = Json.map(r.rollup.get("env")); // no OS account name in what the page shows or exports
        if (env != null && env.get("gameDir") != null) env.put("gameDir", dev.playchrono.util.Config.display(env.get("gameDir")));
        String j = Json.write(r.rollup);
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve("data.js.tmp");
            Files.write(tmp, ("window.PLAYCHRONO_DATA=" + j + ";\n").getBytes(StandardCharsets.UTF_8));
            Files.move(tmp, dir.resolve("data.js"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Log.warn("cannot write dashboard data", e);
        }
        json = j;
        jsonAt = System.currentTimeMillis();
        last = r;
        return r;
    }

    public String json(long maxAgeMs) {
        if (json == null || System.currentTimeMillis() - jsonAt > maxAgeMs) refresh();
        return json;
    }

    public Map<String, Object> rollup() {
        if (last == null) refresh();
        return last.rollup;
    }

    /** Opens a URL or file with the OS default handler (AWT Desktop is unavailable: the game runs headless AWT). */
    public static boolean open(String target) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] cmd = os.contains("win") ? new String[]{"rundll32", "url.dll,FileProtocolHandler", target}
                : os.contains("mac") ? new String[]{"open", target} : new String[]{"xdg-open", target};
        try {
            new ProcessBuilder(cmd).redirectErrorStream(true).start();
            return true;
        } catch (IOException e) {
            Log.warn("cannot open " + target, e);
            return false;
        }
    }
}
