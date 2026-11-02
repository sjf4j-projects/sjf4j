package org.sjf4j.testbench.node;

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
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.Nodes;
import org.sjf4j.node.NodeWalker;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Isolates object iteration cost from schema evaluator work and regex allocations. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 4, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
public class ObjectCursorTraversalOnlyBenchmark {
    @Param({"map", "pojo", "jojo"})
    public String representation;

    private Object node;

    @Setup(Level.Trial)
    public void setup() throws ReflectiveOperationException {
        ObjectEntryTraversalBenchmark.Bean bean = new ObjectEntryTraversalBenchmark.Bean();
        if ("map".equals(representation)) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Field field : bean.getClass().getFields()) {
                map.put(field.getName(), field.get(bean));
            }
            node = map;
        } else if ("pojo".equals(representation)) {
            node = bean;
        } else if ("jojo".equals(representation)) {
            ObjectEntryTraversalBenchmark.Jojo jojo = new ObjectEntryTraversalBenchmark.Jojo();
            jojo.put("dynamic", "ok");
            node = jojo;
        } else {
            throw new IllegalArgumentException(representation);
        }

        long a = entrySet(), b = cursor();
        if (a != b) throw new IllegalStateException(representation + ": entrySet=" + a + ", cursor=" + b);
    }

    @Benchmark
    public long entrySet() {
        long result = 0;
        for (Map.Entry<String, Object> entry : Nodes.entrySetInObject(node)) {
            result += entry.getKey().length() + ((String) entry.getValue()).length();
        }
        return result;
    }

    @Benchmark
    public long cursor() {
        long result = 0;
        NodeWalker.ObjectCursor cur = NodeWalker.objectCursor(node);
        while (cur.next()) {
            result += cur.key.length() + ((String) cur.value).length();
        }
        return result;
    }
}
