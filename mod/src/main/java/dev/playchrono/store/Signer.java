package dev.playchrono.store;

import dev.playchrono.util.Json;
import dev.playchrono.util.Log;
import dev.playchrono.util.Secure;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tamper evidence for the session journals: every line carries {@code "h"}, an HMAC-SHA256 (truncated) of the
 * previous line's tag and the line itself, keyed with a random per-install secret (.key, owner-only). Editing,
 * deleting or reordering a line breaks the chain from that point on; such sessions still show in the player's own
 * dashboard but never count for the online leaderboard.
 *
 * <p>New lines are always signed with this install's own key. Keys of the player's previous installs (brought back
 * by restoring a backup, see {@link Backup}) are kept in {@code "alt"} and only used to verify the sessions they
 * signed, so a restored history keeps counting on a new computer.
 *
 * <p>Honest limit: the key has to live on the player's computer, so this stops casual edits, not a determined
 * reverse-engineer. The leaderboard server adds its own checks on top.
 */
public final class Signer {
    private static final Map<Path, Signer> CACHE = new ConcurrentHashMap<>();

    /** One HMAC key and the instant it started signing (sessions before it were written unsigned). */
    private static final class Key {
        final byte[] bytes;
        final long since;
        final ThreadLocal<Mac> macs;

        Key(byte[] bytes, long since) {
            this.bytes = bytes;
            this.since = since;
            this.macs = ThreadLocal.withInitial(() -> {
                try {
                    Mac m = Mac.getInstance("HmacSHA256");
                    m.init(new SecretKeySpec(bytes, "HmacSHA256"));
                    return m;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }

    private final Path file;
    private final Key own;
    private final List<Key> alt = new CopyOnWriteArrayList<>();

    private Signer(Path file, Key own) {
        this.file = file;
        this.own = own;
    }

    public static Signer of(Path dataDir) {
        return CACHE.computeIfAbsent(dataDir.toAbsolutePath().normalize(), Signer::load);
    }

    private static Signer load(Path dir) {
        Path f = dir.resolve(".key");
        try {
            if (Files.exists(f)) {
                Map<String, Object> m = Json.map(Json.tryParse(new String(Files.readAllBytes(f), StandardCharsets.UTF_8)));
                if (m != null && m.get("k") instanceof String) {
                    Signer s = new Signer(f, new Key(hex((String) m.get("k")), Json.num(m.get("since"), 0)));
                    for (Object o : listOf(m.get("alt"))) {
                        Map<String, Object> a = Json.map(o);
                        if (a != null && a.get("k") instanceof String) s.alt.add(new Key(hex((String) a.get("k")), Json.num(a.get("since"), 0)));
                    }
                    return s;
                }
            }
            byte[] k = new byte[32];
            new SecureRandom().nextBytes(k);
            Signer s = new Signer(f, new Key(k, System.currentTimeMillis()));
            Files.createDirectories(dir);
            s.save();
            return s;
        } catch (IOException | RuntimeException e) {
            Log.warn("cannot load signing key: sessions will not count for the leaderboard", e);
            return new Signer(null, new Key(new byte[32], Long.MAX_VALUE));
        }
    }

    /**
     * Trusts the keys found in another install's .key file (a restored backup) for verification. Returns the number
     * of keys that were not known yet. The own signing key never changes.
     */
    public synchronized int trust(String keyFileJson) {
        Map<String, Object> m = Json.map(Json.tryParse(keyFileJson));
        if (m == null) return 0;
        List<Map<String, Object>> found = new ArrayList<>();
        found.add(m);
        for (Object o : listOf(m.get("alt"))) if (Json.map(o) != null) found.add(Json.map(o));
        int added = 0;
        for (Map<String, Object> k : found) {
            if (!(k.get("k") instanceof String) || !((String) k.get("k")).matches("[0-9a-f]{64}")) continue;
            byte[] b = hex((String) k.get("k"));
            if (java.util.Arrays.equals(b, own.bytes) || alt.stream().anyMatch(a -> java.util.Arrays.equals(a.bytes, b))) continue;
            alt.add(new Key(b, Json.num(k.get("since"), 0)));
            added++;
        }
        if (added > 0) {
            try { save(); } catch (IOException e) { Log.warn("cannot save restored signing keys", e); }
        }
        return added;
    }

    private void save() throws IOException {
        if (file == null) return;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("k", hex(own.bytes));
        m.put("since", own.since);
        if (!alt.isEmpty()) {
            List<Object> l = new ArrayList<>();
            for (Key k : alt) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("k", hex(k.bytes));
                a.put("since", k.since);
                l.add(a);
            }
            m.put("alt", l);
        }
        Path tmp = file.resolveSibling(".key.tmp");
        Files.write(tmp, Json.write(m).getBytes(StandardCharsets.UTF_8));
        Secure.ownerOnly(tmp);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        Secure.ownerOnly(file);
    }

    /** Sessions started before this instant were written by a version that did not sign: accepted as is. */
    public long signedSince() {
        long s = own.since;
        for (Key k : alt) s = Math.min(s, k.since);
        return s;
    }

    /** Tag of a line given the previous tag ("" for the first line), with this install's own key. */
    public String tag(String prev, String json) { return tag(own, prev, json); }

    private static String tag(Key key, String prev, String json) {
        try {
            Mac mac = key.macs.get();
            mac.reset();
            mac.update(prev.getBytes(StandardCharsets.UTF_8));
            byte[] h = mac.doFinal(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(24);
            for (int i = 0; i < 12; i++) sb.append(String.format("%02x", h[i]));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * True when a session file's chain is intact with one of the trusted keys (or it predates signing). A wrong key
     * fails on the first line, so trying the previous installs' keys costs nothing.
     */
    public boolean verify(List<String> lines, long sessionStart) {
        if (verify(own, lines, sessionStart)) return true;
        for (Key k : alt) if (verify(k, lines, sessionStart)) return true;
        return false;
    }

    private boolean verify(Key key, List<String> lines, long sessionStart) {
        String prev = "";
        boolean unsigned = false;
        for (String line : lines) {
            if (line.isEmpty() || Json.tryParse(line) == null) continue; // torn last line after a crash
            String[] p = split(line);
            if (p[1] == null) { unsigned = true; continue; }
            if (unsigned || !p[1].equals(tag(key, prev, p[0]))) return false;
            prev = p[1];
        }
        return !unsigned || sessionStart < signedSince();
    }

    /** Appends the tag to a serialised JSON object: {...} -> {...,"h":"tag"}. */
    public static String withTag(String json, String tag) {
        return json.substring(0, json.length() - 1) + ",\"h\":\"" + tag + "\"}";
    }

    /** Splits a signed line into {json, tag}; tag is null when the line is not signed. */
    public static String[] split(String line) {
        int i = line.lastIndexOf(",\"h\":\"");
        if (i < 0 || !line.endsWith("\"}") || line.length() - i != 32) return new String[]{line, null};
        return new String[]{line.substring(0, i) + "}", line.substring(i + 6, line.length() - 2)};
    }

    private static List<Object> listOf(Object o) {
        List<Object> l = Json.list(o);
        return l != null ? l : new ArrayList<>();
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return b;
    }
}
