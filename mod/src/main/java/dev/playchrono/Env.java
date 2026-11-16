package dev.playchrono;

import dev.playchrono.util.Json;

import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Facts about the running game and machine, gathered once (mod list lazily, once loading is finished). */
public final class Env {
    public String loader = "unknown", loaderVersion = "?", mcVersion = "?", mcVersionType = "?";
    public String javaVersion, javaVendor, os, osVersion, arch, cpu, launcherBrand, launcherVersion;
    public int cpus;
    public long heapMaxMb, systemRamMb, jvmStart;
    public Path gameDir;
    public final List<String[]> mods = new ArrayList<>();

    public static Env detect(String loaderHint, Class<?> minecraftClass) {
        Env e = new Env();
        e.loader = loaderHint;
        if ("fabric".equals(loaderHint) && classExists("org.quiltmc.loader.api.QuiltLoader")) e.loader = "quilt";
        e.javaVersion = System.getProperty("java.version");
        e.javaVendor = System.getProperty("java.vendor");
        e.os = System.getProperty("os.name");
        e.osVersion = System.getProperty("os.version");
        e.arch = System.getProperty("os.arch");
        e.cpus = Runtime.getRuntime().availableProcessors();
        e.heapMaxMb = Runtime.getRuntime().maxMemory() / 1048576L;
        e.systemRamMb = systemRam();
        e.cpu = cpuName();
        e.jvmStart = ManagementFactory.getRuntimeMXBean().getStartTime();
        e.launcherBrand = System.getProperty("minecraft.launcher.brand");
        e.launcherVersion = System.getProperty("minecraft.launcher.version");
        e.gameDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        if (minecraftClass != null) e.readVersionJson(minecraftClass);
        return e;
    }

    /** The client jar ships a version.json ("id", "name", "type") since 1.14: exact on every loader. */
    private void readVersionJson(Class<?> mc) {
        String json = null;
        try {
            URL loc = mc.getProtectionDomain().getCodeSource().getLocation();
            Path p = Paths.get(loc.toURI());
            if (Files.isRegularFile(p)) {
                try (ZipFile z = new ZipFile(p.toFile())) {
                    ZipEntry en = z.getEntry("version.json");
                    if (en != null) try (InputStream in = z.getInputStream(en)) { json = read(in); }
                }
            }
        } catch (Throwable ignored) { /* union:/ or jar-in-jar URLs: fall back below */ }
        if (json == null) {
            try (InputStream in = mc.getResourceAsStream("/version.json")) { if (in != null) json = read(in); } catch (Throwable ignored) { /* */ }
        }
        Map<String, Object> m = json == null ? null : Json.map(Json.tryParse(json));
        if (m != null) {
            mcVersion = Json.str(m.get("name"), Json.str(m.get("id"), mcVersion));
            Object stable = m.get("stable");
            mcVersionType = Boolean.FALSE.equals(stable) ? "snapshot" : "release";
        }
    }

