package org.sjf4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.GsonBuilder;
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
import org.sjf4j.annotation.node.NodeNaming;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.gson.GsonJsonFacade;
import org.sjf4j.facade.jackson.JacksonJsonFacade;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.node.NamingStrategy;

import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 4, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 6, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class NamingReadBenchmark {

    private static final String CAMEL_JSON = "{"
            + "\"userName\":\"Alice\","
            + "\"loginCount\":12,"
            + "\"createdAt\":1712345678901,"
            + "\"primaryEmail\":\"alice@example.com\","
            + "\"accountMeta\":{"
            + "\"regionCode\":\"sg\","
            + "\"riskLevel\":3,"
            + "\"tags\":[\"vip\",\"trial\"]},"
            + "\"homeAddresses\":[{"
            + "\"streetName\":\"Main Street\","
            + "\"postalCode\":\"018989\","
            + "\"buildingNo\":10},{"
            + "\"streetName\":\"River Valley\","
            + "\"postalCode\":\"248372\","
            + "\"buildingNo\":22}]}";

    private static final String SNAKE_JSON = "{"
            + "\"user_name\":\"Alice\","
            + "\"login_count\":12,"
            + "\"created_at\":1712345678901,"
            + "\"primary_email\":\"alice@example.com\","
            + "\"account_meta\":{"
            + "\"region_code\":\"sg\","
            + "\"risk_level\":3,"
            + "\"tags\":[\"vip\",\"trial\"]},"
            + "\"home_addresses\":[{"
            + "\"street_name\":\"Main Street\","
            + "\"postal_code\":\"018989\","
            + "\"building_no\":10},{"
            + "\"street_name\":\"River Valley\","
            + "\"postal_code\":\"248372\","
            + "\"building_no\":22}]}";

    public static class CamelProfile {
        public String userName;
        public int loginCount;
        public long createdAt;
        public String primaryEmail;
        public CamelMeta accountMeta;
        public List<CamelAddress> homeAddresses;
    }

    public static class CamelMeta {
        public String regionCode;
        public int riskLevel;
        public List<String> tags;
    }

    public static class CamelAddress {
        public String streetName;
        public String postalCode;
        public int buildingNo;
    }

    @NodeNaming(NamingStrategy.SNAKE_CASE)
    public static class SnakeProfile {
        public String userName;
        public int loginCount;
        public long createdAt;
        public String primaryEmail;
        public SnakeMeta accountMeta;
        public List<SnakeAddress> homeAddresses;
    }

    @NodeNaming(NamingStrategy.SNAKE_CASE)
    public static class SnakeMeta {
        public String regionCode;
        public int riskLevel;
        public List<String> tags;
    }

    @NodeNaming(NamingStrategy.SNAKE_CASE)
    public static class SnakeAddress {
        public String streetName;
        public String postalCode;
        public int buildingNo;
    }

    public SimpleJsonFacade simpleFacade;
    public JacksonJsonFacade jacksonFacade;
    public GsonJsonFacade gsonFacade;
    public Fastjson2JsonFacade fastjson2Facade;

    @Setup(Level.Trial)
    public void setup() {
        Sjf4jConfig.global(new Sjf4jConfig.Builder().build());
        simpleFacade = new SimpleJsonFacade();
        jacksonFacade = new JacksonJsonFacade(new ObjectMapper(), StreamingFacade.StreamingMode.PLUGIN_MODULE);
        gsonFacade = new GsonJsonFacade(new GsonBuilder(), StreamingFacade.StreamingMode.PLUGIN_MODULE);
        fastjson2Facade = new Fastjson2JsonFacade(StreamingFacade.StreamingMode.PLUGIN_MODULE);

        simpleFacade.readNode(CAMEL_JSON, CamelProfile.class);
        simpleFacade.readNode(SNAKE_JSON, SnakeProfile.class);
        jacksonFacade.readNode(CAMEL_JSON, CamelProfile.class);
        jacksonFacade.readNode(SNAKE_JSON, SnakeProfile.class);
        gsonFacade.readNode(CAMEL_JSON, CamelProfile.class);
        gsonFacade.readNode(SNAKE_JSON, SnakeProfile.class);
        fastjson2Facade.readNode(CAMEL_JSON, CamelProfile.class);
        fastjson2Facade.readNode(SNAKE_JSON, SnakeProfile.class);
    }

    @Benchmark
    public Object simple_camel_pojo() {
        return simpleFacade.readNode(CAMEL_JSON, CamelProfile.class);
    }

    @Benchmark
    public Object simple_snake_pojo() {
        return simpleFacade.readNode(SNAKE_JSON, SnakeProfile.class);
    }

    @Benchmark
    public Object jackson_plugin_camel_pojo() {
        return jacksonFacade.readNode(CAMEL_JSON, CamelProfile.class);
    }

    @Benchmark
    public Object jackson_plugin_snake_pojo() {
        return jacksonFacade.readNode(SNAKE_JSON, SnakeProfile.class);
    }

    @Benchmark
    public Object gson_plugin_camel_pojo() {
        return gsonFacade.readNode(CAMEL_JSON, CamelProfile.class);
    }

    @Benchmark
    public Object gson_plugin_snake_pojo() {
        return gsonFacade.readNode(SNAKE_JSON, SnakeProfile.class);
    }

    @Benchmark
    public Object fastjson2_plugin_camel_pojo() {
        return fastjson2Facade.readNode(CAMEL_JSON, CamelProfile.class);
    }

    @Benchmark
    public Object fastjson2_plugin_snake_pojo() {
        return fastjson2Facade.readNode(SNAKE_JSON, SnakeProfile.class);
    }
}
