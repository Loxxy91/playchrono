package dev.playchrono.store;

import dev.playchrono.util.Json;
import dev.playchrono.util.Log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Append-only JSON-lines journal, one file per game session: sessions/2026-11/20261112-153012-a3f9.jsonl.
 * One file per process means several game instances can run at once without any locking, and a crash loses at
 * most the last few seconds (the buffer is flushed every 10 s).
 */
public final class EventLog implements AutoCloseable {
    private final Path file;
    private final String sessionId;
    private BufferedWriter out;
    private final Signer signer;
    private String prev = "";

    public EventLog(Path sessionsDir, long startMillis, Signer signer) throws IOException {
        this.signer = signer;
        Date d = new Date(startMillis);
        String rand = Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF));
        this.sessionId = new SimpleDateFormat("yyyyMMdd-HHmmss").format(d) + "-" + rand;
        Path dir = sessionsDir.resolve(new SimpleDateFormat("yyyy-MM").format(d));
        Files.createDirectories(dir);
        this.file = dir.resolve(sessionId + ".jsonl");
        this.out = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public String sessionId() { return sessionId; }

    public Path file() { return file; }

    public static Map<String, Object> rec(String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", type);
        return m;
    }

    public synchronized void write(Map<String, Object> record) {
        if (out == null) return;
        try {
            String s = Json.write(record);
            prev = signer.tag(prev, s);
            out.write(Signer.withTag(s, prev));
            out.write('\n');
        } catch (IOException e) {
            Log.warn("cannot write session journal", e);
        }
    }

    public synchronized void flush() {
        if (out == null) return;
        try { out.flush(); } catch (IOException e) { Log.warn("flush failed", e); }
    }

    @Override
    public synchronized void close() {
        if (out == null) return;
        try { out.close(); } catch (IOException ignored) { /* best effort at shutdown */ }
        out = null;
    }
}
