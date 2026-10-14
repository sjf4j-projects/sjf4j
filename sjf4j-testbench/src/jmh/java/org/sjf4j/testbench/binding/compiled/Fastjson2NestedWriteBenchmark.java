package org.sjf4j.testbench.binding.compiled;

import com.alibaba.fastjson2.JSONWriter;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Writer;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Isolates List<String> and nested POJO writer cost.
 * All paths construct a UTF-8 writer and return its completed byte[].
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@State(Scope.Thread)
public class Fastjson2NestedWriteBenchmark {
    private static final Fastjson2Binder BINDER = new Fastjson2Binder();
    private static final User USER;

    static {
        try {
            USER = new ObjectMapper().readValue(CompiledReadBenchmark.JSON_DATA, User.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Benchmark
    public byte[] list_facade_loop() throws Exception {
        try (Fastjson2Writer w = BINDER.createWriterUtf8()) {
            List<String> list = USER.getTags();
            if (list == null) {
                w.writeNull();
            } else {
                w.startArray();
                int i = 0;
                for (String element : list) {
                    if (i++ != 0) w.separateElement();
                    w.writeString(element);
                }
                w.endArray();
            }
            return w.toBytes();
        }
    }

    @Benchmark
    public byte[] list_native_loop() {
        try (JSONWriter w = JSONWriter.ofUTF8()) {
            List<String> list = USER.getTags();
            if (list == null) {
                w.writeNull();
            } else {
                w.startArray();
                int i = 0;
                for (String element : list) {
                    if (i++ != 0) w.writeComma();
                    w.writeString(element);
                }
                w.endArray();
            }
            return w.getBytes();
        }
    }

    @Benchmark
    public byte[] list_native_collection() {
        try (JSONWriter w = JSONWriter.ofUTF8()) {
            w.writeAny(USER.getTags());
            return w.getBytes();
        }
    }

    @Benchmark
    public byte[] address_facade_fields() throws Exception {
        try (Fastjson2Writer w = BINDER.createWriterUtf8()) {
            Address a = USER.getAddress();
            w.startObject();
            w.writeName("street"); w.writeString(a.getStreet());
            w.writeName("city"); w.writeString(a.getCity());
            w.writeName("state"); w.writeString(a.getState());
            w.writeName("zip"); w.writeString(a.getZip());
            w.writeName("country"); w.writeString(a.getCountry());
            w.endObject();
            return w.toBytes();
        }
    }

    @Benchmark
    public byte[] address_native_fields() {
        try (JSONWriter w = JSONWriter.ofUTF8()) {
            Address a = USER.getAddress();
            w.startObject();
            w.writeName("street"); w.writeColon(); w.writeString(a.getStreet());
            w.writeName("city"); w.writeColon(); w.writeString(a.getCity());
            w.writeName("state"); w.writeColon(); w.writeString(a.getState());
            w.writeName("zip"); w.writeColon(); w.writeString(a.getZip());
            w.writeName("country"); w.writeColon(); w.writeString(a.getCountry());
            w.endObject();
            return w.getBytes();
        }
    }

    @Benchmark
    public byte[] address_native_object() {
        try (JSONWriter w = JSONWriter.ofUTF8()) {
            w.writeAny(USER.getAddress());
            return w.getBytes();
        }
    }
}
