package org.sjf4j.testbench.binding.runtime;

import org.openjdk.jmh.annotations.*;
import org.sjf4j.meta.CreatorInfo;
import org.sjf4j.meta.TypeRegistry;

import java.util.concurrent.TimeUnit;

/**
 * Isolates the generic 1-5 argument Creator invocation strategy. Both methods
 * share the same metadata and argument array; only invocation differs.
 * Deliberately tests multiple primitive/reference combinations, not a
 * backend-specific double constructor.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 8, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
public class GenericCreatorInvokeBenchmark {
    public record One(double value) {}
    public record Three(int id, String name, double value) {}
    public record Five(long count, String name, boolean active, float ratio, int id) {}

    @Param({"1", "3", "5"})
    public int arity;

    private CreatorInfo info;
    private Object[] arguments;

    @Setup(Level.Trial)
    public void setup() throws Throwable {
        switch (arity) {
            case 1:
                info = TypeRegistry.requireRegisteredPojoInfo(One.class).creatorInfo;
                arguments = new Object[]{12.5};
                if (info.argsCreatorLambda1 == null) throw new IllegalStateException("missing Func1");
                break;
            case 3:
                info = TypeRegistry.requireRegisteredPojoInfo(Three.class).creatorInfo;
                arguments = new Object[]{7, "seven", 12.5};
                if (info.argsCreatorLambda3 == null) throw new IllegalStateException("missing Func3");
                break;
            case 5:
                info = TypeRegistry.requireRegisteredPojoInfo(Five.class).creatorInfo;
                arguments = new Object[]{99L, "ninety nine", true, 1.25f, 7};
                if (info.argsCreatorLambda5 == null) throw new IllegalStateException("missing Func5");
                break;
            default:
                throw new IllegalArgumentException("arity");
        }
        Object optimized = info.newPojoWithArgs(arguments.clone());
        Object original = info.argsCreatorHandle.invokeWithArguments(arguments);
        if (!optimized.equals(original)) throw new IllegalStateException("creator results differ");
    }

    @Benchmark
    public Object generic_func_invoke_exact() {
        return info.newPojoWithArgs(arguments);
    }

    @Benchmark
    public Object invoke_with_arguments() throws Throwable {
        return info.argsCreatorHandle.invokeWithArguments(arguments);
    }
}
