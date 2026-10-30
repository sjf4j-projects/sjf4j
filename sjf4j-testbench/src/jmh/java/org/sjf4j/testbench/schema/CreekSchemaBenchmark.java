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
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaDialect;
import org.sjf4j.schema.SchemaPlan;
import org.sjf4j.schema.SchemaRegistry;
import org.sjf4j.testbench.model.creek.ModelState;
import org.sjf4j.testbench.model.creek.TestModel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private CreekValidator compiledValidator;
    private TestModel model;
    private TestModel deserializedModel;
    private Map<String, Object> mapNode;
    private Object invalidRoot;
    private Map<String, Object> invalidMap;
    private Map<String, Object> invalidNestedMap;
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

        compiledValidator = CompiledInstances.create(CreekValidator.class);
        model = modelState.model;
        jsonBytes = JACKSON.writeValueAsBytes(model);
        deserializedModel = JACKSON.readValue(jsonBytes, TestModel.class);
        mapNode = JACKSON.readValue(jsonBytes, new TypeReference<Map<String, Object>>() {});

        invalidRoot = new String(new char[]{'w', 'r', 'o', 'n', 'g'});
        invalidMap = new LinkedHashMap<>(mapNode);
        invalidMap.put("name", 123);

        invalidNestedMap = new LinkedHashMap<>(mapNode);
        List<Object> poly = new ArrayList<>((List<?>) mapNode.get("polymorphicTypes"));
        @SuppressWarnings("unchecked")
        Map<String, Object> first = new LinkedHashMap<>((Map<String, Object>) poly.get(0));
        first.put("@type", "not-a-valid-subtype");
        poly.set(0, first);
        invalidNestedMap.put("polymorphicTypes", poly);

        if (plan.isValid(invalidMap) || plan.isValid(invalidNestedMap) || plan.isValid(invalidRoot)) {
            throw new IllegalStateException("Invalid Creek benchmark fixtures unexpectedly passed");
        }

        // Fail before measurement if the POJO/Map representations or JSON round trip
        // do not satisfy the exact schema used by the comparison project.
        plan.requireValid(model, false);
        plan.requireValid(deserializedModel, false);
        plan.requireValid(mapNode, false);
        // The generated validator directly checks ordinary Creek properties
        // and uses a child SchemaPlan only for polymorphic oneOf items.
        if (!plan.isValid(model, true) || !compiledValidator.isValid(model)
                || !compiledValidator.isValid(model, false)
                || !compiledValidator.isValid(deserializedModel)
                || !compiledValidator.isValid(deserializedModel, false)) {
            throw new IllegalStateException("Compiled Creek validation disagrees with the strict schema plan");
        }
        if (!model.equals(deserializedModel)
                || model.getAnEnum() != deserializedModel.getAnEnum()
                || !model.getDecimal().equals(deserializedModel.getDecimal())) {
            throw new IllegalStateException("Creek model differs after Jackson2 round trip");
        }
    }

    /**
     * Creek phase 1: direct root required/additionalProperties, decimal, enum,
     * String and List checks; only the oneOf array-item branch uses a cached
     * SchemaPlan fragment. The exact original Creek schema is unchanged.
     */
    @CompiledValidator
    public interface CreekValidator {
        @ValidJsonSchema(ref = "classpath:/schema/creek-2020-12.json")
        boolean isValid(TestModel value);
        boolean isValid(TestModel value, boolean strictFormat);
    }

    /** Strict format control matching the generated validator's fallback path. */
    @Benchmark
    public boolean schema_is_valid_pojo_strict() {
        return plan.isValid(model, true);
    }

    /** Generated direct Creek checks with strict format assertion. */
    @Benchmark
    public boolean compiled_schema_is_valid_pojo() {
        return compiledValidator.isValid(model);
    }

    /** Equivalent format behavior to the original non-strict Creek baseline. */
    @Benchmark
    public boolean compiled_schema_is_valid_pojo_non_strict() {
        return compiledValidator.isValid(model, false);
    }

    @Benchmark
    public TestModel round_trip_is_valid_compiled() throws IOException {
        byte[] bytes = JACKSON.writeValueAsBytes(model);
        TestModel result = JACKSON.readValue(bytes, TestModel.class);
        if (!compiledValidator.isValid(result)) {
            throw new IllegalStateException("compiled round-trip schema validation failed");
        }
        return result;
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

    /** A root type mismatch: the simplest invalid input. */
    @Benchmark
    public boolean schema_is_valid_fail_root() {
        return plan.isValid(invalidRoot);
    }

    @Benchmark
    public org.sjf4j.schema.ValidationResult schema_validate_fail_root() {
        return plan.validate(invalidRoot, true, false);
    }

    /** Invalid ordinary property, with fast-fail diagnostics as the control. */
    @Benchmark
    public boolean schema_is_valid_fail_property() {
        return plan.isValid(invalidMap);
    }

    @Benchmark
    public org.sjf4j.schema.ValidationResult schema_validate_fail_property() {
        return plan.validate(invalidMap, true, false);
    }

    /** A malformed polymorphic child that reaches a oneOf branch. */
    @Benchmark
    public boolean schema_is_valid_fail_nested() {
        return plan.isValid(invalidNestedMap);
    }

    @Benchmark
    public org.sjf4j.schema.ValidationResult schema_validate_fail_nested() {
        return plan.validate(invalidNestedMap, true, false);
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
