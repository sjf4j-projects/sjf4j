package org.sjf4j.testbench.binding.runtime;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.RuntimeContext;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Writer;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.binding.CompiledName;
import org.sjf4j.binding.PropertyWriter;
import org.sjf4j.binding.StreamingIO;
import org.sjf4j.node.PojoInfo;
import org.sjf4j.node.TypeRegistry;
import org.sjf4j.testbench.model.UserRecord;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Focused, temporary diagnostic of runtime-write dispatch and writer overhead.
 *
 * WARNING: Each flat-property test operates on exactly the same 16-field payload.
 * nativeExtra additionally exercises Fastjson2's unwrapped-map handling; the
 * nativeRecord methods use a smaller 4-field record and must be compared ONLY
 * with their matching runtimeRecord methods.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 4, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@Threads(1)
@State(Scope.Thread)
public class RuntimeWriteProbeBenchmark {

    private static final JSONWriter.Context CONTEXT =
            JSONFactory.createWriteContext(JSONWriter.Feature.WriteNulls);
    private static final Fastjson2Binder FAST = new Fastjson2Binder();
    private static final Jackson2Binder J2 = new Jackson2Binder();
    private static final Jackson3Binder J3 = new Jackson3Binder();
    private static final ObjectMapper NATIVE_J2 = new ObjectMapper();
    private static final JsonMapper NATIVE_J3 = new JsonMapper();

    private static final JojoReadBenchmark.PojoModel POJO = new JojoReadBenchmark.PojoModel();
    private static final JojoReadBenchmark.JojoModel JOJO = new JojoReadBenchmark.JojoModel();
    private static final JojoReadBenchmark.ExtraModel EXTRA = new JojoReadBenchmark.ExtraModel();
    private static final UserRecord RECORD = new UserRecord(839201L, 34, "alice.builder", true);
    private static final PojoInfo POJO_INFO = TypeRegistry.requireRegisteredPojoInfo(POJO.getClass());
    private static final String[] NAMES = {"id", "createdAt", "updatedAt", "reputation", "loginCount", "age", "active", "verified", "admin", "suspended", "score", "latitude", "longitude", "username", "email", "department"};
    private static final CompiledName[] PREPARED = new CompiledName[NAMES.length];

    static {
        POJO.id = 839201L;
        JOJO.id = 839201L;
        EXTRA.id = 839201L;
        POJO.createdAt = 1700000000123L;
        JOJO.createdAt = 1700000000123L;
        EXTRA.createdAt = 1700000000123L;
        POJO.updatedAt = 1701234567890L;
        JOJO.updatedAt = 1701234567890L;
        EXTRA.updatedAt = 1701234567890L;
        POJO.reputation = 9876543210L;
        JOJO.reputation = 9876543210L;
        EXTRA.reputation = 9876543210L;
        POJO.loginCount = 421;
        JOJO.loginCount = 421;
        EXTRA.loginCount = 421;
        POJO.age = 34;
        JOJO.age = 34;
        EXTRA.age = 34;
        POJO.active = true;
        JOJO.active = true;
        EXTRA.active = true;
        POJO.verified = true;
        JOJO.verified = true;
        EXTRA.verified = true;
        POJO.admin = false;
        JOJO.admin = false;
        EXTRA.admin = false;
        POJO.suspended = false;
        JOJO.suspended = false;
        EXTRA.suspended = false;
        POJO.score = 98.75;
        JOJO.score = 98.75;
        EXTRA.score = 98.75;
        POJO.latitude = 37.7749;
        JOJO.latitude = 37.7749;
        EXTRA.latitude = 37.7749;
        POJO.longitude = -122.4194;
        JOJO.longitude = -122.4194;
        EXTRA.longitude = -122.4194;
        POJO.username = "alice.builder";
        JOJO.username = "alice.builder";
        EXTRA.username = "alice.builder";
        POJO.email = null;
        JOJO.email = null;
        EXTRA.email = null;
        POJO.department = "Platform Engineering";
        JOJO.department = "Platform Engineering";
        EXTRA.department = "Platform Engineering";
        try (JSONWriter json = JSONWriter.of(CONTEXT)) {
            Fastjson2Writer writer = new Fastjson2Writer(json);
            for (int i = 0; i < NAMES.length; i++) PREPARED[i] = writer.createCompiledName(NAMES[i]);
        }
    }

