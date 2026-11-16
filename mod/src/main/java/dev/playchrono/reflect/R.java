package dev.playchrono.reflect;

import dev.playchrono.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Version-independent access to Minecraft internals.
 *
 * <p>Names come from {@code playchrono/names.tsv}, generated from the official Mojang, Fabric intermediary and
 * MCPConfig (SRG) mappings of every release from 1.20 to the latest (see tools/mappings). At runtime we only need
 * to know whether the game runs on intermediary names (Fabric/Quilt up to 1.21.11) or on Mojang class names
 * (Forge, NeoForge, and every loader on the unobfuscated 26.x) whose members are either Mojang or SRG names.
 *
 * <p>Every lookup degrades gracefully: a missing member returns {@code null}, and the caller has a fallback.
 */
public final class R {
    public enum Ns { INTERMEDIARY, OFFICIAL }

    private static final class Spec {
        final char kind;          // F or M
        final String owner;       // class key
        final String[] params;    // M only: "*" or JDK/primitive class name
        final List<String> names; // runtime candidates, most likely first
        Spec(char kind, String owner, String[] params, List<String> names) {
            this.kind = kind; this.owner = owner; this.params = params; this.names = names;
        }
    }

    private static final Object MISSING = new Object();
    private static ClassLoader loader;
    private static Ns ns = Ns.OFFICIAL;
    private static final Map<String, List<String>> CLASS_NAMES = new HashMap<>();
    private static final Map<String, Spec> SPECS = new HashMap<>();
    private static final Map<String, Object> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Object>> MEMBER_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> SCREEN_NAMES = new HashMap<>();
    private static final Set<String> REPORTED = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<String, Integer> FAILURES = new ConcurrentHashMap<>();
    private static boolean loaded;

    private R() {}

    public static synchronized void init(ClassLoader cl) {
        if (loaded) return;
        loader = cl;
        ns = classExists("net.minecraft.class_310") ? Ns.INTERMEDIARY : Ns.OFFICIAL;
        readTable("playchrono/names.tsv", true);
        readTable("playchrono/screens.tsv", false);
        loaded = true;
        Log.info("reflection namespace: " + ns + " (" + SPECS.size() + " members known)");
    }

    public static Ns namespace() { return ns; }

    private static boolean classExists(String n) {
        try { Class.forName(n, false, loader); return true; } catch (Throwable t) { return false; }
    }

