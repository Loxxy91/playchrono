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
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tamper evidence for the session journals: every line carries {@code "h"}, an HMAC-SHA256 (truncated) of the
 * previous line's tag and the line itself, keyed with a random per-install secret (.key, owner-only). Editing,
 * deleting or reordering a line breaks the chain from that point on; such sessions still show in the player's own
 * dashboard but never count for the online leaderboard.
 *
 * <p>Honest limit: the key has to live on the player's computer, so this stops casual edits, not a determined
 * reverse-engineer. The leaderboard server adds its own checks on top.
 */
public final class Signer {
    private static final Map<Path, Signer> CACHE = new ConcurrentHashMap<>();
    private final byte[] key;
    /** Sessions started before this instant were written by a version that did not sign: accepted as is. */
    public final long signedSince;

    private Signer(byte[] key, long signedSince) {
        this.key = key;
        this.signedSince = signedSince;
    }

    public static Signer of(Path dataDir) {
        return CACHE.computeIfAbsent(dataDir.toAbsolutePath().normalize(), Signer::load);
    }

    private static Signer load(Path dir) {
        Path f = dir.resolve(".key");
        try {
            if (Files.exists(f)) {
                Map<String, Object> m = Json.map(Json.tryParse(new String(Files.readAllBytes(f), StandardCharsets.UTF_8)));
                if (m != null && m.get("k") instanceof String) return new Signer(hex((String) m.get("k")), Json.num(m.get("since"), 0));
            }
            byte[] k = new byte[32];
            new SecureRandom().nextBytes(k);
            long since = System.currentTimeMillis();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("k", hex(k));
            m.put("since", since);
            Files.createDirectories(dir);
            Files.write(f, Json.write(m).getBytes(StandardCharsets.UTF_8));
            Secure.ownerOnly(f);
            return new Signer(k, since);
        } catch (IOException | RuntimeException e) {
            Log.warn("cannot load signing key: sessions will not count for the leaderboard", e);
            return new Signer(new byte[32], Long.MAX_VALUE);
        }
    }

    /** Tag of a line given the previous tag ("" for the first line). */
    private final ThreadLocal<Mac> macs = ThreadLocal.withInitial(this::newMac);

    private Mac newMac() {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(key, "HmacSHA256"));
            return m;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String tag(String prev, String json) {
        try {
            Mac mac = macs.get();
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

    /** True when a session file's chain is intact (or it predates signing). Reads the whole file once. */
    public boolean verify(java.util.List<String> lines, long sessionStart) {
        String prev = "";
        boolean unsigned = false;
        for (String line : lines) {
            if (line.isEmpty() || Json.tryParse(line) == null) continue; // torn last line after a crash
            String[] p = split(line);
            if (p[1] == null) { unsigned = true; continue; }
            if (unsigned || !p[1].equals(tag(prev, p[0]))) return false;
            prev = p[1];
        }
        return !unsigned || sessionStart < signedSince;
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
