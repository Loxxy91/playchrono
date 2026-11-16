package dev.playchrono.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Global configuration, stored in ~/.playchrono/config.properties (shared by every game instance and launcher).
 * Missing keys are appended with their documented default, user values are never touched.
 */
public final class Config {
    /** key -> {default, comment} in display order. */
    private static final Map<String, String[]> DEFAULTS = new LinkedHashMap<>();

    static {
        def("afk.seconds", "300", "Seconds without keyboard/mouse input before you are considered AFK / Secondes sans input avant d'etre AFK");
        def("language", "auto", "auto = game language. Or: fr, en, es, de, it, pt, pl, ru, zh, ja");
        def("dashboard.hotkey", "F8", "Key that opens the dashboard in-game (F1-F12, or 'none') / Touche du tableau de bord");
        def("dashboard.theme", "auto", "Dashboard theme: auto (follows your system), light or dark. Also saved when you click the theme button");
        def("dashboard.keyboard", "auto", "Keyboard heatmap layout: auto, qwerty or azerty");
        def("dashboard.port", "47823", "Local web dashboard port, bound to 127.0.0.1 only. 0 = disabled (open the HTML file instead)");
        def("chat.welcome", "true", "First world join of the day: short summary in chat (only you can see it)");
        def("chat.milestones", "true", "Chat message every 10 h of total playtime");
        def("chat.afkReturn", "false", "Message when you come back after a long AFK");
        def("chat.breakReminderMinutes", "0", "Remind you to take a break after N minutes of continuous play. 0 = off");
        def("import.logs", "false", "true = also show an ESTIMATE of your past playtime rebuilt from the game's logs folder");
        def("privacy.commands", "true", "Count which /commands you use (name only, never arguments or chat content)");
        def("privacy.keys", "true", "Count key presses per key (keyboard heatmap)");
        def("privacy.serverAddresses", "true", "Store server addresses (false = stores only the server-list name)");
        def("leaderboard.enabled", "false", "OPT-IN: publish your totals (playtime, distance, streaks...) on the online leaderboard under your Minecraft name. Toggle it in the dashboard (F8 > Leaderboard)");
        def("dashboard.skin", "true", "Download your own skin from Mojang (once a day) to show your head in the dashboard");
        def("leaderboard.url", "", "Leaderboard server. Empty = the official PlayChrono server");
    }

    private static void def(String k, String v, String comment) { DEFAULTS.put(k, new String[]{v, comment}); }

    /** Values baked in at build time from mod.properties (playchrono/defaults.properties inside the jar). */
    public static String builtIn(String key) {
        try (java.io.InputStream in = Config.class.getClassLoader().getResourceAsStream("playchrono/defaults.properties")) {
            if (in == null) return "";
            Properties p = new Properties();
            p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            return p.getProperty(key, "").trim();
        } catch (IOException e) {
            return "";
        }
    }


    private final Path file;
    private final Properties props = new Properties();

    public Config(Path dir) {
        this.file = dir.resolve("config.properties");
        load();
    }

    public static Path dataDir() {
        String p = System.getProperty("playchrono.dir");
        if (p == null || p.isEmpty()) p = System.getenv("PLAYCHRONO_DIR");
        if (p == null || p.isEmpty()) return Paths.get(System.getProperty("user.home"), ".playchrono");
        return Paths.get(p);
    }


    private void load() {
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { props.load(r); }
            }
            boolean missing = DEFAULTS.keySet().stream().anyMatch(k -> !props.containsKey(k));
            if (missing) appendMissing();
        } catch (IOException e) {
            Log.warn("cannot read config, using defaults", e);
        }
    }

    private void appendMissing() throws IOException {
        boolean fresh = !Files.exists(file);
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
            if (fresh) {
                w.write("# PlayChrono configuration - shared by all your Minecraft instances.\n");
                w.write("# Changes apply on next game start. Docs: README.md\n\n");
            }
            for (Map.Entry<String, String[]> e : DEFAULTS.entrySet()) {
                if (props.containsKey(e.getKey())) continue;
                w.write("# " + e.getValue()[1] + "\n" + e.getKey() + "=" + e.getValue()[0] + "\n\n");
                props.setProperty(e.getKey(), e.getValue()[0]);
            }
        }
    }

    public String str(String key) {
        String v = props.getProperty(key);
        if (v == null) v = DEFAULTS.containsKey(key) ? DEFAULTS.get(key)[0] : "";
        return v.trim();
    }

    /** Saves the dashboard display preferences sent by the page (only known values are accepted). */
    public void savePrefs(Map<String, Object> m) {
        Object th = m.get("theme"), kb = m.get("keyboard");
        if ("auto".equals(th) || "light".equals(th) || "dark".equals(th)) set("dashboard.theme", (String) th);
        if ("auto".equals(kb) || "qwerty".equals(kb) || "azerty".equals(kb)) set("dashboard.keyboard", (String) kb);
    }

    public Map<String, Object> prefs() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("theme", str("dashboard.theme"));
        m.put("keyboard", str("dashboard.keyboard"));
        return m;
    }

    public boolean bool(String key) { return "true".equalsIgnoreCase(str(key)); }

    /** Updates one key in memory and in the file, keeping comments and the user's other lines untouched. */
    public synchronized void set(String key, String value) {
        if (!DEFAULTS.containsKey(key) || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) throw new IllegalArgumentException(key);
        props.setProperty(key, value);
        try {
            java.util.List<String> lines = Files.exists(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : new java.util.ArrayList<>();
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                String l = lines.get(i).trim();
                if (l.startsWith(key + "=") || l.startsWith(key + " =")) { lines.set(i, key + "=" + value); found = true; }
            }
            if (!found) lines.add(key + "=" + value);
            Path tmp = file.resolveSibling("config.properties.tmp");
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Log.warn("cannot save config", e);
        }
    }

    public int integer(String key) {
        try { return Integer.parseInt(str(key)); } catch (NumberFormatException e) {
            return Integer.parseInt(DEFAULTS.get(key)[0]);
        }
    }
}
