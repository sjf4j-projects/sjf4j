package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.backend.jackson2.binding.Jackson2Generator;
import org.sjf4j.binding.CompiledName;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Isolates Jackson2Generator delegation overhead from native JsonGenerator calls.
 * Both paths construct a fresh output and JsonGenerator, use identical field
 * order and field-name representation, close the generator, and materialize output.
 * Pooled buffers and codec/POJO-generated code are deliberately excluded.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 20, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@Threads(1)
@State(Scope.Thread)
public class Jackson2GeneratorWriteExperiment {
    private static final JsonFactory FACTORY = new JsonFactory();
    private static final String[] NAMES = {"name", "age", "active", "score"};
    private static final SerializedString[] SERIALIZED = {
            new SerializedString("name"), new SerializedString("age"),
            new SerializedString("active"), new SerializedString("score")
    };
    private static final CompiledName[] COMPILED = {
            new org.sjf4j.backend.jackson2.binding.Jackson2CompiledName("name"),
            new org.sjf4j.backend.jackson2.binding.Jackson2CompiledName("age"),
            new org.sjf4j.backend.jackson2.binding.Jackson2CompiledName("active"),
            new org.sjf4j.backend.jackson2.binding.Jackson2CompiledName("score")
    };

    @Param({"string", "utf8"})
    public String output;

    @Param({"plain", "nullable"})
    public String shape;

    @Setup(Level.Trial)
    public void verify() throws IOException {
        String adapter = writeAdapter();
        String nativeResult = writeNative();
        if (!adapter.equals(nativeResult)) {
            throw new IllegalStateException("JSON mismatch: " + adapter + " vs " + nativeResult);
        }
    }

    @Benchmark
    public String adapter() throws IOException {
        return writeAdapter();
    }

    @Benchmark
    public String nativeGenerator() throws IOException {
        return writeNative();
    }

    private String writeAdapter() throws IOException {
        if ("utf8".equals(output)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(128);
            try (Jackson2Generator g = new Jackson2Generator(FACTORY.createGenerator(bytes))) {
                emitAdapter(g);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
        StringWriter chars = new StringWriter(128);
        try (Jackson2Generator g = new Jackson2Generator(FACTORY.createGenerator(chars))) {
            emitAdapter(g);
        }
        return chars.toString();
    }

    private String writeNative() throws IOException {
        if ("utf8".equals(output)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(128);
            try (JsonGenerator g = FACTORY.createGenerator(bytes)) {
                emitNative(g);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
        StringWriter chars = new StringWriter(128);
        try (JsonGenerator g = FACTORY.createGenerator(chars)) {
            emitNative(g);
        }
        return chars.toString();
    }

    private void emitAdapter(Jackson2Generator g) throws IOException {
        g.writeObjectStart();
        g.writeName(COMPILED[0]);
        if ("nullable".equals(shape)) g.writeNull();
        else g.writeStringValue("alice");
        g.writeName(COMPILED[1]);
        g.writeIntValue(34);
        g.writeName(COMPILED[2]);
        g.writeBooleanValue(true);
        g.writeName(COMPILED[3]);
        g.writeDoubleValue(98.75);
        g.writeObjectEnd();
    }

    private void emitNative(JsonGenerator g) throws IOException {
        g.writeStartObject();
        g.writeFieldName(SERIALIZED[0]);
        if ("nullable".equals(shape)) g.writeNull();
        else g.writeString("alice");
        g.writeFieldName(SERIALIZED[1]);
        g.writeNumber(34);
        g.writeFieldName(SERIALIZED[2]);
        g.writeBoolean(true);
        g.writeFieldName(SERIALIZED[3]);
        g.writeNumber(98.75);
        g.writeEndObject();
    }
}
