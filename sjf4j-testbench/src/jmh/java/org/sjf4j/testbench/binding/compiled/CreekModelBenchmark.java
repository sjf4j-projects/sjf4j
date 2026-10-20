package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.annotation.binding.WriteTo;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.testbench.model.creek.ModelState;
import org.sjf4j.testbench.model.creek.PolyBase;
import org.sjf4j.testbench.model.creek.PolyTypeA;
import org.sjf4j.testbench.model.creek.PolyTypeB;
import org.sjf4j.testbench.model.creek.TestModel;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Same Creek POJO, String input/output, with parsing and serialization inside each operation. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 15, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class CreekModelBenchmark {

    public static void main(String[] args) throws IOException {
        Main.main(new String[]{CreekModelBenchmark.class.getName()});
    }

    private static final ObjectMapper JACKSON_NATIVE = new ObjectMapper();
    private static final Jackson2Binder SJF4J_RUNTIME = new Jackson2Binder();
    private static final CreekCompiledBinder SJF4J_COMPILED =
            CompiledInstances.global(CreekCompiledBinder.class);

    private String json;

    @Setup(Level.Trial)
    public void verify(ModelState state) throws IOException {
        TestModel expected = state.model;
        json = JACKSON_NATIVE.writeValueAsString(expected);
        JsonNode expectedTree = JACKSON_NATIVE.readTree(json);
        List<PolyBase> types = expected.getPolymorphicTypes();
        if (!expectedTree.get("decimal").isNumber()
                || expectedTree.get("polymorphicTypes").size() != types.size()) {
            throw new IllegalStateException("Creek fixture JSON is incomplete");
        }
        for (int i = 0; i < types.size(); i++) {
            PolyBase value = types.get(i);
            String type = value instanceof PolyTypeA ? "poly-a"
                    : value instanceof PolyTypeB ? "poly-b" : null;
            if (type == null
                    || !type.equals(expectedTree.get("polymorphicTypes").get(i).path("@type").asText())) {
                throw new IllegalStateException("Creek discriminator differs at index " + i);
            }
        }

        // The mapped private @type fields are compile-time constants, so both
        // Creek subtypes must now have dedicated generated writers.
        boolean compiledA = false;
        boolean compiledB = false;
        for (java.lang.reflect.Method method : SJF4J_COMPILED.getClass().getDeclaredMethods()) {
            compiledA |= method.getName().startsWith("_writePolyTypeA");
            compiledB |= method.getName().startsWith("_writePolyTypeB");
        }
        if (!compiledA || !compiledB) {
            throw new IllegalStateException("Creek mapped subtypes did not receive compiled writers");
        }

        checkRead("native", expected, read_native());
        checkRead("runtime", expected, read_runtime());
        checkRead("compiled", expected, read_compiled());
        checkWrite("native", expectedTree, write_native(state), expected);
        checkWrite("runtime", expectedTree, write_runtime(state), expected);
        checkWrite("compiled", expectedTree, write_compiled(state), expected);
    }

    private static void checkRead(String label, TestModel expected, TestModel actual) {
        // TestModel.equals does not compare anEnum; BigDecimal.equals also checks scale.
        if (!expected.equals(actual) || expected.getAnEnum() != actual.getAnEnum()
                || !expected.getDecimal().equals(actual.getDecimal())) {
            throw new IllegalStateException("Creek read differs: " + label);
        }
    }

    private static void checkWrite(String label, JsonNode expectedTree, String output,
                                   TestModel expected) throws IOException {
        if (!expectedTree.equals(JACKSON_NATIVE.readTree(output))) {
            throw new IllegalStateException("Creek JSON differs: " + label + ": " + output);
        }
        checkRead(label + " round trip", expected, JACKSON_NATIVE.readValue(output, TestModel.class));
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

    @Benchmark
    public String write_native(ModelState state) throws IOException {
        return JACKSON_NATIVE.writeValueAsString(state.model);
    }

    @Benchmark
    public String write_runtime(ModelState state) {
        return SJF4J_RUNTIME.writeNodeAsString(state.model);
    }

    @Benchmark
    public String write_compiled(ModelState state) throws IOException {
        return SJF4J_COMPILED.write(state.model);
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface CreekCompiledBinder {
        @ReadFrom TestModel read(String json) throws IOException;
        @WriteTo String write(TestModel model) throws IOException;
    }
}
