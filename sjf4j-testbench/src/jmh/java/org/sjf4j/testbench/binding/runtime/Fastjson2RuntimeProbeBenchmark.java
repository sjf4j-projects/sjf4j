package org.sjf4j.testbench.binding.runtime;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReaderProvider;
import com.alibaba.fastjson2.util.Fnv;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.RuntimeContext;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2NameMatcher;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Reader;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.binding.PropertyReader;
import org.sjf4j.binding.StreamingIO;
import org.sjf4j.node.PojoInfo;
import org.sjf4j.node.TypeRegistry;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * DIAGNOSTIC ONLY. Isolates Fastjson2 input/parser from SJF4J name matching,
 * generic property dispatch, runtime binding and native generated deserialization.
 *
 * All methods return equivalent objects from the same 16-field static JSON.
 * The readerSwitch method deliberately represents a Compiled-style switch over
 * a prepared NameMatcher; it is NOT a CompiledBinder benchmark.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 6, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@Threads(1)
@State(Scope.Thread)
public class Fastjson2RuntimeProbeBenchmark {

    private static final String JSON = "{\"id\":839201,\"createdAt\":1700000000123,\"updatedAt\":1701234567890,\"reputation\":9876543210,\"loginCount\":421,\"age\":34,\"active\":true,\"verified\":true,\"admin\":false,\"suspended\":false,\"score\":98.75,\"latitude\":37.7749,\"longitude\":-122.4194,\"username\":\"alice.builder\",\"email\":null,\"department\":\"Platform Engineering\"}";
    private static final JSONReader.Context CTX =
            JSONFactory.createReadContext(new ObjectReaderProvider(), JSONReader.Feature.UseDoubleForDecimals);
    private static final Fastjson2Binder BINDER = new Fastjson2Binder();
    private static final Class<JojoReadBenchmark.PojoModel> TYPE = JojoReadBenchmark.PojoModel.class;
    private static final PojoInfo POJO_INFO = TypeRegistry.requireRegisteredPojoInfo(TYPE);
    private static final Fastjson2NameMatcher MATCHER = new Fastjson2NameMatcher("id", "createdAt", "updatedAt", "reputation", "loginCount", "age", "active", "verified", "admin", "suspended", "score", "latitude", "longitude", "username", "email", "department");

    private static final long H_ID = Fnv.hashCode64("id");
    private static final long H_CREATEDAT = Fnv.hashCode64("createdAt");
    private static final long H_UPDATEDAT = Fnv.hashCode64("updatedAt");
    private static final long H_REPUTATION = Fnv.hashCode64("reputation");
    private static final long H_LOGINCOUNT = Fnv.hashCode64("loginCount");
    private static final long H_AGE = Fnv.hashCode64("age");
    private static final long H_ACTIVE = Fnv.hashCode64("active");
    private static final long H_VERIFIED = Fnv.hashCode64("verified");
    private static final long H_ADMIN = Fnv.hashCode64("admin");
    private static final long H_SUSPENDED = Fnv.hashCode64("suspended");
    private static final long H_SCORE = Fnv.hashCode64("score");
    private static final long H_LATITUDE = Fnv.hashCode64("latitude");
    private static final long H_LONGITUDE = Fnv.hashCode64("longitude");
    private static final long H_USERNAME = Fnv.hashCode64("username");
    private static final long H_EMAIL = Fnv.hashCode64("email");
    private static final long H_DEPARTMENT = Fnv.hashCode64("department");

    @Setup(Level.Trial)
    public void validate() throws IOException {
        JojoReadBenchmark.PojoModel base = (JojoReadBenchmark.PojoModel) runtimePojo();
        validateResult("nativePojo", nativePojo());
        validateResult("runtimePojo", base);
        validateResult("runtimeJojo", runtimeJojo());
        validateResult("readerPropertyDispatch", readerPropertyDispatch());
        validateResult("readerSwitch", readerSwitch());
        validateResult("rawHashIf", rawHashIf());
    }

    private static void validateResult(String label, Object candidate) {
        JojoReadBenchmark.PojoModel p;
        if (candidate instanceof JojoReadBenchmark.PojoModel) {
            p = (JojoReadBenchmark.PojoModel) candidate;
        } else if (candidate instanceof JojoReadBenchmark.JojoModel) {
            JojoReadBenchmark.JojoModel jojo = (JojoReadBenchmark.JojoModel) candidate;
            if (jojo.id != 839201 || jojo.createdAt != 1700000000123L || jojo.reputation != 9876543210L
                    || jojo.loginCount != 421 || jojo.age != 34 || !jojo.active
                    || jojo.suspended || jojo.score != 98.75 || jojo.latitude != 37.7749
                    || !"alice.builder".equals(jojo.username) || jojo.email != null
                    || !"Platform Engineering".equals(jojo.department)
                    || !jojo.dynamicProperties().isEmpty()) {
                throw new IllegalStateException(label + ": JOJO content differs");
            }
            return;
        } else {
            throw new IllegalStateException(label + ": unexpected result type");
        }
        if (p.id != 839201 || p.createdAt != 1700000000123L || p.updatedAt != 1701234567890L
                || p.reputation != 9876543210L || p.loginCount != 421 || p.age != 34
                || !p.active || !p.verified || p.admin || p.suspended || p.score != 98.75
                || p.latitude != 37.7749 || p.longitude != -122.4194
                || !"alice.builder".equals(p.username) || p.email != null
                || !"Platform Engineering".equals(p.department)) {
            throw new IllegalStateException(label + ": object contents differ");
        }
    }

