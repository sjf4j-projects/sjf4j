package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.backend.jackson2.binding.Jackson2Parser;
import org.sjf4j.backend.jackson3.binding.Jackson3Parser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Flat scalar Map/List diagnostic. Native and SJF4J Raw use exactly the same
 * JsonFactory instance, parser input bytes/chars, and close behavior.
 *
 * No nested data. Compares Jackson databind, direct native token loops,
 * SJF4J Raw with/without EOF verification. Report both latency and B/op.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 160, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 160, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class JacksonFlatRawExperiment {
    @Param({"j2", "j3"}) public String backend;
    @Param({"map", "list"}) public String shape;
    @Param({"string", "utf8"}) public String input;
    @Param({"1", "8", "32"}) public int count;

    private final ObjectMapper mapper2 = new ObjectMapper();
    private final JsonMapper mapper3 = new JsonMapper();
    private com.fasterxml.jackson.core.JsonFactory factory2;
    private tools.jackson.core.json.JsonFactory factory3;
    private String json;
    private byte[] bytes;
    private Class<?> target;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        factory2 = mapper2.getFactory();
        factory3 = (tools.jackson.core.json.JsonFactory) mapper3.tokenStreamFactory();
        target = "map".equals(shape) ? Map.class : List.class;
        StringBuilder sb = new StringBuilder("map".equals(shape) ? "{" : "[");
        for (int i = 0; i < count; i++) {
            if (i != 0) sb.append(',');
            if (target == Map.class) sb.append('"').append("field").append(i).append("\":");
            switch (i & 3) {
                case 0: sb.append(i); break;
                case 1: sb.append('"').append("value").append(i).append('"'); break;
                case 2: sb.append(i % 2 == 0 ? "true" : "false"); break;
                default: sb.append("null");
            }
        }
        sb.append("map".equals(shape) ? '}' : ']');
        json = sb.toString();
        bytes = json.getBytes(StandardCharsets.UTF_8);
        Object reference = nativeCursor();
        for (Object actual : new Object[] {
                nativeDirect(), nativeCursorEnd(), rawNoEnd(), rawEnd(), manualNative()
        }) {
            if (!reference.equals(actual)) {
                throw new IllegalStateException("Different result for " + backend + "/"
                        + shape + "/" + input + "/" + count + ": " + actual);
            }
        }
    }

    private JsonParser parser2() throws IOException {
        return "string".equals(input) ? factory2.createParser(json) : factory2.createParser(bytes);
    }

    private tools.jackson.core.JsonParser parser3() {
        return "string".equals(input)
                ? factory3.createParser(ObjectReadContext.empty(), json)
                : factory3.createParser(ObjectReadContext.empty(), bytes);
    }

    @Benchmark public Object nativeDirect() throws Exception {
        if ("j2".equals(backend)) {
            return "string".equals(input) ? mapper2.readValue(json, target)
                    : mapper2.readValue(bytes, target);
        }
        return "string".equals(input) ? mapper3.readValue(json, target)
                : mapper3.readValue(bytes, target);
    }

    @Benchmark public Object nativeCursor() throws Exception {
        if ("j2".equals(backend)) {
            try (JsonParser p = parser2()) { return mapper2.readValue(p, target); }
        }
        try (tools.jackson.core.JsonParser p = parser3()) {
            return mapper3.readValue(p, target);
        }
    }

    @Benchmark public Object nativeCursorEnd() throws Exception {
        if ("j2".equals(backend)) {
            try (JsonParser p = parser2()) {
                Object result = mapper2.readValue(p, target);
                if (p.nextToken() != null) throw new IOException("trailing data");
                return result;
            }
        }
        try (tools.jackson.core.JsonParser p = parser3()) {
            Object result = mapper3.readValue(p, target);
            if (p.nextToken() != null) throw new IllegalStateException("trailing data");
            return result;
        }
    }

    @Benchmark public Object rawNoEnd() throws Exception {
        if ("j2".equals(backend)) {
            try (Jackson2Parser p = new Jackson2Parser(parser2())) {
                p.start();
                return target == Map.class ? p.readRawObject() : p.readRawArray();
            }
        }
        try (Jackson3Parser p = new Jackson3Parser(parser3())) {
            p.start();
            return target == Map.class ? p.readRawObject() : p.readRawArray();
        }
    }

    @Benchmark public Object rawEnd() throws Exception {
        if ("j2".equals(backend)) {
            try (Jackson2Parser p = new Jackson2Parser(parser2())) {
                p.start();
                Object result = target == Map.class ? p.readRawObject() : p.readRawArray();
                p.end();
                return result;
            }
        }
        try (Jackson3Parser p = new Jackson3Parser(parser3())) {
            p.start();
            Object result = target == Map.class ? p.readRawObject() : p.readRawArray();
            p.end();
            return result;
        }
    }

    @Benchmark public Object manualNative() throws Exception {
        if ("j2".equals(backend)) {
            try (JsonParser p = parser2()) {
                Object result;
                if (target == Map.class) {
                    if (p.nextToken() != JsonToken.START_OBJECT) throw new IOException("object");
                    Map<String, Object> map = new LinkedHashMap<>();
                    String name;
                    while ((name = p.nextFieldName()) != null) {
                        p.nextToken();
                        map.put(name, scalar2(p));
                    }
                    result = map;
                } else {
                    if (p.nextToken() != JsonToken.START_ARRAY) throw new IOException("array");
                    List<Object> list = new ArrayList<>();
                    JsonToken t;
                    while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
                        if (t == null) throw new IOException("truncated array");
                        list.add(scalar2(p));
                    }
                    result = list;
                }
                if (p.nextToken() != null) throw new IOException("trailing data");
                return result;
            }
        }
        try (tools.jackson.core.JsonParser p = parser3()) {
            Object result;
            if (target == Map.class) {
                if (p.nextToken() != tools.jackson.core.JsonToken.START_OBJECT) {
                    throw new IllegalStateException("object");
                }
                Map<String, Object> map = new LinkedHashMap<>();
                String name;
                while ((name = p.nextName()) != null) {
                    p.nextToken();
                    map.put(name, scalar3(p));
                }
                result = map;
            } else {
                if (p.nextToken() != tools.jackson.core.JsonToken.START_ARRAY) {
                    throw new IllegalStateException("array");
                }
                List<Object> list = new ArrayList<>();
                tools.jackson.core.JsonToken t;
                while ((t = p.nextToken()) != tools.jackson.core.JsonToken.END_ARRAY) {
                    if (t == null) throw new IllegalStateException("truncated array");
                    list.add(scalar3(p));
                }
                result = list;
            }
            if (p.nextToken() != null) throw new IllegalStateException("trailing data");
            return result;
        }
    }

    private static Object scalar2(JsonParser p) throws IOException {
        switch (p.currentToken()) {
            case VALUE_STRING: return p.getText();
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT: return p.getNumberValue();
            case VALUE_TRUE: return Boolean.TRUE;
            case VALUE_FALSE: return Boolean.FALSE;
            case VALUE_NULL: return null;
            default: throw new IOException("unexpected scalar token " + p.currentToken());
        }
    }

    private static Object scalar3(tools.jackson.core.JsonParser p) {
        switch (p.currentToken()) {
            case VALUE_STRING: return p.getString();
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT: return p.getNumberValue();
            case VALUE_TRUE: return Boolean.TRUE;
            case VALUE_FALSE: return Boolean.FALSE;
            case VALUE_NULL: return null;
            default: throw new IllegalStateException("unexpected scalar token " + p.currentToken());
        }
    }
}
