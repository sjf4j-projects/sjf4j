package org.sjf4j.schema;

import org.openjdk.jmh.Main;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * End-to-end evaluation of unevaluatedProperties and unevaluatedItems.
 * The first half of each instance is explicitly evaluated; the second half
 * is validated by the unevaluated keyword. Plans and inputs are prepared
 * outside the timed methods. Run with -prof gc to compare B/op.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@State(Scope.Thread)
public class UnevaluatedBenchmark {

    @Param({"8", "64", "65", "128"})
    public int size;

    private SchemaPlan propertiesPlan;
    private SchemaPlan itemsPlan;
    private Map<String, Object> object;
    private List<Object> array;

    public static void main(String[] args) throws IOException {
        Main.main(new String[]{UnevaluatedBenchmark.class.getName(), "-prof", "gc"});
    }

    @Setup(Level.Trial)
    public void setup() {
        StringBuilder properties = new StringBuilder("{\"properties\":{");
        StringBuilder items = new StringBuilder("{\"prefixItems\":[");
        object = new LinkedHashMap<>();
        array = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            object.put("p" + i, i);
            array.add(i);
            if (i < size / 2) {
                if (i > 0) {
                    properties.append(',');
                    items.append(',');
                }
                properties.append("\"p").append(i).append("\":true");
                items.append("true");
            }
        }
        properties.append("},\"unevaluatedProperties\":{\"type\":\"integer\"}}");
        items.append("],\"unevaluatedItems\":{\"type\":\"integer\"}}");
        propertiesPlan = JsonSchema.fromJson(properties.toString()).createPlan();
        itemsPlan = JsonSchema.fromJson(items.toString()).createPlan();
        if (!propertiesPlan.validate(object).isValid() || !itemsPlan.validate(array).isValid()) {
            throw new IllegalStateException("Invalid unevaluated benchmark fixture");
        }
    }

    @Benchmark
    public ValidationResult unevaluated_properties() {
        return propertiesPlan.validate(object, true, false);
    }

    @Benchmark
    public ValidationResult unevaluated_items() {
        return itemsPlan.validate(array, true, false);
    }
}
