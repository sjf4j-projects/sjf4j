package org.sjf4j.testbench.schema;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.OutputFormat;

import com.networknt.schema.SpecificationVersion;
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
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.Read;
import org.sjf4j.annotation.binding.Write;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaDialect;
import org.sjf4j.schema.SchemaPlan;

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
    private JsonNode jsonNode;
    private com.networknt.schema.Schema networkntSchema;
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
                    .createPlan(new org.sjf4j.schema.SchemaRegistry(SchemaDialect.DRAFT_2020_12));
            networkntSchema = com.networknt.schema.SchemaRegistry
                    .withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(schema);
            networkntSchema.initializeValidators();
        }

        model = modelState.model;
        jsonBytes = JACKSON.writeValueAsBytes(model);
        deserializedModel = JACKSON.readValue(jsonBytes, TestModel.class);
        mapNode = JACKSON.readValue(jsonBytes, new TypeReference<Map<String, Object>>() {});
        jsonNode = JACKSON.readTree(jsonBytes);

        // Fail before measurement if the POJO/Map representations or JSON round trip
        // do not satisfy the exact schema used by the comparison project.
        plan.requireValid(model, false);
        plan.requireValid(deserializedModel, false);
        plan.requireValid(mapNode, false);
        plan.requireValid(jsonNode, false);
        CreekValidator.INSTANCE.requireValid(model);
        // The generated validator checks known polymorphic Java subtypes directly;
        // unknown subtypes and strict format checks retain the SchemaPlan fallback.
        if (!plan.isValid(model, true) || !CreekValidator.INSTANCE.isValid(model, true)
                || !CreekValidator.INSTANCE.isValid(model, false)
                || !CreekValidator.INSTANCE.isValid(deserializedModel)
                || !CreekValidator.INSTANCE.isValid(deserializedModel, false)
                || !CreekValidator.INSTANCE.isValid(mapNode)
                || !plan.isValid(jsonNode, false)
                || !networkntSchema.validate(jsonNode, OutputFormat.BOOLEAN)) {
            throw new IllegalStateException("Compiled Creek validation disagrees with the schema plan");
        }
        if (!model.equals(deserializedModel)
                || model.getAnEnum() != deserializedModel.getAnEnum()
                || !model.getDecimal().equals(deserializedModel.getDecimal())) {
            throw new IllegalStateException("Creek model differs after Jackson2 round trip");
        }

        // Verify the compiled pipeline outside the timed region, including its JSON shape.
        byte[] compiledBytes = CreekBinder.INSTANCE.writeTestModel(model);
        TestModel compiledModel = CreekBinder.INSTANCE.readTestModel(compiledBytes);
        if (!model.equals(compiledModel)
                || model.getAnEnum() != compiledModel.getAnEnum()
                || !model.getDecimal().equals(compiledModel.getDecimal())
                || !JACKSON.readTree(jsonBytes).equals(JACKSON.readTree(compiledBytes))) {
            throw new IllegalStateException("Creek model differs after CompiledBinder round trip");
        }
        plan.requireValid(compiledModel, false);
        CreekValidator.INSTANCE.requireValid(compiledModel);
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface CreekBinder {
        CreekBinder INSTANCE = CompiledInstances.create(CreekBinder.class);

        @Read
        TestModel readTestModel(byte[] input) throws IOException;

        @Write
        byte[] writeTestModel(TestModel input) throws IOException;
    }

    /**
     * Direct root and nested oneOf checks for the known Creek subtypes;
     * unsupported runtime subtypes retain a cached SchemaPlan fallback.
     * The original Creek schema is unchanged.
     */
    @CompiledValidator(fallback=true)
    public interface CreekValidator {
        CreekValidator INSTANCE = CompiledInstances.create(CreekValidator.class);

        @ValidJsonSchema(ref = "classpath:/schema/creek-2020-12.json")
        boolean isValid(TestModel value);

        @ValidJsonSchema(ref = "classpath:/schema/creek-2020-12.json")
        boolean isValid(TestModel value, boolean strictFormat);

        @ValidJsonSchema(ref = "classpath:/schema/creek-2020-12.json")
        void requireValid(TestModel value);

        @ValidJsonSchema(ref = "classpath:/schema/creek-2020-12.json")
        boolean isValid(Map<String, Object> value);
    }


    /** Strict format control matching the generated validator's fallback path. */
    @Benchmark
    public boolean pojo_isValid_runtime() {
        return plan.isValid(model, false);
    }

    /** Generated direct Creek checks with the default non-strict format behavior. */
    @Benchmark
    public boolean pojo_isValid_compiled() {
        return CreekValidator.INSTANCE.isValid(model, false);
    }

    /** Generated direct Creek checks with the default non-strict format behavior. */
    @Benchmark
    public boolean pojo_isValid_compiled_strict() {
        return CreekValidator.INSTANCE.isValid(model, true);
    }

    @Benchmark
    public void pojo_requireValid_runtime() {
        plan.requireValid(model, false);
    }

    /** Compiled fail-fast checks; failure diagnostics are cold-path only. */
    @Benchmark
    public void pojo_requireValid_compiled() {
        CreekValidator.INSTANCE.requireValid(model);
    }



    @Benchmark
    public TestModel roundtrip_jackson() throws IOException {
        byte[] bytes = JACKSON.writeValueAsBytes(model);
        return JACKSON.readValue(bytes, TestModel.class);
    }

    @Benchmark
    public TestModel roundtrip_compiled() throws IOException {
        byte[] bytes = CreekBinder.INSTANCE.writeTestModel(model);
        return CreekBinder.INSTANCE.readTestModel(bytes);
    }

    @Benchmark
    public TestModel roundtrip_isValid_compiled() throws IOException {
        byte[] bytes = CreekBinder.INSTANCE.writeTestModel(model);
        TestModel result = CreekBinder.INSTANCE.readTestModel(bytes);
        if (!CreekValidator.INSTANCE.isValid(result)) {
            throw new IllegalStateException("compiled round-trip schema validation failed");
        }
        return result;
    }

    @Benchmark
    public TestModel roundtrip_isValid_runtime() throws IOException {
        byte[] bytes = JACKSON.writeValueAsBytes(model);
        TestModel result = JACKSON.readValue(bytes, TestModel.class);
        if (!plan.isValid(result)) {
            throw new IllegalStateException("round-trip schema validation failed");
        }
        return result;
    }

    @Benchmark
    public TestModel roundtrip_requireValid_runtime() throws IOException {
        byte[] bytes = JACKSON.writeValueAsBytes(model);
        TestModel result = JACKSON.readValue(bytes, TestModel.class);
        plan.requireValid(result, false);
        return result;
    }

    @Benchmark
    public TestModel roundtrip_requireValid_compiled() throws IOException {
        byte[] bytes = CreekBinder.INSTANCE.writeTestModel(model);
        TestModel result = CreekBinder.INSTANCE.readTestModel(bytes);
        CreekValidator.INSTANCE.requireValid(result);
        return result;
    }

    /// Map

    @Benchmark
    public boolean map_isValid_runtime() {
        return plan.isValid(mapNode, false);
    }

    @Benchmark
    public boolean map_isValid_compiled() {
        return CreekValidator.INSTANCE.isValid(mapNode);
    }

    @Benchmark
    public boolean jsonNode_isValid_runtime() {
        return plan.isValid(jsonNode, false);
    }

    @Benchmark
    public boolean jsonNode_isValid_networknt() {
        return networkntSchema.validate(jsonNode, OutputFormat.BOOLEAN);
    }

    @Benchmark
    public void map_runtime_requireValid() {
        plan.requireValid(mapNode, false);
    }



}
