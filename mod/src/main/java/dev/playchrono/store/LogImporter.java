package dev.playchrono.store;

import dev.playchrono.probe.State;
import dev.playchrono.util.Json;
import dev.playchrono.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Rebuilds approximate past sessions from the game's own logs folder (logs/2026-09-07-1.log.gz ...), so the
 * dashboard has history from day one. Imported data is flagged "imp": no AFK / menu detail, world time = play time.
 * Sessions the mod tracked itself are dropped by the aggregator (overlap check), so re-importing is always safe.
 */
public final class LogImporter {
    private static final Pattern NAME = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})-\\d+\\.log(\\.gz)?");
    private static final Pattern T_SHORT = Pattern.compile("^\\[(\\d{2}):(\\d{2}):(\\d{2})]");
    private static final Pattern T_FORGE = Pattern.compile("^\\[(\\d{2})([A-Za-z]{3})(\\d{4}) (\\d{2}):(\\d{2}):(\\d{2})");
    private static final Pattern FABRIC = Pattern.compile("Loading Minecraft (\\S+) with (Fabric|Quilt) Loader (\\S+)");
    private static final Pattern FML_MC = Pattern.compile("--fml\\.mcVersion, ([^,\\]\\s]+)");
    private static final Pattern FML_FORGE = Pattern.compile("--fml\\.forgeVersion, ([^,\\]\\s]+)");
    private static final Pattern FML_NEO = Pattern.compile("--fml\\.neoForgeVersion, ([^,\\]\\s]+)");
    private static final Pattern OLD_FORGE = Pattern.compile("Forge Mod Loader version (\\S+) for Minecraft (\\S+)");
    private static final Pattern INTEGRATED = Pattern.compile("Starting integrated minecraft server version (\\S+)");
    private static final Pattern CONNECT = Pattern.compile("^Connecting to ([^,\\s]+), ?(\\d+)");
    private static final Pattern LEVEL = Pattern.compile("^Preparing level \"(.*)\"");
    private static final Pattern SAVING = Pattern.compile("^Saving chunks for level '(?:ServerLevel\\[)?(.+?)]?'/");
    private static final DateTimeFormatter FORGE_DATE = DateTimeFormatter.ofPattern("ddMMMyyyy", Locale.ENGLISH);

    /** Bump when parsing improves: every game dir gets re-imported once. */
    private static final String PARSER = "3";

    private LogImporter() {}

    /** @return number of sessions written, or -1 when the logs did not change since the last import. */
    public static int importGameDir(Path gameDir, Path dataDir) {
        Path logs = gameDir.resolve("logs");
        if (!Files.isDirectory(logs)) return 0;
        List<Path> files = new ArrayList<>();
        StringBuilder sig = new StringBuilder();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(logs)) {
            for (Path p : ds) {
                String n = p.getFileName().toString();
                if (NAME.matcher(n).matches() || n.equals("latest.log")) files.add(p);
            }
        } catch (IOException e) {
            return 0;
        }
        files.sort(null);
        String[] hint = instanceHint(gameDir);
        sig.append("parser=").append(PARSER).append(';').append(String.join("|", java.util.Arrays.asList(hint).toString())).append(';');
        for (Path p : files) {
            try { sig.append(p.getFileName()).append(':').append(Files.size(p)).append(';'); } catch (IOException ignored) { /* */ }
        }
        String id = hash(gameDir.toAbsolutePath().normalize().toString()).substring(0, 12);
        String signature = hash(sig.toString());
        Path out = dataDir.resolve("imported").resolve("logs-" + id + ".jsonl");
        if (Files.exists(out)) {
            try (BufferedReader r = Files.newBufferedReader(out, StandardCharsets.UTF_8)) {
                Map<String, Object> head = Json.map(Json.tryParse(r.readLine()));
                if (head != null && signature.equals(head.get("sig"))) return -1;
            } catch (IOException ignored) { /* re-import */ }
        }
        long t0 = System.currentTimeMillis();
        int sessions = 0;
        try {
            Files.createDirectories(out.getParent());
            Path tmp = out.resolveSibling(out.getFileName() + ".tmp");
            try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                Map<String, Object> head = new LinkedHashMap<>();
                head.put("t", "import");
                head.put("dir", gameDir.toAbsolutePath().toString());
                head.put("sig", signature);
                head.put("files", files.size());
                if (hint[3] != null) head.put("inst", hint[3]);
                head.put("at", System.currentTimeMillis());
                w.write(Json.write(head));
                w.write('\n');
                for (Path p : files) {
                    try {
                        List<Map<String, Object>> recs = parse(p, hint);
                        if (recs.isEmpty()) continue;
                        for (Map<String, Object> rec : recs) { w.write(Json.write(rec)); w.write('\n'); }
                        sessions++;
                    } catch (Exception e) {
                        Log.warn("skipping unreadable log " + p.getFileName(), e);
                    }
                }
            }
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Log.warn("log import failed", e);
        }
        Log.info("imported " + sessions + " past sessions from " + logs + " in " + (System.currentTimeMillis() - t0) + " ms");
        return sessions;
    }

    // ------------------------------------------------------------------------------------------ one log file
    private static final class Ev {
        final int day; final int sec; final String kind; String a, b;
        Ev(int day, int sec, String kind, String a, String b) { this.day = day; this.sec = sec; this.kind = kind; this.a = a; this.b = b; }
    }

    static List<Map<String, Object>> parse(Path p, String[] hint) throws IOException {
        String name = p.getFileName().toString();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate endDate;
        Matcher nm = NAME.matcher(name);
        if (nm.matches()) endDate = LocalDate.parse(nm.group(1));
        else endDate = Files.getLastModifiedTime(p).toInstant().atZone(zone).toLocalDate();

        List<Ev> evs = new ArrayList<>();
        String mc = null, ldr = null, ldrv = null, acct = null;
        int day = 0, prevSec = -1, firstSec = -1, lastSec = -1, firstDay = 0;
        LocalDate forgeFirst = null;
        boolean any = false;
        Ev pendingWorld = null; // singleplayer world whose name is only logged later ("Saving chunks for level ...")
        InputStream in = Files.newInputStream(p);
        if (name.endsWith(".gz")) in = new GZIPInputStream(in, 65536);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 65536)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.length() < 12 || line.charAt(0) != '[') continue;
                int sec;
                Matcher m = T_SHORT.matcher(line);
                if (m.find()) {
                    sec = Integer.parseInt(m.group(1)) * 3600 + Integer.parseInt(m.group(2)) * 60 + Integer.parseInt(m.group(3));
                } else {
                    Matcher f = T_FORGE.matcher(line);
                    if (!f.find()) continue;
                    sec = Integer.parseInt(f.group(4)) * 3600 + Integer.parseInt(f.group(5)) * 60 + Integer.parseInt(f.group(6));
                    try {
                        LocalDate d = LocalDate.parse(f.group(1) + cap(f.group(2)) + f.group(3), FORGE_DATE);
                        if (forgeFirst == null) forgeFirst = d;
                    } catch (Exception ignored) { /* */ }
                }
                if (prevSec >= 0 && sec < prevSec - 3600) day++; // midnight
                prevSec = sec;
                if (!any) { firstSec = sec; firstDay = day; any = true; }
                lastSec = sec;
                int colon = line.indexOf("]: ");
                if (colon < 0) continue;
                String msg = line.substring(colon + 3);
                boolean server = line.contains("[Server thread/");
                if (msg.startsWith("Setting user: ")) acct = msg.substring(14).trim();
                else if (msg.startsWith("Connecting to ")) {
                    Matcher c = CONNECT.matcher(msg);
                    if (c.find()) {
                        String host = c.group(1).toLowerCase(Locale.ROOT);
                        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
                        if (!"25565".equals(c.group(2))) host += ":" + c.group(2);
                        evs.add(new Ev(day, sec, "join", "server:" + host, host));
                    }
                } else if (server && msg.startsWith("Preparing level ")) {
                    Matcher l = LEVEL.matcher(msg);
                    if (l.find()) {
                        if (pendingWorld != null) { pendingWorld.a = "world:" + l.group(1); pendingWorld.b = l.group(1); pendingWorld = null; }
                        else evs.add(new Ev(day, sec, "join", "world:" + l.group(1), l.group(1)));
                    }
                } else if (server && msg.startsWith("Starting integrated minecraft server version ")) {
                    Matcher v = INTEGRATED.matcher(msg);
                    if (v.find() && mc == null) mc = v.group(1);
                    pendingWorld = new Ev(day, sec, "join", "world:?", "?");
                    evs.add(pendingWorld);
                } else if (server && pendingWorld != null && msg.startsWith("Saving chunks for level ")) {
                    Matcher l = SAVING.matcher(msg);
                    if (l.find()) { pendingWorld.a = "world:" + l.group(1); pendingWorld.b = l.group(1); pendingWorld = null; }
                } else if (server && (msg.startsWith("Stopping server") || msg.startsWith("Stopping singleplayer server"))) {
                    evs.add(new Ev(day, sec, "leave", null, null));
                } else if (msg.startsWith("Client disconnected with reason: ")) {
                    evs.add(new Ev(day, sec, "disc", msg.substring(33).trim(), null));
                } else if (msg.equals("Stopping!")) {
                    evs.add(new Ev(day, sec, "stop", null, null));
                } else if (msg.contains("[CHAT]")) {
                    if (msg.contains("Saved screenshot as")) evs.add(new Ev(day, sec, "shot", null, null));
                    else evs.add(new Ev(day, sec, "chat", null, null));
                } else if (msg.startsWith("Saved screenshot as")) {
                    evs.add(new Ev(day, sec, "shot", null, null));
                } else {
                    Matcher x;
                    if ((x = FABRIC.matcher(msg)).find()) { mc = x.group(1); ldr = x.group(2).toLowerCase(Locale.ROOT); ldrv = x.group(3); }
                    else if ((x = FML_NEO.matcher(msg)).find()) { ldr = "neoforge"; ldrv = x.group(1); Matcher v = FML_MC.matcher(msg); if (v.find()) mc = v.group(1); }
                    else if ((x = FML_FORGE.matcher(msg)).find()) { ldr = "forge"; ldrv = x.group(1); Matcher v = FML_MC.matcher(msg); if (v.find()) mc = v.group(1); }
                    else if ((x = OLD_FORGE.matcher(msg)).find()) { ldr = "forge"; ldrv = x.group(1); mc = x.group(2); }
                }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        if (!any) return out;
        LocalDate base = forgeFirst != null ? forgeFirst : endDate.minusDays(day);
        final LocalDate fb = base;
        java.util.function.BiFunction<Integer, Integer, Long> at = (d, s) ->
                LocalDateTime.of(fb.plusDays(d), java.time.LocalTime.ofSecondOfDay(s)).atZone(zone).toInstant().toEpochMilli();
        long start = at.apply(firstDay, firstSec), end = at.apply(day, lastSec);
        if (end - start < 5_000) return out;

        Map<String, Object> st = rec("start");
        st.put("ts", start);
        st.put("imp", 1);
        st.put("src", name);
        st.put("mc", mc != null ? mc : hint[0] != null ? hint[0] : "?");
        st.put("ldr", ldr != null ? ldr : hint[1] != null ? hint[1] : "vanilla");
        if (ldrv != null || hint[2] != null) st.put("ldrv", ldrv != null ? ldrv : hint[2]);
        if (hint[3] != null) st.put("inst", hint[3]);
        if (acct != null) st.put("acct", acct);
        out.add(st);

        String ctx = null;
        long since = start;
        int chat = 0, shots = 0;
        for (Ev e : evs) {
            long ts = at.apply(e.day, e.sec);
            switch (e.kind) {
                case "join":
                    if (e.a.equals(ctx)) break;
                    closeCtx(out, ctx, since, ts, chat, shots);
                    chat = 0; shots = 0;
                    ctx = e.a;
                    since = ts;
                    Map<String, Object> j = ev("join", ts);
                    j.put("c", ctx);
                    j.put("n", e.b);
                    out.add(j);
                    break;
                case "leave": case "stop": case "disc":
                    if ("disc".equals(e.kind)) {
                        Map<String, Object> d = ev("disc", ts);
                        if (ctx != null) d.put("c", ctx);
                        d.put("r", e.a.length() > 140 ? e.a.substring(0, 140) : e.a);
                        out.add(d);
                    }
                    if (ctx != null) {
                        closeCtx(out, ctx, since, ts, chat, shots);
                        chat = 0; shots = 0;
                        ctx = null;
                        since = ts;
                    }
                    break;
                case "chat": chat++; break;
                case "shot": shots++; break;
                default:
            }
        }
        closeCtx(out, ctx, since, end, chat, shots);
        Map<String, Object> en = rec("end");
        en.put("ts", end);
        out.add(en);
        return out;
    }

    private static void closeCtx(List<Map<String, Object>> out, String ctx, long from, long to, int chat, int shots) {
        if (to <= from) return;
        Map<String, Object> seg = rec("seg");
        seg.put("s", from);
        seg.put("d", to - from);
        seg.put("st", ctx == null ? State.MENU : State.PLAY);
        if (ctx != null) seg.put("c", ctx);
        out.add(seg);
        if (chat > 0 || shots > 0) {
            Map<String, Object> m = rec("m");
            m.put("s", from);
            m.put("e", to);
            if (ctx != null) m.put("c", ctx);
            Map<String, Object> n = new LinkedHashMap<>();
            if (chat > 0) n.put("chatSeen", chat);
            if (shots > 0) n.put("shot", shots);
            m.put("n", n);
            out.add(m);
        }
        if (ctx != null) {
            Map<String, Object> l = ev("leave", to);
            l.put("c", ctx);
            l.put("d", to - from);
            out.add(l);
        }
    }

    private static Map<String, Object> rec(String t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", t);
        return m;
    }

    private static Map<String, Object> ev(String kind, long ts) {
        Map<String, Object> m = rec("ev");
        m.put("ts", ts);
        m.put("k", kind);
        return m;
    }

    private static String cap(String s) { return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT); }

    static String hash(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    /**
     * Instance metadata written by third-party launchers, used when a log does not say which version it was:
     * {minecraft version, loader, loader version, instance name}. Prism/MultiMC: ../mmc-pack.json + instance.cfg;
     * Modrinth App: profile.json; CurseForge: minecraftinstance.json.
     */
    static String[] instanceHint(Path gameDir) {
        String[] h = new String[4];
        try {
            Path inst = gameDir.toAbsolutePath().normalize().getParent();
            Path pack = inst == null ? null : inst.resolve("mmc-pack.json");
            if (pack != null && Files.isRegularFile(pack)) {
                h[3] = inst.getFileName().toString();
                Path cfg = inst.resolve("instance.cfg");
                if (Files.isRegularFile(cfg)) {
                    for (String l : Files.readAllLines(cfg, StandardCharsets.UTF_8)) if (l.startsWith("name=")) h[3] = l.substring(5).trim();
                }
                Map<String, Object> root = Json.map(Json.tryParse(new String(Files.readAllBytes(pack), StandardCharsets.UTF_8)));
                List<Object> comps = root == null ? null : Json.list(root.get("components"));
                for (Object o : comps == null ? new ArrayList<>() : comps) {
                    Map<String, Object> c = Json.map(o);
                    if (c == null) continue;
                    String uid = Json.str(c.get("uid"), ""), v = Json.str(c.get("version"), null);
                    if (uid.equals("net.minecraft")) h[0] = v;
                    else if (uid.equals("net.fabricmc.fabric-loader")) { h[1] = "fabric"; h[2] = v; }
                    else if (uid.equals("org.quiltmc.quilt-loader")) { h[1] = "quilt"; h[2] = v; }
                    else if (uid.equals("net.minecraftforge")) { h[1] = "forge"; h[2] = v; }
                    else if (uid.equals("net.neoforged")) { h[1] = "neoforge"; h[2] = v; }
                }
                if (h[1] == null && h[0] != null) h[1] = "vanilla";
                return h;
            }
            Path profile = gameDir.resolve("profile.json"); // Modrinth App
            if (Files.isRegularFile(profile)) {
                Map<String, Object> m = Json.map(Json.tryParse(new String(Files.readAllBytes(profile), StandardCharsets.UTF_8)));
                Map<String, Object> meta = m == null ? null : Json.map(m.get("metadata"));
                Map<String, Object> src = meta != null ? meta : m;
                if (src != null) {
                    h[0] = Json.str(src.get("game_version"), null);
                    h[1] = Json.str(src.get("loader"), null);
                    h[3] = Json.str(src.get("name"), gameDir.getFileName().toString());
                }
                return h;
            }
            Path cf = gameDir.resolve("minecraftinstance.json"); // CurseForge
            if (Files.isRegularFile(cf)) {
                Map<String, Object> m = Json.map(Json.tryParse(new String(Files.readAllBytes(cf), StandardCharsets.UTF_8)));
                if (m != null) {
                    h[0] = Json.str(m.get("gameVersion"), null);
                    Map<String, Object> ml = Json.map(m.get("baseModLoader"));
                    String n = ml == null ? null : Json.str(ml.get("name"), null); // e.g. "fabric-0.16.9-1.21.4"
                    if (n != null) h[1] = n.split("-")[0].toLowerCase(Locale.ROOT);
                    h[3] = Json.str(m.get("name"), gameDir.getFileName().toString());
                }
            }
        } catch (Exception ignored) { /* best effort */ }
        return h;
    }

    /** Every game directory with a logs/ folder that the usual launchers keep on this machine. */
    public static List<Path> discoverGameDirs() {
        List<Path> out = new ArrayList<>();
        String home = System.getProperty("user.home"), appdata = System.getenv("APPDATA");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<Path> roots = new ArrayList<>();
        if (os.contains("win") && appdata != null) {
            roots.add(java.nio.file.Paths.get(appdata, "PrismLauncher", "instances"));
            roots.add(java.nio.file.Paths.get(appdata, "ModrinthApp", "profiles"));
            roots.add(java.nio.file.Paths.get(appdata, "com.modrinth.theseus", "profiles"));
            roots.add(java.nio.file.Paths.get(appdata, "ATLauncher", "instances"));
            roots.add(java.nio.file.Paths.get(appdata, "gdlauncher_next", "instances"));
        } else if (os.contains("mac")) {
            Path sup = java.nio.file.Paths.get(home, "Library", "Application Support");
            roots.add(sup.resolve("PrismLauncher").resolve("instances"));
            roots.add(sup.resolve("ModrinthApp").resolve("profiles"));
        } else {
            roots.add(java.nio.file.Paths.get(home, ".local", "share", "PrismLauncher", "instances"));
            roots.add(java.nio.file.Paths.get(home, ".var", "app", "org.prismlauncher.PrismLauncher", "data", "PrismLauncher", "instances"));
            roots.add(java.nio.file.Paths.get(home, ".local", "share", "ModrinthApp", "profiles"));
        }
        roots.add(java.nio.file.Paths.get(home, "curseforge", "minecraft", "Instances"));
        Path vanilla = defaultGameDir();
        if (Files.isDirectory(vanilla.resolve("logs"))) out.add(vanilla);
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
                for (Path inst : ds) {
                    for (Path cand : new Path[]{inst.resolve(".minecraft"), inst.resolve("minecraft"), inst}) {
                        if (Files.isDirectory(cand.resolve("logs"))) { out.add(cand); break; }
                    }
                }
            } catch (IOException ignored) { /* unreadable launcher folder */ }
        }
        return out;
    }

    /** Default .minecraft folder of the vanilla launcher on this OS. */
    public static Path defaultGameDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            return java.nio.file.Paths.get(appdata != null ? appdata : home, ".minecraft");
        }
        if (os.contains("mac")) return java.nio.file.Paths.get(home, "Library", "Application Support", "minecraft");
        return java.nio.file.Paths.get(home, ".minecraft");
    }
}
