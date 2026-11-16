package dev.playchrono.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dependency-free JSON writer/parser (the mod must not rely on any library shipped by a given MC version). */
public final class Json {
    private Json() {}

    // ------------------------------------------------------------------ writing
    public static String write(Object o) {
        StringBuilder sb = new StringBuilder(256);
        write(sb, o);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static void write(StringBuilder sb, Object o) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String) {
            str(sb, (String) o);
        } else if (o instanceof Double || o instanceof Float) {
            double d = ((Number) o).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) sb.append("null");
            else if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) d);
            else sb.append(Math.round(d * 1000.0) / 1000.0);
        } else if (o instanceof Number || o instanceof Boolean) {
            sb.append(o);
        } else if (o instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<Object, Object> e : ((Map<Object, Object>) o).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                str(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (o instanceof Iterable) {
            sb.append('[');
            Iterator<Object> it = ((Iterable<Object>) o).iterator();
            while (it.hasNext()) {
                write(sb, it.next());
                if (it.hasNext()) sb.append(',');
            }
            sb.append(']');
        } else if (o instanceof long[]) {
            long[] a = (long[]) o;
            sb.append('[');
            for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(a[i]); }
            sb.append(']');
        } else if (o instanceof int[]) {
            int[] a = (int[]) o;
            sb.append('[');
            for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(a[i]); }
            sb.append(']');
        } else if (o instanceof double[]) {
            double[] a = (double[]) o;
            sb.append('[');
            for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); write(sb, a[i]); }
            sb.append(']');
        } else if (o instanceof Object[]) {
            sb.append('[');
            Object[] a = (Object[]) o;
            for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); write(sb, a[i]); }
            sb.append(']');
        } else {
            str(sb, o.toString());
        }
    }

    private static void str(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '<': sb.append("\\u003c"); break; // safe to embed in <script>
                default:
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------ parsing
    public static Object parse(String s) {
        P p = new P(s);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != s.length()) throw new IllegalArgumentException("trailing data at " + p.i);
        return v;
    }

    /** Lenient parse: returns null instead of throwing (used for possibly truncated JSONL lines). */
    public static Object tryParse(String s) {
        try { return parse(s); } catch (RuntimeException e) { return null; }
    }

    private static final class P {
        final String s;
        int i;
        P(String s) { this.s = s; }

        void ws() { while (i < s.length() && s.charAt(i) <= ' ') i++; }

        Object value() {
            if (i >= s.length()) throw new IllegalArgumentException("eof");
            char c = s.charAt(i);
            switch (c) {
                case '{': return obj();
                case '[': return arr();
                case '"': return string();
                case 't': expect("true"); return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null"); return null;
                default: return num();
            }
        }

        void expect(String w) {
            if (!s.startsWith(w, i)) throw new IllegalArgumentException("expected " + w + " at " + i);
            i += w.length();
        }

        Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            ws();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws();
                String k = string();
                ws();
                if (s.charAt(i++) != ':') throw new IllegalArgumentException("':' expected at " + i);
                ws();
                m.put(k, value());
                ws();
                char c = s.charAt(i++);
                if (c == '}') return m;
                if (c != ',') throw new IllegalArgumentException("',' expected at " + i);
            }
        }

        List<Object> arr() {
            List<Object> l = new ArrayList<>();
            i++;
            ws();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) {
                ws();
                l.add(value());
                ws();
                char c = s.charAt(i++);
                if (c == ']') return l;
                if (c != ',') throw new IllegalArgumentException("',' expected at " + i);
            }
        }

        String string() {
            if (s.charAt(i) != '"') throw new IllegalArgumentException("string expected at " + i);
            i++;
            StringBuilder sb = null;
            int start = i;
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return sb == null ? s.substring(start, i - 1) : sb.toString();
                if (c == '\\') {
                    if (sb == null) { sb = new StringBuilder(); sb.append(s, start, i - 1); }
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u': sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                        default: sb.append(e);
                    }
                } else if (sb != null) {
                    sb.append(c);
                }
            }
        }

        Number num() {
            int start = i;
            boolean dbl = false;
            while (i < s.length()) {
                char c = s.charAt(i);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+') i++;
                else if (c == '.' || c == 'e' || c == 'E') { dbl = true; i++; }
                else break;
            }
            String t = s.substring(start, i);
            if (t.isEmpty()) throw new IllegalArgumentException("unexpected char at " + start);
            if (!dbl) {
                try { return Long.parseLong(t); } catch (NumberFormatException ignored) { /* fall through */ }
            }
            return Double.parseDouble(t);
        }
    }

    // ------------------------------------------------------------------ typed helpers for parsed maps
    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object o) { return o instanceof Map ? (Map<String, Object>) o : null; }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object o) { return o instanceof List ? (List<Object>) o : null; }

    public static long num(Object o, long def) { return o instanceof Number ? ((Number) o).longValue() : def; }

    public static double dbl(Object o, double def) { return o instanceof Number ? ((Number) o).doubleValue() : def; }

    public static String str(Object o, String def) { return o instanceof String ? (String) o : def; }
}
