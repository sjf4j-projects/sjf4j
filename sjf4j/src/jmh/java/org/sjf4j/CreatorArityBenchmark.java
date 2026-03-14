package org.sjf4j;

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
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.node.NodeRegistry;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 8, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 2)
@Threads(1)
public class CreatorArityBenchmark {

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(new String[]{CreatorArityBenchmark.class.getName()});
    }

    public static class P1 extends JsonObject {
        public final String a1;
        public P1(@NodeProperty("a1") String a1) { this.a1 = a1; }
    }

    public static class P2 extends JsonObject {
        public final String a1;
        public final Object a2;
        public P2(@NodeProperty("a1") String a1, @NodeProperty("a2") Object a2) { this.a1 = a1; this.a2 = a2; }
    }

    public static class P3 extends JsonObject {
        public final String a1;
        public final Object a2;
        public final Object a3;
        public P3(@NodeProperty("a1") String a1, @NodeProperty("a2") Object a2, @NodeProperty("a3") Object a3) {
            this.a1 = a1; this.a2 = a2; this.a3 = a3;
        }
    }

    public static class P4 extends JsonObject {
        public final String a1;
        public final Object a2;
        public final Object a3;
        public final Object a4;
        public P4(@NodeProperty("a1") String a1, @NodeProperty("a2") Object a2,
                  @NodeProperty("a3") Object a3, @NodeProperty("a4") Object a4) {
            this.a1 = a1; this.a2 = a2; this.a3 = a3; this.a4 = a4;
        }
    }

    public static class P5 extends JsonObject {
        public final String a1;
        public final Object a2;
        public final Object a3;
        public final Object a4;
        public final Object a5;
        public P5(@NodeProperty("a1") String a1, @NodeProperty("a2") Object a2,
                  @NodeProperty("a3") Object a3, @NodeProperty("a4") Object a4,
                  @NodeProperty("a5") Object a5) {
            this.a1 = a1; this.a2 = a2; this.a3 = a3; this.a4 = a4; this.a5 = a5;
        }
    }

    public static class P6 extends JsonObject {
        public final String a1;
        public final Object a2;
        public final Object a3;
        public final Object a4;
        public final Object a5;
        public final Object a6;
        public P6(@NodeProperty("a1") String a1, @NodeProperty("a2") Object a2,
                  @NodeProperty("a3") Object a3, @NodeProperty("a4") Object a4,
                  @NodeProperty("a5") Object a5, @NodeProperty("a6") Object a6) {
            this.a1 = a1; this.a2 = a2; this.a3 = a3; this.a4 = a4; this.a5 = a5; this.a6 = a6;
        }
    }

    public abstract static class BaseState {
        NodeRegistry.CreatorInfo ci;
        Object[] args;

        protected abstract Class<?> modelClass();
        protected abstract Object[] initArgs();

        @Setup(Level.Trial)
        public void setup() {
            ci = NodeRegistry.registerPojoOrElseThrow(modelClass()).creatorInfo;
            args = initArgs();
        }
    }

    @State(Scope.Thread)
    public static class S1 extends BaseState {
        @Override protected Class<?> modelClass() { return P1.class; }
        @Override protected Object[] initArgs() { return new Object[]{"a1"}; }
    }

    @State(Scope.Thread)
    public static class S2 extends BaseState {
        @Override protected Class<?> modelClass() { return P2.class; }
        @Override protected Object[] initArgs() { return new Object[]{"a1", 2}; }
    }

    @State(Scope.Thread)
    public static class S3 extends BaseState {
        @Override protected Class<?> modelClass() { return P3.class; }
        @Override protected Object[] initArgs() { return new Object[]{"a1", 2, 3L}; }
    }

    @State(Scope.Thread)
    public static class S4 extends BaseState {
        @Override protected Class<?> modelClass() { return P4.class; }
        @Override protected Object[] initArgs() { return new Object[]{"a1", 2, 3L, true}; }
    }

    @State(Scope.Thread)
    public static class S5 extends BaseState {
        @Override protected Class<?> modelClass() { return P5.class; }
        @Override protected Object[] initArgs() { return new Object[]{"a1", 2, 3L, true, 5.0d}; }
    }

    @State(Scope.Thread)
    public static class S6 extends BaseState {
        @Override protected Class<?> modelClass() { return P6.class; }
        @Override protected Object[] initArgs() { return new Object[]{"a1", 2, 3L, true, 5.0d, "a6"}; }
    }

    private static Object invokeMethodHandle(NodeRegistry.CreatorInfo ci, Object[] args) {
        try {
            return ci.argsCreatorHandle.invokeWithArguments(args);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static Object invokeLambdaDirect(NodeRegistry.CreatorInfo ci, Object[] args) {
        switch (args.length) {
            case 1:
                if (ci.argsCreatorLambda1 == null) throw new IllegalStateException("Missing argsCreatorLambda1");
                return ci.argsCreatorLambda1.apply(args[0]);
            case 2:
                if (ci.argsCreatorLambda2 == null) throw new IllegalStateException("Missing argsCreatorLambda2");
                return ci.argsCreatorLambda2.apply(args[0], args[1]);
            case 3:
                if (ci.argsCreatorLambda3 == null) throw new IllegalStateException("Missing argsCreatorLambda3");
                return ci.argsCreatorLambda3.apply(args[0], args[1], args[2]);
            case 4:
                if (ci.argsCreatorLambda4 == null) throw new IllegalStateException("Missing argsCreatorLambda4");
                return ci.argsCreatorLambda4.apply(args[0], args[1], args[2], args[3]);
            case 5:
                if (ci.argsCreatorLambda5 == null) throw new IllegalStateException("Missing argsCreatorLambda5");
                return ci.argsCreatorLambda5.apply(args[0], args[1], args[2], args[3], args[4]);
            default: throw new IllegalArgumentException("Unsupported arity: " + args.length);
        }
    }

    private static Object invokeNative(Object[] args) {
        switch (args.length) {
            case 1: return new P1((String) args[0]);
            case 2: return new P2((String) args[0], args[1]);
            case 3: return new P3((String) args[0], args[1], args[2]);
            case 4: return new P4((String) args[0], args[1], args[2], args[3]);
            case 5: return new P5((String) args[0], args[1], args[2], args[3], args[4]);
            case 6: return new P6((String) args[0], args[1], args[2], args[3], args[4], args[5]);
            default: throw new IllegalArgumentException("Unsupported arity: " + args.length);
        }
    }

    @Benchmark public Object creator_native_1(S1 s) { return invokeNative(s.args); }
    @Benchmark public Object creator_methodHandle_1(S1 s) { return invokeMethodHandle(s.ci, s.args); }
    @Benchmark public Object creator_lambdaDirect_1(S1 s) { return invokeLambdaDirect(s.ci, s.args); }

    @Benchmark public Object creator_native_2(S2 s) { return invokeNative(s.args); }
    @Benchmark public Object creator_methodHandle_2(S2 s) { return invokeMethodHandle(s.ci, s.args); }
    @Benchmark public Object creator_lambdaDirect_2(S2 s) { return invokeLambdaDirect(s.ci, s.args); }

    @Benchmark public Object creator_native_3(S3 s) { return invokeNative(s.args); }
    @Benchmark public Object creator_methodHandle_3(S3 s) { return invokeMethodHandle(s.ci, s.args); }
    @Benchmark public Object creator_lambdaDirect_3(S3 s) { return invokeLambdaDirect(s.ci, s.args); }

    @Benchmark public Object creator_native_4(S4 s) { return invokeNative(s.args); }
    @Benchmark public Object creator_methodHandle_4(S4 s) { return invokeMethodHandle(s.ci, s.args); }
    @Benchmark public Object creator_lambdaDirect_4(S4 s) { return invokeLambdaDirect(s.ci, s.args); }

    @Benchmark public Object creator_native_5(S5 s) { return invokeNative(s.args); }
    @Benchmark public Object creator_methodHandle_5(S5 s) { return invokeMethodHandle(s.ci, s.args); }
    @Benchmark public Object creator_lambdaDirect_5(S5 s) { return invokeLambdaDirect(s.ci, s.args); }

    @Benchmark public Object creator_native_6(S6 s) { return invokeNative(s.args); }
    @Benchmark public Object creator_methodHandle_6(S6 s) { return invokeMethodHandle(s.ci, s.args); }
}
