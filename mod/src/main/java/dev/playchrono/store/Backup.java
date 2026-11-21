package dev.playchrono.store;

import dev.playchrono.util.Config;
import dev.playchrono.util.Json;
import dev.playchrono.util.Log;
import dev.playchrono.util.Secure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Backups, so a new computer or a reinstalled system does not mean starting from zero.
 *
 * <p>A backup is a plain .zip of everything that cannot be rebuilt: the session journals, the imported history,
 * the signing key, state.json and config.properties. It is written automatically to {@code backup.dir} (daily and
 * when the game closes), can be downloaded from the dashboard, or made with {@code java -jar playchrono.jar backup}.
 *
 * <p>Restoring MERGES, it never wipes: sessions missing here are added, a session present on both sides keeps its
 * most complete copy, the previous install's signing key is trusted for verification (so a restored history still
 * counts for the leaderboard), the install date becomes the earliest of the two, and settings still at their default
 * here take the backup's value. Restoring the same backup twice changes nothing.
 */
public final class Backup {
    private Backup() {}

    public static final String PREFIX = "playchrono-backup-";
    /** Every file a backup may contain. Anything else in a zip is ignored (no path tricks possible). */
    private static final Pattern ALLOWED = Pattern.compile(
            "sessions/\\d{4}-\\d{2}/[A-Za-z0-9._-]{1,80}\\.jsonl|imported/[A-Za-z0-9._-]{1,80}\\.jsonl|\\.key|state\\.json|config\\.properties|backup\\.json");
    private static final long MAX_ENTRY = 256L << 20;
    private static final long MIN_INTERVAL = 20L * 3600_000L;

    // ============================================================================================ create
    /** Writes a complete backup as a zip to {@code out}. Returns the number of session files included. */
    public static int write(Path dataDir, OutputStream out, String modVersion) throws IOException {
        List<String> files = new ArrayList<>();
        for (String f : new String[]{".key", "state.json", "config.properties"}) if (Files.isRegularFile(dataDir.resolve(f))) files.add(f);
        int sessions = 0;
        for (String dir : new String[]{"sessions", "imported"}) {
            Path d = dataDir.resolve(dir);
            if (!Files.isDirectory(d)) continue;
            try (Stream<Path> s = Files.walk(d)) {
                for (Path p : s.filter(Files::isRegularFile).sorted().collect(Collectors.toList())) {
                    String rel = dataDir.relativize(p).toString().replace('\\', '/');
                    if (!ALLOWED.matcher(rel).matches()) continue;
                    files.add(rel);
                    if (dir.equals("sessions")) sessions++;
                }
            }
        }
        ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("format", 1);
        manifest.put("mod", modVersion);
        manifest.put("created", System.currentTimeMillis());
        manifest.put("sessions", sessions);
        zip.putNextEntry(new ZipEntry("backup.json"));
        zip.write(Json.write(manifest).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        for (String f : files) {
            byte[] b;
            try {
                b = Files.readAllBytes(dataDir.resolve(f));
            } catch (IOException e) {
                continue; // deleted meanwhile (compaction temp files, a pruned import): skip it
            }
            zip.putNextEntry(new ZipEntry(f));
            zip.write(b);
            zip.closeEntry();
        }
        zip.finish();
        zip.flush();
        return sessions;
    }

    /** The whole backup in memory (dashboard download). */
    public static byte[] bytes(Path dataDir, String modVersion) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(1 << 16);
        write(dataDir, b, modVersion);
        return b.toByteArray();
    }

    public static String fileName() {
        return PREFIX + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".zip";
    }

