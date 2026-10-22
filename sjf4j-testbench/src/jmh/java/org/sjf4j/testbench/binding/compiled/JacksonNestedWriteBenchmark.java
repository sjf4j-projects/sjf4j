package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
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
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Isolates Jackson2/3 compiled Friend writing from List<Friend> traversal and
 * the enclosing User method. The same preconstructed inputs are used by
 * Compiled, native databind, and Blackbird for each operation.
 *
 * Every measurement includes generator/output allocation and materialization
 * of the returned String or byte[]; construction of input objects and
 * correctness checks happen only in trial setup.
 *
 * Default list has three entries (Friend, null, Friend), matching the original
 * CompiledWriteBenchmark fixture. Change -p friendCount=0,1,2,3,4,8 and
 * -p listKind=array,linked to isolate the per-element cost and List#get(i).
 * Use -p nullSecond=false,true to separate null-element handling.
 * The default input contains null at index 1, matching the User fixture.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
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

    private static final ObjectMapper JACKSON2_NATIVE = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final ObjectMapper JACKSON2_BLACKBIRD = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new BlackbirdModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final JsonMapper JACKSON3_NATIVE = new JsonMapper();
    private static final JsonMapper JACKSON3_BLACKBIRD = JsonMapper.builder()
            .addModule(new tools.jackson.module.blackbird.BlackbirdModule()).build();

    private static final Jackson2CompiledBinder JACKSON2_COMPILED =
            CompiledInstances.global(Jackson2CompiledBinder.class);
    private static final Jackson3CompiledBinder JACKSON3_COMPILED =
            CompiledInstances.global(Jackson3CompiledBinder.class);

    private Friend friend;
    private List<Friend> friends;
    private User user;
    private User userWithNullFriends;

    @Setup(org.openjdk.jmh.annotations.Level.Trial)
    public void setup() throws Exception {
        User fixture = JACKSON2_NATIVE.readValue(CompiledReadBenchmark.JSON_DATA, User.class);
        // Verify, rather than assume, the concrete List chosen by Jackson databind.
        if (!(fixture.getFriends() instanceof ArrayList)) {
            throw new IllegalStateException("Jackson2 List<Friend> default is "
                    + (fixture.getFriends() == null ? "null" : fixture.getFriends().getClass()));
        }
        if (friendCount < 0) {
            throw new IllegalArgumentException("friendCount must be >= 0");
        }

        friend = fixture.getFriends().get(0);
        if ("array".equals(listKind)) {
            friends = new ArrayList<>(friendCount);
        } else if ("linked".equals(listKind)) {
            friends = new LinkedList<>();
        } else {
            throw new IllegalArgumentException("listKind must be array or linked: " + listKind);
        }

        for (int i = 0; i < friendCount; i++) {
            if (i < fixture.getFriends().size()) {
                Friend value = fixture.getFriends().get(i);
                if (value == null && !nullSecond) {
                    value = new Friend(1000L + i, "friend-" + i,
                            1600000000000L + i, (i & 1) == 0);
                }
                friends.add(value);
            } else {
                friends.add(new Friend(1000L + i, "friend-" + i,
                        1600000000000L + i, (i & 1) == 0));
            }
        }

        // Do not mutate shared benchmark data; keep the same User type and
        // all properties identical except for the value of the friends field.
        // This is a null-value control, NOT a User class without that field.
        user = JACKSON2_NATIVE.readValue(CompiledReadBenchmark.JSON_DATA, User.class);
        user.setFriends(friends);
        userWithNullFriends = JACKSON2_NATIVE.readValue(CompiledReadBenchmark.JSON_DATA, User.class);
        userWithNullFriends.setFriends(null);

        verifyOutputs();
    }

    private static void check(String label, JsonNode expected, Object result)
            throws IOException {
        JsonNode actual;
        if (result instanceof String) {
            actual = JACKSON2_NATIVE.readTree((String) result);
        } else if (result instanceof byte[]) {
            actual = JACKSON2_NATIVE.readTree((byte[]) result);
        } else {
            throw new IllegalArgumentException("Unexpected benchmark result: " + result);
        }
        if (!expected.equals(actual)) {
            throw new IllegalStateException("JSON differs for " + label
                    + ": expected=" + expected + ", actual=" + actual);
        }
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2CompiledBinder {
        @WriteTo String writeFriend(Friend value) throws IOException;
        @WriteTo byte[] writeFriendUtf8(Friend value) throws IOException;
        @WriteTo String writeFriends(List<Friend> value) throws IOException;
        @WriteTo byte[] writeFriendsUtf8(List<Friend> value) throws IOException;
        @WriteTo String writeUser(User value) throws IOException;
        @WriteTo byte[] writeUserUtf8(User value) throws IOException;
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3CompiledBinder {
        @WriteTo String writeFriend(Friend value) throws IOException;
        @WriteTo byte[] writeFriendUtf8(Friend value) throws IOException;
        @WriteTo String writeFriends(List<Friend> value) throws IOException;
        @WriteTo byte[] writeFriendsUtf8(List<Friend> value) throws IOException;
        @WriteTo String writeUser(User value) throws IOException;
        @WriteTo byte[] writeUserUtf8(User value) throws IOException;
    }

    private void verifyOutputs() throws Exception {
        JsonNode friendExpected = JACKSON2_NATIVE.readTree(JACKSON2_NATIVE.writeValueAsString(friend));
        check("jackson2_friend_string_compiled", friendExpected, jackson2_friend_string_compiled());
        check("jackson2_friend_string_native", friendExpected, jackson2_friend_string_native());
        check("jackson2_friend_string_blackbird", friendExpected, jackson2_friend_string_blackbird());
        check("jackson2_friend_utf8_compiled", friendExpected, jackson2_friend_utf8_compiled());
        check("jackson2_friend_utf8_native", friendExpected, jackson2_friend_utf8_native());
        check("jackson2_friend_utf8_blackbird", friendExpected, jackson2_friend_utf8_blackbird());
        check("jackson3_friend_string_compiled", friendExpected, jackson3_friend_string_compiled());
        check("jackson3_friend_string_native", friendExpected, jackson3_friend_string_native());
        check("jackson3_friend_string_blackbird", friendExpected, jackson3_friend_string_blackbird());
        check("jackson3_friend_utf8_compiled", friendExpected, jackson3_friend_utf8_compiled());
        check("jackson3_friend_utf8_native", friendExpected, jackson3_friend_utf8_native());
        check("jackson3_friend_utf8_blackbird", friendExpected, jackson3_friend_utf8_blackbird());

        JsonNode listExpected = JACKSON2_NATIVE.readTree(JACKSON2_NATIVE.writeValueAsString(friends));
        check("jackson2_list_string_compiled", listExpected, jackson2_list_string_compiled());
        check("jackson2_list_string_native", listExpected, jackson2_list_string_native());
        check("jackson2_list_string_blackbird", listExpected, jackson2_list_string_blackbird());
        check("jackson2_list_utf8_compiled", listExpected, jackson2_list_utf8_compiled());
        check("jackson2_list_utf8_native", listExpected, jackson2_list_utf8_native());
        check("jackson2_list_utf8_blackbird", listExpected, jackson2_list_utf8_blackbird());
        check("jackson3_list_string_compiled", listExpected, jackson3_list_string_compiled());
        check("jackson3_list_string_native", listExpected, jackson3_list_string_native());
        check("jackson3_list_string_blackbird", listExpected, jackson3_list_string_blackbird());
        check("jackson3_list_utf8_compiled", listExpected, jackson3_list_utf8_compiled());
        check("jackson3_list_utf8_native", listExpected, jackson3_list_utf8_native());
        check("jackson3_list_utf8_blackbird", listExpected, jackson3_list_utf8_blackbird());

        JsonNode userExpected = JACKSON2_NATIVE.readTree(JACKSON2_NATIVE.writeValueAsString(user));
        check("jackson2_user_string_compiled", userExpected, jackson2_user_string_compiled());
        check("jackson2_user_string_native", userExpected, jackson2_user_string_native());
        check("jackson2_user_string_blackbird", userExpected, jackson2_user_string_blackbird());
        check("jackson2_user_utf8_compiled", userExpected, jackson2_user_utf8_compiled());
        check("jackson2_user_utf8_native", userExpected, jackson2_user_utf8_native());
        check("jackson2_user_utf8_blackbird", userExpected, jackson2_user_utf8_blackbird());
        check("jackson3_user_string_compiled", userExpected, jackson3_user_string_compiled());
        check("jackson3_user_string_native", userExpected, jackson3_user_string_native());
        check("jackson3_user_string_blackbird", userExpected, jackson3_user_string_blackbird());
        check("jackson3_user_utf8_compiled", userExpected, jackson3_user_utf8_compiled());
        check("jackson3_user_utf8_native", userExpected, jackson3_user_utf8_native());
        check("jackson3_user_utf8_blackbird", userExpected, jackson3_user_utf8_blackbird());

        JsonNode user_null_friendsExpected = JACKSON2_NATIVE.readTree(JACKSON2_NATIVE.writeValueAsString(userWithNullFriends));
        check("jackson2_user_null_friends_string_compiled", user_null_friendsExpected, jackson2_user_null_friends_string_compiled());
        check("jackson2_user_null_friends_string_native", user_null_friendsExpected, jackson2_user_null_friends_string_native());
        check("jackson2_user_null_friends_string_blackbird", user_null_friendsExpected, jackson2_user_null_friends_string_blackbird());
        check("jackson2_user_null_friends_utf8_compiled", user_null_friendsExpected, jackson2_user_null_friends_utf8_compiled());
        check("jackson2_user_null_friends_utf8_native", user_null_friendsExpected, jackson2_user_null_friends_utf8_native());
        check("jackson2_user_null_friends_utf8_blackbird", user_null_friendsExpected, jackson2_user_null_friends_utf8_blackbird());
        check("jackson3_user_null_friends_string_compiled", user_null_friendsExpected, jackson3_user_null_friends_string_compiled());
        check("jackson3_user_null_friends_string_native", user_null_friendsExpected, jackson3_user_null_friends_string_native());
        check("jackson3_user_null_friends_string_blackbird", user_null_friendsExpected, jackson3_user_null_friends_string_blackbird());
        check("jackson3_user_null_friends_utf8_compiled", user_null_friendsExpected, jackson3_user_null_friends_utf8_compiled());
        check("jackson3_user_null_friends_utf8_native", user_null_friendsExpected, jackson3_user_null_friends_utf8_native());
        check("jackson3_user_null_friends_utf8_blackbird", user_null_friendsExpected, jackson3_user_null_friends_utf8_blackbird());

    }

    @Benchmark
    public String jackson2_friend_string_compiled() throws IOException {
        return JACKSON2_COMPILED.writeFriend(friend);
    }

    @Benchmark
    public String jackson2_friend_string_native() throws IOException {
        return JACKSON2_NATIVE.writeValueAsString(friend);
    }

    @Benchmark
    public String jackson2_friend_string_blackbird() throws IOException {
        return JACKSON2_BLACKBIRD.writeValueAsString(friend);
    }

    @Benchmark
    public byte[] jackson2_friend_utf8_compiled() throws IOException {
        return JACKSON2_COMPILED.writeFriendUtf8(friend);
    }

    @Benchmark
    public byte[] jackson2_friend_utf8_native() throws IOException {
        return JACKSON2_NATIVE.writeValueAsBytes(friend);
    }

    @Benchmark
    public byte[] jackson2_friend_utf8_blackbird() throws IOException {
        return JACKSON2_BLACKBIRD.writeValueAsBytes(friend);
    }

    @Benchmark
    public String jackson3_friend_string_compiled() throws IOException {
        return JACKSON3_COMPILED.writeFriend(friend);
    }

    @Benchmark
    public String jackson3_friend_string_native() throws IOException {
        return JACKSON3_NATIVE.writeValueAsString(friend);
    }

    @Benchmark
    public String jackson3_friend_string_blackbird() throws IOException {
        return JACKSON3_BLACKBIRD.writeValueAsString(friend);
    }

    @Benchmark
    public byte[] jackson3_friend_utf8_compiled() throws IOException {
        return JACKSON3_COMPILED.writeFriendUtf8(friend);
    }

    @Benchmark
    public byte[] jackson3_friend_utf8_native() throws IOException {
        return JACKSON3_NATIVE.writeValueAsBytes(friend);
    }

    @Benchmark
    public byte[] jackson3_friend_utf8_blackbird() throws IOException {
        return JACKSON3_BLACKBIRD.writeValueAsBytes(friend);
    }

    @Benchmark
    public String jackson2_list_string_compiled() throws IOException {
        return JACKSON2_COMPILED.writeFriends(friends);
    }

    @Benchmark
    public String jackson2_list_string_native() throws IOException {
        return JACKSON2_NATIVE.writeValueAsString(friends);
    }

    @Benchmark
    public String jackson2_list_string_blackbird() throws IOException {
        return JACKSON2_BLACKBIRD.writeValueAsString(friends);
    }

    @Benchmark
    public byte[] jackson2_list_utf8_compiled() throws IOException {
        return JACKSON2_COMPILED.writeFriendsUtf8(friends);
    }

    @Benchmark
    public byte[] jackson2_list_utf8_native() throws IOException {
        return JACKSON2_NATIVE.writeValueAsBytes(friends);
    }

    @Benchmark
    public byte[] jackson2_list_utf8_blackbird() throws IOException {
        return JACKSON2_BLACKBIRD.writeValueAsBytes(friends);
    }

    @Benchmark
    public String jackson3_list_string_compiled() throws IOException {
        return JACKSON3_COMPILED.writeFriends(friends);
    }

    @Benchmark
    public String jackson3_list_string_native() throws IOException {
        return JACKSON3_NATIVE.writeValueAsString(friends);
    }

    @Benchmark
    public String jackson3_list_string_blackbird() throws IOException {
        return JACKSON3_BLACKBIRD.writeValueAsString(friends);
    }

    @Benchmark
    public byte[] jackson3_list_utf8_compiled() throws IOException {
        return JACKSON3_COMPILED.writeFriendsUtf8(friends);
    }

    @Benchmark
    public byte[] jackson3_list_utf8_native() throws IOException {
        return JACKSON3_NATIVE.writeValueAsBytes(friends);
    }

    @Benchmark
    public byte[] jackson3_list_utf8_blackbird() throws IOException {
        return JACKSON3_BLACKBIRD.writeValueAsBytes(friends);
    }

    @Benchmark
    public String jackson2_user_string_compiled() throws IOException {
        return JACKSON2_COMPILED.writeUser(user);
    }

    @Benchmark
    public String jackson2_user_string_native() throws IOException {
        return JACKSON2_NATIVE.writeValueAsString(user);
    }

    @Benchmark
    public String jackson2_user_string_blackbird() throws IOException {
        return JACKSON2_BLACKBIRD.writeValueAsString(user);
    }

    @Benchmark
    public byte[] jackson2_user_utf8_compiled() throws IOException {
        return JACKSON2_COMPILED.writeUserUtf8(user);
    }

    @Benchmark
    public byte[] jackson2_user_utf8_native() throws IOException {
        return JACKSON2_NATIVE.writeValueAsBytes(user);
    }

    @Benchmark
    public byte[] jackson2_user_utf8_blackbird() throws IOException {
        return JACKSON2_BLACKBIRD.writeValueAsBytes(user);
    }

    @Benchmark
    public String jackson3_user_string_compiled() throws IOException {
        return JACKSON3_COMPILED.writeUser(user);
    }

    @Benchmark
    public String jackson3_user_string_native() throws IOException {
        return JACKSON3_NATIVE.writeValueAsString(user);
    }

    @Benchmark
    public String jackson3_user_string_blackbird() throws IOException {
        return JACKSON3_BLACKBIRD.writeValueAsString(user);
    }

    @Benchmark
    public byte[] jackson3_user_utf8_compiled() throws IOException {
        return JACKSON3_COMPILED.writeUserUtf8(user);
    }

    @Benchmark
    public byte[] jackson3_user_utf8_native() throws IOException {
        return JACKSON3_NATIVE.writeValueAsBytes(user);
    }

    @Benchmark
    public byte[] jackson3_user_utf8_blackbird() throws IOException {
        return JACKSON3_BLACKBIRD.writeValueAsBytes(user);
    }

    @Benchmark
    public String jackson2_user_null_friends_string_compiled() throws IOException {
        return JACKSON2_COMPILED.writeUser(userWithNullFriends);
    }

    @Benchmark
    public String jackson2_user_null_friends_string_native() throws IOException {
        return JACKSON2_NATIVE.writeValueAsString(userWithNullFriends);
    }

    @Benchmark
    public String jackson2_user_null_friends_string_blackbird() throws IOException {
        return JACKSON2_BLACKBIRD.writeValueAsString(userWithNullFriends);
    }

    @Benchmark
    public byte[] jackson2_user_null_friends_utf8_compiled() throws IOException {
        return JACKSON2_COMPILED.writeUserUtf8(userWithNullFriends);
    }

    @Benchmark
    public byte[] jackson2_user_null_friends_utf8_native() throws IOException {
        return JACKSON2_NATIVE.writeValueAsBytes(userWithNullFriends);
    }

    @Benchmark
    public byte[] jackson2_user_null_friends_utf8_blackbird() throws IOException {
        return JACKSON2_BLACKBIRD.writeValueAsBytes(userWithNullFriends);
    }

    @Benchmark
    public String jackson3_user_null_friends_string_compiled() throws IOException {
        return JACKSON3_COMPILED.writeUser(userWithNullFriends);
    }

    @Benchmark
    public String jackson3_user_null_friends_string_native() throws IOException {
        return JACKSON3_NATIVE.writeValueAsString(userWithNullFriends);
    }

    @Benchmark
    public String jackson3_user_null_friends_string_blackbird() throws IOException {
        return JACKSON3_BLACKBIRD.writeValueAsString(userWithNullFriends);
    }

    @Benchmark
    public byte[] jackson3_user_null_friends_utf8_compiled() throws IOException {
        return JACKSON3_COMPILED.writeUserUtf8(userWithNullFriends);
    }

    @Benchmark
    public byte[] jackson3_user_null_friends_utf8_native() throws IOException {
        return JACKSON3_NATIVE.writeValueAsBytes(userWithNullFriends);
    }

    @Benchmark
    public byte[] jackson3_user_null_friends_utf8_blackbird() throws IOException {
        return JACKSON3_BLACKBIRD.writeValueAsBytes(userWithNullFriends);
    }

}
