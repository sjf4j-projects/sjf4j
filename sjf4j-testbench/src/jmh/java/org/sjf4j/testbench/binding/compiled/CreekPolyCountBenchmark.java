package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.testbench.model.creek.ModelState;
import org.sjf4j.testbench.model.creek.PolyBase;
import org.sjf4j.testbench.model.creek.PolyTypeA;
import org.sjf4j.testbench.model.creek.PolyTypeB;
import org.sjf4j.testbench.model.creek.TestModel;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Measures the incremental cost of Creek's OneOf members independently from
 * its fixed root POJO, large string list and other scalar fields.
 *
 * Compare the slope from 0 to 8 for native/runtime/compiled. The measured
 * operation always parses the same prepared JSON for a given @Param value.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@Threads(1)
@State(Scope.Thread)
public class CreekPolyCountBenchmark {

    @Param({"0", "1", "4", "8"})
    public int polyCount;

    private static final ObjectMapper JACKSON_NATIVE = new ObjectMapper();
    private static final Jackson2Binder SJF4J_RUNTIME = new Jackson2Binder();
    private static final Reader SJF4J_COMPILED = CompiledInstances.create(Reader.class);

    private String json;

    @Setup(Level.Trial)
    public void setup(ModelState state) throws IOException {
        TestModel full = state.model;
        List<PolyBase> values = full.getPolymorphicTypes();
        TestModel expected = new TestModel(full.getName(), full.getDecimal(),
                full.getAnEnum(), full.getList(), values.subList(0, polyCount));
        json = JACKSON_NATIVE.writeValueAsString(expected);

        check(expected, read_native());
        check(expected, read_runtime());
        check(expected, read_compiled());

        // Both discriminator-first and discriminator-last use compiled subtype
        // bodies. The latter restores the raw prefix before streaming onward.
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        PolyBase first = SJF4J_COMPILED.readPoly("{\"@type\":\"poly-a\",\"id\":\"" + id + "\"}");
        PolyBase last = SJF4J_COMPILED.readPoly("{\"id\":\"" + id + "\",\"@type\":\"poly-a\"}");
        PolyBase b = SJF4J_COMPILED.readPoly("{\"@type\":\"poly-b\",\"num\":12.5}");
        if (!new PolyTypeA(id).equals(first) || !new PolyTypeA(id).equals(last)
                || !new PolyTypeB(12.5).equals(b)) {
            throw new IllegalStateException("compiled Creek OneOf reads differ");
        }
        try {
            SJF4J_COMPILED.readPoly("{\"@type\":\"poly-a\",\"id\":\"" + id
                    + "\",\"@type\":\"poly-a\"}");
            throw new IllegalStateException("duplicate discriminator was accepted");
        } catch (org.sjf4j.exception.BindingException expectedDuplicate) {
            if (!expectedDuplicate.getMessage().contains("duplicate oneOf discriminator")) {
                throw expectedDuplicate;
            }
        }

        boolean compiledA = false, compiledB = false;
        for (java.lang.reflect.Method method : SJF4J_COMPILED.getClass().getDeclaredMethods()) {
            compiledA |= method.getReturnType() == PolyTypeA.class
                    && method.getName().endsWith("_content");
            compiledB |= method.getReturnType() == PolyTypeB.class
                    && method.getName().endsWith("_content");
        }
        if (!compiledA || !compiledB) {
            throw new IllegalStateException("Creek OneOf was not compiled into subtype read bodies");
        }
    }

    private static void check(TestModel expected, TestModel actual) {
        if (!expected.equals(actual) || expected.getAnEnum() != actual.getAnEnum()
                || !expected.getDecimal().equals(actual.getDecimal())) {
            throw new IllegalStateException("Creek OneOf count read differs");
        }
    }

    @Benchmark
    public TestModel read_native() throws IOException {
        return JACKSON_NATIVE.readValue(json, TestModel.class);
    }

    @Benchmark
    public TestModel read_runtime() {
        return (TestModel) SJF4J_RUNTIME.readNode(json, TestModel.class);
    }

    @Benchmark
    public TestModel read_compiled() throws IOException {
        return SJF4J_COMPILED.read(json);
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Reader {
        @ReadFrom TestModel read(String json) throws IOException;
        @ReadFrom PolyBase readPoly(String json) throws IOException;
    }
}
