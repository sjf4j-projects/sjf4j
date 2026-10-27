package org.sjf4j.testbench.node;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.Nodes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Synthetic schema-like properties, patternProperties and additionalProperties over Map and POJO entries. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
public class ObjectEntryTraversalBenchmark {
    private static final int FIELD_COUNT = 12;
    private static final String[] KEYS = {
            "name", "status", "title", "category",
            "metric01", "metric02", "metric03", "metric04",
            "extra01", "extra02", "extra03", "extra04"
    };

    // An ordinary POJO: no JOJO annotations or generated accessors.
    public static class Bean {
        public String name = "Ada";
        public String status = "active";
        public String title = "Ada";
        public String category = "ok";
        public String metric01 = "20";
        public String metric02 = "20";
        public String metric03 = "20";
        public String metric04 = "20";
        public String extra01 = "ok";
        public String extra02 = "ok";
        public String extra03 = "ok";
        public String extra04 = "ok";
    }

    @Param({"map", "pojo"})
    public String representation;

    @Param({"valid", "firstFailure", "lastFailure", "aggregate"})
    public String scenario;

    private Object node;
    private boolean failFast;
    private Map<String, String> properties;
    private Pattern[] patterns;
    private String[] patternConstraints;

    @Setup(Level.Trial)
    public void setup() throws ReflectiveOperationException {
        properties = Map.of("name", "Ada", "status", "active", "metric02", "20");
        patterns = new Pattern[]{Pattern.compile("^metric"), Pattern.compile("^(name|title)$")};
        patternConstraints = new String[]{"20", "Ada"};

        Bean bean = new Bean();
        if ("map".equals(representation)) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : KEYS) {
                map.put(key, Bean.class.getField(key).get(bean));
            }
            node = map;
        } else if ("pojo".equals(representation)) {
            node = bean;
        } else {
            throw new IllegalArgumentException(representation);
        }

        // POJO property order comes from Nodes, not the declaration order above.
        String first = null;
        String last = null;
        int count = 0;
        for (Map.Entry<String, Object> entry : Nodes.entrySetInObject(node)) {
            if (first == null) first = entry.getKey();
            last = entry.getKey();
            count++;
        }
        if (count != FIELD_COUNT) throw new IllegalStateException("Expected 12 gettable members: " + count);

        failFast = !"aggregate".equals(scenario);
        if ("firstFailure".equals(scenario) || "aggregate".equals(scenario)) {
            setValue(first, "wrong");
        }
        if ("lastFailure".equals(scenario) || "aggregate".equals(scenario)) {
            setValue(last, "wrong");
        }
        if (!"valid".equals(scenario) && !"firstFailure".equals(scenario)
                && !"lastFailure".equals(scenario) && !"aggregate".equals(scenario)) {
            throw new IllegalArgumentException(scenario);
        }

        long expectedMask = (1L << FIELD_COUNT) - 1;
        int expectedVisited = FIELD_COUNT;
        boolean expectedValid = "valid".equals(scenario);
        if ("firstFailure".equals(scenario)) {
            expectedMask = 0;
            expectedVisited = 1;
        } else if ("lastFailure".equals(scenario)) {
            expectedMask &= ~(1L << (FIELD_COUNT - 1));
        } else if ("aggregate".equals(scenario)) {
            expectedMask &= ~1L;
            expectedMask &= ~(1L << (FIELD_COUNT - 1));
        }
        long entries = entrySet();
        long matches = anyMatch();
        long observedMask = entries & 0xfffL;
        long observedVisited = (entries >>> 12) & 0xffL;
        boolean observedValid = ((entries >>> 20) & 1L) != 0;
        long observedFailures = entries >>> 24;
        int minFailures = "aggregate".equals(scenario) ? 2 : expectedValid ? 0 : 1;
        if (entries != matches || observedMask != expectedMask
                || observedVisited != expectedVisited || observedValid != expectedValid
                || observedFailures < minFailures || (expectedValid && observedFailures != 0)) {
            throw new IllegalStateException(representation + "/" + scenario
                    + ": entrySet=" + entries + ", anyMatch=" + matches);
        }
    }

    @SuppressWarnings("unchecked")
    private void setValue(String key, String value) throws ReflectiveOperationException {
        if (node instanceof Map) {
            ((Map<String, Object>) node).put(key, value);
        } else {
            Bean.class.getField(key).set(node, value);
        }
    }

    @Benchmark
    public long anyMatch() {
        boolean[] valid = {true};
        int[] propIdx = {0};
        long[] evaluated = {0};
        int[] failures = {0};
        boolean aborted = Nodes.anyMatchInObject(node, (key, value) -> {
            int index = propIdx[0];
            boolean matched = false;
            String constraint = properties.get(key);
            if (constraint != null) {
                if (constraint.equals(value)) evaluated[0] |= 1L << index;
                else {
                    valid[0] = false;
                    failures[0]++;
                    if (failFast) return true;
                }
                matched = true;
            }

            for (int i = 0; i < patterns.length; i++) {
                if (patterns[i].matcher(key).find()) {
                    if (patternConstraints[i].equals(value)) evaluated[0] |= 1L << index;
                    else {
                        valid[0] = false;
                        failures[0]++;
                        if (failFast) return true;
                    }
                    matched = true;
                }
            }

            if (!matched) {
                if ("ok".equals(value)) evaluated[0] |= 1L << index;
                else {
                    valid[0] = false;
                    failures[0]++;
                    if (failFast) return true;
                }
            }
            propIdx[0]++;
            return false;
        });
        // The failing entry is visited even when the predicate aborts before incrementing propIdx.
        int visited = propIdx[0] + (aborted ? 1 : 0);
        return evaluated[0] | ((long) visited << 12)
                | ((valid[0] && !aborted ? 1L : 0L) << 20) | ((long) failures[0] << 24);
    }

    @Benchmark
    public long entrySet() {
        boolean valid = true;
        int visited = 0;
        long evaluated = 0;
        int failures = 0;
        entries:
        for (Map.Entry<String, Object> entry : Nodes.entrySetInObject(node)) {
            String key = entry.getKey();
            Object value = entry.getValue();
            int index = visited++;
            boolean matched = false;
            String constraint = properties.get(key);
            if (constraint != null) {
                if (constraint.equals(value)) evaluated |= 1L << index;
                else {
                    valid = false;
                    failures++;
                    if (failFast) break entries;
                }
                matched = true;
            }

            for (int i = 0; i < patterns.length; i++) {
                if (patterns[i].matcher(key).find()) {
                    if (patternConstraints[i].equals(value)) evaluated |= 1L << index;
                    else {
                        valid = false;
                        failures++;
                        if (failFast) break entries;
                    }
                    matched = true;
                }
            }
            if (!matched) {
                if ("ok".equals(value)) evaluated |= 1L << index;
                else {
                    valid = false;
                    failures++;
                    if (failFast) break entries;
                }
            }
        }
        // Include successful evaluation indices, traversal length, validity and failure count.
        return evaluated | ((long) visited << 12) | ((valid ? 1L : 0L) << 20)
                | ((long) failures << 24);
    }
}
