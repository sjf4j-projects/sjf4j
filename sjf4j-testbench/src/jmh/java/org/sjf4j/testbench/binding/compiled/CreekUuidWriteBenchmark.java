package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.ser.std.UUIDSerializer;
import org.openjdk.jmh.Main;
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
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson2.binding.Jackson2Writer;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.testbench.model.creek.ModelState;
import org.sjf4j.testbench.model.creek.PolyBase;
import org.sjf4j.testbench.model.creek.PolyTypeA;
import org.sjf4j.value.ValueInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Isolates the UUID streaming-codec call used by Creek PolyTypeA.
 *
 * Each benchmark writes the same four objects [{"@type":"poly-a","id":...}]
 * using the same Jackson2Binder writer lifecycle. Only UUID dispatch differs:
 * the generated-style ValueInfo streamWriter, Jackson2Writer.writeUuid(), or
 * the Jackson native UUIDSerializer directly. All output is verified at setup.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 15, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class CreekUuidWriteBenchmark {

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{CreekUuidWriteBenchmark.class.getName()});
    }

    private static final Jackson2Binder BINDER = new Jackson2Binder();
    private static final ValueInfo UUID_INFO =
            TypeRegistry.requireRegisteredValueInfo(UUID.class, null);
    private static final UUIDSerializer SERIALIZER = new UUIDSerializer();
    private static final SerializedString TYPE_NAME = new SerializedString("@type");
    private static final SerializedString ID_NAME = new SerializedString("id");

    private UUID[] ids;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        List<UUID> values = new ArrayList<>();
        for (PolyBase item : ModelState.TEST_MODEL.getPolymorphicTypes()) {
            if (item instanceof PolyTypeA) {
                values.add(((PolyTypeA) item).getId());
            }
        }
        ids = values.toArray(new UUID[0]);
        if (ids.length != 4 || UUID_INFO.streamWriter == null) {
            throw new IllegalStateException("Expected four PolyTypeA UUIDs and streaming codec");
        }

        String expected = write_native_serializer();
        if (!expected.equals(write_direct_writer())
                || !expected.equals(write_stream_codec())) {
            throw new IllegalStateException("UUID write variants differ");
        }
    }

    @Benchmark
    public String write_stream_codec() throws IOException {
        try (Jackson2Writer writer = BINDER.createWriter()) {
            writer.startDocument();
            JsonGenerator g = writer.nativeWriter();
            g.writeStartArray();
            for (UUID id : ids) {
                g.writeStartObject();
                g.writeFieldName(TYPE_NAME);
                g.writeString("poly-a");
                g.writeFieldName(ID_NAME);
                UUID_INFO.streamWriter.write(writer, id);
                g.writeEndObject();
            }
            g.writeEndArray();
            writer.endDocument();
            writer.flush();
            return writer.toString();
        }
    }

    @Benchmark
    public String write_direct_writer() throws IOException {
        try (Jackson2Writer writer = BINDER.createWriter()) {
            writer.startDocument();
            JsonGenerator g = writer.nativeWriter();
            g.writeStartArray();
            for (UUID id : ids) {
                g.writeStartObject();
                g.writeFieldName(TYPE_NAME);
                g.writeString("poly-a");
                g.writeFieldName(ID_NAME);
                writer.writeUuid(id);
                g.writeEndObject();
            }
            g.writeEndArray();
            writer.endDocument();
            writer.flush();
            return writer.toString();
        }
    }

    @Benchmark
    public String write_native_serializer() throws IOException {
        try (Jackson2Writer writer = BINDER.createWriter()) {
            writer.startDocument();
            JsonGenerator g = writer.nativeWriter();
            g.writeStartArray();
            for (UUID id : ids) {
                g.writeStartObject();
                g.writeFieldName(TYPE_NAME);
                g.writeString("poly-a");
                g.writeFieldName(ID_NAME);
                SERIALIZER.serialize(id, g, null);
                g.writeEndObject();
            }
            g.writeEndArray();
            writer.endDocument();
            writer.flush();
            return writer.toString();
        }
    }
}
