package org.sjf4j;

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
import org.sjf4j.path.PathSegment;
import org.sjf4j.path.PathStack;
import org.sjf4j.path.Paths;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Threads(1)
@State(Scope.Thread)
public class PathTrackingBenchmark {

    @State(Scope.Thread)
    public static class PathState {
        @Param({"8", "32", "128"})
        public int depth;

        public String[] names;
        public PathStack stack;

        @Setup(Level.Trial)
        public void setup() {
            names = new String[depth];
            for (int i = 0; i < depth; i++) {
                names[i] = "f" + i;
            }
            stack = new PathStack(Math.max(8, depth));
        }

        public PathSegment buildWithPathSegment() {
            PathSegment ps = PathSegment.Root.INSTANCE;
            for (int i = 0; i < depth; i++) {
                if ((i & 1) == 0) {
                    ps = new PathSegment.Name(ps, Map.class, names[i]);
                } else {
                    ps = new PathSegment.Index(ps, List.class, i);
                }
            }
            return ps;
        }

        public void pushToStack() {
            stack.clear();
            for (int i = 0; i < depth; i++) {
                if ((i & 1) == 0) {
                    stack.pushName(Map.class, names[i]);
                } else {
                    stack.pushIndex(List.class, i);
                }
            }
        }
    }

    @Benchmark
    public PathSegment pathsegment_build(PathState state) {
        return state.buildWithPathSegment();
    }

    @Benchmark
    public String pathsegment_build_and_render(PathState state) {
        return Paths.rootedInspect(state.buildWithPathSegment());
    }

    @Benchmark
    public int pathstack_push_only(PathState state) {
        state.pushToStack();
        return state.stack.size();
    }

    @Benchmark
    public int pathstack_push_pop_only(PathState state) {
        state.pushToStack();
        for (int i = 0; i < state.depth; i++) {
            state.stack.pop();
        }
        return state.stack.size();
    }

    @Benchmark
    public PathSegment pathstack_push_and_materialize(PathState state) {
        state.pushToStack();
        return state.stack.toPathSegment();
    }

    @Benchmark
    public String pathstack_push_materialize_and_render(PathState state) {
        state.pushToStack();
        return Paths.rootedInspect(state.stack.toPathSegment());
    }
}
