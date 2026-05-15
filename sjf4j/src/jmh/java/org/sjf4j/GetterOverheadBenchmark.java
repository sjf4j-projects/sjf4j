package org.sjf4j;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.sjf4j.exception.JsonException;
import org.sjf4j.node.Nodes;

import java.util.concurrent.TimeUnit;

/**
 * Compare getter overhead: current (with try-catch) vs direct Nodes call.
 */
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class GetterOverheadBenchmark {

    private JsonObject jo;

    @Setup
    public void setup() {
        jo = new JsonObject();
        jo.put("name", "alice");
        jo.put("age", 30);
        jo.put("active", true);
        jo.put("score", 95.5);
    }

    // Baseline: field lookup only
    @Benchmark
    public void getNode(Blackhole bh) {
        bh.consume(jo.getNode("name"));
        bh.consume(jo.getNode("age"));
        bh.consume(jo.getNode("active"));
        bh.consume(jo.getNode("score"));
    }

    // Current: JsonObject.getString with try-catch wrapper
    @Benchmark
    public void getString(Blackhole bh) {
        bh.consume(jo.getString("name"));
    }

    // Direct Nodes.toString without try-catch
    @Benchmark
    public void directToString(Blackhole bh) {
        bh.consume(Nodes.toString(jo.getNode("name")));
    }

    // Template helper (simulates the proposed refactor)
    @Benchmark
    public void templateGetString(Blackhole bh) {
        bh.consume(_getString(jo, "name"));
    }

    // Current: JsonObject.getInt with try-catch
    @Benchmark
    public void getInt(Blackhole bh) {
        bh.consume(jo.getInt("age"));
    }

    // Direct Nodes.toInt without try-catch
    @Benchmark
    public void directToInt(Blackhole bh) {
        bh.consume(Nodes.toInt(jo.getNode("age")));
    }

    // Template helper (using lambda)
    @Benchmark
    public void templateGetInt(Blackhole bh) {
        bh.consume(_get(jo, "age", Nodes::toInt, "Integer"));
    }

    // Current: JsonObject.getBoolean with try-catch
    @Benchmark
    public void getBoolean(Blackhole bh) {
        bh.consume(jo.getBoolean("active"));
    }

    // Direct Nodes.toBoolean
    @Benchmark
    public void directToBoolean(Blackhole bh) {
        bh.consume(Nodes.toBoolean(jo.getNode("active")));
    }

    // ---- Error path (exception creation) ----
    @Benchmark
    public void getStringFromInt(Blackhole bh) {
        try { jo.getString("age"); } catch (Exception e) { bh.consume(e); }
    }

    @Benchmark
    public void directToStringFromInt(Blackhole bh) {
        try { Nodes.toString(jo.getNode("age")); } catch (Exception e) { bh.consume(e); }
    }

    // Helper: template method pattern
    private static <T> T _get(JsonObject obj, String key, java.util.function.Function<Object, T> fn, String type) {
        try {
            return fn.apply(obj.getNode(key));
        } catch (Exception e) {
            throw new JsonException("cannot get " + type + " at '" + key + "'", e);
        }
    }

    private static String _getString(JsonObject obj, String key) {
        try {
            return Nodes.toString(obj.getNode(key));
        } catch (Exception e) {
            throw new JsonException("cannot get String at '" + key + "'", e);
        }
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}
