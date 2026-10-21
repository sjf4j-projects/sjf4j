package org.sjf4j.testbench.binding.runtime;

import org.openjdk.jmh.annotations.*;
import org.sjf4j.meta.CreatorInfo;
import org.sjf4j.meta.TypeRegistry;

import java.util.concurrent.TimeUnit;

/**
 * Isolates both fixed-arity (1-5) and cached array-spreader (>5) Creator invocation. Both methods
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
    public record Six(int id, String name, long count, double value, boolean active, float ratio) {}
    public record Ten(int id, String name, long count, double value, boolean active,
                      float ratio, String note, int level, long version, double fraction) {}

    @Param({"1", "3", "5", "6", "10"})
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
            case 6:
                info = TypeRegistry.requireRegisteredPojoInfo(Six.class).creatorInfo;
                arguments = new Object[]{7, "six", 123456789L, 12.5, true, 1.25f};
                if (info.argsCreatorLambda5 != null) throw new IllegalStateException("unexpected Func5");
                break;
            case 10:
                info = TypeRegistry.requireRegisteredPojoInfo(Ten.class).creatorInfo;
                arguments = new Object[]{7, "ten", 123456789L, 12.5, true,
                        1.25f, "note", 3, 9000L, 0.125};
                if (info.argsCreatorLambda5 != null) throw new IllegalStateException("unexpected Func5");
                break;
            default:
                throw new IllegalArgumentException("arity");
        }
        Object optimized = info.newPojoWithArgs(arguments.clone());
        Object original = info.argsCreatorHandle.invokeWithArguments(arguments);
        if (!optimized.equals(original)) throw new IllegalStateException("creator results differ");
    }

    @Benchmark
    public Object cached_creator_invoke_exact() {
        return info.newPojoWithArgs(arguments);
    }

    @Benchmark
    public Object invoke_with_arguments() throws Throwable {
        return info.argsCreatorHandle.invokeWithArguments(arguments);
    }
}
