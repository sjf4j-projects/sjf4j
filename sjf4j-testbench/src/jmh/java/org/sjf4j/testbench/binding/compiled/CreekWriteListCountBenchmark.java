package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.WriteTo;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.testbench.model.creek.ModelState;
import org.sjf4j.testbench.model.creek.TestModel;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Investigates whether Creek's List<String> masks the compiled POJO advantage.
 * All variants have zero polymorphic children. Both root and standalone list
 * variants use identical string elements for native/compiled/runtime.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 7, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@Threads(1)
@State(Scope.Thread)
public class CreekWriteListCountBenchmark {

    @Param({"0", "1", "8", "34"})
    public int listCount;

    private static final ObjectMapper NATIVE = new ObjectMapper();
    private static final Jackson2Binder RUNTIME = new Jackson2Binder();
    private static final Writer COMPILED = CompiledInstances.create(Writer.class);

    private TestModel model;
    private List<String> strings;

    @Setup(Level.Trial)
    public void verify(ModelState state) throws IOException {
        TestModel fixture = state.model;
        strings = List.copyOf(fixture.getList().subList(0, listCount));
        model = new TestModel(fixture.getName(), fixture.getDecimal(),
                fixture.getAnEnum(), strings, List.of());

        verifyEqual(NATIVE.writeValueAsString(model),
                write_model_compiled(), write_model_runtime(), "model");
        verifyEqual(NATIVE.writeValueAsString(strings),
                write_list_compiled(), write_list_runtime(), "list");
    }

    private void verifyEqual(String expectedJson, String compiledJson,
                             String runtimeJson, String source) throws IOException {
        JsonNode expected = NATIVE.readTree(expectedJson);
        if (!expected.equals(NATIVE.readTree(compiledJson))
                || !expected.equals(NATIVE.readTree(runtimeJson))) {
            throw new IllegalStateException("Creek " + source
                    + " write differs at listCount=" + listCount);
        }
    }

    @Benchmark
    public String write_model_native() throws IOException {
        return NATIVE.writeValueAsString(model);
    }

    @Benchmark
    public String write_model_compiled() throws IOException {
        return COMPILED.writeModel(model);
    }

    @Benchmark
    public String write_model_runtime() {
        return RUNTIME.writeNodeAsString(model);
    }

    @Benchmark
    public String write_list_native() throws IOException {
        return NATIVE.writeValueAsString(strings);
    }

    @Benchmark
    public String write_list_compiled() throws IOException {
        return COMPILED.writeList(strings);
    }

    @Benchmark
    public String write_list_runtime() {
        return RUNTIME.writeNodeAsString(strings);
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Writer {
        @WriteTo String writeModel(TestModel model) throws IOException;
        @WriteTo String writeList(List<String> list) throws IOException;
    }
}
