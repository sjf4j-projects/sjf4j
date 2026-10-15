package org.sjf4j.testbench.binding.compiled;

import com.alibaba.fastjson2.JSONWriter;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Writer;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Direct primitive/native-writer control matching the SJF4J field schema.
 * Not a bytecode-identical comparison: compare generated source separately.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@State(Scope.Thread)
public class Fastjson2EquivalentFacadeBenchmark {
    private static final Fastjson2Binder BINDER = new Fastjson2Binder();
    private static final User USER;
    private static final byte[] N_ID = ("\"id\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_CREATEDAT = ("\"createdAt\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_UPDATEDAT = ("\"updatedAt\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_REPUTATION = ("\"reputation\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_LOGINCOUNT = ("\"loginCount\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_AGE = ("\"age\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_ACTIVE = ("\"active\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_VERIFIED = ("\"verified\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_ADMIN = ("\"admin\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_SUSPENDED = ("\"suspended\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_SCORE = ("\"score\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_LATITUDE = ("\"latitude\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_LONGITUDE = ("\"longitude\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_USERNAME = ("\"username\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_EMAIL = ("\"email\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_DISPLAYNAME = ("\"displayName\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_PASSWORDHASH = ("\"passwordHash\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_BIO = ("\"bio\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_WEBSITE = ("\"website\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_DEPARTMENT = ("\"department\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_ADDRESS = ("\"address\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_TAGS = ("\"tags\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_FRIENDS = ("\"friends\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_STREET = ("\"street\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_CITY = ("\"city\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_STATE = ("\"state\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_ZIP = ("\"zip\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_COUNTRY = ("\"country\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_SINCE = ("\"since\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_NAME = ("\"name\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_CLOSE = ("\"close\":").getBytes(StandardCharsets.UTF_8);
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_ID = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("id");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_CREATEDAT = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("createdAt");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_UPDATEDAT = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("updatedAt");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_REPUTATION = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("reputation");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_LOGINCOUNT = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("loginCount");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_AGE = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("age");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_ACTIVE = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("active");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_VERIFIED = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("verified");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_ADMIN = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("admin");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_SUSPENDED = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("suspended");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_SCORE = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("score");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_LATITUDE = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("latitude");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_LONGITUDE = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("longitude");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_USERNAME = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("username");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_EMAIL = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("email");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_DISPLAYNAME = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("displayName");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_PASSWORDHASH = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("passwordHash");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_BIO = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("bio");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_WEBSITE = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("website");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_DEPARTMENT = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("department");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_ADDRESS = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("address");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_TAGS = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("tags");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_FRIENDS = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("friends");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_STREET = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("street");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_CITY = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("city");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_STATE = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("state");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_ZIP = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("zip");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_COUNTRY = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("country");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_SINCE = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("since");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_NAME = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("name");
    private static final org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName C_CLOSE = new org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName("close");
    static {
        try {
            USER = new ObjectMapper().readValue(CompiledReadBenchmark.JSON_DATA, User.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    @Benchmark public byte[] facade() {
        try (Fastjson2Writer writer = JSONWriter.ofUTF8()) {
            writeUser(writer, USER);
            return writer.toBytes();
        }
    }
    private static void writeString(Fastjson2Writer w, String v) {
        if (v == null) w.writeNull();
        else w.writeStringValue(v);
    }
    private static void writeUser(Fastjson2Writer w, User v) {
        if (v == null) { w.writeNull(); return; }
        w.startObject();
        w.writeName(C_ID);
        w.writeLongValue(v.getId());
        w.writeName(C_CREATEDAT);
        w.writeLongValue(v.getCreatedAt());
        w.writeName(C_UPDATEDAT);
        w.writeLongValue(v.getUpdatedAt());
        w.writeName(C_REPUTATION);
        w.writeLongValue(v.getReputation());
        w.writeName(C_LOGINCOUNT);
        w.writeIntValue(v.getLoginCount());
        w.writeName(C_AGE);
        w.writeIntValue(v.getAge());
        w.writeName(C_ACTIVE);
        w.writeBooleanValue(v.isActive());
        w.writeName(C_VERIFIED);
        w.writeBooleanValue(v.isVerified());
        w.writeName(C_ADMIN);
        w.writeBooleanValue(v.isAdmin());
        w.writeName(C_SUSPENDED);
        w.writeBooleanValue(v.isSuspended());
        w.writeName(C_SCORE);
        w.writeDoubleValue(v.getScore());
        w.writeName(C_LATITUDE);
        w.writeDoubleValue(v.getLatitude());
        w.writeName(C_LONGITUDE);
        w.writeDoubleValue(v.getLongitude());
        w.writeName(C_USERNAME);
        writeString(w, v.getUsername());
        w.writeName(C_EMAIL);
        writeString(w, v.getEmail());
        w.writeName(C_DISPLAYNAME);
        writeString(w, v.getDisplayName());
        w.writeName(C_PASSWORDHASH);
        writeString(w, v.getPasswordHash());
        w.writeName(C_BIO);
        writeString(w, v.getBio());
        w.writeName(C_WEBSITE);
        writeString(w, v.getWebsite());
        w.writeName(C_DEPARTMENT);
        writeString(w, v.getDepartment());
        w.writeName(C_ADDRESS);
        writeAddress(w, v.getAddress());
        w.writeName(C_TAGS);
        writeTags(w, v.getTags());
        w.writeName(C_FRIENDS);
        writeFriends(w, v.getFriends());
        w.endObject();
    }
    private static void writeAddress(Fastjson2Writer w, Address v) {
        if (v == null) { w.writeNull(); return; }
        w.startObject();
        w.writeName(C_STREET);
        writeString(w, v.getStreet());
        w.writeName(C_CITY);
        writeString(w, v.getCity());
        w.writeName(C_STATE);
        writeString(w, v.getState());
        w.writeName(C_ZIP);
        writeString(w, v.getZip());
        w.writeName(C_COUNTRY);
        writeString(w, v.getCountry());
        w.endObject();
    }
    private static void writeTags(Fastjson2Writer w, List<String> values) {
        if (values == null) { w.writeNull(); return; }
        w.startArray();
        int count = 0;
        for (String v : values) {
            if (count++ > 0) w.separateElement();
            writeString(w, v);
        }
        w.endArray();
    }
    private static void writeFriends(Fastjson2Writer w, List<Friend> values) {
        if (values == null) { w.writeNull(); return; }
        w.startArray();
        int count = 0;
        for (Friend v : values) {
            if (count++ > 0) w.separateElement();
            if (v == null) { w.writeNull(); continue; }
            w.startObject();
            w.writeName(C_ID);
            w.writeLongValue(v.getId());
            w.writeName(C_SINCE);
            w.writeLongValue(v.getSince());
            w.writeName(C_NAME);
            writeString(w, v.getName());
            w.writeName(C_CLOSE);
            w.writeBooleanValue(v.isClose());
            w.endObject();
        }
        w.endArray();
    }
}