    @Setup(Level.Trial)
    public void verify() throws IOException {
        Object reference = JSON.parseObject(nativePojo());
        verifySame("runtimePojo", reference, runtimePojo());
        verifySame("runtimeJojo", reference, runtimeJojo());
        verifySame("nativeExtra", reference, nativeExtra());
        verifySame("directPojo", reference, directPojo());
        verifySame("propertyLoop", reference, propertyLoop());
        verifySame("writerStringNames", reference, writerStringNames());
        verifySame("writerPreparedNames", reference, writerPreparedNames());
        verifySame("rawJsonWriter", reference, rawJsonWriter());
        Object recordRef = JSON.parseObject(nativeJackson2Record());
        verifySame("runtimeJackson2Record", recordRef, runtimeJackson2Record());
        verifySame("nativeJackson3Record", recordRef, nativeJackson3Record());
        verifySame("runtimeJackson3Record", recordRef, runtimeJackson3Record());
    }

    private static void verifySame(String label, Object reference, String actual) {
        Object result = JSON.parseObject(actual);
        if (!reference.equals(result)) throw new IllegalStateException(label +
                " content mismatch: expected=" + reference + " actual=" + result);
    }

    @Benchmark
    public String nativePojo() {
        return JSON.toJSONString(POJO, CONTEXT);
    }

    @Benchmark
    public String nativeExtra() {
        return JSON.toJSONString(EXTRA, CONTEXT);
    }

    @Benchmark
    public String runtimePojo() {
        return FAST.writeNodeAsString(POJO);
    }

    @Benchmark
    public String runtimeJojo() {
        return FAST.writeNodeAsString(JOJO);
    }

    @Benchmark
    public String directPojo() throws IOException {
        try (JSONWriter json = JSONWriter.of(CONTEXT)) {
            Fastjson2Writer writer = new Fastjson2Writer(json);
            StreamingIO.writePojo(writer, POJO, POJO_INFO, RuntimeContext.EMPTY);
            return json.toString();
        }
    }

    @Benchmark
    public String propertyLoop() throws IOException {
        try (JSONWriter json = JSONWriter.of(CONTEXT)) {
            Fastjson2Writer writer = new Fastjson2Writer(json);
            writer.startObject();
            PropertyWriter[] props = POJO_INFO.propertyWriters;
            CompiledName[] names = writer.compiledNames(POJO_INFO);
            int count = 0;
            for (int i = 0; i < props.length; i++) {
                count = props[i].write(writer, names[i], POJO, RuntimeContext.EMPTY, count);
            }
            writer.endObject();
            return json.toString();
        }
    }

    @Benchmark
    public String writerStringNames() throws IOException {
        try (JSONWriter json = JSONWriter.of(CONTEXT)) {
            Fastjson2Writer writer = new Fastjson2Writer(json);
            writer.startObject();
        writer.writeName("id");
        writer.writeLongValue(POJO.id);
        writer.writeName("createdAt");
        writer.writeLongValue(POJO.createdAt);
        writer.writeName("updatedAt");
        writer.writeLongValue(POJO.updatedAt);
        writer.writeName("reputation");
        writer.writeLongValue(POJO.reputation);
        writer.writeName("loginCount");
        writer.writeIntValue(POJO.loginCount);
        writer.writeName("age");
        writer.writeIntValue(POJO.age);
        writer.writeName("active");
        writer.writeBooleanValue(POJO.active);
        writer.writeName("verified");
        writer.writeBooleanValue(POJO.verified);
        writer.writeName("admin");
        writer.writeBooleanValue(POJO.admin);
        writer.writeName("suspended");
        writer.writeBooleanValue(POJO.suspended);
        writer.writeName("score");
        writer.writeDoubleValue(POJO.score);
        writer.writeName("latitude");
        writer.writeDoubleValue(POJO.latitude);
        writer.writeName("longitude");
        writer.writeDoubleValue(POJO.longitude);
        writer.writeName("username");
        writer.writeStringValue(POJO.username);
        writer.writeName("email");
        writer.writeStringValue(POJO.email);
        writer.writeName("department");
        writer.writeStringValue(POJO.department);
            writer.endObject();
            return json.toString();
        }
    }

