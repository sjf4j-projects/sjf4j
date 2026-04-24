package org.sjf4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.GsonBuilder;
import org.openjdk.jmh.Main;
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
import org.sjf4j.exception.BindingException;
import org.sjf4j.facade.StreamingContext;
import org.sjf4j.facade.StreamingFacade;
import org.sjf4j.facade.StreamingReader;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.gson.GsonJsonFacade;
import org.sjf4j.facade.gson.GsonModule;
import org.sjf4j.facade.jackson2.Jackson2JsonFacade;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.node.ReflectUtil;
import org.sjf4j.path.PathSegment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 8, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
public class StreamingPathSegmentOverheadBenchmark {

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{StreamingPathSegmentOverheadBenchmark.class.getName()});
    }

    private static final String FLAT_JSON =
            "{\"name\":\"Alice\",\"age\":30,\"active\":true,\"score\":88.5,\"city\":\"SG\",\"x1\":1,\"x2\":2,\"x3\":3}";

    private static final String NESTED_JSON = "{\n" +
            "  \"name\": \"Alice\",\n" +
            "  \"friends\": [\n" +
            "    {\"name\": \"Bill\", \"active\": true, \"score\": 88.5 },\n" +
            "    {\"name\": \"Eve\", \"active\": false, \"tags\": [\"x\",\"y\"] },\n" +
            "    {\n" +
            "      \"name\": \"Cindy\",\n" +
            "      \"friends\": [\n" +
            "        {\"name\": \"David\"},\n" +
            "        {\"id\": 5, \"info\": \"blabla\"},\n" +
            "        {\"name\": \"Frank\", \"friends\": [{\"name\": \"Gina\"}, {\"name\": \"Hank\"}]},\n" +
            "        {\"name\": \"Ivy\", \"meta\": {\"a\":1,\"b\":2}},\n" +
            "        {}\n" +
            "      ]\n" +
            "    },\n" +
            "    {\"name\": \"Jane\"},\n" +
            "    {\"name\": \"Kyle\", \"friends\": [{\"name\": \"Liam\"}, {\"name\": \"Mia\"}]},\n" +
            "    {\"name\": \"Nina\", \"age\": 19, \"city\": \"SG\"}\n" +
            "  ],\n" +
            "  \"age\": 18,\n" +
            "  \"city\": \"Singapore\",\n" +
            "  \"ext\": {\"k1\": 1, \"k2\": true, \"k3\": [1,2,3]},\n" +
            "  \"extra1\": 12345,\n" +
            "  \"extra2\": \"hello\",\n" +
            "  \"extra3\": {\"nested\": {\"x\": 1, \"y\": [1,2,3,4]}}\n" +
            "}\n";

    @State(Scope.Thread)
    public static class ReaderState {
        @Param({"simple", "jackson2", "gson", "fastjson2"})
        public String backend;

        @Param({"flat", "nested"})
        public String shape;

        private SimpleJsonFacade simpleFacade;
        private Jackson2JsonFacade jackson2Facade;
        private GsonJsonFacade gsonFacade;
        private Fastjson2JsonFacade fastjson2Facade;
        private String json;

        @Setup(Level.Trial)
        public void setup() {
            StreamingContext shared = new StreamingContext(StreamingContext.StreamingMode.SHARED_IO);
            simpleFacade = new SimpleJsonFacade();
            jackson2Facade = new Jackson2JsonFacade(new ObjectMapper(), shared);

            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.setNumberToNumberStrategy(new GsonModule.MyToNumberStrategy());
            gsonBuilder.setObjectToNumberStrategy(new GsonModule.MyToNumberStrategy());
            gsonBuilder.setFieldNamingStrategy(field -> {
                String name = ReflectUtil.getExplicitName(field);
                return name != null ? name : field.getName();
            });
            gsonFacade = new GsonJsonFacade(gsonBuilder, shared);

            fastjson2Facade = new Fastjson2JsonFacade(null, null, shared);

            json = "flat".equals(shape) ? FLAT_JSON : NESTED_JSON;
        }

        public StreamingReader newReader() throws IOException {
            switch (backend) {
                case "simple":
                    return simpleFacade.createReader(json);
                case "jackson2":
                    return jackson2Facade.createReader(json);
                case "gson":
                    return gsonFacade.createReader(json);
                case "fastjson2":
                    return fastjson2Facade.createReader(json);
                default:
                    throw new IllegalStateException("Unknown backend: " + backend);
            }
        }
    }

    @Benchmark
    public Object shared_raw_baseline(ReaderState state) throws IOException {
        try (StreamingReader reader = state.newReader()) {
            reader.startDocument();
            Object value = readRawBaseline(reader);
            reader.endDocument();
            return value;
        }
    }

    @Benchmark
    public Object shared_raw_pathsegment(ReaderState state) throws IOException {
        try (StreamingReader reader = state.newReader()) {
            reader.startDocument();
            Object value = readRawTracked(reader, PathSegment.Root.INSTANCE);
            reader.endDocument();
            return value;
        }
    }

    private static Object readRawTracked(StreamingReader reader, PathSegment ps) throws IOException {
        try {
            switch (reader.peekToken()) {
                case START_OBJECT:
                    return readRawObjectTracked(reader, ps);
                case START_ARRAY:
                    return readRawArrayTracked(reader, ps);
                case STRING:
                    return reader.nextString();
                case NUMBER:
                    return reader.nextNumber();
                case BOOLEAN:
                    return reader.nextBoolean();
                case NULL:
                    reader.nextNull();
                    return null;
                default:
                    throw new BindingException("Unexpected token '" + reader.peekToken() + "'", ps);
            }
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("Failed to read tracked raw node", ps, e);
        }
    }

    private static Object readRawBaseline(StreamingReader reader) throws IOException {
        try {
            switch (reader.peekToken()) {
                case START_OBJECT:
                    return readRawObjectBaseline(reader);
                case START_ARRAY:
                    return readRawArrayBaseline(reader);
                case STRING:
                    return reader.nextString();
                case NUMBER:
                    return reader.nextNumber();
                case BOOLEAN:
                    return reader.nextBoolean();
                case NULL:
                    reader.nextNull();
                    return null;
                default:
                    throw new BindingException("Unexpected token '" + reader.peekToken() + "'");
            }
        } catch (BindingException e) {
            throw e;
        } catch (Exception e) {
            throw new BindingException("Failed to read baseline raw node", e);
        }
    }

    private static Map<String, Object> readRawObjectBaseline(StreamingReader reader) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        reader.startObject();
        while (reader.peekToken() != StreamingReader.Token.END_OBJECT) {
            String key = reader.nextName();
            map.put(key, readRawBaseline(reader));
        }
        reader.endObject();
        return map;
    }

    private static List<Object> readRawArrayBaseline(StreamingReader reader) throws IOException {
        List<Object> list = new ArrayList<>();
        reader.startArray();
        while (reader.peekToken() != StreamingReader.Token.END_ARRAY) {
            list.add(readRawBaseline(reader));
        }
        reader.endArray();
        return list;
    }

    private static Map<String, Object> readRawObjectTracked(StreamingReader reader, PathSegment ps) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        reader.startObject();
        while (reader.peekToken() != StreamingReader.Token.END_OBJECT) {
            String key = reader.nextName();
            PathSegment cps = new PathSegment.Name(ps, key);
            map.put(key, readRawTracked(reader, cps));
        }
        reader.endObject();
        return map;
    }

    private static List<Object> readRawArrayTracked(StreamingReader reader, PathSegment ps) throws IOException {
        List<Object> list = new ArrayList<>();
        reader.startArray();
        for (int i = 0; reader.peekToken() != StreamingReader.Token.END_ARRAY; i++) {
            PathSegment cps = new PathSegment.Index(ps, i);
            list.add(readRawTracked(reader, cps));
        }
        reader.endArray();
        return list;
    }
}