    private static String read(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Mod list through the loader's public API, by reflection (no compile-time dependency on any loader). */
    public void readMods() {
        mods.clear();
        try {
            if ("fabric".equals(loader) || "quilt".equals(loader)) {
                Class<?> fl = Class.forName("net.fabricmc.loader.api.FabricLoader");
                Object inst = fl.getMethod("getInstance").invoke(null);
                Method getMeta = Class.forName("net.fabricmc.loader.api.ModContainer").getMethod("getMetadata");
                Class<?> metaI = Class.forName("net.fabricmc.loader.api.metadata.ModMetadata");
                Method id = metaI.getMethod("getId"), ver = metaI.getMethod("getVersion"), name = metaI.getMethod("getName");
                Method friendly = Class.forName("net.fabricmc.loader.api.Version").getMethod("getFriendlyString");
                for (Object c : (Collection<?>) fl.getMethod("getAllMods").invoke(inst)) {
                    Object meta = getMeta.invoke(c);
                    mods.add(new String[]{(String) id.invoke(meta), (String) friendly.invoke(ver.invoke(meta)), (String) name.invoke(meta)});
                }
            } else {
                String pkg = "neoforge".equals(loader) ? "net.neoforged.fml.ModList" : "net.minecraftforge.fml.ModList";
                Class<?> ml = Class.forName(pkg);
                Object list = ml.getMethod("get").invoke(null);
                for (Object info : (Collection<?>) ml.getMethod("getMods").invoke(list)) {
                    mods.add(new String[]{String.valueOf(call(info, "getModId")), String.valueOf(call(info, "getVersion")),
                            String.valueOf(call(info, "getDisplayName"))});
                }
            }
        } catch (Throwable ignored) { /* mod list is a nice-to-have */ }
        for (String[] m : mods) {
            String id = m[0];
            if ("minecraft".equals(id) && "?".equals(mcVersion)) mcVersion = m[1];
            if (id.equals("fabricloader") || id.equals("quilt_loader") || id.equals("forge") || id.equals("neoforge")) loaderVersion = m[1];
        }
    }

    private static Object call(Object o, String name) {
        try {
            for (Class<?> i : o.getClass().getInterfaces()) {
                try { return i.getMethod(name).invoke(o); } catch (NoSuchMethodException ignored) { /* next */ }
            }
            return o.getClass().getMethod(name).invoke(o);
        } catch (Throwable t) {
            return "?";
        }
    }

    private static boolean classExists(String n) {
        try { Class.forName(n, false, Env.class.getClassLoader()); return true; } catch (Throwable t) { return false; }
    }

    private static long systemRam() {
        try {
            Object os = ManagementFactory.getOperatingSystemMXBean();
            Class<?> i = Class.forName("com.sun.management.OperatingSystemMXBean");
            for (String m : new String[]{"getTotalMemorySize", "getTotalPhysicalMemorySize"}) {
                try { return ((Number) i.getMethod(m).invoke(os)).longValue() / 1048576L; } catch (NoSuchMethodException ignored) { /* */ }
            }
        } catch (Throwable ignored) { /* */ }
        return -1;
    }

    private static String cpuName() {
        try {
            String w = System.getenv("PROCESSOR_IDENTIFIER");
            if (w != null && !w.isEmpty()) return w;
            Path p = Paths.get("/proc/cpuinfo");
            if (Files.isReadable(p)) {
                for (String l : Files.readAllLines(p)) {
                    if (l.toLowerCase(Locale.ROOT).startsWith("model name")) return l.substring(l.indexOf(':') + 1).trim();
                }
            }
        } catch (Throwable ignored) { /* */ }
        return System.getProperty("os.arch");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mc", mcVersion);
        m.put("mcType", mcVersionType);
        m.put("ldr", loader);
        m.put("ldrv", loaderVersion);
        m.put("java", javaVersion);
        m.put("javaVendor", javaVendor);
        m.put("os", os + " " + osVersion);
        m.put("arch", arch);
        m.put("cpu", cpu);
        m.put("cpus", cpus);
        m.put("heapMb", heapMaxMb);
        m.put("ramMb", systemRamMb);
        if (launcherBrand != null) m.put("launcher", launcherBrand + (launcherVersion == null ? "" : " " + launcherVersion));
        m.put("gameDir", gameDir.toString());
        m.put("jvmStart", jvmStart);
        return m;
    }

    public List<Object> modsList() {
        List<Object> l = new ArrayList<>();
        for (String[] m : mods) {
            if (m[0].startsWith("fabric-") && !m[0].equals("fabric-api")) continue; // Fabric API sub-modules: noise
            if (m[0].equals("java") || m[0].equals("minecraft") || m[0].equals("mixinextras")) continue;
            List<Object> one = new ArrayList<>();
            one.add(m[0]);
            one.add(m[1]);
            l.add(one);
        }
        return l;
    }
}
