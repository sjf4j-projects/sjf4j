package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.ser.std.UUIDSerializer;
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
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.WriteTo;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson2.binding.Jackson2Writer;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.testbench.model.creek.ModelState;
import org.sjf4j.testbench.model.creek.PolyBase;
import org.sjf4j.testbench.model.creek.PolyTypeA;
import org.sjf4j.testbench.model.creek.PolyTypeB;
import org.sjf4j.value.ValueInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Isolates Creek's polymorphic list from the parent TestModel.
 *
 * Compiled uses the actual APT-generated @OneOf writer.
 * Manual_codec writes the same list through Jackson's JsonGenerator while
 * retaining the registered UUID streaming codec. Manual_serializer differs
 * only in calling Jackson's UUIDSerializer for UUID values.
 *
 * The handwritten methods are diagnostic reference paths, not production
 * behavior or general replacements for @OneOf/ValueCodec.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 15, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class CreekPolyListWriteBenchmark {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectWriter NATIVE =
            MAPPER.writerFor(new TypeReference<List<PolyBase>>() {});
    private static final PolyBinder COMPILED = CompiledInstances.global(PolyBinder.class);
    private static final Jackson2Binder BINDER = new Jackson2Binder();
    private static final ValueInfo UUID_INFO =
            TypeRegistry.requireRegisteredValueInfo(java.util.UUID.class, null);
    private static final UUIDSerializer UUID_SERIALIZER = new UUIDSerializer();

    private static final SerializedString TYPE = new SerializedString("@type");
    private static final SerializedString ID = new SerializedString("id");
    private static final SerializedString NUM = new SerializedString("num");

    @Param({"full", "only_a", "only_b"})
    public String variant;

    private List<PolyBase> values;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        List<PolyBase> original = ModelState.TEST_MODEL.getPolymorphicTypes();
        if ("full".equals(variant)) {
            values = original;
        } else {
            List<PolyBase> selected = new ArrayList<>();
            for (PolyBase value : original) {
                if (("only_a".equals(variant) && value instanceof PolyTypeA)
                        || ("only_b".equals(variant) && value instanceof PolyTypeB)) {
                    selected.add(value);
                }
            }
            if (!"only_a".equals(variant) && !"only_b".equals(variant)) {
                throw new IllegalArgumentException("Unknown variant " + variant);
            }
            values = selected;
        }

        if (UUID_INFO.streamWriter == null) {
            throw new IllegalStateException("UUID streaming codec not found");
        }
        JsonNode expected = MAPPER.readTree(write_native());
        verify("compiled", expected, write_compiled());
        verify("manual_codec", expected, write_manual_codec());
        verify("manual_serializer", expected, write_manual_serializer());
    }

    private static void verify(String method, JsonNode expected, String output)
            throws IOException {
        if (!expected.equals(MAPPER.readTree(output))) {
            throw new IllegalStateException("Creek " + method + " differs: " + output);
        }
    }

    @Benchmark
    public String write_native() throws IOException {
        return NATIVE.writeValueAsString(values);
    }

    @Benchmark
    public String write_compiled() throws IOException {
        return COMPILED.write(values);
    }

    @Benchmark
    public String write_manual_codec() throws IOException {
        try (Jackson2Writer writer = BINDER.createWriter()) {
            writer.startDocument();
            JsonGenerator g = writer.nativeWriter();
            g.writeStartArray();
            for (PolyBase value : values) {
                writeOneOfCodec(writer, g, value);
            }
            g.writeEndArray();
            writer.endDocument();
            writer.flush();
            return writer.toString();
        }
    }

    private static void writeOneOfCodec(Jackson2Writer writer, JsonGenerator g,
                                        PolyBase value) throws IOException {
        if (value == null) {
            g.writeNull();
        } else if (value.getClass() == PolyTypeA.class) {
            PolyTypeA a = (PolyTypeA) value;
            g.writeStartObject();
            g.writeFieldName(TYPE);
            g.writeString("poly-a");
            g.writeFieldName(ID);
            UUID_INFO.streamWriter.write(writer, a.getId());
            g.writeEndObject();
        } else if (value.getClass() == PolyTypeB.class) {
            PolyTypeB b = (PolyTypeB) value;
            g.writeStartObject();
            g.writeFieldName(TYPE);
            g.writeString("poly-b");
            g.writeFieldName(NUM);
            g.writeNumber(b.getNum());
            g.writeEndObject();
        } else {
            throw new IllegalArgumentException("Unexpected Creek subtype: " + value.getClass());
        }
    }

    @Benchmark
    public String write_manual_serializer() throws IOException {
        try (Jackson2Writer writer = BINDER.createWriter()) {
            writer.startDocument();
            JsonGenerator g = writer.nativeWriter();
            g.writeStartArray();
            for (PolyBase value : values) {
                writeOneOfSerializer(g, value);
            }
            g.writeEndArray();
            writer.endDocument();
            writer.flush();
            return writer.toString();
        }
    }

    private static void writeOneOfSerializer(JsonGenerator g, PolyBase value)
            throws IOException {
        if (value == null) {
            g.writeNull();
        } else if (value.getClass() == PolyTypeA.class) {
            PolyTypeA a = (PolyTypeA) value;
            g.writeStartObject();
            g.writeFieldName(TYPE);
            g.writeString("poly-a");
            g.writeFieldName(ID);
            UUID_SERIALIZER.serialize(a.getId(), g, null);
            g.writeEndObject();
        } else if (value.getClass() == PolyTypeB.class) {
            PolyTypeB b = (PolyTypeB) value;
            g.writeStartObject();
            g.writeFieldName(TYPE);
            g.writeString("poly-b");
            g.writeFieldName(NUM);
            g.writeNumber(b.getNum());
            g.writeEndObject();
        } else {
            throw new IllegalArgumentException("Unexpected Creek subtype: " + value.getClass());
        }
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface PolyBinder {
        @WriteTo String write(List<PolyBase> values) throws IOException;
    }

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{CreekPolyListWriteBenchmark.class.getName()});
    }
}