    @Benchmark
    public String writerPreparedNames() throws IOException {
        try (JSONWriter json = JSONWriter.of(CONTEXT)) {
            Fastjson2Writer writer = new Fastjson2Writer(json);
            writer.startObject();
        writer.writeName(PREPARED[0]);
        writer.writeLongValue(POJO.id);
        writer.writeName(PREPARED[1]);
        writer.writeLongValue(POJO.createdAt);
        writer.writeName(PREPARED[2]);
        writer.writeLongValue(POJO.updatedAt);
        writer.writeName(PREPARED[3]);
        writer.writeLongValue(POJO.reputation);
        writer.writeName(PREPARED[4]);
        writer.writeIntValue(POJO.loginCount);
        writer.writeName(PREPARED[5]);
        writer.writeIntValue(POJO.age);
        writer.writeName(PREPARED[6]);
        writer.writeBooleanValue(POJO.active);
        writer.writeName(PREPARED[7]);
        writer.writeBooleanValue(POJO.verified);
        writer.writeName(PREPARED[8]);
        writer.writeBooleanValue(POJO.admin);
        writer.writeName(PREPARED[9]);
        writer.writeBooleanValue(POJO.suspended);
        writer.writeName(PREPARED[10]);
        writer.writeDoubleValue(POJO.score);
        writer.writeName(PREPARED[11]);
        writer.writeDoubleValue(POJO.latitude);
        writer.writeName(PREPARED[12]);
        writer.writeDoubleValue(POJO.longitude);
        writer.writeName(PREPARED[13]);
        writer.writeStringValue(POJO.username);
        writer.writeName(PREPARED[14]);
        writer.writeStringValue(POJO.email);
        writer.writeName(PREPARED[15]);
        writer.writeStringValue(POJO.department);
            writer.endObject();
            return json.toString();
        }
    }

    @Benchmark
    public String rawJsonWriter() {
        try (JSONWriter writer = JSONWriter.of(CONTEXT)) {
            writer.startObject();
            writer.writeName("id");
            writer.writeColon();
            writer.writeInt64(POJO.id);
            writer.writeName("createdAt");
            writer.writeColon();
            writer.writeInt64(POJO.createdAt);
            writer.writeName("updatedAt");
            writer.writeColon();
            writer.writeInt64(POJO.updatedAt);
            writer.writeName("reputation");
            writer.writeColon();
            writer.writeInt64(POJO.reputation);
            writer.writeName("loginCount");
            writer.writeColon();
            writer.writeInt32(POJO.loginCount);
            writer.writeName("age");
            writer.writeColon();
            writer.writeInt32(POJO.age);
            writer.writeName("active");
            writer.writeColon();
            writer.writeBool(POJO.active);
            writer.writeName("verified");
            writer.writeColon();
            writer.writeBool(POJO.verified);
            writer.writeName("admin");
            writer.writeColon();
            writer.writeBool(POJO.admin);
            writer.writeName("suspended");
            writer.writeColon();
            writer.writeBool(POJO.suspended);
            writer.writeName("score");
            writer.writeColon();
            writer.writeDouble(POJO.score);
            writer.writeName("latitude");
            writer.writeColon();
            writer.writeDouble(POJO.latitude);
            writer.writeName("longitude");
            writer.writeColon();
            writer.writeDouble(POJO.longitude);
            writer.writeName("username");
            writer.writeColon();
            writer.writeString(POJO.username);
            writer.writeName("email");
            writer.writeColon();
            writer.writeString(POJO.email);
            writer.writeName("department");
            writer.writeColon();
            writer.writeString(POJO.department);
            writer.endObject();
            return writer.toString();
        }
    }

    @Benchmark
    public String nativeJackson2Record() throws IOException {
        return NATIVE_J2.writeValueAsString(RECORD);
    }

    @Benchmark
    public String runtimeJackson2Record() {
        return J2.writeNodeAsString(RECORD);
    }

    @Benchmark
    public String nativeJackson3Record() {
        return NATIVE_J3.writeValueAsString(RECORD);
    }

    @Benchmark
    public String runtimeJackson3Record() {
        return J3.writeNodeAsString(RECORD);
    }
}
