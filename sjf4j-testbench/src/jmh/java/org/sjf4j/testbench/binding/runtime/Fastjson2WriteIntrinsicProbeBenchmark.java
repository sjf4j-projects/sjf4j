package org.sjf4j.testbench.binding.runtime;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONWriter;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Writer;
import org.sjf4j.binding.CompiledName;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Experimental investigation of Fastjson2 2.0.59 intrinsic packed field-name
 * writes (writeName2Raw..writeName16Raw), vs pre-encoded char[] field names.
 * This has no changes to the SJF4J production source.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations=4, time=300, timeUnit=TimeUnit.MILLISECONDS)
@Measurement(iterations=5, time=300, timeUnit=TimeUnit.MILLISECONDS)
@Fork(2)
@Threads(1)
public class Fastjson2WriteIntrinsicProbeBenchmark {
    static final JojoReadBenchmark.PojoModel USER = new JojoReadBenchmark.PojoModel();
    static final JSONWriter.Context CTX = JSONFactory.createWriteContext(JSONWriter.Feature.WriteNulls);
    static final Fastjson2Binder BINDER = new Fastjson2Binder();
    static final String[] NAMES = {"id", "createdAt", "updatedAt", "reputation", "loginCount", "age", "active", "verified", "admin", "suspended", "score", "latitude", "longitude", "username", "email", "department"};
    static final char[][] RAW = new char[NAMES.length][];
    static final long[] PACKED0 = new long[NAMES.length];
    static final long[] PACKED1 = new long[NAMES.length];
    static final CompiledName[] PREPARED = new CompiledName[NAMES.length];

    static {
        USER.id = 839201L;
        USER.createdAt = 1700000000123L;
        USER.updatedAt = 1701234567890L;
        USER.reputation = 9876543210L;
        USER.loginCount = 421;
        USER.age = 34;
        USER.active = true;
        USER.verified = true;
        USER.admin = false;
        USER.suspended = false;
        USER.score = 98.75;
        USER.latitude = 37.7749;
        USER.longitude = -122.4194;
        USER.username = "alice.builder";
        USER.email = null;
        USER.department = "Platform Engineering";
        JSONWriter json = JSONWriter.of(CTX);
        Fastjson2Writer writer = new Fastjson2Writer(json);
        for (int i=0;i<NAMES.length;i++) {
            String name = NAMES[i];
            RAW[i] = ("\\"" + name + "\":").toCharArray();
            PREPARED[i] = writer.createCompiledName(name);
            int len=name.length();
            if (len < 2 || len > 13) throw new IllegalArgumentException(name);
            String first = len==8 ? name : len<=7 ? "\"" + name + "\":" : "\"" + name.substring(0,7);
            String second = len<=8 ? "" : name.substring(7)+"\":";
            PACKED0[i] = pack(first);
            PACKED1[i] = pack(second);
        }
        json.close();
    }

