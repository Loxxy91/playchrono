import javax.imageio.ImageIO;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds the universal PlayChrono jar with nothing but a JDK (17+):
 *
 *     cd mod
 *     java Build.java
 *
 * -> ../release/playchrono-VERSION.jar (Fabric + Quilt + Forge + NeoForge, Minecraft 1.20 .. 26.x)
 *
 * No Gradle / Loom / ForgeGradle on purpose: the mod has no compile-time dependency on Minecraft or on any loader
 * (it talks to the game through reflection with names from tools/mappings), so a plain javac build is enough and
 * takes ~2 seconds. Compile-only stubs (src/stubs) stand in for the 3 loader entry-point types and LWJGL's GLFW.
 */
public class Build {
    static final Path MOD = Paths.get("").toAbsolutePath();
    static final Path ROOT = MOD.getParent();
    static final Path OUT = MOD.resolve("build");

    public static void main(String[] args) throws Exception {
        if (!Files.exists(MOD.resolve("mod.properties"))) throw new IllegalStateException("run from the mod/ folder");
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(MOD.resolve("mod.properties"), StandardCharsets.UTF_8)) { p.load(r); }
        String version = p.getProperty("version");
        delete(OUT);
        Path stubs = OUT.resolve("stubs"), classes = OUT.resolve("classes");
        Files.createDirectories(stubs);
        Files.createDirectories(classes);

        javac(sources(MOD.resolve("src/stubs/java")), stubs, null);
        javac(sources(MOD.resolve("src/main/java")), classes, stubs);

        copyTree(MOD.resolve("src/main/resources"), classes);
        copyTree(ROOT.resolve("web"), classes.resolve("playchrono/web"));
        write(classes.resolve("playchrono/version.txt"), version);
        write(classes.resolve("playchrono/defaults.properties"), "leaderboard.url=" + p.getProperty("leaderboardUrl", "").trim() + "\n");
        write(classes.resolve("fabric.mod.json"), fabric(p));
        write(classes.resolve("META-INF/mods.toml"), toml(p, false));
        write(classes.resolve("META-INF/neoforge.mods.toml"), toml(p, true));
        Path brandIcon = ROOT.resolve("branding/icon.png"); // designed icon (branding/src/icon.svg), fallback: generated
        byte[] icon = Files.exists(brandIcon) ? scaled(brandIcon, 128) : icon(128);
        Files.createDirectories(classes.resolve("assets/" + p.getProperty("id")));
        Files.write(classes.resolve("assets/" + p.getProperty("id") + "/icon.png"), icon);
        Files.write(classes.resolve(p.getProperty("id") + "_logo.png"), icon);

        Path jar = ROOT.resolve("release").resolve(p.getProperty("id") + "-" + version + ".jar");
        Files.createDirectories(jar.getParent());
        Path tmp = OUT.resolve(jar.getFileName());
        jar(classes, tmp, p);
        try {
            Files.move(tmp, jar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            jar = jar.resolveSibling(p.getProperty("id") + "-" + version + "-" + System.currentTimeMillis() / 1000 + ".jar");
            Files.move(tmp, jar);
            System.out.println("NOTE: the previous jar is locked by another program (IDE indexer, running game...): wrote " + jar.getFileName());
        }
        System.out.printf("OK  %s  (%d KiB)%n", ROOT.relativize(jar), Files.size(jar) / 1024);
    }

