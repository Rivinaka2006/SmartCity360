package com.smartcity360.ejb.streetlights.benchmark;

import com.smartcity360.ejb.streetlights.domain.flyweight.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.openjdk.jol.info.GraphLayout;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A bounded, repeatable VIVA demonstration. Both variants contain the same immutable streetlight
 * contexts; only configuration ownership differs. JOL reports each complete dataset's reachable
 * retained graph, not JVM-wide live heap, and this test intentionally never calls System.gc().
 */
@Tag("benchmark")
class StreetlightFlyweightBenchmarkTest {
    static {
        // Java records hide physical field offsets from Unsafe. JOL's documented fallback provides
        // a deterministic estimate without attaching an agent.
        System.setProperty("jol.magicFieldOffset", "true");
    }

    private static final int PROFILE_COUNT = 10;
    private static final int WARMUPS = 2;
    private static final int REPETITIONS = 5;

    @Test
    void compareEquivalentNaiveAndFlyweightStreetlightDatasets() throws Exception {
        StringBuilder csv = new StringBuilder("streetlights,profiles,naive_config_allocations,flyweight_config_allocations,")
                .append("naive_dataset_retained_bytes,flyweight_dataset_retained_bytes,naive_median_ns,flyweight_median_ns,jvm\n");
        for (int size : new int[]{1_000, 10_000, 50_000}) {
            for (int i = 0; i < WARMUPS; i++) {
                naive(size);
                flyweight(size);
            }
            long[] naiveTimes = new long[REPETITIONS];
            long[] flyweightTimes = new long[REPETITIONS];
            Dataset naive = null;
            Dataset shared = null;
            for (int i = 0; i < REPETITIONS; i++) {
                long start = System.nanoTime();
                naive = naive(size);
                naiveTimes[i] = System.nanoTime() - start;
                start = System.nanoTime();
                shared = flyweight(size);
                flyweightTimes[i] = System.nanoTime() - start;
            }

            assertNotNull(naive);
            assertNotNull(shared);
            assertEquals(size, naive.lights().size());
            assertEquals(size, shared.lights().size());
            assertEquals(size, naive.configurationAllocations());
            assertEquals(PROFILE_COUNT, shared.configurationAllocations());
            assertEquals(size, distinctProfileIdentities(naive.lights()));
            assertEquals(PROFILE_COUNT, distinctProfileIdentities(shared.lights()));
            for (int i = PROFILE_COUNT; i < size; i++)
                assertSame(shared.lights().get(i % PROFILE_COUNT).profile(), shared.lights().get(i).profile());
            assertEquals(naive.lights().get(size - 1).context(), shared.lights().get(size - 1).context());
            assertEquals(naive.lights().get(size - 1).profile(), shared.lights().get(size - 1).profile());

            long naiveBytes = GraphLayout.parseInstance((Object) naive.lights()).totalSize();
            long flyweightBytes = GraphLayout.parseInstance((Object) shared.lights()).totalSize();
            assertTrue(flyweightBytes < naiveBytes);
            csv.append(size).append(',').append(PROFILE_COUNT).append(',')
                    .append(naive.configurationAllocations()).append(',').append(shared.configurationAllocations()).append(',')
                    .append(naiveBytes).append(',').append(flyweightBytes).append(',')
                    .append(median(naiveTimes)).append(',').append(median(flyweightTimes)).append(',')
                    .append('"').append(System.getProperty("java.vm.name")).append(' ')
                    .append(System.getProperty("java.version")).append("\"\n");
        }
        Path output = Path.of("target", "flyweight-benchmark.csv");
        Files.createDirectories(output.getParent());
        Files.writeString(output, csv.toString(), StandardCharsets.UTF_8);
        Files.writeString(Path.of("target", "flyweight-benchmark-methodology.txt"), methodology(), StandardCharsets.UTF_8);
        assertTrue(Files.size(output) > 0);
    }

    private static Dataset naive(int size) {
        AtomicInteger constructions = new AtomicInteger();
        ArrayList<BenchStreetlight> lights = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            SharedLightingProfile profile = profile(i % PROFILE_COUNT);
            constructions.incrementAndGet();
            lights.add(new BenchStreetlight(context(i), profile));
        }
        return new Dataset(lights, constructions.get());
    }

    private static Dataset flyweight(int size) {
        AtomicInteger constructions = new AtomicInteger();
        LightingProfileFactory factory = new LightingProfileFactory(PROFILE_COUNT);
        ArrayList<BenchStreetlight> lights = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int profileIndex = i % PROFILE_COUNT;
            ProfileKey key = key(profileIndex);
            SharedLightingProfile profile = factory.resolve(key, () -> {
                constructions.incrementAndGet();
                return profile(profileIndex);
            });
            lights.add(new BenchStreetlight(context(i), profile));
        }
        assertEquals(PROFILE_COUNT, factory.statistics().cacheSize());
        return new Dataset(lights, constructions.get());
    }

    private static StreetlightContext context(int index) {
        return new StreetlightContext(new UUID(1L, index + 1L), new UUID(2L, 1L),
                new BigDecimal("6.927079"), new BigDecimal("79.861244"), "Pole " + index,
                index % 2 == 0 ? LightState.ON : LightState.OFF, index % 3 == 0 ? 60 : null);
    }

    private static ProfileKey key(int index) { return new ProfileKey(new UUID(0L, index + 1L), 1); }

    private static SharedLightingProfile profile(int index) {
        return new SharedLightingProfile(key(index), "SmartLuminaire", "SL-" + index,
                new BigDecimal("75.00"), 80, "DALI-2", "FW-A",
                "{\"dimmingCurve\":\"linear\",\"channel\":" + index + "}");
    }

    private static int distinctProfileIdentities(List<BenchStreetlight> lights) {
        Set<Object> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        lights.forEach(light -> identities.add(light.profile()));
        return identities.size();
    }

    private static long median(long[] values) {
        long[] copy = values.clone();
        Arrays.sort(copy);
        return copy[copy.length / 2];
    }

    private static String methodology() {
        return "JVM: " + System.getProperty("java.vm.name") + " " + System.getProperty("java.version") + "\n"
                + "JVM arguments: " + String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments()) + "\n"
                + "Distinct profiles: " + PROFILE_COUNT + "\nWarmups: " + WARMUPS
                + "\nMeasured repetitions: " + REPETITIONS + " (median reported)\n"
                + "Retained bytes: JOL graph reachable from the complete List<BenchStreetlight> root; estimate only.\n"
                + "Timing: construction of contexts, configuration resolution/allocation, and holders; not a JMH throughput claim.\n"
                + "Execution: mvn -f backend/pom.xml -Pflyweight-benchmark -pl smartcity360-ejb -am "
                + "-Dtest=StreetlightFlyweightBenchmarkTest -Dsurefire.failIfNoSpecifiedTests=false -DargLine=-Xmx1536m test\n"
                + "Environment: reserve at least 2 GiB of free physical/commit memory; close Payara or other large JVMs first.\n";
    }

    private record BenchStreetlight(StreetlightContext context, SharedLightingProfile profile) { }
    private record Dataset(List<BenchStreetlight> lights, int configurationAllocations) { }
}
