package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
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
import org.sjf4j.backend.jackson2.binding.Jackson2CompiledName;
import org.sjf4j.backend.jackson2.binding.Jackson2Writer;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Focused Jackson2 List<Friend> write A/B test.
 *
 * The three manual strategies use the same Jackson2 writer lifecycle as the
 * generated compiled entrypoint; only the Friend call boundary changes.
 * Default inputs reproduce [Friend, null, Friend] from CompiledReadBenchmark.
 *
 * Example:
 * -p friendCount=3 -p listKind=array -p nullSecond=true,false
 *
 * All inputs and JSON equality checks are prepared outside measurement.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 15, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class JacksonNestedWriteBenchmark {

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{JacksonNestedWriteBenchmark.class.getName()});
    }

    @Param({"3"})
    public int friendCount;

    @Param({"array"})
    public String listKind;

    @Param({"true"})
    public boolean nullSecond;

    private static final ObjectMapper NATIVE = new ObjectMapper();
    private static final ObjectMapper BLACKBIRD =
            new ObjectMapper().registerModule(new BlackbirdModule());

    private static final Jackson2Binder MANUAL_BINDER = new Jackson2Binder();
    private static final Jackson2CompiledBinder COMPILED =
            CompiledInstances.global(Jackson2CompiledBinder.class);
    private static final Jackson2CompiledBinderFull COMPILED_FULL =
            CompiledInstances.global(Jackson2CompiledBinderFull.class);

    private static final SerializedString ID =
            new Jackson2CompiledName("id").serializedName;
    private static final SerializedString SINCE =
            new Jackson2CompiledName("since").serializedName;
    private static final SerializedString NAME =
            new Jackson2CompiledName("name").serializedName;
    private static final SerializedString CLOSE =
            new Jackson2CompiledName("close").serializedName;

    private List<Friend> friends;

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2CompiledBinder {
        @WriteTo
        String writeFriends(List<Friend> value) throws IOException;
    }

    // Same six write methods as the original broad benchmark; the only
    // measured difference is the generated binder's interface shape.
    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2CompiledBinderFull {
        @WriteTo String writeFriend(Friend value) throws IOException;
        @WriteTo byte[] writeFriendUtf8(Friend value) throws IOException;
        @WriteTo String writeFriends(List<Friend> value) throws IOException;
        @WriteTo byte[] writeFriendsUtf8(List<Friend> value) throws IOException;
        @WriteTo String writeUser(User value) throws IOException;
        @WriteTo byte[] writeUserUtf8(User value) throws IOException;
    }

    @Setup(Level.Trial)
    public void setup() throws Exception {
        User fixture = NATIVE.readValue(CompiledReadBenchmark.JSON_DATA, User.class);
        if (!(fixture.getFriends() instanceof ArrayList)) {
            throw new IllegalStateException("Expected Jackson to deserialize friends as ArrayList");
        }
        if (friendCount < 0) {
            throw new IllegalArgumentException("friendCount must be non-negative");
        }
        if ("array".equals(listKind)) {
            friends = new ArrayList<>(friendCount);
        } else if ("linked".equals(listKind)) {
            friends = new LinkedList<>();
        } else {
            throw new IllegalArgumentException("Unknown listKind: " + listKind);
        }

        List<Friend> fixtureFriends = fixture.getFriends();
        for (int i = 0; i < friendCount; i++) {
            Friend item = i < fixtureFriends.size() ? fixtureFriends.get(i) : null;
            if (item == null && !(nullSecond && i == 1 && i < fixtureFriends.size())) {
                item = new Friend(1000L + i, "friend-" + i,
                        1600000000000L + i, (i & 1) == 0);
            }
            friends.add(item);
        }

        JsonNode expected = NATIVE.readTree(NATIVE.writeValueAsString(friends));
        verify("compiled", expected, jackson2_list_string_compiled());
        verify("compiled_full", expected, jackson2_list_string_compiled_full());
        verify("native", expected, jackson2_list_string_native());
        verify("blackbird", expected, jackson2_list_string_blackbird());
        verify("manual_helper", expected, jackson2_list_string_manual_helper());
        verify("manual_inline", expected, jackson2_list_string_manual_inline());
        verify("manual_noinline", expected, jackson2_list_string_manual_noinline());
    }

    private static void verify(String name, JsonNode expected, String json) throws IOException {
        JsonNode actual = NATIVE.readTree(json);
        if (!expected.equals(actual)) {
            throw new IllegalStateException(name + ": expected " + expected + ", actual " + actual);
        }
    }

    @Benchmark
    public String jackson2_list_string_compiled() throws IOException {
        return COMPILED.writeFriends(friends);
    }

    @Benchmark
    public String jackson2_list_string_compiled_full() throws IOException {
        return COMPILED_FULL.writeFriends(friends);
    }

    @Benchmark
    public String jackson2_list_string_native() throws IOException {
        return NATIVE.writeValueAsString(friends);
    }

    @Benchmark
    public String jackson2_list_string_blackbird() throws IOException {
        return BLACKBIRD.writeValueAsString(friends);
    }

    @Benchmark
    public String jackson2_list_string_manual_helper() throws IOException {
        try (Jackson2Writer writer = MANUAL_BINDER.createWriter()) {
            writer.startDocument();
            writeListHelper(writer.nativeWriter(), friends);
            writer.endDocument();
            writer.flush();
            return writer.toString();
        }
    }

    @Benchmark
    public String jackson2_list_string_manual_inline() throws IOException {
        try (Jackson2Writer writer = MANUAL_BINDER.createWriter()) {
            writer.startDocument();
            writeListInline(writer.nativeWriter(), friends);
            writer.endDocument();
            writer.flush();
            return writer.toString();
        }
    }

    @Benchmark
    public String jackson2_list_string_manual_noinline() throws IOException {
        try (Jackson2Writer writer = MANUAL_BINDER.createWriter()) {
            writer.startDocument();
            writeListNoInline(writer.nativeWriter(), friends);
            writer.endDocument();
            writer.flush();
            return writer.toString();
        }
    }

    private static void writeListHelper(JsonGenerator g, List<Friend> list)
            throws IOException {
        g.writeStartArray();
        for (int i = 0, n = list.size(); i < n; i++) {
            writeFriend(g, list.get(i));
        }
        g.writeEndArray();
    }

    private static void writeListNoInline(JsonGenerator g, List<Friend> list)
            throws IOException {
        g.writeStartArray();
        for (int i = 0, n = list.size(); i < n; i++) {
            writeFriendNoInline(g, list.get(i));
        }
        g.writeEndArray();
    }

    private static void writeFriend(JsonGenerator g, Friend value) throws IOException {
        if (value == null) {
            g.writeNull();
            return;
        }
        g.writeStartObject();
        g.writeFieldName(ID);
        g.writeNumber(value.getId());
        g.writeFieldName(SINCE);
        g.writeNumber(value.getSince());
        g.writeFieldName(NAME);
        String name = value.getName();
        if (name == null) {
            g.writeNull();
        } else {
            g.writeString(name);
        }
        g.writeFieldName(CLOSE);
        g.writeBoolean(value.isClose());
        g.writeEndObject();
    }

    // Keep the call boundary out of the hot List loop.
    // HotSpot may still inline writeFriend() inside this wrapper.
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static void writeFriendNoInline(JsonGenerator g, Friend value)
            throws IOException {
        writeFriend(g, value);
    }

    private static void writeListInline(JsonGenerator g, List<Friend> list)
            throws IOException {
        g.writeStartArray();
        for (int i = 0, n = list.size(); i < n; i++) {
            Friend value = list.get(i);
            if (value == null) {
                g.writeNull();
                continue;
            }
            g.writeStartObject();
            g.writeFieldName(ID);
            g.writeNumber(value.getId());
            g.writeFieldName(SINCE);
            g.writeNumber(value.getSince());
            g.writeFieldName(NAME);
            String name = value.getName();
            if (name == null) {
                g.writeNull();
            } else {
                g.writeString(name);
            }
            g.writeFieldName(CLOSE);
            g.writeBoolean(value.isClose());
            g.writeEndObject();
        }
        g.writeEndArray();
    }
}
