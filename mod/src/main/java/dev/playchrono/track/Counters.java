package dev.playchrono.track;

import java.util.LinkedHashMap;
import java.util.Map;

/** A bag of numeric counters (flat or one level nested), serialised as one JSON object. */
public final class Counters {
    private final Map<String, Object> data = new LinkedHashMap<>();

    public void add(String k, double v) {
        if (v == 0) return;
        data.merge(k, v, (a, b) -> ((Number) a).doubleValue() + ((Number) b).doubleValue());
    }

    @SuppressWarnings("unchecked")
    public void add(String group, String k, double v) {
        if (v == 0 || k == null) return;
        Map<String, Object> m = (Map<String, Object>) data.computeIfAbsent(group, x -> new LinkedHashMap<String, Object>());
        m.merge(k, v, (a, b) -> ((Number) a).doubleValue() + ((Number) b).doubleValue());
    }

    public void max(String k, double v) {
        Object o = data.get(k);
        if (o == null || ((Number) o).doubleValue() < v) data.put(k, v);
    }

    public void min(String k, double v) {
        Object o = data.get(k);
        if (o == null || ((Number) o).doubleValue() > v) data.put(k, v);
    }

    public boolean isEmpty() { return data.isEmpty(); }

    public Map<String, Object> map() { return data; }
}
