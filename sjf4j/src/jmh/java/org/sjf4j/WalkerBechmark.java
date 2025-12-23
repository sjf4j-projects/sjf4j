package org.sjf4j;


import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.sjf4j.node.NodeWalker;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Threads(1)
public class WalkerBechmark {

    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":\"Singapore\"},\"babies\":[{\"name\":\"Baby-0\",\"age\":1},{\"name\":\"Baby-1\",\"age\":2},{\"name\":\"Baby-2\",\"age\":3}]}";
    private static final JsonObject JO = JsonObject.fromJson(JSON_DATA);

    @Benchmark
    public void walk_1(Blackhole bh) {
        // Each benchmark pre-walks 100 times to amortize JMH harness overhead; only the walker implementation differs.
        for (int i = 0; i < 100; i++) {
            NodeWalker.walk(JO, NodeWalker.Target.ANY, NodeWalker.Order.TOP_DOWN, 0,
                    (k, v) -> {
                bh.consume(k);
                return null;
            });
        }
    }


    @Benchmark
    public void walk_2(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            NodeWalker.walk2(JO, NodeWalker.Target.ANY, NodeWalker.Order.TOP_DOWN, 0,
                    (k, v) -> bh.consume(k));
        }
    }


}
