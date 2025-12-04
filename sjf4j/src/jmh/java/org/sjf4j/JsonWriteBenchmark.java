package org.sjf4j;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
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
import org.sjf4j.facades.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facades.fastjson2.Fastjson2Walker;
import org.sjf4j.facades.gson.GsonJsonFacade;
import org.sjf4j.facades.gson.GsonWalker;
import org.sjf4j.facades.jackson.JacksonJsonFacade;
import org.sjf4j.facades.jackson.JacksonWalker;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Threads(1)
@State(Scope.Thread)
public class JsonWriteBenchmark {

//    private static final String JSON_DATA = "{\"name\":\"Alice\"}";
//    private static final String JSON_DATA = "{\"age\":25}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"city\":\"Singapore\"}}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"babies\":[{\"name\":\"Baby-0\",\"age\":1}]}";
//    private static final String JSON_DATA = "{\"name\":\"Alice\",\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":\"Singapore\"}}";
    // Mixed structure JSON keeps nested objects/arrays so each framework covers the same workload.
    private static final String JSON_DATA = "{\"name\":\"Alice\",\"no_way\":99,\"age\":30,\"info\":{\"email\":\"alice@example.com\",\"city\":\"Singapore\"},\"babies\":[{\"name\":\"Baby-0\",\"age\":1},{\"name\":\"Baby-1\",\"age\":2},{\"name\":\"Baby-2\",\"age\":3}]}";
    private static final Person PERSON = Sjf4j.fromJson(JSON_DATA, Person.class);

    private static final ObjectMapper JACKSON = new ObjectMapper();
    private static final GsonBuilder GSON_BUILDER = new GsonBuilder();
    private static final Gson GSON = GSON_BUILDER.create();

    private static final JacksonJsonFacade JACKSON_FACADE = new JacksonJsonFacade(JACKSON);
    private static final GsonJsonFacade GSON_FACADE = new GsonJsonFacade(GSON_BUILDER);
    private static final Fastjson2JsonFacade FASTJSON2_FACADE = new Fastjson2JsonFacade();


    @Param({"STREAMING_GENERAL", "STREAMING_SPECIFIC", "USE_MODULE"})
    public String writeMode;

    @Setup(Level.Trial)
    public void setup() {
        JsonConfig.global(new JsonConfig.Builder().writeMode(JsonConfig.WriteMode.valueOf(writeMode)).build());
    }



    // ----- Jackson baselines -----
    @Benchmark
    public Object write_jackson_native() throws IOException {
        StringWriter output = new StringWriter();
        JACKSON.writeValue(output, PERSON);
        return output.toString();
    }

    @Benchmark
    public Object write_jackson_jojo() throws IOException {
        StringWriter output = new StringWriter();
        JACKSON_FACADE.writeNode(output, PERSON);
        return output.toString();
    }

//    @Benchmark
//    public Object write_jackson_general() throws IOException {
//        StringWriter output = new StringWriter();
//        JACKSON_FACADE.writeNodeWithGeneral(output, PERSON);
//        return output.toString();
//    }
//
//    @Benchmark
//    public Object write_jackson_module() throws IOException {
//        StringWriter output = new StringWriter();
//        JACKSON_FACADE.writeNodeWithModule(output, PERSON);
//        return output.toString();
//    }


    // ----- Gson baselines -----
    @Benchmark
    public Object write_gson_native() {
        StringWriter output = new StringWriter();
        GSON.toJson(PERSON);
        return output.toString();
    }

    @Benchmark
    public Object write_gson_jojo() {
        StringWriter output = new StringWriter();
        GSON_FACADE.writeNode(output, PERSON);
        return output.toString();
    }

//    @Benchmark
//    public Object write_gson_spec() {
//        StringWriter output = new StringWriter();
//        GSON_FACADE.writeNodeWithSpecific(output, PERSON);
//        return output.toString();
//    }
//
//    @Benchmark
//    public Object write_gson_module() {
//        StringWriter output = new StringWriter();
//        GSON_FACADE.writeNodeWithModule(output, PERSON);
//        return output.toString();
//    }

    // ----- Fastjson2 baselines -----
    @Benchmark
    public Object write_fastjson2_native() {
        StringWriter output = new StringWriter();
        JSON.toJSONString(PERSON);
        return output.toString();
    }

    @Benchmark
    public Object write_fastjson2_jojo() {
        StringWriter output = new StringWriter();
        FASTJSON2_FACADE.writeNode(output, PERSON);
        return output.toString();
    }



    // --------- 模拟的 POJO ------------
    // Extend JsonObject so every framework can reuse the same helper methods when populating nested structures.
    public static class Person extends JsonObject {
        public String name;
        public String nick;
        public int age;
        public Info info;
        public List<Baby> babies;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNick() {
            return nick;
        }

        public void setNick(String nick) {
            this.nick = nick;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public Info getInfo() {
            return info;
        }

        public void setInfo(Info info) {
            this.info = info;
        }

        public List<Baby> getBabies() {
            return babies;
        }

        public void setBabies(List<Baby> babies) {
            this.babies = babies;
        }
    }

    @Getter @Setter
    public static class Info extends JsonObject {
        public String email;
        public String city;
    }

    @Getter @Setter
    public static class Baby extends JsonObject {
        public String name;
//        public int age;
    }


    ///

//    @Benchmark
//    public Object fastjson2_pojo2() {
////        return JSONReader.of(new StringReader(JSON_DATA)).read(Person.class);
//        final JSONReader.Context context = JSONFactory.createReadContext(new JSONReader.Feature[]{});
//        final ObjectReader<Object> objectReader = context.getObjectReader(Person.class);
//        JSONReader reader = JSONReader.of(new StringReader(JSON_DATA));
//        Object object = objectReader.readObject(reader, Person.class, null, 0);
//        return object;
//
//    }

//    @Benchmark
//    public Object fastjson2_pojo3() {
//        final JSONReader.Context context = JSONFactory.createReadContext(new JSONReader.Feature[]{});
//        final ObjectReader<Object> objectReader = context.getObjectReader(Person.class);
//        try (JSONReader reader = JSONReader.of(new StringReader(JSON_DATA), context)) {
//            if (reader.isEnd()) {
//                return null;
//            }
//            Object object = objectReader.readObject(reader, Person.class, null, 0);
//            return object;
//        }
//    }

}