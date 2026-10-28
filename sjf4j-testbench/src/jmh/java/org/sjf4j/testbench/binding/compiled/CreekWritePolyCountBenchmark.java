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
import org.sjf4j.testbench.model.creek.PolyBase;
import org.sjf4j.testbench.model.creek.TestModel;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Creek write-only component scaling: same root POJO and string list,
 * varying only the number of polymorphic children.
 *
 * Each JMH invocation includes Jackson generator creation and String output.
 * Interpret differences against polyCount=0, not raw total cost per subtype.
 * The original Creek fixture is mixed (UUID and double subtypes).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 7, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@Threads(1)
@State(Scope.Thread)
public class CreekWritePolyCountBenchmark {

    @Param({"0", "1", "4", "8"})
    public int polyCount;

    private static final ObjectMapper NATIVE = new ObjectMapper();
    private static final Jackson2Binder RUNTIME = new Jackson2Binder();
    private static final Writer COMPILED = CompiledInstances.create(Writer.class);

    private TestModel model;

    @Setup(Level.Trial)
    public void verify(ModelState state) throws IOException {
        TestModel fixture = state.model;
        List<PolyBase> types = fixture.getPolymorphicTypes();
        model = new TestModel(fixture.getName(), fixture.getDecimal(),
                fixture.getAnEnum(), fixture.getList(), types.subList(0, polyCount));

        String expected = NATIVE.writeValueAsString(model);
        JsonNode expectedTree = NATIVE.readTree(expected);
        JsonNode compiledTree = NATIVE.readTree(write_compiled());
        JsonNode runtimeTree = NATIVE.readTree(write_runtime());

        if (!expectedTree.equals(compiledTree) || !expectedTree.equals(runtimeTree)) {
            throw new IllegalStateException("Creek write outputs differ for polyCount=" + polyCount);
        }
        if (expectedTree.get("polymorphicTypes").size() != polyCount) {
            throw new IllegalStateException("Unexpected Creek poly count " + polyCount);
        }
    }

    @Benchmark
    public String write_native() throws IOException {
        return NATIVE.writeValueAsString(model);
    }

    @Benchmark
    public String write_compiled() throws IOException {
        return COMPILED.write(model);
    }

    @Benchmark
    public String write_runtime() {
        return RUNTIME.writeNodeAsString(model);
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Writer {
        @WriteTo String write(TestModel model) throws IOException;
    }
}