    // ------------------------------------------------------------------------------------------ compile
    static List<String> sources(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(f -> f.toString().endsWith(".java")).map(Path::toString).collect(Collectors.toList());
        }
    }

    static void javac(List<String> files, Path out, Path cp) {
        JavaCompiler c = ToolProvider.getSystemJavaCompiler();
        if (c == null) throw new IllegalStateException("a JDK is required (not a JRE)");
        List<String> a = new ArrayList<>(List.of("--release", "17", "-encoding", "UTF-8", "-Xlint:all,-options,-serial", "-d", out.toString()));
        if (cp != null) { a.add("-cp"); a.add(cp.toString()); }
        a.addAll(files);
        int rc = c.run(null, null, null, a.toArray(new String[0]));
        if (rc != 0) throw new IllegalStateException("compilation failed");
    }

    // ------------------------------------------------------------------------------------------ metadata
    static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }

    static String fabric(Properties p) {
        String authors = Stream.of(p.getProperty("authors").split(",")).map(s -> "\"" + esc(s.trim()) + "\"").collect(Collectors.joining(", "));
        return "{\n"
                + "  \"schemaVersion\": 1,\n"
                + "  \"id\": \"" + p.getProperty("id") + "\",\n"
                + "  \"version\": \"" + p.getProperty("version") + "\",\n"
                + "  \"name\": \"" + esc(p.getProperty("name")) + "\",\n"
                + "  \"description\": \"" + esc(p.getProperty("description")) + "\",\n"
                + "  \"authors\": [" + authors + "],\n"
                + "  \"contact\": {\"homepage\": \"" + p.getProperty("homepage") + "\", \"sources\": \"" + p.getProperty("sources")
                + "\", \"issues\": \"" + p.getProperty("issues") + "\"},\n"
                + "  \"license\": \"" + p.getProperty("license") + "\",\n"
                + "  \"icon\": \"assets/" + p.getProperty("id") + "/icon.png\",\n"
                + "  \"environment\": \"client\",\n"
                + "  \"entrypoints\": {\"client\": [\"dev.playchrono.loader.FabricEntry\"]},\n"
                + "  \"depends\": {\"fabricloader\": \">=0.14.21\", \"minecraft\": \">=" + p.getProperty("minecraft") + "\", \"java\": \">=17\"}\n"
                + "}\n";
    }

    /** Forge (all versions) + NeoForge 20.2-20.4 read META-INF/mods.toml; NeoForge 20.5+ reads neoforge.mods.toml. */
    static String toml(Properties p, boolean neo) {
        String id = p.getProperty("id");
        return "modLoader=\"javafml\"\n"
                + "loaderVersion=\"[1,)\"\n"
                + "license=\"" + p.getProperty("license") + "\"\n"
                + "issueTrackerURL=\"" + p.getProperty("issues") + "\"\n\n"
                + "[[mods]]\n"
                + "modId=\"" + id + "\"\n"
                + "version=\"" + p.getProperty("version") + "\"\n"
                + "displayName=\"" + esc(p.getProperty("name")) + "\"\n"
                + "displayURL=\"" + p.getProperty("homepage") + "\"\n"
                + "logoFile=\"" + id + "_logo.png\"\n"
                + "authors=\"" + esc(p.getProperty("authors")) + "\"\n"
                + "displayTest=\"IGNORE_ALL_VERSION\"\n"
                + "description='''\n" + p.getProperty("description") + "\n'''\n\n"
                + "[[dependencies." + id + "]]\n"
                + "modId=\"minecraft\"\n"
                + (neo ? "type=\"required\"\n" : "mandatory=true\n")
                + "versionRange=\"[" + p.getProperty("minecraft") + ",)\"\n"
                + "ordering=\"NONE\"\n"
                + "side=\"CLIENT\"\n";
    }

    // ------------------------------------------------------------------------------------------ icon
    static byte[] scaled(Path png, int size) throws IOException {
        BufferedImage src = ImageIO.read(png.toFile());
        BufferedImage cur = src;
        while (cur.getWidth() / 2 >= size) { // progressive halving keeps it sharp
            BufferedImage half = new BufferedImage(cur.getWidth() / 2, cur.getHeight() / 2, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = half.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(cur, 0, 0, half.getWidth(), half.getHeight(), null);
            g.dispose();
            cur = half;
        }
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ImageIO.write(cur, "png", b);
        return b.toByteArray();
    }

    static byte[] icon(int size) throws IOException {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double s = size / 128.0;
        g.setPaint(new GradientPaint(0, 0, new Color(0x16243A), size, size, new Color(0x0B1220)));
        g.fill(new RoundRectangle2D.Double(4 * s, 4 * s, 120 * s, 120 * s, 28 * s, 28 * s));
        // grass-block strip
        g.setColor(new Color(0x3F8F3A));
        g.fill(new RoundRectangle2D.Double(4 * s, 92 * s, 120 * s, 32 * s, 28 * s, 28 * s));
        g.setColor(new Color(0x6B4A2E));
        g.fillRect((int) (4 * s), (int) (100 * s), (int) (120 * s), (int) (8 * s));
        // chrono / compass dial
        g.setColor(new Color(0x1C1C1C));
        g.fill(new java.awt.geom.Ellipse2D.Double(26 * s, 18 * s, 76 * s, 76 * s));
        g.setColor(new Color(0xF2C53D));
        g.fill(new java.awt.geom.Ellipse2D.Double(31 * s, 23 * s, 66 * s, 66 * s));
        g.setColor(new Color(0xF4EEDB));
        g.fill(new java.awt.geom.Ellipse2D.Double(39 * s, 31 * s, 50 * s, 50 * s));
        g.setStroke(new BasicStroke((float) (7 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0xE23B2E));
        g.draw(new java.awt.geom.Line2D.Double(64 * s, 56 * s, 78 * s, 40 * s));
        g.setColor(new Color(0x7D8A96));
        g.draw(new java.awt.geom.Line2D.Double(64 * s, 56 * s, 54 * s, 68 * s));
        g.dispose();
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ImageIO.write(img, "png", b);
        return b.toByteArray();
    }

    // ------------------------------------------------------------------------------------------ jar & files
    static void jar(Path dir, Path jar, Properties p) throws IOException {
        Manifest mf = new Manifest();
        Attributes a = mf.getMainAttributes();
        a.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        a.put(Attributes.Name.MAIN_CLASS, "dev.playchrono.Cli");
        a.put(Attributes.Name.IMPLEMENTATION_TITLE, p.getProperty("name"));
        a.put(Attributes.Name.IMPLEMENTATION_VERSION, p.getProperty("version"));
        a.putValue("Automatic-Module-Name", p.getProperty("id"));
        List<Path> files;
        try (Stream<Path> s = Files.walk(dir)) { files = s.filter(Files::isRegularFile).sorted().collect(Collectors.toList()); }
        try (OutputStream o = Files.newOutputStream(jar); JarOutputStream j = new JarOutputStream(o, mf)) {
            for (Path f : files) {
                String name = dir.relativize(f).toString().replace('\\', '/');
                JarEntry e = new JarEntry(name);
                e.setTime(0L); // reproducible builds
                j.putNextEntry(e);
                Files.copy(f, j);
                j.closeEntry();
            }
        }
    }

    static void copyTree(Path from, Path to) throws IOException {
        if (!Files.isDirectory(from)) return;
        try (Stream<Path> s = Files.walk(from)) {
            for (Path f : s.filter(Files::isRegularFile).collect(Collectors.toList())) {
                Path t = to.resolve(from.relativize(f).toString());
                Files.createDirectories(t.getParent());
                Files.copy(f, t);
            }
        }
    }

    static void write(Path f, String s) throws IOException {
        Files.createDirectories(f.getParent());
        Files.write(f, s.getBytes(StandardCharsets.UTF_8));
    }

    static void delete(Path d) throws IOException {
        if (!Files.exists(d)) return;
        try (Stream<Path> s = Files.walk(d)) {
            for (Path f : s.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) Files.delete(f);
        }
    }
}
