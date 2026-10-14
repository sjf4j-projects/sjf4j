package org.sjf4j.testbench.binding.compiled;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONWriter;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Writer;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Isolates field-name dispatch overhead. Both paths write identical UTF-8
 * JSON documents, including writer allocation and byte[] materialization.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@State(Scope.Thread)
public class Fastjson2NameDispatchBenchmark {
    private static final int COUNT = 24;
    private static final String[] NAMES = new String[COUNT];
    private static final byte[][] RAW = new byte[COUNT][];
    private static final Fastjson2CompiledName[] COMPILED = new Fastjson2CompiledName[COUNT];
    private static final Fastjson2Binder BINDER = new Fastjson2Binder();
    private static final JSONWriter.Context CONTEXT = JSONFactory.createWriteContext(JSONWriter.Feature.WriteNulls);

    static {
        for (int i = 0; i < COUNT; i++) {
            NAMES[i] = "field" + i;
            RAW[i] = ('"' + NAMES[i] + "\":").getBytes(StandardCharsets.UTF_8);
            COMPILED[i] = new Fastjson2CompiledName(NAMES[i]);
        }
    }

    @Benchmark
    public byte[] native_raw_name() {
        try (JSONWriter w = JSONWriter.ofUTF8(CONTEXT)) {
            w.startObject();
            for (int i = 0; i < COUNT; i++) {
                if (i != 0) w.writeComma();
                w.writeNameRaw(RAW[i]);
                w.writeInt32(i);
            }
            w.endObject();
            return w.getBytes();
        }
    }

    @Benchmark
    public byte[] facade_compiled_name() throws Exception {
        try (Fastjson2Writer w = BINDER.createWriterUtf8()) {
            w.startObject();
            for (int i = 0; i < COUNT; i++) {
                if (i != 0) w.separateProperty();
                w.writeName(COMPILED[i]);
                w.writeIntValue(i);
            }
            w.endObject();
            return w.toBytes();
        }
    }
}
