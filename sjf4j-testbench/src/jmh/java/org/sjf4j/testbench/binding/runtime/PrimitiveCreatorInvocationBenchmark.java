package org.sjf4j.testbench.binding.runtime;

import org.openjdk.jmh.annotations.*;
import org.sjf4j.meta.CreatorInfo;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.testbench.model.creek.PolyTypeB;

import java.util.concurrent.TimeUnit;

/** Isolate the primitive-double creator call independently of JSON decoding. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 350, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 8, time = 350, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
public class PrimitiveCreatorInvocationBenchmark {
    private CreatorInfo info;
    private Double boxed;

    @Setup(Level.Trial)
    public void setup() {
        info = TypeRegistry.registerTypeInfo(PolyTypeB.class).pojoInfo.creatorInfo;
        if (info.argsCreatorDouble1 == null) {
            throw new IllegalStateException("missing primitive double lambda");
        }
        boxed = 12.5;
        assertValue(info.newPojoWithOneArg(boxed));
        assertValue(info.newPojoWithArgs(new Object[]{boxed}));
        assertValue(info.argsCreatorDouble1.apply(12.5));
    }

    private static void assertValue(Object value) {
        if (!(value instanceof PolyTypeB) || ((PolyTypeB) value).getNum() != 12.5) {
            throw new IllegalStateException("wrong constructed value: " + value);
        }
    }

    @Benchmark
    public Object native_primitive_constructor() {
        return new PolyTypeB(boxed.doubleValue());
    }

    @Benchmark
    public Object direct_double_lambda() {
        return info.argsCreatorDouble1.apply(boxed.doubleValue());
    }

    @Benchmark
    public Object existing_method_handle_array() {
        return info.newPojoWithArgs(new Object[]{boxed});
    }

    @Benchmark
    public Object new_one_arg_creator() {
        return info.newPojoWithOneArg(boxed);
    }
}