    private static long pack(String value) {
        byte[] bytes=value.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length>8) throw new IllegalArgumentException(value);
        boolean little=ByteOrder.nativeOrder()==ByteOrder.LITTLE_ENDIAN;
        long x=0;
        for (int i=0;i<bytes.length;i++) {
            x |= (bytes[i] & 0xffL) << (little ? (8*i) : (8*(7-i)));
        }
        return x;
    }

    @Setup(Level.Trial)
    public void verify() {
        Object expected = JSON.parse(JSON.toJSONString(USER,CTX));
        verifyOne("directRawName",expected,directRawName());
        verifyOne("directPackedName",expected,directPackedName());
        verifyOne("directStandardName",expected,directStandardName());
        verifyOne("directWrapperName",expected,directWrapperName());
        verifyOne("runtimePojo",expected,runtimePojo());
    }
    private static void verifyOne(String name,Object reference,String text) {
        Object actual=JSON.parse(text);
        if (!reference.equals(actual)) throw new IllegalStateException(name+" differs: "+reference+" vs "+actual);
    }

    @Benchmark
    public String nativePojo() {
        return JSON.toJSONString(USER,CTX);
    }
    @Benchmark
    public String runtimePojo() {
        return BINDER.writeNodeAsString(USER);
    }
    @Benchmark
    public String directRawName() {
        try (JSONWriter writer=JSONWriter.of(CTX)) {
            writer.startObject();
            writer.writeNameRaw(RAW[0]);
            writer.writeInt64(USER.id);
            writer.writeNameRaw(RAW[1]);
            writer.writeInt64(USER.createdAt);
            writer.writeNameRaw(RAW[2]);
            writer.writeInt64(USER.updatedAt);
            writer.writeNameRaw(RAW[3]);
            writer.writeInt64(USER.reputation);
            writer.writeNameRaw(RAW[4]);
            writer.writeInt32(USER.loginCount);
            writer.writeNameRaw(RAW[5]);
            writer.writeInt32(USER.age);
            writer.writeNameRaw(RAW[6]);
            writer.writeBool(USER.active);
            writer.writeNameRaw(RAW[7]);
            writer.writeBool(USER.verified);
            writer.writeNameRaw(RAW[8]);
            writer.writeBool(USER.admin);
            writer.writeNameRaw(RAW[9]);
            writer.writeBool(USER.suspended);
            writer.writeNameRaw(RAW[10]);
            writer.writeDouble(USER.score);
            writer.writeNameRaw(RAW[11]);
            writer.writeDouble(USER.latitude);
            writer.writeNameRaw(RAW[12]);
            writer.writeDouble(USER.longitude);
            writer.writeNameRaw(RAW[13]);
            writer.writeString(USER.username);
            writer.writeNameRaw(RAW[14]);
            writer.writeString(USER.email);
            writer.writeNameRaw(RAW[15]);
            writer.writeString(USER.department);
            writer.endObject();
            return writer.toString();
        }
    }
    @Benchmark
    public String directPackedName() {
        try (JSONWriter writer=JSONWriter.of(CTX)) {
            writer.startObject();
            writer.writeName2Raw(PACKED0[0]);
            writer.writeInt64(USER.id);
            writer.writeName9Raw(PACKED0[1], (int) PACKED1[1]);
            writer.writeInt64(USER.createdAt);
            writer.writeName9Raw(PACKED0[2], (int) PACKED1[2]);
            writer.writeInt64(USER.updatedAt);
            writer.writeName10Raw(PACKED0[3], PACKED1[3]);
            writer.writeInt64(USER.reputation);
            writer.writeName10Raw(PACKED0[4], PACKED1[4]);
            writer.writeInt32(USER.loginCount);
            writer.writeName3Raw(PACKED0[5]);
            writer.writeInt32(USER.age);
            writer.writeName6Raw(PACKED0[6]);
            writer.writeBool(USER.active);
            writer.writeName8Raw(PACKED0[7]);
            writer.writeBool(USER.verified);
            writer.writeName5Raw(PACKED0[8]);
            writer.writeBool(USER.admin);
            writer.writeName9Raw(PACKED0[9], (int) PACKED1[9]);
            writer.writeBool(USER.suspended);
            writer.writeName5Raw(PACKED0[10]);
            writer.writeDouble(USER.score);
            writer.writeName8Raw(PACKED0[11]);
            writer.writeDouble(USER.latitude);
            writer.writeName9Raw(PACKED0[12], (int) PACKED1[12]);
            writer.writeDouble(USER.longitude);
            writer.writeName8Raw(PACKED0[13]);
            writer.writeString(USER.username);
            writer.writeName5Raw(PACKED0[14]);
            writer.writeString(USER.email);
            writer.writeName10Raw(PACKED0[15], PACKED1[15]);
            writer.writeString(USER.department);
            writer.endObject();
            return writer.toString();
        }
    }
    @Benchmark
    public String directStandardName() {
        try (JSONWriter writer=JSONWriter.of(CTX)) {
            writer.startObject();
            writer.writeName(NAMES[0]);
            writer.writeColon();
            writer.writeInt64(USER.id);
            writer.writeName(NAMES[1]);
            writer.writeColon();
            writer.writeInt64(USER.createdAt);
            writer.writeName(NAMES[2]);
            writer.writeColon();
            writer.writeInt64(USER.updatedAt);
            writer.writeName(NAMES[3]);
            writer.writeColon();
            writer.writeInt64(USER.reputation);
            writer.writeName(NAMES[4]);
            writer.writeColon();
            writer.writeInt32(USER.loginCount);
            writer.writeName(NAMES[5]);
            writer.writeColon();
            writer.writeInt32(USER.age);
            writer.writeName(NAMES[6]);
            writer.writeColon();
            writer.writeBool(USER.active);
            writer.writeName(NAMES[7]);
            writer.writeColon();
            writer.writeBool(USER.verified);
            writer.writeName(NAMES[8]);
            writer.writeColon();
            writer.writeBool(USER.admin);
            writer.writeName(NAMES[9]);
            writer.writeColon();
            writer.writeBool(USER.suspended);
            writer.writeName(NAMES[10]);
            writer.writeColon();
            writer.writeDouble(USER.score);
            writer.writeName(NAMES[11]);
            writer.writeColon();
            writer.writeDouble(USER.latitude);
            writer.writeName(NAMES[12]);
            writer.writeColon();
            writer.writeDouble(USER.longitude);
            writer.writeName(NAMES[13]);
            writer.writeColon();
            writer.writeString(USER.username);
            writer.writeName(NAMES[14]);
            writer.writeColon();
            writer.writeString(USER.email);
            writer.writeName(NAMES[15]);
            writer.writeColon();
            writer.writeString(USER.department);
            writer.endObject();
            return writer.toString();
        }
    }
    @Benchmark
    public String directWrapperName() {
        try (JSONWriter json=JSONWriter.of(CTX)) {
            Fastjson2Writer writer=new Fastjson2Writer(json);
            writer.startObject();
            writer.writeName(PREPARED[0]);
            writer.writeLongValue(USER.id);
            writer.writeName(PREPARED[1]);
            writer.writeLongValue(USER.createdAt);
            writer.writeName(PREPARED[2]);
            writer.writeLongValue(USER.updatedAt);
            writer.writeName(PREPARED[3]);
            writer.writeLongValue(USER.reputation);
            writer.writeName(PREPARED[4]);
            writer.writeIntValue(USER.loginCount);
            writer.writeName(PREPARED[5]);
            writer.writeIntValue(USER.age);
            writer.writeName(PREPARED[6]);
            writer.writeBooleanValue(USER.active);
            writer.writeName(PREPARED[7]);
            writer.writeBooleanValue(USER.verified);
            writer.writeName(PREPARED[8]);
            writer.writeBooleanValue(USER.admin);
            writer.writeName(PREPARED[9]);
            writer.writeBooleanValue(USER.suspended);
            writer.writeName(PREPARED[10]);
            writer.writeDoubleValue(USER.score);
            writer.writeName(PREPARED[11]);
            writer.writeDoubleValue(USER.latitude);
            writer.writeName(PREPARED[12]);
            writer.writeDoubleValue(USER.longitude);
            writer.writeName(PREPARED[13]);
            writer.writeString(USER.username);
            writer.writeName(PREPARED[14]);
            writer.writeString(USER.email);
            writer.writeName(PREPARED[15]);
            writer.writeString(USER.department);
            writer.endObject();
            return json.toString();
        }
    }
}
