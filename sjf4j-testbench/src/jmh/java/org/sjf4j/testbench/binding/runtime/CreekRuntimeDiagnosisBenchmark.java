package org.sjf4j.testbench.binding.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.testbench.model.creek.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.lang.reflect.Type;
import org.sjf4j.TypeReference;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Runtime-only diagnosis of the Creek workload. Same JSON and subtype,
 * selecting the concrete POJO directly versus selecting through OneOf.
 * Explicit discriminator order and incremental list sizes isolate costs.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 8, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@Threads(1)
public class CreekRuntimeDiagnosisBenchmark {
    private static final ObjectMapper JACKSON = new ObjectMapper();
    private static final Jackson2Binder RUNTIME = new Jackson2Binder();
    private static final UUID ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @Param({"0", "1", "4", "8"})
    public int polyCount;

    private static final Type POLY_LIST_TYPE = new TypeReference<List<PolyBase>>() {}.getType();
    private static final com.fasterxml.jackson.core.type.TypeReference<List<PolyBase>> JACKSON_LIST_TYPE =
            new com.fasterxml.jackson.core.type.TypeReference<List<PolyBase>>() {};
    private String listJson;
    private String creekJson;
    private byte[] creekUtf8;
    private String firstA;
    private String lastA;
    private byte[] firstABytes;
    private String firstB;

    @Setup(Level.Trial)
    public void setup(ModelState state) throws IOException {
        TestModel full = state.model;
        List<PolyBase> values = full.getPolymorphicTypes();
        TestModel reduced = new TestModel(full.getName(), full.getDecimal(),
                full.getAnEnum(), full.getList(), values.subList(0, polyCount));
        creekJson = JACKSON.writeValueAsString(reduced);
        // Preserve polymorphic @type metadata from the typed Creek property.
        // Serializing an untyped root List loses Jackson's declared PolyBase element type.
        listJson = JACKSON.readTree(creekJson).get("polymorphicTypes").toString();
        creekUtf8 = creekJson.getBytes(StandardCharsets.UTF_8);
        firstA = "{\"@type\":\"poly-a\",\"id\":\"" + ID + "\"}";
        lastA = "{\"id\":\"" + ID + "\",\"@type\":\"poly-a\"}";
        firstABytes = firstA.getBytes(StandardCharsets.UTF_8);
        firstB = "{\"@type\":\"poly-b\",\"num\":12.5}";

        verify(reduced, JACKSON.readValue(creekJson, TestModel.class),
                "native Creek");
        verify(reduced, RUNTIME.readNode(creekJson, TestModel.class),
                "runtime Creek");
        verify(reduced.getPolymorphicTypes(), RUNTIME.readNode(listJson, POLY_LIST_TYPE),
                "runtime polymorphic list");
        verify(reduced.getPolymorphicTypes(),
                JACKSON.readValue(listJson, JACKSON_LIST_TYPE),
                "native polymorphic list");
        verify(new PolyTypeA(ID), RUNTIME.readNode(firstA, PolyBase.class),
                "A first OneOf");
        verify(new PolyTypeA(ID), RUNTIME.readNode(lastA, PolyBase.class),
                "A last OneOf");
        verify(new PolyTypeA(ID), RUNTIME.readNode(firstA, PolyTypeA.class),
                "A direct");
        verify(new PolyTypeA(ID), RUNTIME.readNode(firstABytes, PolyBase.class),
                "A OneOf bytes");
        verify(new PolyTypeB(12.5), RUNTIME.readNode(firstB, PolyBase.class),
                "B first OneOf");
        verify(new PolyTypeB(12.5), RUNTIME.readNode(firstB, PolyTypeB.class),
                "B direct");
    }

    private static void verify(Object expected, Object actual, String name) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(name + ": " + actual + " != " + expected);
        }
    }

    @Benchmark public Object creek_runtime_string() {
        return RUNTIME.readNode(creekJson, TestModel.class);
    }
    @Benchmark public Object creek_runtime_utf8() {
        return RUNTIME.readNode(creekUtf8, TestModel.class);
    }
    @Benchmark public Object creek_jackson_native() throws IOException {
        return JACKSON.readValue(creekJson, TestModel.class);
    }

    @Benchmark public Object list_runtime_string() {
        return RUNTIME.readNode(listJson, POLY_LIST_TYPE);
    }
    @Benchmark public Object list_jackson_native() throws IOException {
        return JACKSON.readValue(listJson,
                JACKSON_LIST_TYPE);
    }
    @Benchmark public Object subtype_a_jackson_native() throws IOException {
        return JACKSON.readValue(firstA, PolyBase.class);
    }
    @Benchmark public Object subtype_a_jackson_direct() throws IOException {
        return JACKSON.readValue(firstA, PolyTypeA.class);
    }
    @Benchmark public Object subtype_b_jackson_native() throws IOException {
        return JACKSON.readValue(firstB, PolyBase.class);
    }
    @Benchmark public Object subtype_a_oneof_first_string() {
        return RUNTIME.readNode(firstA, PolyBase.class);
    }
    @Benchmark public Object subtype_a_direct_string() {
        return RUNTIME.readNode(firstA, PolyTypeA.class);
    }
    @Benchmark public Object subtype_a_oneof_last_string() {
        return RUNTIME.readNode(lastA, PolyBase.class);
    }
    @Benchmark public Object subtype_a_oneof_first_utf8() {
        return RUNTIME.readNode(firstABytes, PolyBase.class);
    }
    @Benchmark public Object subtype_a_direct_utf8() {
        return RUNTIME.readNode(firstABytes, PolyTypeA.class);
    }
    @Benchmark public Object subtype_b_oneof_first_string() {
        return RUNTIME.readNode(firstB, PolyBase.class);
    }
    @Benchmark public Object subtype_b_direct_string() {
        return RUNTIME.readNode(firstB, PolyTypeB.class);
    }
}
