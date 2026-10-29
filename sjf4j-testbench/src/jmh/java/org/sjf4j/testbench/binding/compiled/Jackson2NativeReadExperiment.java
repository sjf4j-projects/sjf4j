package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.SerializedString;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.backend.jackson2.binding.Jackson2NameMatcher;
import org.sjf4j.backend.jackson2.binding.Jackson2Parser;
import org.sjf4j.binding.NameMatcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Isolates Jackson2 compiled-read scalar dispatch from nested POJO/ValueCodec costs.
 *
 * Both variants parse the same input, construct the same result, check the trailing
 * document token, and include parser creation/closing in measured time.
 * The adapter path mirrors ReadEmitter's generated nextNameMatch/readXxx calls.
 * The native path uses Jackson's nextFieldName(SerializedString) plus native token
 * access, without changing the owning SJF4J StreamingParser protocol.
 *
 * Run on the same JVM with:
 *   -f 2 -wi 20 -i 5 -w 400ms -r 400ms -t 1
 * Include normal/reversed, String/UTF-8, and null/unknown-property fixtures.
 * This is an experimental baseline, not an optimization to production code.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 20, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@Threads(1)
@State(Scope.Thread)
public class Jackson2NativeReadExperiment {

    private static final JsonFactory FACTORY = new JsonFactory();
    private static final NameMatcher MATCHER =
            new Jackson2NameMatcher("name", "age", "active", "score");
    private static final SerializedString[] NAMES = {
            new SerializedString("name"), new SerializedString("age"),
            new SerializedString("active"), new SerializedString("score")
    };

    @Param({"ordered", "reversed", "null_unknown"})
    public String shape;

    @Param({"string", "utf8"})
    public String input;

    private String json;
    private byte[] utf8;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        switch (shape) {
            case "ordered":
                json = "{\"name\":\"alice\",\"age\":34,\"active\":true,\"score\":98.75}";
                break;
            case "reversed":
                json = "{\"score\":98.75,\"active\":true,\"age\":34,\"name\":\"alice\"}";
                break;
            case "null_unknown":
                json = "{\"unused\":[1,2],\"name\":null,\"age\":34,\"active\":true,\"score\":98.75}";
                break;
            default:
                throw new IllegalArgumentException(shape);
        }
        utf8 = json.getBytes(StandardCharsets.UTF_8);
        Person a = readAdapter();
        Person b = readNative();
        if (!a.same(b)) {
            throw new IllegalStateException("Different results for " + shape + "/" + input);
        }
    }

    private JsonParser create() throws IOException {
        return "utf8".equals(input) ? FACTORY.createParser(utf8) : FACTORY.createParser(json);
    }

    @Benchmark
    public Person adapter() throws IOException {
        return readAdapter();
    }

    @Benchmark
    public Person nativeParser() throws IOException {
        return readNative();
    }

    private Person readAdapter() throws IOException {
        try (Jackson2Parser parser = new Jackson2Parser(create())) {
            parser.start();
            if (!parser.nextIfObjectStart()) parser.readObjectStart();
            Person value = new Person();
            int expected = 0;
            int field;
            while ((field = parser.nextNameMatch(MATCHER, expected)) != NameMatcher.OBJECT_END) {
                switch (field) {
                    case 0:
                        expected = 1;
                        value.name = parser.readString();
                        break;
                    case 1:
                        expected = 2;
                        value.age = parser.readIntValue();
                        break;
                    case 2:
                        expected = 3;
                        value.active = parser.readBooleanValue();
                        break;
                    case 3:
                        expected = 4;
                        value.score = parser.readDoubleValue();
                        break;
                    default:
                        parser.skipNode();
                }
            }
            parser.end();
            return value;
        }
    }

    private Person readNative() throws IOException {
        try (JsonParser parser = create()) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IOException("Expected START_OBJECT");
            }
            Person value = new Person();
            int expected = 0;
            while (true) {
                int field;
                if (expected < NAMES.length && parser.nextFieldName(NAMES[expected])) {
                    field = expected;
                } else {
                    if (parser.currentToken() == JsonToken.END_OBJECT) break;
                    if (parser.currentToken() != JsonToken.FIELD_NAME) {
                        throw new IOException("Expected FIELD_NAME");
                    }
                    String name = parser.currentName();
                    switch (name) {
                        case "name": field = 0; break;
                        case "age": field = 1; break;
                        case "active": field = 2; break;
                        case "score": field = 3; break;
                        default: field = -1;
                    }
                }
                switch (field) {
                    case 0: {
                        expected = 1;
                        String text = parser.nextTextValue();
                        if (text == null && parser.currentToken() != JsonToken.VALUE_NULL) {
                            throw new IOException("Expected string/null");
                        }
                        value.name = text;
                        break;
                    }
                    case 1:
                        expected = 2;
                        parser.nextToken();
                        value.age = parser.getIntValue();
                        break;
                    case 2:
                        expected = 3;
                        parser.nextToken();
                        value.active = parser.getBooleanValue();
                        break;
                    case 3:
                        expected = 4;
                        parser.nextToken();
                        value.score = parser.getDoubleValue();
                        break;
                    default:
                        parser.nextToken();
                        parser.skipChildren();
                }
            }
            if (parser.nextToken() != null) throw new IOException("Trailing tokens");
            return value;
        }
    }

    public static final class Person {
        String name;
        int age;
        boolean active;
        double score;

        boolean same(Person other) {
            return java.util.Objects.equals(name, other.name)
                    && age == other.age && active == other.active
                    && Double.compare(score, other.score) == 0;
        }
    }
}
