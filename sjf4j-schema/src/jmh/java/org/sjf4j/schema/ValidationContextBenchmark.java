package org.sjf4j.schema;

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

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Measures the incremental cost of allocating a context versus reusing one.
 * Use JMH's -prof gc to compare allocation rates. The schema and input are
 * prepared outside timed operations, and the context is thread-confined.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
public class ValidationContextBenchmark {

    @State(Scope.Thread)
    public static class Data {
        @Param({"scalar", "object", "nested"})
        public String shape;

        @Param({"true", "false"})
        public boolean valid;

        SchemaPlan plan;
        Object node;
        ValidationContext context;

        @Setup(Level.Trial)
        public void setup() {
            context = new ValidationContext(true, false, true);
            switch (shape) {
                case "scalar":
                    plan = JsonSchema.fromJson("{\"type\":\"string\",\"minLength\":3}").createPlan();
                    node = valid ? "hello" : "x";
                    break;
                case "object":
                    plan = JsonSchema.fromJson(
                            "{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"integer\"}},"
                                    + "\"required\":[\"x\"]}").createPlan();
                    node = valid ? Map.of("x", 42) : Map.of("x", "bad");
                    break;
                case "nested":
                    plan = JsonSchema.fromJson(
                            "{\"type\":\"object\",\"properties\":{\"values\":{"
                                    + "\"type\":\"array\",\"items\":{\"type\":\"integer\"}}},"
                                    + "\"required\":[\"values\"]}").createPlan();
                    node = valid ? Map.of("values", List.of(1, 2, 3))
                            : Map.of("values", List.of(1, "bad", 3));
                    break;
                default:
                    throw new IllegalArgumentException(shape);
            }
            if (plan.isValid(node) != valid || plan.evaluate(node, context) != valid) {
                throw new IllegalStateException("mismatched benchmark fixture");
            }
        }
    }

    /** Existing API: constructs a context for each call. */
    @Benchmark
    public boolean newContext(Data data) {
        return data.plan.isValid(data.node);
    }

    /** New API: reuses immutable settings and the scalar scratch wrapper. */
    @Benchmark
    public boolean reusableContext(Data data) {
        return data.plan.evaluate(data.node, data.context);
    }
}
