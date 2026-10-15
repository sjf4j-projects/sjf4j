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
public class Fastjson2EquivalentWriterBenchmark {
    private static final Fastjson2Binder BINDER = new Fastjson2Binder();
    private static final User USER;
    private static final byte[] N_ID = ("\\\"id\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_CREATEDAT = ("\\\"createdAt\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_UPDATEDAT = ("\\\"updatedAt\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_REPUTATION = ("\\\"reputation\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_LOGINCOUNT = ("\\\"loginCount\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_AGE = ("\\\"age\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_ACTIVE = ("\\\"active\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_VERIFIED = ("\\\"verified\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_ADMIN = ("\\\"admin\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_SUSPENDED = ("\\\"suspended\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_SCORE = ("\\\"score\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_LATITUDE = ("\\\"latitude\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_LONGITUDE = ("\\\"longitude\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_USERNAME = ("\\\"username\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_EMAIL = ("\\\"email\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_DISPLAYNAME = ("\\\"displayName\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_PASSWORDHASH = ("\\\"passwordHash\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_BIO = ("\\\"bio\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_WEBSITE = ("\\\"website\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_DEPARTMENT = ("\\\"department\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_ADDRESS = ("\\\"address\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_TAGS = ("\\\"tags\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_FRIENDS = ("\\\"friends\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_STREET = ("\\\"street\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_CITY = ("\\\"city\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_STATE = ("\\\"state\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_ZIP = ("\\\"zip\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_COUNTRY = ("\\\"country\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_SINCE = ("\\\"since\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_NAME = ("\\\"name\\\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] N_CLOSE = ("\\\"close\\\":").getBytes(StandardCharsets.UTF_8);
    static {
        try {
            USER = new ObjectMapper().readValue(CompiledReadBenchmark.JSON_DATA, User.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    @Benchmark public byte[] direct() {
        try (JSONWriter writer = JSONWriter.ofUTF8()) {
            writeUser(writer, USER);
            return writer.getBytes();
        }
    }
    private static void writeString(JSONWriter w, String v) {
        if (v == null) w.writeNull();
        else w.writeString(v);
    }
    private static void writeUser(JSONWriter w, User v) {
        if (v == null) { w.writeNull(); return; }
        w.startObject();
        w.writeNameRaw(N_ID);
        w.writeInt64(v.getId());
        w.writeNameRaw(N_CREATEDAT);
        w.writeInt64(v.getCreatedAt());
        w.writeNameRaw(N_UPDATEDAT);
        w.writeInt64(v.getUpdatedAt());
        w.writeNameRaw(N_REPUTATION);
        w.writeInt64(v.getReputation());
        w.writeNameRaw(N_LOGINCOUNT);
        w.writeInt32(v.getLoginCount());
        w.writeNameRaw(N_AGE);
        w.writeInt32(v.getAge());
        w.writeNameRaw(N_ACTIVE);
        w.writeBool(v.isActive());
        w.writeNameRaw(N_VERIFIED);
        w.writeBool(v.isVerified());
        w.writeNameRaw(N_ADMIN);
        w.writeBool(v.isAdmin());
        w.writeNameRaw(N_SUSPENDED);
        w.writeBool(v.isSuspended());
        w.writeNameRaw(N_SCORE);
        w.writeDouble(v.getScore());
        w.writeNameRaw(N_LATITUDE);
        w.writeDouble(v.getLatitude());
        w.writeNameRaw(N_LONGITUDE);
        w.writeDouble(v.getLongitude());
        w.writeNameRaw(N_USERNAME);
        writeString(w, v.getUsername());
        w.writeNameRaw(N_EMAIL);
        writeString(w, v.getEmail());
        w.writeNameRaw(N_DISPLAYNAME);
        writeString(w, v.getDisplayName());
        w.writeNameRaw(N_PASSWORDHASH);
        writeString(w, v.getPasswordHash());
        w.writeNameRaw(N_BIO);
        writeString(w, v.getBio());
        w.writeNameRaw(N_WEBSITE);
        writeString(w, v.getWebsite());
        w.writeNameRaw(N_DEPARTMENT);
        writeString(w, v.getDepartment());
        w.writeNameRaw(N_ADDRESS);
        writeAddress(w, v.getAddress());
        w.writeNameRaw(N_TAGS);
        writeTags(w, v.getTags());
        w.writeNameRaw(N_FRIENDS);
        writeFriends(w, v.getFriends());
        w.endObject();
    }
    private static void writeAddress(JSONWriter w, Address v) {
        if (v == null) { w.writeNull(); return; }
        w.startObject();
        w.writeNameRaw(N_STREET);
        writeString(w, v.getStreet());
        w.writeNameRaw(N_CITY);
        writeString(w, v.getCity());
        w.writeNameRaw(N_STATE);
        writeString(w, v.getState());
        w.writeNameRaw(N_ZIP);
        writeString(w, v.getZip());
        w.writeNameRaw(N_COUNTRY);
        writeString(w, v.getCountry());
        w.endObject();
    }
    private static void writeTags(JSONWriter w, List<String> values) {
        if (values == null) { w.writeNull(); return; }
        w.startArray();
        int count = 0;
        for (String v : values) {
            if (count++ > 0) w.writeComma();
            writeString(w, v);
        }
        w.endArray();
    }
    private static void writeFriends(JSONWriter w, List<Friend> values) {
        if (values == null) { w.writeNull(); return; }
        w.startArray();
        int count = 0;
        for (Friend v : values) {
            if (count++ > 0) w.writeComma();
            if (v == null) { w.writeNull(); continue; }
            w.startObject();
            w.writeNameRaw(N_ID);
            w.writeInt64(v.getId());
            w.writeNameRaw(N_SINCE);
            w.writeInt64(v.getSince());
            w.writeNameRaw(N_NAME);
            writeString(w, v.getName());
            w.writeNameRaw(N_CLOSE);
            w.writeBool(v.isClose());
            w.endObject();
        }
        w.endArray();
    }
}
