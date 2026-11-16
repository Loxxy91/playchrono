package dev.playchrono.store;

import dev.playchrono.util.Json;
import dev.playchrono.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small persistent state (first run, last welcome day). Written atomically. */
public final class AppState {
    private final Path file;
    private final Map<String, Object> data;

    public AppState(Path dir) {
        this.file = dir.resolve("state.json");
        Map<String, Object> d = null;
        try {
            if (Files.exists(file)) d = Json.map(Json.tryParse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            Log.warn("cannot read state.json", e);
        }
        data = d != null ? d : new LinkedHashMap<>();
        if (!(data.get("created") instanceof Number)) {
            data.put("created", System.currentTimeMillis());
            save();
        }
    }

    public String str(String k) { return Json.str(data.get(k), null); }

    public long num(String k) { return Json.num(data.get(k), 0); }

    public synchronized void put(String k, Object v) {
        data.put(k, v);
        save();
    }

    public synchronized void remove(String k) {
        if (data.remove(k) != null) save();
    }

    private synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("state.json.tmp");
            Files.write(tmp, Json.write(data).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try { Files.write(file, Json.write(data).getBytes(StandardCharsets.UTF_8)); } catch (IOException ignored) { /* */ }
        }
    }
}
