package org.sjf4j;


import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Getter;
import lombok.Setter;
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
import org.sjf4j.facade.simple.SimpleJsonFacade;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

//@BenchmarkMode(Mode.AverageTime)
//@OutputTimeUnit(TimeUnit.MICROSECONDS)
//@Warmup(iterations = 10, time = 200, timeUnit = TimeUnit.MILLISECONDS)
//@Measurement(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
//@Fork(value = 1)
//@Threads(1)
//@State(Scope.Thread)
public class BindingBenchmark {

    // Define a POJO `User`
    @Getter
    @Setter
    static class User {
        String name;
        List<ReadBenchmark.User> friends;
        @JsonAnySetter
        @JsonAnyGetter
        Map<String, Object> ext = new LinkedHashMap<>();
    }

    // Define a JOJO `User2`
    @Getter @Setter
    static class User2 extends JsonObject {
        String name;
        List<ReadBenchmark.User2> friends;
    }


    private static final JsonFacade<?, ?> jsonFacade = new SimpleJsonFacade();

    private static final String JSON1 =
            "{\n" +
            "  \"name\": \"Alice\",\n" +
            "  \"friends\": [\n" +
            "    {\"name\": \"Bill\", \"active\": true },\n" +
            "    {\n" +
            "      \"name\": \"Cindy\",\n" +
            "      \"friends\": [\n" +
            "        {\"name\": \"David\"},\n" +
            "        {\"id\": 5, \"info\": \"blabla\"}\n" +
            "      ]\n" +
            "    }\n" +
            "  ],\n" +
            "  \"age\": 18\n" +
            "}\n";

//    @Param({"path_off", "path_on"})
//    public String bindingPath;

//    @Setup(Level.Trial)
//    public void setup() {
//        if (bindingPath.equals("path_on")) {
//            Sjf4jConfig.global(new Sjf4jConfig.Builder().bindingPath(true).build());
//        } else {
//            Sjf4jConfig.global(new Sjf4jConfig.Builder().bindingPath(false).build());
//        }
//    }

//    @Benchmark
//    public Object read_io() {
//        Object node = jsonFacade.readNode(JSON1, User.class);
//        return node;
//    }

}

