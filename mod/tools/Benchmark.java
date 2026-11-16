import dev.playchrono.store.Aggregator;
import dev.playchrono.store.Compactor;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Aggregation benchmark on a synthetic history (default 1000 hours of play in 2-hour sessions, same record shapes
 * and sizes as real journals).
 *
 *   java -cp build/classes tools/Benchmark.java [hours]
 */
public class Benchmark {
    public static void main(String[] a) throws Exception {
        int hours = a.length > 0 ? Integer.parseInt(a[0]) : 1000;
        Path dir = Files.createTempDirectory("playchrono-bench");
        Random r = new Random(1);
        long t = System.currentTimeMillis() - hours * 3_600_000L * 6, lines = 0;
        String[] ctx = {"server:smp.example.net", "server:skyblock.example.org", "world:Island"};
        for (int s = 0; s < hours / 2; s++) {
            Path f = dir.resolve("sessions").resolve("2026-01").resolve("s" + s + ".jsonl");
            Files.createDirectories(f.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                w.write("{\"t\":\"start\",\"ts\":" + t + ",\"mc\":\"1.21.4\",\"ldr\":\"fabric\"}\n");
                String c = ctx[r.nextInt(ctx.length)];
                for (int m = 0; m < 120; m++) {
                    long ts = t + m * 60_000L;
                    w.write("{\"t\":\"seg\",\"s\":" + ts + ",\"d\":60000,\"st\":\"" + (r.nextInt(10) == 0 ? "CONTAINER" : "PLAY")
                            + "\",\"c\":\"" + c + "\",\"dm\":\"minecraft:overworld\"" + (r.nextInt(20) == 0 ? ",\"k\":1" : "") + "}\n");
                    w.write("{\"t\":\"m\",\"s\":" + ts + ",\"e\":" + (ts + 60_000) + ",\"c\":\"" + c + "\",\"dm\":\"minecraft:overworld\",\"n\":{\"keys\":"
                            + r.nextInt(80) + ",\"kc\":{\"87\":30,\"32\":9,\"65\":7,\"68\":6,\"340\":4},\"clickL\":" + r.nextInt(40) + ",\"clickR\":9,\"mousePx\":2600,"
                            + "\"jump\":6,\"mv\":{\"walk\":" + r.nextInt(90) + ".5,\"sprint\":120.2},\"mvDim\":{\"minecraft:overworld\":200.1},"
                            + "\"bio\":{\"minecraft:plains\":40000,\"minecraft:forest\":20000},\"wx\":{\"clear\":60000},\"tod\":{\"day\":60000},"
                            + "\"gm\":{\"survival\":60000},\"fpsSum\":6000000,\"fpsMs\":60000,\"fpsMin\":80,\"fpsB\":{\"60_120\":60000},"
                            + "\"pingSum\":2400000,\"pingMs\":60000,\"ramMax\":3100,\"yMax\":71,\"yMin\":60,\"farMax\":1200.5,\"speedMax\":7.1}}\n");
                    lines += 2;
                }
                w.write("{\"t\":\"end\",\"ts\":" + (t + 7_200_000L) + "}\n");
            }
            t += 7_200_000L * 3;
        }
        System.out.printf("%d h, %d sessions, %,d lines, %.1f MB%n", hours, hours / 2, lines, Files.walk(dir).filter(Files::isRegularFile)
                .mapToLong(p -> p.toFile().length()).sum() / 1e6);
        for (int i = 0; i < 2; i++) {
            long t0 = System.nanoTime();
            Aggregator.run(dir);
            System.out.printf("raw run %d: %d ms%n", i + 1, (System.nanoTime() - t0) / 1_000_000);
        }
        Object before = Aggregator.run(dir).me;
        long c0 = System.nanoTime();
        Compactor.compactAll(dir, null);
        System.out.printf("compaction: %d ms -> %.1f MB%n", (System.nanoTime() - c0) / 1_000_000, Files.walk(dir).filter(Files::isRegularFile)
                .mapToLong(p -> p.toFile().length()).sum() / 1e6);
        Object after = Aggregator.run(dir).me;
        System.out.println("leaderboard totals identical after compaction: " + before.toString().equals(after.toString()));
        for (int i = 0; i < 3; i++) {
            long t0 = System.nanoTime();
            Aggregator.Result res = Aggregator.run(dir);
            System.out.printf("compacted run %d: %d ms (days %d)%n", i + 1, (System.nanoTime() - t0) / 1_000_000, ((java.util.Map<?, ?>) res.rollup.get("days")).size());
        }
    }
}
