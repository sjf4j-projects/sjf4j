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
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.ValidationOptions;

import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 15, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Threads(1)
@State(Scope.Thread)
public class SchemaBenchmark {

    private static final String OBJECT_SCHEMA_JSON =
            "{" +
            "   \"type\":\"object\"," +
            "   \"required\":[\"id\",\"name\",\"tags\",\"attrs\"]," +
            "   \"properties\":{" +
            "       \"id\":{\"type\":\"integer\",\"minimum\":1}," +
            "       \"name\":{\"type\":\"string\",\"minLength\":2,\"maxLength\":32,\"pattern\":\"^[A-Za-z0-9_\\\\- ]+$\"}," +
            "       \"active\":{\"type\":\"boolean\"}," +
            "       \"tags\":{\"type\":\"array\",\"minItems\":1,\"items\":{\"type\":\"string\"},\"uniqueItems\":true}," +
            "       \"attrs\":{\"type\":\"object\",\"minProperties\":1,\"maxProperties\":4," +
            "       \"patternProperties\":{\"^x-\":{\"type\":\"string\"}}," +
            "       \"additionalProperties\":false}" +
            "   }," +
            "   \"additionalProperties\":false," +
            "   \"dependentRequired\":{\"active\":[\"name\"]}," +
            "   \"if\":{\"properties\":{\"active\":{\"const\":true}}}," +
            "   \"then\":{\"required\":[\"tags\"]}," +
            "   \"else\":{\"required\":[\"attrs\"]}" +
            "}";

    private static final String ARRAY_SCHEMA_JSON =
            "{" +
            "\"type\":\"array\"," +
            "\"minItems\":3," +
            "\"maxItems\":100," +
            "\"uniqueItems\":true," +
            "\"items\":{\"type\":\"number\",\"minimum\":0}," +
            "\"contains\":{\"type\":\"number\",\"minimum\":10}," +
            "\"minContains\":1," +
            "\"maxContains\":3" +
            "}";

    private static final String STRING_SCHEMA_JSON =
            "{" +
            "\"type\":\"string\"," +
            "\"minLength\":5," +
            "\"maxLength\":64," +
            "\"pattern\":\"^[a-z0-9._%+-]+@[a-z0-9.-]+\\\\.[a-z]{2,}$\"," +
            "\"format\":\"email\"" +
            "}";

    private static final String NUMBER_SCHEMA_JSON =
            "{" +
            "\"type\":\"number\"," +
            "\"minimum\":10," +
            "\"maximum\":1000," +
            "\"multipleOf\":2" +
            "}";

    private static final String OBJECT_NODE_JSON =
            "{" +
            "\"id\":10," +
            "\"name\":\"user_10\"," +
            "\"active\":true," +
            "\"tags\":[\"alpha\",\"beta\",\"gamma\"]," +
            "\"attrs\":{\"x-a\":\"1\",\"x-b\":\"2\"}" +
            "}";

    private static final String ARRAY_NODE_JSON =
            "[1,2,10,20,30,4,5]";

    @State(Scope.Thread)
    public static class SchemaState {
        @Param({"true", "false"})
        public String failFast;

        @Param({"true", "false"})
        public String strictFormat;

        public ValidationOptions options;

        public JsonSchema objectSchema;
        public JsonSchema arraySchema;
        public JsonSchema stringSchema;
        public JsonSchema numberSchema;

        public Object objectNode;
        public Object arrayNode;
        public String stringNode;
        public Number numberNode;

        @Setup(Level.Trial)
        public void setup() {
            options = new ValidationOptions.Builder()
                    .failFast(Boolean.parseBoolean(failFast))
                    .strictFormats(Boolean.parseBoolean(strictFormat))
                    .build();

            objectSchema = JsonSchema.fromJson(OBJECT_SCHEMA_JSON);
            objectSchema.compile();

            arraySchema = JsonSchema.fromJson(ARRAY_SCHEMA_JSON);
            arraySchema.compile();

            stringSchema = JsonSchema.fromJson(STRING_SCHEMA_JSON);
            stringSchema.compile();

            numberSchema = JsonSchema.fromJson(NUMBER_SCHEMA_JSON);
            numberSchema.compile();

            objectNode = Sjf4j.fromJson(OBJECT_NODE_JSON);
            arrayNode = Sjf4j.fromJson(ARRAY_NODE_JSON);
            stringNode = "alice@example.com";
            numberNode = 84;
        }
    }

    @Benchmark
    public Object schema_compile_object() {
        JsonSchema schema = JsonSchema.fromJson(OBJECT_SCHEMA_JSON);
        schema.compile();
        return schema;
    }

    @Benchmark
    public Object schema_validate_object(SchemaState state) {
        return state.objectSchema.validate(state.objectNode, state.options);
    }

    @Benchmark
    public Object schema_validate_array(SchemaState state) {
        return state.arraySchema.validate(state.arrayNode, state.options);
    }

    @Benchmark
    public Object schema_validate_string(SchemaState state) {
        return state.stringSchema.validate(state.stringNode, state.options);
    }

    @Benchmark
    public Object schema_validate_number(SchemaState state) {
        return state.numberSchema.validate(state.numberNode, state.options);
    }

}
