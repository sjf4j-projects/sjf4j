package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.RuntimeContext;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Temporary isolated performance comparison of two Jackson2 compiled string-member helper shapes.
 * All cases write the same logical values and use the same name, factory, generator and output buffer.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 4, time = 350, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 350, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
public class StringMemberShapeBenchmark {

    private static final JsonFactory FACTORY = new JsonFactory();
    private static final SerializableString NAME = new SerializedString("field");

    @Param({"true", "false"})
    public boolean includeNulls;

    @Param({"none", "half", "all"})
    public String nullPattern;

    private RuntimeContext context;
    private String[] values;

    @Setup(Level.Trial)
    public void setup() {
        context = new RuntimeContext(Map.of(), includeNulls);
        values = new String[16];
        for (int i = 0; i < values.length; i++) {
            values[i] = nullPattern.equals("all")
                    || (nullPattern.equals("half") && (i & 1) == 1) ? null : "aValue";
        }
    }

    private static void _writeString(JsonGenerator g, String value) throws IOException {
        if (value == null) {
            g.writeNull();
        } else {
            g.writeString(value);
        }
    }

    private void old(JsonGenerator g, SerializableString name, String value) throws IOException {
        if (value == null && !this.context.includeNulls) {
            return;
        }
        g.writeFieldName(name);
        _writeString(g, value);
    }

    private void split(JsonGenerator g, SerializableString name, String value) throws IOException {
        if (value == null) {
            if (this.context.includeNulls) {
                g.writeFieldName(name);
                g.writeNull();
            }
        } else {
            g.writeFieldName(name);
            g.writeString(value);
        }
    }

    @Benchmark
    public String old() throws IOException {
        StringWriter output = new StringWriter(320);
        try (JsonGenerator g = FACTORY.createGenerator(output)) {
            g.writeStartObject();
            for (String v : values) old(g, NAME, v);
            g.writeEndObject();
        }
        return output.toString();
    }

    @Benchmark
    public String split() throws IOException {
        StringWriter output = new StringWriter(320);
        try (JsonGenerator g = FACTORY.createGenerator(output)) {
            g.writeStartObject();
            for (String v : values) split(g, NAME, v);
            g.writeEndObject();
        }
        return output.toString();
    }
}
