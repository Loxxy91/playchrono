package dev.playchrono;

import dev.playchrono.store.AppState;
import dev.playchrono.store.Aggregator;
import dev.playchrono.store.Compactor;
import dev.playchrono.store.LogImporter;
import dev.playchrono.util.Config;
import dev.playchrono.util.Json;
import dev.playchrono.web.Dashboard;
import dev.playchrono.web.MiniHttp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The mod jar is also a small command-line tool, so stats can be viewed without starting the game:
 * <pre>
 *   java -jar playchrono.jar                 import logs from .minecraft, build and open the dashboard
 *   java -jar playchrono.jar import DIR...   import the logs/ folder of other game directories (Prism, CurseForge...)
 *   java -jar playchrono.jar serve [port]    live dashboard on http://127.0.0.1:port
 *   java -jar playchrono.jar export FILE     write the full rollup JSON
 * </pre>
 */
public final class Cli {
    private Cli() {}

    public static void main(String[] args) throws Exception {
        String cmd = args.length > 0 ? args[0] : "dashboard";
        Path dataDir = Config.dataDir();
        Config cfg = new Config(dataDir);
        Dashboard dash = new Dashboard(dataDir, PlayChrono.VERSION);
        dash.extra("prefs", cfg::prefs);
        System.out.println("PlayChrono " + PlayChrono.VERSION + "  data: " + dataDir);
        switch (cmd) {
            case "dashboard":
            case "open": {
                List<Path> dirs = new ArrayList<>();
                for (int i = 1; i < args.length; i++) dirs.add(Paths.get(args[i]));
                if (dirs.isEmpty()) dirs.addAll(LogImporter.discoverGameDirs());
                if (cfg.bool("import.logs")) for (Path d : dirs) report(d, LogImporter.importGameDir(d, dataDir));
                Compactor.compactAll(dataDir, null);
                dash.installAssets();
                summary(dash.refresh());
                System.out.println("Dashboard: " + dash.index());
                if (!Boolean.getBoolean("playchrono.noOpen")) Dashboard.open(dash.index().toUri().toString());
                break;
            }
            case "import":
                for (int i = 1; i < args.length; i++) report(Paths.get(args[i]), LogImporter.importGameDir(Paths.get(args[i]), dataDir));
                break;
            case "serve": {
                int port = args.length > 1 ? Integer.parseInt(args[1]) : Math.max(1, cfg.integer("dashboard.port"));
                dash.installAssets();
                summary(dash.refresh());
                MiniHttp http = new MiniHttp(dash).prefs(cfg::savePrefs);
                if (http.start(port) < 0) { System.err.println("No free port near " + port); return; }
                System.out.println("Live dashboard: " + http.url() + "   (Ctrl+C to stop)");
                if (!Boolean.getBoolean("playchrono.noOpen")) Dashboard.open(http.url());
                Thread.currentThread().join();
                break;
            }
            case "export": {
                Path out = Paths.get(args.length > 1 ? args[1] : "playchrono-export.json");
                Map<String, Object> r = dash.refresh().rollup;
                r.put("mod", PlayChrono.VERSION);
                Files.write(out, Json.write(r).getBytes(StandardCharsets.UTF_8));
                System.out.println("Wrote " + out.toAbsolutePath());
                break;
            }
            default:
                System.out.println("Usage: java -jar playchrono.jar [dashboard [gameDir...] | import gameDir... | serve [port] | export file]");
        }
    }

    private static void report(Path dir, int n) {
        if (n < 0) System.out.println("  " + dir + ": logs unchanged since last import");
        else System.out.println("  " + dir + ": " + n + " past sessions imported from logs/");
    }

    private static void summary(Aggregator.Result r) {
        Map<String, Object> days = Json.map(r.rollup.get("days"));
        int sessions = Json.list(r.rollup.get("sessions")).size();
        System.out.printf("  %d days, %d sessions, %.1f h in-game (active), current streak %d day(s)%n",
                days == null ? 0 : days.size(), sessions, r.totalActive / 3_600_000.0, r.streak);
    }
}
