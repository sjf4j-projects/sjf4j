package org.sjf4j.testbench.binding.compiled;

import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.annotation.binding.WriteTo;
import org.sjf4j.binding.Binder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * UUID end-to-end benchmarks: same root/POJO payload, Runtime and Compiled,
 * String and UTF-8, across all SJF4J JSON backends.
 *
 * The backend's standalone native UUID read/write methods are measured by
 * StreamingUuidBenchmark, which also retains the old raw-String baseline.
 * Compare scores within the same JMH run and input/output shape.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 6, time = 350, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 8, time = 350, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
public class CompiledUuidBenchmark {
    private static final UUID ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final String ROOT_JSON = "\"" + ID + "\"";
    private static final String POJO_JSON = "{\"id\":\"" + ID
            + "\",\"optional\":null,\"label\":\"sample\"}";
    private static final byte[] ROOT_BYTES = ROOT_JSON.getBytes(StandardCharsets.UTF_8);
    private static final byte[] POJO_BYTES = POJO_JSON.getBytes(StandardCharsets.UTF_8);

    public static final class UuidPojo {
        public UUID id;
        public UUID optional;
        public String label;
    }

    @Param({"fastjson2", "jackson2", "jackson3", "gson", "jsonp", "simple"})
    public String backend;

    private Binder<?, ?> runtime;
    private ReadString<UUID> compiledReadRootString;
    private ReadBytes<UUID> compiledReadRootUtf8;
    private WriteString<UUID> compiledWriteRootString;
    private WriteBytes<UUID> compiledWriteRootUtf8;
    private ReadString<UuidPojo> compiledReadPojoString;
    private ReadBytes<UuidPojo> compiledReadPojoUtf8;
    private WriteString<UuidPojo> compiledWritePojoString;
    private WriteBytes<UuidPojo> compiledWritePojoUtf8;
    private UuidPojo pojo;

    @FunctionalInterface private interface ReadString<T> { T read(String json) throws IOException; }
    @FunctionalInterface private interface ReadBytes<T> { T read(byte[] json) throws IOException; }
    @FunctionalInterface private interface WriteString<T> { String write(T value) throws IOException; }
    @FunctionalInterface private interface WriteBytes<T> { byte[] write(T value) throws IOException; }

    @Setup(Level.Trial)
    public void setUp() throws IOException {
        switch (backend) {
            case "fastjson2": {
                Fastjson2Compiled c = CompiledInstances.create(Fastjson2Compiled.class);
                runtime = new org.sjf4j.backend.fastjson2.binding.Fastjson2Binder();
                bind(c::readRootString, c::readRootUtf8, c::writeRootString, c::writeRootUtf8,
                        c::readPojoString, c::readPojoUtf8, c::writePojoString, c::writePojoUtf8);
                break;
            }
            case "jackson2": {
                Jackson2Compiled c = CompiledInstances.create(Jackson2Compiled.class);
                runtime = new org.sjf4j.backend.jackson2.binding.Jackson2Binder();
                bind(c::readRootString, c::readRootUtf8, c::writeRootString, c::writeRootUtf8,
                        c::readPojoString, c::readPojoUtf8, c::writePojoString, c::writePojoUtf8);
                break;
            }
            case "jackson3": {
                Jackson3Compiled c = CompiledInstances.create(Jackson3Compiled.class);
                runtime = new org.sjf4j.backend.jackson3.binding.Jackson3Binder();
                bind(c::readRootString, c::readRootUtf8, c::writeRootString, c::writeRootUtf8,
                        c::readPojoString, c::readPojoUtf8, c::writePojoString, c::writePojoUtf8);
                break;
            }
            case "gson": {
                GsonCompiled c = CompiledInstances.create(GsonCompiled.class);
                runtime = new org.sjf4j.backend.gson.binding.GsonBinder();
                bind(c::readRootString, c::readRootUtf8, c::writeRootString, c::writeRootUtf8,
                        c::readPojoString, c::readPojoUtf8, c::writePojoString, c::writePojoUtf8);
                break;
            }
            case "jsonp": {
                JsonpCompiled c = CompiledInstances.create(JsonpCompiled.class);
                runtime = new org.sjf4j.backend.jsonp.binding.JsonpBinder();
                bind(c::readRootString, c::readRootUtf8, c::writeRootString, c::writeRootUtf8,
                        c::readPojoString, c::readPojoUtf8, c::writePojoString, c::writePojoUtf8);
                break;
            }
            case "simple": {
                SimpleCompiled c = CompiledInstances.create(SimpleCompiled.class);
                runtime = new org.sjf4j.binding.simple.SimpleJsonBinder();
                bind(c::readRootString, c::readRootUtf8, c::writeRootString, c::writeRootUtf8,
                        c::readPojoString, c::readPojoUtf8, c::writePojoString, c::writePojoUtf8);
                break;
            }
            default: throw new IllegalStateException(backend);
        }

        pojo = new UuidPojo();
        pojo.id = ID;
        pojo.label = "sample";

        verify(ID, compiledReadRootString.read(ROOT_JSON), "compiled root/string");
        verify(ID, compiledReadRootUtf8.read(ROOT_BYTES), "compiled root/utf8");
        verify(ID, runtime.readNode(ROOT_JSON, UUID.class), "runtime root/string");
        verify(ID, runtime.readNode(ROOT_BYTES, UUID.class), "runtime root/utf8");
        verify(ROOT_JSON, compiledWriteRootString.write(ID), "compiled root write");
        verify(ROOT_JSON, runtime.writeNodeAsString(ID), "runtime root write");
        verify(ROOT_JSON, new String(compiledWriteRootUtf8.write(ID), StandardCharsets.UTF_8),
                "compiled root bytes");
        verify(ROOT_JSON, new String(runtime.writeNodeAsBytes(ID), StandardCharsets.UTF_8),
                "runtime root bytes");

        checkPojo(compiledReadPojoString.read(POJO_JSON));
        checkPojo(compiledReadPojoUtf8.read(POJO_BYTES));
        checkPojo(runtime.readNode(POJO_JSON, UuidPojo.class));
        checkPojo(runtime.readNode(POJO_BYTES, UuidPojo.class));
        verify(POJO_JSON, compiledWritePojoString.write(pojo), "compiled pojo write");
        verify(POJO_JSON, runtime.writeNodeAsString(pojo), "runtime pojo write");
        verify(POJO_JSON, new String(compiledWritePojoUtf8.write(pojo), StandardCharsets.UTF_8),
                "compiled pojo bytes");
        verify(POJO_JSON, new String(runtime.writeNodeAsBytes(pojo), StandardCharsets.UTF_8),
                "runtime pojo bytes");
    }

