package dev.playchrono.util;

import java.lang.reflect.Method;

/** Logs through SLF4J when the game provides it (every supported version does), stdout otherwise (CLI). */
public final class Log {
    private static Object logger;
    private static Method info, warn;

    static {
        try {
            Class<?> factory = Class.forName("org.slf4j.LoggerFactory");
            logger = factory.getMethod("getLogger", String.class).invoke(null, "PlayChrono");
            Class<?> l = Class.forName("org.slf4j.Logger");
            info = l.getMethod("info", String.class);
            warn = l.getMethod("warn", String.class);
        } catch (Throwable t) {
            logger = null;
        }
    }

    private Log() {}

    public static void info(String msg) { emit(info, "INFO", msg); }

    public static void warn(String msg) { emit(warn, "WARN", msg); }

    public static void warn(String msg, Throwable t) {
        emit(warn, "WARN", msg + " (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")");
    }

    private static void emit(Method m, String level, String msg) {
        if (logger != null) {
            try { m.invoke(logger, msg); return; } catch (Throwable ignored) { /* fall back */ }
        }
        System.out.println("[PlayChrono/" + level + "] " + msg);
    }
}
