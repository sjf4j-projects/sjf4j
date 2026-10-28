package org.sjf4j.testbench.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.annotation.binding.WriteTo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real annotation-processed Enum binding fixtures, including native write and
 * Fastjson2 hash-read paths. No generated-source assertions: inspect the actual
 * generated binders under
 * sjf4j-testbench/build/generated/sources/annotationProcessor/java/test/.
 *
 * Run: ./gradlew :sjf4j-testbench:test --tests '*CompiledEnumBindingTest'
 */
public class CompiledEnumBindingTest {

    public enum Status {
        A, ACTIVE, VERY_LONG_NAME, 中文,
        OVERRIDE {
            @Override public String toString() { return "not-enum-name"; }
        }
    }

    public static class Payload {
        public Status state;
        public List<Status> values;
        public Map<String, Status> byName;
    }

    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface Fastjson2EnumBinder {
        @ReadFrom Status readEnum(String json) throws IOException;
        @ReadFrom Status readEnumBytes(byte[] json) throws IOException;
        @ReadFrom Payload readPojo(String json) throws IOException;
        @ReadFrom Payload readPojoBytes(byte[] json) throws IOException;
        @WriteTo String writePojo(Payload value) throws IOException;
        @WriteTo byte[] writePojoBytes(Payload value) throws IOException;
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2EnumBinder {
        @ReadFrom Status readEnum(String json) throws IOException;
        @ReadFrom Status readEnumBytes(byte[] json) throws IOException;
        @ReadFrom Payload readPojo(String json) throws IOException;
        @ReadFrom Payload readPojoBytes(byte[] json) throws IOException;
        @WriteTo String writePojo(Payload value) throws IOException;
        @WriteTo byte[] writePojoBytes(Payload value) throws IOException;
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3EnumBinder {
        @ReadFrom Status readEnum(String json) throws IOException;
        @ReadFrom Status readEnumBytes(byte[] json) throws IOException;
        @ReadFrom Payload readPojo(String json) throws IOException;
        @ReadFrom Payload readPojoBytes(byte[] json) throws IOException;
        @WriteTo String writePojo(Payload value) throws IOException;
        @WriteTo byte[] writePojoBytes(Payload value) throws IOException;
    }

    @FunctionalInterface
    private interface StringReader<T> {
        T read(String json) throws IOException;
    }

    @FunctionalInterface
    private interface BytesReader<T> {
        T read(byte[] json) throws IOException;
    }

    @FunctionalInterface
    private interface StringWriter<T> {
        String write(T value) throws IOException;
    }

    @FunctionalInterface
    private interface BytesWriter<T> {
        byte[] write(T value) throws IOException;
    }

    @Test
    void compiledEnumReadWriteAcrossBackends() throws IOException {
        Fastjson2EnumBinder fastjson2 = CompiledInstances.create(Fastjson2EnumBinder.class);
        verify("Fastjson2", fastjson2::readEnum, fastjson2::readEnumBytes,
                fastjson2::readPojo, fastjson2::readPojoBytes,
                fastjson2::writePojo, fastjson2::writePojoBytes);

        Jackson2EnumBinder jackson2 = CompiledInstances.create(Jackson2EnumBinder.class);
        verify("Jackson2", jackson2::readEnum, jackson2::readEnumBytes,
                jackson2::readPojo, jackson2::readPojoBytes,
                jackson2::writePojo, jackson2::writePojoBytes);

        Jackson3EnumBinder jackson3 = CompiledInstances.create(Jackson3EnumBinder.class);
        verify("Jackson3", jackson3::readEnum, jackson3::readEnumBytes,
                jackson3::readPojo, jackson3::readPojoBytes,
                jackson3::writePojo, jackson3::writePojoBytes);
    }

    private static void verify(String backend, StringReader<Status> scalar,
                               BytesReader<Status> scalarBytes,
                               StringReader<Payload> pojo,
                               BytesReader<Payload> pojoBytes,
                               StringWriter<Payload> write,
                               BytesWriter<Payload> writeBytes) throws IOException {
        for (Status status : Status.values()) {
            String json = "\"" + status.name() + "\"";
            assertSame(status, scalar.read(json), backend);
            assertSame(status, scalarBytes.read(json.getBytes(StandardCharsets.UTF_8)), backend);
        }
        assertSame(Status.中文, scalar.read("\"\\u4e2d\\u6587\""), backend);
        assertSame(Status.ACTIVE, scalar.read("1"), backend);
        assertNull(scalar.read("null"), backend);
        assertThrows(IllegalArgumentException.class, () -> scalar.read("\"UNKNOWN\""), backend);

        String json = "{\"state\":\"OVERRIDE\","
                + "\"values\":[\"A\",null,\"中文\",2],"
                + "\"byName\":{\"first\":\"VERY_LONG_NAME\",\"empty\":null}}";
        Payload fromString = pojo.read(json);
        Payload fromBytes = pojoBytes.read(json.getBytes(StandardCharsets.UTF_8));
        for (Payload value : new Payload[]{fromString, fromBytes}) {
            assertSame(Status.OVERRIDE, value.state, backend);
            assertEquals(4, value.values.size(), backend);
            assertSame(Status.A, value.values.get(0), backend);
            assertNull(value.values.get(1), backend);
            assertSame(Status.中文, value.values.get(2), backend);
            assertSame(Status.VERY_LONG_NAME, value.values.get(3), backend);
            assertSame(Status.VERY_LONG_NAME, value.byName.get("first"), backend);
            assertNull(value.byName.get("empty"), backend);
        }

        String encoded = write.write(fromString);
        String utf8 = new String(writeBytes.write(fromString), StandardCharsets.UTF_8);
        assertEquals(encoded, utf8, backend);
        assertFalse(encoded.contains("not-enum-name"), backend);
        Payload roundTrip = pojo.read(encoded);
        assertSame(Status.OVERRIDE, roundTrip.state, backend);
        assertSame(Status.中文, roundTrip.values.get(2), backend);
        assertSame(Status.VERY_LONG_NAME, roundTrip.byName.get("first"), backend);
    }
}
