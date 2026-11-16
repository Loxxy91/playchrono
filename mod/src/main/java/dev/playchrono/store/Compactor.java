package dev.playchrono.store;

import dev.playchrono.util.Json;
import dev.playchrono.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Shrinks finished session journals once, losslessly for every statistic the dashboard shows:
 * contiguous identical segments are merged and per-minute counters are folded per (local hour, context,
 * dimension) with the same merge rule as the Aggregator. Typically 5-10x fewer lines, so aggregation stays
 * fast after years of play. The running session is never touched.
 */
public final class Compactor {
    private Compactor() {}

    /** Compacts every finished, not yet compacted session file. Returns the number of files rewritten. */
    public static int compactAll(Path dataDir, Path live) {
        Path dir = dataDir.resolve("sessions");
        if (!Files.isDirectory(dir)) return 0;
        List<Path> files;
        try (Stream<Path> s = Files.walk(dir)) {
            files = s.filter(p -> p.toString().endsWith(".jsonl")).collect(Collectors.toList());
        } catch (IOException e) {
            return 0;
        }
        int n = 0;
        long t0 = System.currentTimeMillis();
        for (Path p : files) {
            if (live != null && p.toAbsolutePath().normalize().equals(live.toAbsolutePath().normalize())) continue;
            try {
                if (compact(p, Signer.of(dataDir))) n++;
            } catch (IOException | RuntimeException e) {
                Log.warn("cannot compact " + p.getFileName(), e);
            }
        }
        if (n > 0) Log.info("compacted " + n + " session files in " + (System.currentTimeMillis() - t0) + " ms");
        return n;
    }

    static boolean compact(Path p, Signer signer) throws IOException {
        List<String> raw = Files.readAllLines(p, StandardCharsets.UTF_8);
        Map<String, Object> firstRec = raw.isEmpty() ? null : Json.map(Json.tryParse(Signer.split(raw.get(0))[0]));
        if (firstRec == null || firstRec.get("cv") != null) return false;
        boolean intact = signer.verify(raw, Json.num(firstRec.get("ts"), 0));
        List<Map<String, Object>> head = new ArrayList<>(), evs = new ArrayList<>(), segs = new ArrayList<>();
        Map<String, Map<String, Object>> counters = new LinkedHashMap<>();
        Map<String, Object> start = null, end = null;
        ZoneId zone = ZoneId.systemDefault();
        int lines = 0;
        {
            for (String rawLine : raw) {
                String line = Signer.split(rawLine)[0];
                Map<String, Object> rec = Json.map(Json.tryParse(line));
                if (rec == null) continue;
                lines++;
                String t = Json.str(rec.get("t"), "");
                switch (t) {
                    case "start":
                        if (start != null) return false;       // unexpected: several sessions in one file, leave it
                        if (rec.get("cv") != null) return false; // already compacted
                        start = rec;
                        break;
                    case "end": end = rec; break;
                    case "env": head.add(rec); break;
                    case "ev": evs.add(rec); break;
                    case "seg": {
                        Map<String, Object> last = segs.isEmpty() ? null : segs.get(segs.size() - 1);
                        if (last != null && sameSeg(last, rec)
                                && Math.abs(Json.num(last.get("s"), 0) + Json.num(last.get("d"), 0) - Json.num(rec.get("s"), 0)) < 3000) {
                            last.put("d", Json.num(last.get("d"), 0) + Json.num(rec.get("d"), 0));
                        } else {
                            segs.add(rec);
                        }
                        break;
                    }
                    case "m": {
                        long s = Json.num(rec.get("s"), 0);
                        String hour = Instant.ofEpochMilli(s).atZone(zone).toLocalDateTime().toString().substring(0, 13);
                        String key = hour + "|" + rec.get("c") + "|" + rec.get("dm");
                        Map<String, Object> cur = counters.get(key);
                        Map<String, Object> n = Json.map(rec.get("n"));
                        if (n == null) break;
                        if (cur == null) {
                            Map<String, Object> copy = new LinkedHashMap<>(rec);
                            Map<String, Object> nn = new LinkedHashMap<>();
                            Aggregator.merge(nn, n);
                            copy.put("n", nn);
                            counters.put(key, copy);
                        } else {
                            cur.put("s", Math.min(Json.num(cur.get("s"), s), s));
                            cur.put("e", Math.max(Json.num(cur.get("e"), 0), Json.num(rec.get("e"), 0)));
                            Aggregator.merge(Json.map(cur.get("n")), n);
                        }
                        break;
                    }
                    default:
                }
            }
        }
        if (start == null || end == null) return false; // only finished sessions
        start.put("cv", 1);
        if (!intact) start.put("tp", 1); // never launder an edited session: it stays out of the leaderboard
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(start);
        out.addAll(head);
        out.addAll(segs);
        out.addAll(counters.values());
        out.addAll(evs);
        out.add(end);
        Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
        try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            String prev = "";
            for (Map<String, Object> rec : out) { // re-signed as a fresh chain
                String s = Json.write(rec);
                prev = signer.tag(prev, s);
                w.write(Signer.withTag(s, prev));
                w.write('\n');
            }
        }
        Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    private static boolean sameSeg(Map<String, Object> a, Map<String, Object> b) {
        for (String k : new String[]{"st", "c", "dm", "sc", "f", "k"}) if (!Objects.equals(a.get(k), b.get(k))) return false;
        return true;
    }
}
