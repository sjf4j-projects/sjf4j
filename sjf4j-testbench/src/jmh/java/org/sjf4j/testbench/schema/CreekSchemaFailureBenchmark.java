package org.sjf4j.testbench.schema;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaDialect;
import org.sjf4j.schema.SchemaPlan;
import org.sjf4j.schema.SchemaRegistry;
import org.sjf4j.testbench.model.creek.ModelState;
import org.sjf4j.testbench.model.creek.PolyTypeA;
import org.sjf4j.testbench.model.creek.TestModel;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Negative/short-circuit cases are separate from the complete, valid Creek
 * model measurements in CreekSchemaBenchmark.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 15, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class CreekSchemaFailureBenchmark {
    private static final ObjectMapper JACKSON = new ObjectMapper();

    private SchemaPlan plan;
    private Object invalidRoot;
    private Map<String, Object> invalidMap;
    private Map<String, Object> invalidNestedMap;

    @Setup(Level.Trial)
    public void setup(ModelState state) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/schema/creek-2020-12.json")) {
            if (stream == null) throw new IllegalStateException("Creek schema fixture not found");
            plan = JsonSchema.fromJson(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .createPlan(new SchemaRegistry(SchemaDialect.DRAFT_2020_12));
        }

        TestModel model = state.model;
        byte[] bytes = JACKSON.writeValueAsBytes(model);
        Map<String, Object> data = JACKSON.readValue(bytes, new TypeReference<Map<String, Object>>() {});
        invalidRoot = "wrong";
        invalidMap = new LinkedHashMap<>(data);
        invalidMap.put("name", 123);

        invalidNestedMap = new LinkedHashMap<>(data);
        List<Object> poly = new ArrayList<>((List<?>) data.get("polymorphicTypes"));
        @SuppressWarnings("unchecked")
        Map<String, Object> first = new LinkedHashMap<>((Map<String, Object>) poly.get(0));
        first.put("@type", "not-a-valid-subtype");
        poly.set(0, first);
        invalidNestedMap.put("polymorphicTypes", poly);

        List<TestModel> invalidPojo = List.of(
                new TestModel(null, model.getDecimal(), model.getAnEnum(),
                        model.getList(), model.getPolymorphicTypes()),
                new TestModel(model.getName(), BigDecimal.ZERO, model.getAnEnum(),
                        model.getList(), model.getPolymorphicTypes()),
                new TestModel(model.getName(), model.getDecimal(), model.getAnEnum(),
                        model.getList(), List.of()),
                new TestModel(model.getName(), model.getDecimal(), model.getAnEnum(),
                        model.getList(), List.of(new PolyTypeA(null)))
        );
        CreekSchemaBenchmark.CreekValidator compiled =
                org.sjf4j.CompiledInstances.create(CreekSchemaBenchmark.CreekValidator.class);
        for (TestModel candidate : invalidPojo) {
            for (boolean strictFormat : new boolean[]{false, true}) {
                boolean expected = plan.isValid(candidate, strictFormat);
                if (expected || compiled.isValid(candidate, strictFormat) != expected) {
                    throw new IllegalStateException("Compiled Creek invalid-case mismatch");
                }
            }
        }
        if (plan.isValid(invalidRoot) || plan.isValid(invalidMap) || plan.isValid(invalidNestedMap)) {
            throw new IllegalStateException("Invalid Creek benchmark fixture passed");
        }
    }

    @Benchmark public boolean schema_is_valid_fail_root() {
        return plan.isValid(invalidRoot);
    }

    @Benchmark public org.sjf4j.schema.ValidationResult schema_validate_fail_root() {
        return plan.validate(invalidRoot, true, false);
    }

    @Benchmark public boolean schema_is_valid_fail_property() {
        return plan.isValid(invalidMap);
    }

    @Benchmark public org.sjf4j.schema.ValidationResult schema_validate_fail_property() {
        return plan.validate(invalidMap, true, false);
    }

    @Benchmark public boolean schema_is_valid_fail_nested() {
        return plan.isValid(invalidNestedMap);
    }

    @Benchmark public org.sjf4j.schema.ValidationResult schema_validate_fail_nested() {
        return plan.validate(invalidNestedMap, true, false);
    }
}
