package org.sjf4j;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.jackson.JacksonJsonFacade;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class ReadRealisticBenchmark {

//    @State(Scope.Thread)
//    public static class ScenarioState {
//        @Param({"SHARED_IO", "EXCLUSIVE_IO"})
//        public String streamingMode;
//
//        @Param({"STRING", "BYTES"})
//        public String inputMode;
//
//        @Param({"16", "256"})
//        public int orderCount;
//
//        @Param({"false"})
//        public boolean bindingPath;
//
//        public JacksonJsonFacade jacksonFacade;
//        public Fastjson2JsonFacade fastjson2Facade;
//        public String payload;
//        public byte[] payloadBytes;
//
//        @Setup(Level.Trial)
//        public void setup() {
//            Sjf4jConfig.global(new Sjf4jConfig.Builder()
//                    .streamingMode(StreamingFacade.StreamingMode.valueOf(streamingMode))
//                    .bindingPath(bindingPath)
//                    .build());
//            jacksonFacade = new JacksonJsonFacade(new ObjectMapper());
//            fastjson2Facade = new Fastjson2JsonFacade();
//            payload = buildPayload(orderCount);
//            payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
//        }
//    }
//
//    @Benchmark
//    public Object json_jackson_facade_realistic_pojo(ScenarioState state) {
//        return readWithInput(state.jacksonFacade, state, BookPojo.class);
//    }
//
//    @Benchmark
//    public Object json_jackson_facade_realistic_jojo(ScenarioState state) {
//        return readWithInput(state.jacksonFacade, state, BookJojo.class);
//    }
//
//    @Benchmark
//    public Object json_fastjson2_facade_realistic_pojo(ScenarioState state) {
//        return readWithInput(state.fastjson2Facade, state, BookPojo.class);
//    }
//
//    @Benchmark
//    public Object json_fastjson2_facade_realistic_jojo(ScenarioState state) {
//        return readWithInput(state.fastjson2Facade, state, BookJojo.class);
//    }
//
//    private static Object readWithInput(JsonFacade<?, ?> facade, ScenarioState state, Type type) {
//        if ("BYTES".equals(state.inputMode)) {
//            return facade.readNode(state.payloadBytes, type);
//        }
//        return facade.readNode(state.payload, type);
//    }
//
//    private static String buildPayload(int orderCount) {
//        StringBuilder sb = new StringBuilder(Math.max(2048, orderCount * 280));
//        sb.append('{')
//                .append("\"bookId\":\"BOOK-01\",")
//                .append("\"symbol\":\"AAPL\",")
//                .append("\"exchange\":\"XNAS\",")
//                .append("\"snapshotTime\":1739318400000,")
//                .append("\"orders\":[");
//
//        double notional = 0.0;
//        for (int i = 0; i < orderCount; i++) {
//            if (i > 0) sb.append(',');
//            double price = 100.0 + (i % 97) * 0.125;
//            int qty = 10 + (i % 20);
//            notional += price * qty;
//
//            sb.append('{')
//                    .append("\"id\":\"ORD-").append(i).append("\",")
//                    .append("\"side\":\"").append((i & 1) == 0 ? "BUY" : "SELL").append("\",")
//                    .append("\"price\":").append(price).append(',')
//                    .append("\"qty\":").append(qty).append(',')
//                    .append("\"active\":").append((i & 3) != 0).append(',')
//                    .append("\"tags\":[\"")
//                    .append((i % 3 == 0) ? "urgent" : "normal")
//                    .append("\",\"")
//                    .append((i % 2 == 0) ? "hedge" : "alpha")
//                    .append("\"],")
//                    .append("\"meta\":{")
//                    .append("\"source\":\"gw-").append(i % 4).append("\",")
//                    .append("\"latencyUs\":").append(80 + (i % 50)).append(',')
//                    .append("\"attrs\":{")
//                    .append("\"venue\":\"").append((i & 1) == 0 ? "XNYS" : "XNAS").append("\",")
//                    .append("\"lane\":").append(i % 8).append(',')
//                    .append("\"batch\":\"B").append(i / 16).append("\"")
//                    .append("}")
//                    .append("},")
//                    .append("\"unknownDetail\":{\"a\":").append(i).append(",\"b\":[1,2,3]}")
//                    .append('}');
//        }
//
//        sb.append("],")
//                .append("\"stats\":{")
//                .append("\"totalOrders\":").append(orderCount).append(',')
//                .append("\"notional\":").append(notional).append(',')
//                .append("\"flags\":[\"snapshot\",\"v2\",\"")
//                .append(orderCount >= 128 ? "heavy" : "light")
//                .append("\"]")
//                .append("},")
//                .append("\"extraObj\":{\"nested\":[{\"k\":\"v\"},{\"x\":1}]},")
//                .append("\"trace\":\"t-20260212\"")
//                .append('}');
//        return sb.toString();
//    }

    public static class BookPojo {
        public String bookId;
        public String symbol;
        public String exchange;
        public long snapshotTime;
        public List<OrderPojo> orders;
        public StatsPojo stats;
    }

    public static class OrderPojo {
        public String id;
        public String side;
        public double price;
        public int qty;
        public boolean active;
        public List<String> tags;
        public MetaPojo meta;
    }

    public static class MetaPojo {
        public String source;
        public int latencyUs;
        public Map<String, Object> attrs;
    }

    public static class StatsPojo {
        public int totalOrders;
        public double notional;
        public List<String> flags;
    }

    public static class BookJojo extends JsonObject {
        public String bookId;
        public String symbol;
        public String exchange;
        public long snapshotTime;
        public List<OrderJojo> orders;
        public StatsJojo stats;
    }

    public static class OrderJojo extends JsonObject {
        public String id;
        public String side;
        public double price;
        public int qty;
        public boolean active;
        public List<String> tags;
        public MetaJojo meta;
    }

    public static class MetaJojo extends JsonObject {
        public String source;
        public int latencyUs;
        public Map<String, Object> attrs;
    }

    public static class StatsJojo extends JsonObject {
        public int totalOrders;
        public double notional;
        public List<String> flags;
    }
}