    private static void readTable(String res, boolean names) {
        try (InputStream in = R.class.getClassLoader().getResourceAsStream(res)) {
            if (in == null) { Log.warn("missing resource " + res); return; }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\t", -1);
                if (!names) { SCREEN_NAMES.put(p[0], p[1]); continue; }
                switch (p[0]) {
                    case "C": {
                        List<String> c = new ArrayList<>();
                        if (ns == Ns.INTERMEDIARY) addAll(c, p[3]);
                        addAll(c, p[2]);
                        CLASS_NAMES.put(p[1], c);
                        break;
                    }
                    case "F": {
                        List<String> c = new ArrayList<>();
                        if (ns == Ns.INTERMEDIARY) addAll(c, p[3]); else { addAll(c, p[2]); addAll(c, p[4]); }
                        SPECS.put(p[1], new Spec('F', owner(p[1]), null, c));
                        break;
                    }
                    case "M": {
                        List<String> c = new ArrayList<>();
                        if (ns == Ns.INTERMEDIARY) addAll(c, p[4]); else { addAll(c, p[3]); addAll(c, p[5]); }
                        String[] params = "-".equals(p[2]) ? new String[0] : p[2].split(";");
                        SPECS.put(p[1], new Spec('M', owner(p[1]), params, c));
                        break;
                    }
                    default:
                }
            }
        } catch (Exception e) {
            Log.warn("cannot read " + res, e);
        }
    }

    private static String owner(String key) { return key.substring(0, key.indexOf('.')); }

    private static void addAll(List<String> out, String csv) {
        if (csv == null || csv.isEmpty()) return;
        for (String s : csv.split(",")) if (!s.isEmpty() && !out.contains(s)) out.add(s);
    }

    // ------------------------------------------------------------------ classes
    /** Resolves a class key from names.tsv (e.g. "TitleScreen"); null when absent in this version. */
    public static Class<?> cls(String key) {
        Object c = CLASS_CACHE.get(key);
        if (c == null) {
            c = MISSING;
            List<String> cands = CLASS_NAMES.get(key);
            if (cands != null) {
                for (String n : cands) {
                    try { c = Class.forName(n, false, loader); break; } catch (Throwable ignored) { /* next */ }
                }
            }
            CLASS_CACHE.put(key, c);
        }
        return c == MISSING ? null : (Class<?>) c;
    }

    public static boolean isInstance(String classKey, Object o) {
        if (o == null) return false;
        Class<?> c = cls(classKey);
        return c != null && c.isInstance(o);
    }

    /** Human readable name of a (possibly intermediary-named) class: "class_442" -> "TitleScreen". */
    public static String readableName(Class<?> c) {
        while (c != null && (c.isAnonymousClass() || c.getSimpleName().isEmpty())) c = c.getSuperclass();
        if (c == null) return "?";
        String n = c.getName();
        String mapped = SCREEN_NAMES.get(n);
        if (mapped != null) return mapped;
        if (n.startsWith("net.minecraft.class_")) {
            // unknown vanilla class: use the nearest readable ancestor
            Class<?> s = c.getSuperclass();
            return s == null ? n : readableName(s);
        }
        return c.getSimpleName();
    }

    // ------------------------------------------------------------------ members
    public static boolean has(Object target, String key) {
        return target != null && member(target.getClass(), key) != null;
    }

    public static Object get(Object target, String key) {
        if (target == null) return null;
        Object m = member(target.getClass(), key);
        if (m == null) return null;
        try {
            return m instanceof Field ? ((Field) m).get(target) : ((Method) m).invoke(target);
        } catch (Throwable t) {
            fail(key, t);
            return null;
        }
    }

    public static Object call(Object target, String key, Object... args) {
        if (target == null) return null;
        Object m = member(target.getClass(), key);
        if (!(m instanceof Method)) return null;
        try {
            return ((Method) m).invoke(target, args);
        } catch (Throwable t) {
            fail(key, t);
            return null;
        }
    }

    /** Static field or method, resolved on the owner class of the key. */
    public static Object callStatic(String key, Object... args) {
        Spec s = SPECS.get(key);
        Class<?> owner = s == null ? null : cls(s.owner);
        if (owner == null) return null;
        Object m = member(owner, key);
        try {
            if (m instanceof Method) return ((Method) m).invoke(null, args);
            if (m instanceof Field) return ((Field) m).get(null);
        } catch (Throwable t) {
            fail(key, t);
        }
        return null;
    }

    public static boolean getBool(Object target, String key, boolean def) {
        Object o = get(target, key);
        return o instanceof Boolean ? (Boolean) o : def;
    }

    public static double getNum(Object target, String key, double def) {
        Object o = get(target, key);
        return o instanceof Number ? ((Number) o).doubleValue() : def;
    }

    public static String getStr(Object target, String key) {
        Object o = get(target, key);
        return o == null ? null : String.valueOf(o);
    }

    /** Text of a Component (Component#getString is inherited from brigadier and never obfuscated). */
    public static String text(Object component) {
        if (component == null) return null;
        if (component instanceof String) return (String) component;
        Object s = call(component, "Component.getString");
        if (s == null) {
            try { s = component.getClass().getMethod("getString").invoke(component); } catch (Throwable ignored) { /* */ }
        }
        return s == null ? null : s.toString();
    }

    private static void fail(String key, Throwable t) {
        int n = FAILURES.merge(key, 1, Integer::sum);
        if (n == 1) Log.warn("call failed: " + key, t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null ? t.getCause() : t);
        if (n >= 200) {
            // persistently broken in this environment: stop trying
            for (Map<String, Object> m : MEMBER_CACHE.values()) m.put(key, MISSING);
        }
    }

    private static Object member(Class<?> type, String key) {
        Map<String, Object> cache = MEMBER_CACHE.computeIfAbsent(type, k -> new ConcurrentHashMap<>());
        Object m = cache.get(key);
        if (m == null) {
            m = resolve(type, key);
            cache.put(key, m == null ? MISSING : m);
        }
        return m == MISSING ? null : m;
    }

    private static Object resolve(Class<?> type, String key) {
        Spec s = SPECS.get(key);
        if (s == null) return null;
        Deque<Class<?>> todo = new ArrayDeque<>();
        Set<Class<?>> seen = new HashSet<>();
        todo.add(type);
        while (!todo.isEmpty()) {
            Class<?> c = todo.poll();
            if (c == null || !seen.add(c)) continue;
            try {
                for (String name : s.names) {
                    if (s.kind == 'F') {
                        for (Field f : c.getDeclaredFields()) {
                            if (f.getName().equals(name)) return accessible(f, key);
                        }
                    } else {
                        for (Method m : c.getDeclaredMethods()) {
                            if (m.getName().equals(name) && paramsMatch(m, s.params) && !m.isBridge()) return accessible(m, key);
                        }
                    }
                }
            } catch (Throwable t) {
                // NoClassDefFoundError from a field type absent in this environment: skip that class
            }
            if (c.getSuperclass() != null) todo.add(c.getSuperclass()); // null for interfaces and Object
            Collections.addAll(todo, c.getInterfaces());
        }
        if (REPORTED.add(key)) Log.info("not available in this version: " + key);
        return null;
    }

    /** Resolves every class and member of the table against the running game: key -> runtime name or MISSING. */
    public static Map<String, String> diagnose() {
        Map<String, String> out = new java.util.TreeMap<>();
        for (String k : CLASS_NAMES.keySet()) {
            Class<?> c = cls(k);
            out.put(k, c == null ? "MISSING" : c.getName());
        }
        for (Map.Entry<String, Spec> e : SPECS.entrySet()) {
            Class<?> owner = cls(e.getValue().owner);
            if (owner == null) { out.put(e.getKey(), "MISSING (class)"); continue; }
            Object m = resolve(owner, e.getKey());
            out.put(e.getKey(), m == null ? "MISSING" : m instanceof Field ? ((Field) m).getName() : ((Method) m).getName());
        }
        return out;
    }

    private static boolean paramsMatch(Method m, String[] params) {
        Class<?>[] p = m.getParameterTypes();
        if (p.length != params.length) return false;
        for (int i = 0; i < p.length; i++) {
            if (!"*".equals(params[i]) && !p[i].getName().equals(params[i])) return false;
        }
        return true;
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T o, String key) {
        int mod = o instanceof Field ? ((Field) o).getModifiers() : ((Method) o).getModifiers();
        Class<?> decl = o instanceof Field ? ((Field) o).getDeclaringClass() : ((Method) o).getDeclaringClass();
        if (!Modifier.isPublic(mod) || !Modifier.isPublic(decl.getModifiers())) {
            try { o.setAccessible(true); } catch (Throwable t) {
                Log.warn("cannot open " + key, t);
            }
        }
        return o;
    }
}
