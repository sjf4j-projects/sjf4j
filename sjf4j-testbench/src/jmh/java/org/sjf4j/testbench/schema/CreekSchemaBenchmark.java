package org.sjf4j.testbench.schema;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaDialect;
import org.sjf4j.schema.SchemaPlan;
import org.sjf4j.schema.SchemaRegistry;
import org.sjf4j.testbench.model.creek.ModelState;
import org.sjf4j.testbench.model.creek.TestModel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Reproduces the Draft 2020-12 Creek JSON Serde benchmark with the same TestModel,
 * schema and Jackson2 byte[] round trip.
 *
 * <p>The original operation validates the POJO twice (before and after reading).
 * The individual benchmarks separate Jackson2 serde from schema validation, and
 * compare POJO validation with validation of an equivalent Map.
 *
 * <p>All fixtures, including the compiled schema and JSON bytes, are prepared and
 * checked outside the measured methods. Format assertions are disabled, matching
 * the Creek benchmark.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 15, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class CreekSchemaBenchmark {

    private static final ObjectMapper JACKSON = new ObjectMapper();

    private SchemaPlan plan;
    private TestModel model;
    private TestModel deserializedModel;
    private Map<String, Object> mapNode;
    private byte[] jsonBytes;

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{CreekSchemaBenchmark.class.getName()});
    }

    @Setup(Level.Trial)
    public void setup(ModelState modelState) throws IOException {
        try (InputStream stream = CreekSchemaBenchmark.class.getResourceAsStream(
                "/schema/creek-2020-12.json")) {
            if (stream == null) {
                throw new IllegalStateException("Creek JSON Schema fixture not found");
            }
            String schema = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            plan = JsonSchema.fromJson(schema)
                    .createPlan(new SchemaRegistry(SchemaDialect.DRAFT_2020_12));
        }

        model = modelState.model;
        jsonBytes = JACKSON.writeValueAsBytes(model);
        deserializedModel = JACKSON.readValue(jsonBytes, TestModel.class);
        mapNode = JACKSON.readValue(jsonBytes, new TypeReference<Map<String, Object>>() {});

        // Fail before measurement if the POJO/Map representations or JSON round trip
        // do not satisfy the exact schema used by the comparison project.
        plan.requireValid(model, false);
        plan.requireValid(deserializedModel, false);
        plan.requireValid(mapNode, false);
        if (!model.equals(deserializedModel)
                || model.getAnEnum() != deserializedModel.getAnEnum()
                || !model.getDecimal().equals(deserializedModel.getDecimal())) {
            throw new IllegalStateException("Creek model differs after Jackson2 round trip");
        }
    }

    /** Boolean-only path with no ValidationResult or failure diagnostics. */
    @Benchmark
    public boolean schema_is_valid_pojo() {
        return plan.isValid(model);
    }

    @Benchmark
    public boolean schema_is_valid_map() {
        return plan.isValid(mapNode);
    }

    @Benchmark
    public TestModel round_trip_is_valid() throws IOException {
        byte[] bytes = JACKSON.writeValueAsBytes(model);
        TestModel result = JACKSON.readValue(bytes, TestModel.class);
        if (!plan.isValid(result)) {
            throw new IllegalStateException("round-trip schema validation failed");
        }
        return result;
    }

    @Benchmark
    public void schema_validate_pojo() {
        plan.requireValid(model, false);
    }

    @Benchmark
    public void schema_validate_map() {
        plan.requireValid(mapNode, false);
    }

    @Benchmark
    public TestModel jackson_round_trip() throws IOException {
        byte[] bytes = JACKSON.writeValueAsBytes(model);
        return JACKSON.readValue(bytes, TestModel.class);
    }

    @Benchmark
    public TestModel round_trip_validate() throws IOException {
        byte[] bytes = JACKSON.writeValueAsBytes(model);
        TestModel result = JACKSON.readValue(bytes, TestModel.class);
        plan.requireValid(result, false);
        return result;
    }
}
