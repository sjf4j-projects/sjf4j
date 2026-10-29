package org.sjf4j.testbench.schema;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaDialect;
import org.sjf4j.schema.SchemaPlan;
import org.sjf4j.schema.SchemaRegistry;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Measures end-to-end schema parse + plan construction, not instance validation. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 8, time = 350, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 350, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@State(Scope.Thread)
public class SchemaPlanCompilationBenchmark {
    private String raw;

    @Setup
    public void setup() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/schema/creek-2020-12.json")) {
            if (in == null) throw new IllegalStateException("missing Creek schema");
            raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Benchmark
    public SchemaPlan parseAndCompile() {
        return JsonSchema.fromJson(raw).createPlan(new SchemaRegistry(SchemaDialect.DRAFT_2020_12));
    }
}
