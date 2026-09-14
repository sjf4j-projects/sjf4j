package org.sjf4j.testbench.binding;

import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.TypeReference;
import org.sjf4j.binding.NodeBinding;
import org.sjf4j.binding.simple.SimpleNodeBinding;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.CommentEvent;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.LoginEvent;
import org.sjf4j.testbench.model.StringValue;
import org.sjf4j.testbench.model.User;
import org.sjf4j.testbench.model.UserEvent;
import org.sjf4j.testbench.model.UserGraph;
import org.sjf4j.testbench.model.Users;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Benchmarks direct tree-to-object binding using prebuilt, application-shaped nodes. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Threads(1)
@State(Scope.Thread)
public class NodeBindingBenchmark {

    private static final Type EVENT_LIST_TYPE = new TypeReference<List<UserEvent>>() {}.getType();
    private static final Type VALUE_MAP_TYPE = new TypeReference<Map<String, StringValue>>() {}.getType();

    private final NodeBinding binding = new SimpleNodeBinding();
    private JsonObject userGraphNode;
    private JsonObject usersNode;
    private JsonObject loginEventNode;
    private JsonObject commentEventNode;
    private JsonArray eventsNode;
    private JsonObject valueMapNode;
    private UserGraph userGraph;

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{NodeBindingBenchmark.class.getName()});
    }

    @Setup(Level.Trial)
    public void setup() {
        List<User> userList = new ArrayList<>();
        JsonArray userNodes = new JsonArray();
        for (int i = 0; i < 12; i++) {
            userList.add(createUser(i));
            userNodes.add(createUserNode(i));
        }

        Users users = new Users();
        users.setUsers(userList);
        users.setTotal(247);
        users.setPage(3);
        users.setGeneratedAt(1_712_345_678_901L);
        usersNode = JsonObject.of("users", userNodes, "total", 247L, "page", 3L,
                "generatedAt", 1_712_345_678_901L);

        LoginEvent loginEvent = new LoginEvent();
        loginEvent.setType("login");
        loginEvent.setUserId(1001L);
        loginEvent.setOccurredAt(1_712_345_600_001L);
        loginEvent.setIpAddress("203.0.113.10");
        loginEvent.setDevice("web-chrome");
        loginEventNode = JsonObject.of("type", "login", "userId", 1001L,
                "occurredAt", 1_712_345_600_001L, "ipAddress", "203.0.113.10", "device", "web-chrome");

        CommentEvent commentEvent = new CommentEvent();
        commentEvent.setType("comment");
        commentEvent.setUserId(1002L);
        commentEvent.setOccurredAt(1_712_345_600_042L);
        commentEvent.setCommentId(800_042L);
        commentEvent.setBody("The migration completed without retries.");
        commentEvent.setReplyTo(800_001L);
        commentEventNode = JsonObject.of("type", "comment", "userId", 1002L,
                "occurredAt", 1_712_345_600_042L, "commentId", 800_042L,
                "body", "The migration completed without retries.", "replyTo", 800_001L);
        eventsNode = JsonArray.of(loginEventNode, commentEventNode, loginEventNode, commentEventNode,
                loginEventNode, commentEventNode, loginEventNode, commentEventNode);

        Map<String, StringValue> labels = new LinkedHashMap<>();
        JsonObject labelNodes = new JsonObject();
        for (String label : List.of("region", "environment", "service", "owner", "release", "tier")) {
            StringValue value = new StringValue(label + "-value");
            labels.put(label, value);
            labelNodes.put(label, value.getValue());
        }
        valueMapNode = labelNodes;

        userGraph = new UserGraph();
        userGraph.setOwner(userList.get(0));
        userGraph.setUsers(users);
        userGraph.setEvents(List.of(loginEvent, commentEvent, loginEvent, commentEvent,
                loginEvent, commentEvent, loginEvent, commentEvent));
        userGraph.setLabels(labels);
        userGraph.setPrimaryLabel(new StringValue("primary-production"));
        userGraphNode = JsonObject.of("owner", createUserNode(0), "users", usersNode, "events", eventsNode,
                "labels", labelNodes, "primaryLabel", "primary-production");
    }

    @Benchmark
    public Object nodeBinding_fullGraph_read() {
        return binding.readNode(userGraphNode, UserGraph.class);
    }

    @Benchmark
    public Object nodeBinding_fullGraph_write() {
        return binding.writeNode(userGraph);
    }

    @Benchmark
    public Object nodeBinding_users_nestedCollection_read() {
        return binding.readNode(usersNode, Users.class);
    }

    @Benchmark
    public Object nodeBinding_oneOf_login_read() {
        return binding.readNode(loginEventNode, UserEvent.class);
    }

    @Benchmark
    public Object nodeBinding_oneOf_comment_read() {
        return binding.readNode(commentEventNode, UserEvent.class);
    }

    @Benchmark
    public Object nodeBinding_oneOf_eventsList_read() {
        return binding.readNode(eventsNode, EVENT_LIST_TYPE);
    }

    @Benchmark
    public Object nodeBinding_nodeValue_map_read() {
        return binding.readNode(valueMapNode, VALUE_MAP_TYPE);
    }

    private static User createUser(int index) {
        User user = new User();
        user.setId(1000L + index);
        user.setCreatedAt(1_700_000_000_000L + index);
        user.setUpdatedAt(1_712_345_000_000L + index);
        user.setReputation(50_000L + index * 11L);
        user.setLoginCount(40 + index);
        user.setAge(25 + index % 20);
        user.setActive(index % 3 != 0);
        user.setVerified(index % 2 == 0);
        user.setAdmin(index == 0);
        user.setSuspended(false);
        user.setScore(84.5 + index);
        user.setLatitude(37.70 + index / 100.0);
        user.setLongitude(-122.40 - index / 100.0);
        user.setUsername("user-" + index);
        user.setEmail("user-" + index + "@example.test");
        user.setDisplayName("User " + index);
        user.setPasswordHash("hash-" + index);
        user.setBio("Platform engineer " + index);
        user.setWebsite("https://example.test/users/" + index);
        user.setDepartment(index % 2 == 0 ? "Platform" : "Product");
        user.setAddress(new Address("Main Street " + index, "San Francisco", "CA", "9410" + index, "US"));
        user.setTags(List.of("java", "json", "team-" + index % 3));
        user.setFriends(List.of(new Friend(2000L + index, "Friend " + index, 1_600_000_000_000L + index, true),
                new Friend(3000L + index, "Colleague " + index, 1_610_000_000_000L + index, false)));
        return user;
    }

    private static JsonObject createUserNode(int index) {
        return JsonObject.of(
                "id", 1000L + index, "createdAt", 1_700_000_000_000L + index,
                "updatedAt", 1_712_345_000_000L + index, "reputation", 50_000L + index * 11L,
                "loginCount", 40L + index, "age", 25L + index % 20, "active", index % 3 != 0,
                "verified", index % 2 == 0, "admin", index == 0, "suspended", false,
                "score", 84.5 + index, "latitude", 37.70 + index / 100.0, "longitude", -122.40 - index / 100.0,
                "username", "user-" + index, "email", "user-" + index + "@example.test",
                "displayName", "User " + index, "passwordHash", "hash-" + index,
                "bio", "Platform engineer " + index, "website", "https://example.test/users/" + index,
                "department", index % 2 == 0 ? "Platform" : "Product",
                "address", JsonObject.of("street", "Main Street " + index, "city", "San Francisco",
                        "state", "CA", "zip", "9410" + index, "country", "US"),
                "tags", JsonArray.of("java", "json", "team-" + index % 3),
                "friends", JsonArray.of(
                        JsonObject.of("id", 2000L + index, "since", 1_600_000_000_000L + index,
                                "name", "Friend " + index, "close", true),
                        JsonObject.of("id", 3000L + index, "since", 1_610_000_000_000L + index,
                                "name", "Colleague " + index, "close", false)));
    }
}