    /** Writes a new backup into {@code dir} (atomically) and deletes the oldest beyond {@code keep}. */
    public static Path writeTo(Path dataDir, Path dir, int keep, String modVersion) throws IOException {
        Files.createDirectories(dir);
        Path target = dir.resolve(fileName());
        Path tmp = dir.resolve(target.getFileName() + ".tmp");
        try (OutputStream out = new java.io.BufferedOutputStream(Files.newOutputStream(tmp))) {
            write(dataDir, out, modVersion);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        prune(dir, Math.max(1, keep));
        return target;
    }

    private static void prune(Path dir, int keep) {
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> all = s.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith(PREFIX) && (n.endsWith(".zip") || n.endsWith(".zip.tmp"));
            }).sorted().collect(Collectors.toList());
            // leftovers of an interrupted backup
            for (Path p : all) if (p.toString().endsWith(".tmp") && Files.getLastModifiedTime(p).toMillis() < System.currentTimeMillis() - 3600_000L) Files.deleteIfExists(p);
            List<Path> zips = all.stream().filter(p -> p.toString().endsWith(".zip")).collect(Collectors.toList());
            for (int i = 0; i < zips.size() - keep; i++) Files.deleteIfExists(zips.get(i));
        } catch (IOException e) {
            Log.warn("cannot clean old backups in " + dir, e);
        }
    }

    /**
     * The configured backup folder, or null when backups are off. "~" and %VARIABLES% are expanded; a relative path
     * or a folder inside the data folder itself is refused (reported through {@code problem}).
     */
    public static Path dir(Config cfg, Path dataDir, String[] problem) {
        String v = cfg.str("backup.dir");
        if (v.isEmpty()) return null;
        if (v.startsWith("~")) v = System.getProperty("user.home") + v.substring(1);
        Matcher m = Pattern.compile("%([A-Za-z0-9_]+)%").matcher(v);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String env = System.getenv(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(env != null ? env : m.group()));
        }
        m.appendTail(sb);
        Path p;
        try {
            p = Paths.get(sb.toString()).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            if (problem != null) problem[0] = "invalid";
            return null;
        }
        if (!Paths.get(sb.toString()).isAbsolute()) {
            if (problem != null) problem[0] = "relative";
            return null;
        }
        if (p.startsWith(dataDir.toAbsolutePath().normalize())) {
            if (problem != null) problem[0] = "inside";
            return null;
        }
        return p;
    }

    /**
     * Automatic backup: does nothing when backup.dir is empty, or (unless {@code force}) when the last one is less
     * than 20 h old. Remembers the result in state.json for the dashboard. Never throws.
     */
    public static Path auto(Path dataDir, Config cfg, AppState state, String modVersion, boolean force) {
        String[] problem = new String[1];
        Path dir = dir(cfg, dataDir, problem);
        if (dir == null) {
            if (problem[0] == null) state.remove("backupError"); // backups switched off
            else if (!problem[0].equals(state.str("backupError"))) {
                state.put("backupError", problem[0]);
                Log.warn("backup.dir ignored (" + problem[0] + "): " + cfg.str("backup.dir"));
            }
            return null;
        }
        if (!force && System.currentTimeMillis() - state.num("backupLast") < MIN_INTERVAL) return null;
        try {
            Path parent = dir.getParent();
            if (!Files.isDirectory(dir) && (parent == null || !Files.isDirectory(parent))) throw new IOException("parent folder missing");
            long t0 = System.currentTimeMillis();
            Path f = writeTo(dataDir, dir, cfg.integer("backup.keep"), modVersion);
            state.put("backupLast", System.currentTimeMillis());
            state.put("backupFile", f.toString());
            state.remove("backupError");
            Log.info("backup written: " + f + " (" + (System.currentTimeMillis() - t0) + " ms)");
            return f;
        } catch (IOException | RuntimeException e) {
            if (!"write".equals(state.str("backupError"))) Log.warn("backup to " + dir + " failed", e);
            state.put("backupError", "write");
            return null;
        }
    }

    /** Backup status shown in the dashboard. */
    public static Map<String, Object> info(Config cfg, Path dataDir, AppState state) {
        Map<String, Object> m = new LinkedHashMap<>();
        String[] problem = new String[1];
        Path dir = dir(cfg, dataDir, problem);
        m.put("dir", dir == null ? "" : dir.toString());
        String err = problem[0] != null ? problem[0] : dir != null ? state.str("backupError") : null;
        if (err != null) m.put("error", err);
        if (state.num("backupLast") > 0) m.put("last", state.num("backupLast"));
        if (state.num("backupDownload") > 0) m.put("download", state.num("backupDownload"));
        m.put("restoreDir", dataDir.resolve("restore").toAbsolutePath().toString());
        if (state.get("restored") != null) m.put("restored", state.get("restored"));
        return m;
    }

    // ============================================================================================ restore
    /** What a restore did. */
    public static final class Report {
        public int added, replaced, imported, keys, settings;
        public boolean installDate;
        public long backupCreated;

        public boolean changed() { return added + replaced + imported + keys + settings > 0 || installDate; }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("added", added);
            m.put("replaced", replaced);
            m.put("imported", imported);
            m.put("at", System.currentTimeMillis());
            if (backupCreated > 0) m.put("from", backupCreated);
            return m;
        }

        @Override
        public String toString() {
            return added + " sessions added, " + replaced + " completed, " + imported + " imported-history files, "
                    + keys + " signing key(s) trusted, " + settings + " setting(s) restored"
                    + (installDate ? ", install date moved back" : "");
        }
    }

    /**
     * Merges a backup into {@code dataDir}. {@code source} is a backup .zip, or a copied data folder (an old
     * ".playchrono" from another disk). {@code live} is the session being written right now (never touched), or null.
     */
    public static Report restore(Path source, Path dataDir, Config cfg, AppState state, Path live) throws IOException {
        Map<String, byte[]> files = Files.isDirectory(source) ? readDir(source) : readZip(source);
        if (files.isEmpty()) throw new IOException("no PlayChrono data in " + source.getFileName());
        Report r = new Report();
        Map<String, Object> manifest = files.containsKey("backup.json") ? Json.map(Json.tryParse(utf8(files.get("backup.json")))) : null;
        if (manifest != null) {
            if (Json.num(manifest.get("format"), 1) > 1) throw new IOException("backup made by a newer PlayChrono: update the mod first");
            r.backupCreated = Json.num(manifest.get("created"), 0);
        }
        if (files.containsKey(".key")) r.keys = Signer.of(dataDir).trust(utf8(files.get(".key")));
        Path liveAbs = live == null ? null : live.toAbsolutePath().normalize();
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String name = e.getKey();
            boolean session = name.startsWith("sessions/");
            if (!session && !name.startsWith("imported/")) continue;
            Path target = dataDir.resolve(name);
            if (liveAbs != null && target.toAbsolutePath().normalize().equals(liveAbs)) continue;
            byte[] incoming = e.getValue();
            if (Files.exists(target)) {
                if (!session || !moreComplete(incoming, Files.readAllBytes(target))) continue;
                put(target, incoming);
                r.replaced++;
            } else {
                put(target, incoming);
                if (session) r.added++; else r.imported++;
            }
        }
        if (files.containsKey("state.json")) {
            Map<String, Object> st = Json.map(Json.tryParse(utf8(files.get("state.json"))));
            long created = st == null ? 0 : Json.num(st.get("created"), 0);
            if (created > 0 && created < state.num("created")) {
                state.put("created", created);
                r.installDate = true;
            }
        }
        if (files.containsKey("config.properties")) {
            r.settings = cfg.adopt(Config.read(Arrays.asList(utf8(files.get("config.properties")).split("\r?\n"))));
        }
        Secure.ownerOnlyTree(dataDir);
        return r;
    }

    /**
     * Restores every backup dropped into {@code dataDir/restore} (then moved to restore/done). Creates the folder so
     * players can find it. Returns the reports of the restores that changed something.
     */
    public static List<Report> restoreDropped(Path dataDir, Config cfg, AppState state, Path live) {
        List<Report> out = new ArrayList<>();
        Path dir = dataDir.resolve("restore");
        try {
            Files.createDirectories(dir);
            List<Path> zips;
            try (Stream<Path> s = Files.list(dir)) {
                zips = s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".zip")).sorted().collect(Collectors.toList());
            }
            for (Path z : zips) {
                Path done = dir.resolve("done");
                try {
                    Report r = restore(z, dataDir, cfg, state, live);
                    Log.info("restored " + z.getFileName() + ": " + r);
                    if (r.changed()) out.add(r);
                } catch (IOException | RuntimeException e) {
                    Log.warn("cannot restore " + z.getFileName(), e);
                    done = dir.resolve("failed");
                }
                Files.createDirectories(done);
                Files.move(z, done.resolve(z.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Log.warn("cannot read the restore folder", e);
        }
        if (!out.isEmpty()) state.put("restored", out.get(out.size() - 1).toMap());
        return out;
    }

    /**
     * Two copies of the same session: a finished one beats an unfinished one (the backup was taken while playing,
     * or the game crashed); between two unfinished copies, the longer one. Two finished copies hold the same data
     * (compaction is lossless), so the local one stays.
     */
    static boolean moreComplete(byte[] incoming, byte[] local) {
        if (Arrays.equals(incoming, local)) return false;
        boolean ie = finished(incoming), le = finished(local);
        if (ie != le) return ie;
        return !ie && incoming.length > local.length;
    }

    private static boolean finished(byte[] b) {
        String[] lines = utf8(b).split("\n");
        for (int i = lines.length - 1; i >= 0 && i >= lines.length - 3; i--) {
            Map<String, Object> rec = Json.map(Json.tryParse(Signer.split(lines[i].trim())[0]));
            if (rec != null && "end".equals(rec.get("t"))) return true;
        }
        return false;
    }

    private static void put(Path target, byte[] b) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".restore.tmp");
        Files.write(tmp, b);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Allowed name, also when the player zipped the whole ".playchrono" folder (one extra leading folder). */
    private static String allowed(String name) {
        name = name.replace('\\', '/');
        while (name.startsWith("./")) name = name.substring(2);
        if (ALLOWED.matcher(name).matches()) return name;
        int i = name.indexOf('/');
        if (i > 0 && ALLOWED.matcher(name.substring(i + 1)).matches()) return name.substring(i + 1);
        return null;
    }

    private static Map<String, byte[]> readZip(Path zip) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new java.io.BufferedInputStream(Files.newInputStream(zip)), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                String name = e.isDirectory() ? null : allowed(e.getName());
                if (name == null) continue;
                byte[] b = readLimited(in);
                if (b != null) out.put(name, b);
            }
        }
        return out;
    }

    private static Map<String, byte[]> readDir(Path dir) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (Stream<Path> s = Files.walk(dir, 4)) {
            for (Path p : s.filter(Files::isRegularFile).collect(Collectors.toList())) {
                String name = allowed(dir.relativize(p).toString());
                if (name != null && Files.size(p) <= MAX_ENTRY) out.put(name, Files.readAllBytes(p));
            }
        }
        return out;
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[1 << 16];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > MAX_ENTRY) return null; // not a PlayChrono file: skip
            b.write(buf, 0, n);
        }
        return b.toByteArray();
    }

    private static String utf8(byte[] b) { return new String(b, StandardCharsets.UTF_8); }
}
