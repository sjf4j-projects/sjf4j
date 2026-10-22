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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.CompiledInstances;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.testbench.model.creek.ModelState;
import org.sjf4j.testbench.model.creek.PolyBase;
import org.sjf4j.testbench.model.creek.PolyTypeA;
import org.sjf4j.testbench.model.creek.PolyTypeB;
import org.sjf4j.testbench.model.creek.TestModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Isolates the cost of the Creek string list and polymorphic list.
 *
 * All variants serialize the same TestModel type using the exact compiled
 * binder implementation measured in CreekModelBenchmark. Variants are built
 * and checked outside measurement; benchmark methods contain no branching.
 *
 * Default variants:
 * - full:          34 strings, 4 PolyTypeA (UUID) and 4 PolyTypeB (double)
 * - no_strings:    empty string list, full polymorphic list
 * - no_polymorphic: full string list, empty polymorphic list
 * - empty_lists:   both lists empty
 *
 * Additional -p variant=only_a,only_b isolates each polymorphic subtype.
 * Compare compiled/native gaps within each variant, not just raw cost deltas.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 15, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class CreekWriteBreakdownBenchmark {

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{CreekWriteBreakdownBenchmark.class.getName()});
    }

    private static final ObjectMapper NATIVE = new ObjectMapper();
    private static final Jackson2Binder RUNTIME = new Jackson2Binder();
    private static final CreekModelBenchmark.CreekCompiledBinder COMPILED =
            CompiledInstances.global(CreekModelBenchmark.CreekCompiledBinder.class);

    @Param({"full", "no_strings", "no_polymorphic", "empty_lists"})
    public String variant;

    private TestModel model;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        TestModel original = ModelState.TEST_MODEL;
        List<String> strings = original.getList();
        List<PolyBase> polymorphic = original.getPolymorphicTypes();

        switch (variant) {
            case "full":
                break;
            case "no_strings":
                strings = List.of();
                break;
            case "no_polymorphic":
                polymorphic = List.of();
                break;
            case "empty_lists":
                strings = List.of();
                polymorphic = List.of();
                break;
            case "only_a":
            case "only_b":
                List<PolyBase> filtered = new ArrayList<>();
                for (PolyBase element : polymorphic) {
                    if (("only_a".equals(variant) && element instanceof PolyTypeA)
                            || ("only_b".equals(variant) && element instanceof PolyTypeB)) {
                        filtered.add(element);
                    }
                }
                polymorphic = filtered;
                break;
            default:
                throw new IllegalArgumentException("Unknown Creek variant: " + variant);
        }

        model = new TestModel(original.getName(), original.getDecimal(),
                original.getAnEnum(), strings, polymorphic);

        JsonNode expected = NATIVE.readTree(write_native());
        verify("compiled", expected, write_compiled());
        verify("runtime", expected, write_runtime());
    }

    private static void verify(String label, JsonNode expected, String json)
            throws IOException {
        JsonNode actual = NATIVE.readTree(json);
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Creek " + label + " differs: "
                    + expected + " != " + actual);
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
}