    private void bind(ReadString<UUID> rs, ReadBytes<UUID> rb,
                      WriteString<UUID> ws, WriteBytes<UUID> wb,
                      ReadString<UuidPojo> ps, ReadBytes<UuidPojo> pb,
                      WriteString<UuidPojo> pws, WriteBytes<UuidPojo> pwb) {
        compiledReadRootString = rs;
        compiledReadRootUtf8 = rb;
        compiledWriteRootString = ws;
        compiledWriteRootUtf8 = wb;
        compiledReadPojoString = ps;
        compiledReadPojoUtf8 = pb;
        compiledWritePojoString = pws;
        compiledWritePojoUtf8 = pwb;
    }

    private static void verify(Object expected, Object actual, String mode) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(mode + ": expected " + expected + ", got " + actual);
        }
    }

    private static void checkPojo(Object actual) {
        if (!(actual instanceof UuidPojo)) throw new IllegalStateException("wrong POJO");
        UuidPojo p = (UuidPojo) actual;
        if (!ID.equals(p.id) || p.optional != null || !"sample".equals(p.label)) {
            throw new IllegalStateException("wrong UUID POJO");
        }
    }

    @Benchmark public Object readRootStringCompiled() throws IOException { return compiledReadRootString.read(ROOT_JSON); }
    @Benchmark public Object readRootStringRuntime() { return runtime.readNode(ROOT_JSON, UUID.class); }
    @Benchmark public Object readRootUtf8Compiled() throws IOException { return compiledReadRootUtf8.read(ROOT_BYTES); }
    @Benchmark public Object readRootUtf8Runtime() { return runtime.readNode(ROOT_BYTES, UUID.class); }

    @Benchmark public String writeRootStringCompiled() throws IOException { return compiledWriteRootString.write(ID); }
    @Benchmark public String writeRootStringRuntime() { return runtime.writeNodeAsString(ID); }
    @Benchmark public byte[] writeRootUtf8Compiled() throws IOException { return compiledWriteRootUtf8.write(ID); }
    @Benchmark public byte[] writeRootUtf8Runtime() { return runtime.writeNodeAsBytes(ID); }

    @Benchmark public Object readPojoStringCompiled() throws IOException { return compiledReadPojoString.read(POJO_JSON); }
    @Benchmark public Object readPojoStringRuntime() { return runtime.readNode(POJO_JSON, UuidPojo.class); }
    @Benchmark public Object readPojoUtf8Compiled() throws IOException { return compiledReadPojoUtf8.read(POJO_BYTES); }
    @Benchmark public Object readPojoUtf8Runtime() { return runtime.readNode(POJO_BYTES, UuidPojo.class); }

    @Benchmark public String writePojoStringCompiled() throws IOException { return compiledWritePojoString.write(pojo); }
    @Benchmark public String writePojoStringRuntime() { return runtime.writeNodeAsString(pojo); }
    @Benchmark public byte[] writePojoUtf8Compiled() throws IOException { return compiledWritePojoUtf8.write(pojo); }
    @Benchmark public byte[] writePojoUtf8Runtime() { return runtime.writeNodeAsBytes(pojo); }

    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface Fastjson2Compiled extends UuidCompiled {}

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2Compiled extends UuidCompiled {}

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3Compiled extends UuidCompiled {}

    @CompiledBinder(backend = Backend.GSON)
    public interface GsonCompiled extends UuidCompiled {}

    @CompiledBinder(backend = Backend.JSONP)
    public interface JsonpCompiled extends UuidCompiled {}

    @CompiledBinder(backend = Backend.SIMPLE)
    public interface SimpleCompiled extends UuidCompiled {}

    public interface UuidCompiled {
        @ReadFrom UUID readRootString(String json) throws IOException;
        @ReadFrom UUID readRootUtf8(byte[] json) throws IOException;
        @WriteTo String writeRootString(UUID value) throws IOException;
        @WriteTo byte[] writeRootUtf8(UUID value) throws IOException;
        @ReadFrom UuidPojo readPojoString(String json) throws IOException;
        @ReadFrom UuidPojo readPojoUtf8(byte[] json) throws IOException;
        @WriteTo String writePojoString(UuidPojo value) throws IOException;
        @WriteTo byte[] writePojoUtf8(UuidPojo value) throws IOException;
    }
}
