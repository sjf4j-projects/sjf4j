package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson2.binding.Jackson2CompiledName;
import org.sjf4j.backend.jackson2.binding.Jackson2Writer;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3CompiledName;
import org.sjf4j.backend.jackson3.binding.Jackson3Writer;
import org.sjf4j.binding.FastStringWriter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Isolates Jackson's generated property-write adapter cost.
 * Same POJO-like payload, output type, field names and document lifecycle.
 * This deliberately does not compare against ObjectMapper/Blackbird: it
 * separates the adapter overhead before modifying any generated hot path.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class JacksonPropertyWriteOverheadBenchmark {
    private static final JsonFactory J2_FACTORY = new JsonFactory();
    private static final tools.jackson.core.json.JsonFactory J3_FACTORY =
            new tools.jackson.core.json.JsonFactory();
    private static final Jackson2Binder J2_BINDER = new Jackson2Binder(J2_FACTORY);
    private static final Jackson3Binder J3_BINDER = new Jackson3Binder(J3_FACTORY);
    private static final Jackson2CompiledName J2_COMPILED_ID = new Jackson2CompiledName("id");
    private static final Jackson2CompiledName J2_COMPILED_NAME = new Jackson2CompiledName("name");
    private static final Jackson3CompiledName J3_COMPILED_ID = new Jackson3CompiledName("id");
    private static final Jackson3CompiledName J3_COMPILED_NAME = new Jackson3CompiledName("name");
    private static final SerializedString J2_ID = new SerializedString("id");
    private static final SerializedString J2_NAME = new SerializedString("name");
    private static final tools.jackson.core.io.SerializedString J3_ID =
            new tools.jackson.core.io.SerializedString("id");
    private static final tools.jackson.core.io.SerializedString J3_NAME =
            new tools.jackson.core.io.SerializedString("name");

    @Benchmark
    public String jackson2_direct() throws IOException {
        try (FastStringWriter out = new FastStringWriter();
             JsonGenerator g = J2_FACTORY.createGenerator(out)) {
            g.writeStartObject();
            g.writeFieldName(J2_ID);
            g.writeNumber(42);
            g.writeFieldName(J2_NAME);
            g.writeString("sample");
            g.writeEndObject();
            g.flush();
            return out.toString();
        }
    }

    @Benchmark
    public String jackson2_adapter() throws IOException {
        try (FastStringWriter out = new FastStringWriter();
             Jackson2Writer w = J2_BINDER.createWriter(out)) {
            w.startDocument();
            w.startObject();
            w.writeName(J2_COMPILED_ID);
            w.writeIntValue(42);
            w.writeName(J2_COMPILED_NAME);
            w.writeStringValue("sample");
            w.endObject();
            w.endDocument();
            w.flush();
            return out.toString();
        }
    }

    @Benchmark
    public String jackson3_direct() throws IOException {
        try (FastStringWriter out = new FastStringWriter();
             tools.jackson.core.JsonGenerator g =
                 J3_FACTORY.createGenerator(tools.jackson.core.ObjectWriteContext.empty(), out)) {
            g.writeStartObject();
            g.writeName(J3_ID);
            g.writeNumber(42);
            g.writeName(J3_NAME);
            g.writeString("sample");
            g.writeEndObject();
            g.flush();
            return out.toString();
        }
    }

    @Benchmark
    public String jackson3_adapter() throws IOException {
        try (FastStringWriter out = new FastStringWriter();
             Jackson3Writer w = J3_BINDER.createWriter(out)) {
            w.startDocument();
            w.startObject();
            w.writeName("id");
            w.writeIntValue(42);
            w.writeName("name");
            w.writeStringValue("sample");
            w.endObject();
            w.endDocument();
            w.flush();
            return out.toString();
        }
    }

    @Benchmark
    public byte[] jackson2_utf8_direct() throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (JsonGenerator g = J2_FACTORY.createGenerator(out)) {
            g.writeStartObject();
            g.writeFieldName(J2_ID);
            g.writeNumber(42);
            g.writeFieldName(J2_NAME);
            g.writeString("sample");
            g.writeEndObject();
        }
        return out.toByteArray();
    }

    @Benchmark
    public byte[] jackson2_utf8_adapter() throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (Jackson2Writer w = J2_BINDER.createWriter(out)) {
            w.startDocument();
            w.startObject();
            w.writeName(J2_COMPILED_ID);
            w.writeIntValue(42);
            w.writeName(J2_COMPILED_NAME);
            w.writeStringValue("sample");
            w.endObject();
            w.endDocument();
        }
        return out.toByteArray();
    }

    @Benchmark
    public byte[] jackson3_utf8_direct() throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (tools.jackson.core.JsonGenerator g =
                 J3_FACTORY.createGenerator(tools.jackson.core.ObjectWriteContext.empty(), out)) {
            g.writeStartObject();
            g.writeName(J3_ID);
            g.writeNumber(42);
            g.writeName(J3_NAME);
            g.writeString("sample");
            g.writeEndObject();
        }
        return out.toByteArray();
    }

    @Benchmark
    public byte[] jackson3_utf8_adapter() throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (Jackson3Writer w = J3_BINDER.createWriter(out)) {
            w.startDocument();
            w.startObject();
            w.writeName(J3_COMPILED_ID);
            w.writeIntValue(42);
            w.writeName(J3_COMPILED_NAME);
            w.writeStringValue("sample");
            w.endObject();
            w.endDocument();
        }
        return out.toByteArray();
    }

    @Benchmark
    public byte[] jackson2_utf8_pooled_direct() throws IOException {
        com.fasterxml.jackson.core.util.BufferRecycler recycler = J2_FACTORY._getBufferRecycler();
        com.fasterxml.jackson.core.util.ByteArrayBuilder out =
                new com.fasterxml.jackson.core.util.ByteArrayBuilder(recycler);
        try {
            try (JsonGenerator g = J2_FACTORY.createGenerator(
                    out, com.fasterxml.jackson.core.JsonEncoding.UTF8)) {
                g.writeStartObject();
                g.writeFieldName(J2_ID);
                g.writeNumber(42);
                g.writeFieldName(J2_NAME);
                g.writeString("sample");
                g.writeEndObject();
            }
            return out.toByteArray();
        } finally {
            out.release();
            recycler.releaseToPool();
        }
    }

    @Benchmark
    public byte[] jackson2_utf8_pooled_adapter() throws IOException {
        com.fasterxml.jackson.core.util.BufferRecycler recycler = J2_FACTORY._getBufferRecycler();
        com.fasterxml.jackson.core.util.ByteArrayBuilder out =
                new com.fasterxml.jackson.core.util.ByteArrayBuilder(recycler);
        try {
            try (Jackson2Writer w = new Jackson2Writer(J2_FACTORY.createGenerator(
                    out, com.fasterxml.jackson.core.JsonEncoding.UTF8))) {
                w.startDocument();
                w.startObject();
                w.writeName(J2_COMPILED_ID);
                w.writeIntValue(42);
                w.writeName(J2_COMPILED_NAME);
                w.writeStringValue("sample");
                w.endObject();
                w.endDocument();
            }
            return out.toByteArray();
        } finally {
            out.release();
            recycler.releaseToPool();
        }
    }

    @Benchmark
    public byte[] jackson3_utf8_pooled_direct() throws IOException {
        tools.jackson.core.util.BufferRecycler recycler = J3_FACTORY._getBufferRecycler();
        tools.jackson.core.util.ByteArrayBuilder out =
                new tools.jackson.core.util.ByteArrayBuilder(recycler);
        try {
            try (tools.jackson.core.JsonGenerator g = J3_FACTORY.createGenerator(
                    tools.jackson.core.ObjectWriteContext.empty(), out)) {
                g.writeStartObject();
                g.writeName(J3_ID);
                g.writeNumber(42);
                g.writeName(J3_NAME);
                g.writeString("sample");
                g.writeEndObject();
            }
            return out.toByteArray();
        } finally {
            out.release();
            recycler.releaseToPool();
        }
    }

    @Benchmark
    public byte[] jackson3_utf8_pooled_adapter() throws IOException {
        tools.jackson.core.util.BufferRecycler recycler = J3_FACTORY._getBufferRecycler();
        tools.jackson.core.util.ByteArrayBuilder out =
                new tools.jackson.core.util.ByteArrayBuilder(recycler);
        try {
            try (Jackson3Writer w = new Jackson3Writer(J3_FACTORY.createGenerator(
                    tools.jackson.core.ObjectWriteContext.empty(), out))) {
                w.startDocument();
                w.startObject();
                w.writeName(J3_COMPILED_ID);
                w.writeIntValue(42);
                w.writeName(J3_COMPILED_NAME);
                w.writeStringValue("sample");
                w.endObject();
                w.endDocument();
            }
            return out.toByteArray();
        } finally {
            out.release();
            recycler.releaseToPool();
        }
    }
}