    @Benchmark
    public Object nativePojo() {
        try (JSONReader reader = JSONReader.of(JSON, CTX)) {
            return reader.read(TYPE);
        }
    }

    @Benchmark
    public Object runtimePojo() {
        return BINDER.readNode(JSON, TYPE);
    }

    @Benchmark
    public Object runtimeJojo() {
        return BINDER.readNode(JSON, JojoReadBenchmark.JojoModel.class);
    }

    /** SJF4J matcher + normal PropertyReader lambdas, bypassing StreamingIO.readNode. */
    @Benchmark
    public Object readerPropertyDispatch() throws IOException {
        try (Fastjson2Reader reader = new Fastjson2Reader(JSONReader.of(JSON, CTX))) {
            reader.startObject();
            JojoReadBenchmark.PojoModel value = new JojoReadBenchmark.PojoModel();
            PropertyReader[] propertyReaders = POJO_INFO.propertyReaders;
            NameMatcher matcher = StreamingIO.cacheNameMatcher(reader, POJO_INFO);
            int expected = 0;
            int index;
            while ((index = reader.nextNameMatch(matcher, expected)) != NameMatcher.OBJECT_END) {
                if (index >= 0) {
                    propertyReaders[index].read(reader, value, TYPE, TYPE, RuntimeContext.EMPTY);
                    expected = index + 1;
                } else {
                    reader.skipNode();
                }
            }
            return value;
        }
    }

    /** Same SJF4J Fastjson2Reader + name matcher, but typed switch assignments. */
    @Benchmark
    public Object readerSwitch() throws IOException {
        try (Fastjson2Reader reader = new Fastjson2Reader(JSONReader.of(JSON, CTX))) {
            reader.startObject();
            JojoReadBenchmark.PojoModel value = new JojoReadBenchmark.PojoModel();
            int index;
            while ((index = reader.nextNameMatch(MATCHER)) != NameMatcher.OBJECT_END) {
                switch (index) {
                case 0: value.id = reader.readLongValue(); break;
                case 1: value.createdAt = reader.readLongValue(); break;
                case 2: value.updatedAt = reader.readLongValue(); break;
                case 3: value.reputation = reader.readLongValue(); break;
                case 4: value.loginCount = reader.readIntValue(); break;
                case 5: value.age = reader.readIntValue(); break;
                case 6: value.active = reader.readBooleanValue(); break;
                case 7: value.verified = reader.readBooleanValue(); break;
                case 8: value.admin = reader.readBooleanValue(); break;
                case 9: value.suspended = reader.readBooleanValue(); break;
                case 10: value.score = reader.readDoubleValue(); break;
                case 11: value.latitude = reader.readDoubleValue(); break;
                case 12: value.longitude = reader.readDoubleValue(); break;
                case 13: value.username = reader.readString(); break;
                case 14: value.email = reader.readString(); break;
                case 15: value.department = reader.readString(); break;
                default: reader.skipNode(); break;
                }
            }
            return value;
        }
    }

    /** Fastjson2 JSONReader directly, with native FNV hash and typed assignments. */
    @Benchmark
    public Object rawHashIf() {
        try (JSONReader reader = JSONReader.of(JSON, CTX)) {
            reader.nextIfObjectStart();
            JojoReadBenchmark.PojoModel value = new JojoReadBenchmark.PojoModel();
            while (!reader.nextIfObjectEnd()) {
                long hash = reader.readFieldNameHashCode();
            if (hash == H_ID) value.id = reader.readInt64Value();
            else if (hash == H_CREATEDAT) value.createdAt = reader.readInt64Value();
            else if (hash == H_UPDATEDAT) value.updatedAt = reader.readInt64Value();
            else if (hash == H_REPUTATION) value.reputation = reader.readInt64Value();
            else if (hash == H_LOGINCOUNT) value.loginCount = reader.readInt32Value();
            else if (hash == H_AGE) value.age = reader.readInt32Value();
            else if (hash == H_ACTIVE) value.active = reader.readBoolValue();
            else if (hash == H_VERIFIED) value.verified = reader.readBoolValue();
            else if (hash == H_ADMIN) value.admin = reader.readBoolValue();
            else if (hash == H_SUSPENDED) value.suspended = reader.readBoolValue();
            else if (hash == H_SCORE) value.score = reader.readDoubleValue();
            else if (hash == H_LATITUDE) value.latitude = reader.readDoubleValue();
            else if (hash == H_LONGITUDE) value.longitude = reader.readDoubleValue();
            else if (hash == H_USERNAME) value.username = reader.readString();
            else if (hash == H_EMAIL) value.email = reader.readString();
            else if (hash == H_DEPARTMENT) value.department = reader.readString();
                else reader.skipValue();
            }
            return value;
        }
    }
}
