import dev.playchrono.reflect.R;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Runtime check of the reflection table against a REAL game jar, without starting the game:
 * classes are loaded but never initialised, so no Minecraft code runs.
 *
 *   cd mod && java Build.java
 *   java -cp build/classes tools/ReflectionCheck.java <game jar> <libraries dir> [expected-missing-file]
 *
 * <game jar>: an intermediary jar from a Fabric instance (.fabric/remappedJars/.../client-intermediary.jar)
 *             or an unobfuscated 26.x client jar. Exit code 1 if something unexpected is missing.
 */
public class ReflectionCheck {
    public static void main(String[] a) throws Exception {
        List<URL> urls = new ArrayList<>();
        urls.add(Paths.get(a[0]).toUri().toURL());
        try (Stream<Path> s = Files.walk(Paths.get(a[1]))) {
            List<Path> libs = s.filter(p -> p.toString().endsWith(".jar") && !p.toString().contains("natives")
                    && !p.toString().contains("com" + java.io.File.separator + "mojang" + java.io.File.separator + "minecraft" + java.io.File.separator))
                    .sorted(Comparator.comparing(Path::toString).reversed()) // newest library versions first
                    .collect(Collectors.toList());
            for (Path p : libs) urls.add(p.toUri().toURL());
        }
        URLClassLoader cl = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
        R.init(cl);
        Map<String, String> d = R.diagnose();
        List<String> expected = a.length > 2 ? Files.readAllLines(Paths.get(a[2])) : new ArrayList<>();
        int ok = 0;
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> e : d.entrySet()) {
            if (e.getValue().startsWith("MISSING")) missing.add(e.getKey());
            else ok++;
        }
        Class<?> mc = R.cls("Minecraft");
        System.out.printf("%s  namespace=%s  resolved %d/%d  Minecraft is Executor: %s%n", Paths.get(a[0]).getFileName(),
                R.namespace(), ok, d.size(), mc != null && Executor.class.isAssignableFrom(mc));
        List<String> unexpected = missing.stream().filter(k -> !expected.contains(k)).collect(Collectors.toList());
        System.out.println("  missing (" + missing.size() + "): " + String.join(", ", missing));
        if (!unexpected.isEmpty() && a.length > 2) {
            System.out.println("  UNEXPECTED: " + unexpected);
            System.exit(1);
        }
    }
}
