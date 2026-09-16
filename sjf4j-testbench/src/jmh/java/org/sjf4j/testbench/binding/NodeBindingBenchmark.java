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
import org.sjf4j.CompiledInstances;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.TypeReference;
import org.sjf4j.annotation.mapping.CompiledMapper;
import org.sjf4j.binding.NodeBinder;
import org.sjf4j.binding.simple.SimpleNodeBinder;
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

    private final NodeBinder binding = new SimpleNodeBinder();
    private JsonObject userGraphNode;
    private JsonObject usersNode;
    private JsonObject loginEventNode;
    private JsonObject commentEventNode;
    private JsonArray eventsNode;
    private JsonObject valueMapNode;
    private UserGraph userGraph;
    private UserGraphMapper compiledMapper;

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
        compiledMapper = CompiledInstances.of(UserGraphMapper.class);
        assertGraph(compiledMapper.map(userGraphNode), userGraph);
        assertGraph((UserGraph) binding.readNode(userGraphNode, UserGraph.class), userGraph);
        assertGraph(readUserGraph(userGraphNode), userGraph);
    }

    @Benchmark
    public Object nodeBinding_fullGraph_read() {
        return binding.readNode(userGraphNode, UserGraph.class);
    }

    @Benchmark
    public UserGraph compiledMapper_fullGraph_read() {
        return compiledMapper.map(userGraphNode);
    }

    @Benchmark
    public Object nodeBinding_fullGraph_write() {
        return binding.writeNode(userGraph);
    }

    @Benchmark
    public Object handwritten_fullGraph_read() {
        return readUserGraph(userGraphNode);
    }

    @Benchmark
    public Object handwritten_fullGraph_write() {
        return writeUserGraph(userGraph);
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

    @CompiledMapper
    public interface UserGraphMapper {
        UserGraph map(JsonObject source);
    }

    private static void assertGraph(UserGraph actual, UserGraph expected) {
        require(actual != null && actual.getOwner() != null && actual.getUsers() != null);
        assertUser(actual.getOwner(), expected.getOwner());
        require(actual.getUsers().getTotal() == expected.getUsers().getTotal()
                && actual.getUsers().getPage() == expected.getUsers().getPage()
                && actual.getUsers().getGeneratedAt() == expected.getUsers().getGeneratedAt()
                && actual.getUsers().getUsers().size() == expected.getUsers().getUsers().size());
        for (int i = 0; i < actual.getUsers().getUsers().size(); i++) assertUser(actual.getUsers().getUsers().get(i), expected.getUsers().getUsers().get(i));
        require(actual.getEvents().size() == expected.getEvents().size());
        for (int i = 0; i < actual.getEvents().size(); i++) assertEvent(actual.getEvents().get(i), expected.getEvents().get(i));
        require(actual.getLabels().size() == expected.getLabels().size());
        for (Map.Entry<String, StringValue> entry : expected.getLabels().entrySet())
            require(actual.getLabels().containsKey(entry.getKey()) && entry.getValue().getValue().equals(actual.getLabels().get(entry.getKey()).getValue()));
        require(expected.getPrimaryLabel().getValue().equals(actual.getPrimaryLabel().getValue()));
    }

    private static void assertUser(User actual, User expected) {
        require(actual.getId() == expected.getId() && actual.getCreatedAt() == expected.getCreatedAt()
                && actual.getUpdatedAt() == expected.getUpdatedAt() && actual.getReputation() == expected.getReputation()
                && actual.getLoginCount() == expected.getLoginCount() && actual.getAge() == expected.getAge()
                && actual.isActive() == expected.isActive() && actual.isVerified() == expected.isVerified()
                && actual.isAdmin() == expected.isAdmin() && actual.isSuspended() == expected.isSuspended()
                && actual.getScore() == expected.getScore() && actual.getLatitude() == expected.getLatitude()
                && actual.getLongitude() == expected.getLongitude() && actual.getUsername().equals(expected.getUsername())
                && actual.getEmail().equals(expected.getEmail()) && actual.getDisplayName().equals(expected.getDisplayName())
                && actual.getPasswordHash().equals(expected.getPasswordHash()) && actual.getBio().equals(expected.getBio())
                && actual.getWebsite().equals(expected.getWebsite()) && actual.getDepartment().equals(expected.getDepartment()));
        Address a = actual.getAddress(), e = expected.getAddress();
        require(a.getStreet().equals(e.getStreet()) && a.getCity().equals(e.getCity()) && a.getState().equals(e.getState())
                && a.getZip().equals(e.getZip()) && a.getCountry().equals(e.getCountry()) && actual.getTags().equals(expected.getTags())
                && actual.getFriends().size() == expected.getFriends().size());
        for (int i = 0; i < actual.getFriends().size(); i++) {
            Friend f = actual.getFriends().get(i), ef = expected.getFriends().get(i);
            require(f.getId() == ef.getId() && f.getSince() == ef.getSince() && f.isClose() == ef.isClose() && f.getName().equals(ef.getName()));
        }
    }

    private static void assertEvent(UserEvent actual, UserEvent expected) {
        require(actual.getClass() == expected.getClass());
        if (actual instanceof LoginEvent) {
            LoginEvent a = (LoginEvent) actual, e = (LoginEvent) expected;
            require(a.getType().equals(e.getType()) && a.getUserId() == e.getUserId() && a.getOccurredAt() == e.getOccurredAt()
                    && a.getIpAddress().equals(e.getIpAddress()) && a.getDevice().equals(e.getDevice()));
        } else {
            CommentEvent a = (CommentEvent) actual, e = (CommentEvent) expected;
            require(a.getType().equals(e.getType()) && a.getUserId() == e.getUserId() && a.getOccurredAt() == e.getOccurredAt()
                    && a.getCommentId() == e.getCommentId() && a.getBody().equals(e.getBody()) && a.getReplyTo() == e.getReplyTo());
        }
    }

    private static void require(boolean value) {
        if (!value) throw new AssertionError("compiled mapper graph differs from expected graph");
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

    private static UserGraph readUserGraph(JsonObject node) {
        UserGraph graph = new UserGraph();
        graph.setOwner(readUser(node.getJsonObject("owner")));
        graph.setUsers(readUsers(node.getJsonObject("users")));
        graph.setEvents(readEvents(node.getJsonArray("events")));
        graph.setLabels(readLabels(node.getJsonObject("labels")));
        graph.setPrimaryLabel(StringValue.fromRaw(node.getString("primaryLabel")));
        return graph;
    }

    private static Users readUsers(JsonObject node) {
        Users users = new Users();
        JsonArray userNodes = node.getJsonArray("users");
        List<User> userList = new ArrayList<>(userNodes.size());
        for (int i = 0; i < userNodes.size(); i++) {
            userList.add(readUser(userNodes.getJsonObject(i)));
        }
        users.setUsers(userList);
        users.setTotal(node.getInt("total"));
        users.setPage(node.getInt("page"));
        users.setGeneratedAt(node.getLong("generatedAt"));
        return users;
    }

    private static User readUser(JsonObject node) {
        User user = new User();
        user.setId(node.getLong("id"));
        user.setCreatedAt(node.getLong("createdAt"));
        user.setUpdatedAt(node.getLong("updatedAt"));
        user.setReputation(node.getLong("reputation"));
        user.setLoginCount(node.getInt("loginCount"));
        user.setAge(node.getInt("age"));
        user.setActive(node.getBoolean("active"));
        user.setVerified(node.getBoolean("verified"));
        user.setAdmin(node.getBoolean("admin"));
        user.setSuspended(node.getBoolean("suspended"));
        user.setScore(node.getDouble("score"));
        user.setLatitude(node.getDouble("latitude"));
        user.setLongitude(node.getDouble("longitude"));
        user.setUsername(node.getString("username"));
        user.setEmail(node.getString("email"));
        user.setDisplayName(node.getString("displayName"));
        user.setPasswordHash(node.getString("passwordHash"));
        user.setBio(node.getString("bio"));
        user.setWebsite(node.getString("website"));
        user.setDepartment(node.getString("department"));
        user.setAddress(readAddress(node.getJsonObject("address")));

        JsonArray tagNodes = node.getJsonArray("tags");
        List<String> tags = new ArrayList<>(tagNodes.size());
        for (int i = 0; i < tagNodes.size(); i++) {
            tags.add(tagNodes.getString(i));
        }
        user.setTags(tags);

        JsonArray friendNodes = node.getJsonArray("friends");
        List<Friend> friends = new ArrayList<>(friendNodes.size());
        for (int i = 0; i < friendNodes.size(); i++) {
            friends.add(readFriend(friendNodes.getJsonObject(i)));
        }
        user.setFriends(friends);
        return user;
    }

    private static Address readAddress(JsonObject node) {
        return new Address(node.getString("street"), node.getString("city"), node.getString("state"),
                node.getString("zip"), node.getString("country"));
    }

    private static Friend readFriend(JsonObject node) {
        return new Friend(node.getLong("id"), node.getString("name"), node.getLong("since"),
                node.getBoolean("close"));
    }

    private static List<UserEvent> readEvents(JsonArray nodes) {
        List<UserEvent> events = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            events.add(readEvent(nodes.getJsonObject(i)));
        }
        return events;
    }

    private static UserEvent readEvent(JsonObject node) {
        String type = node.getString("type");
        if ("login".equals(type)) {
            LoginEvent event = new LoginEvent();
            event.setType(type);
            event.setUserId(node.getLong("userId"));
            event.setOccurredAt(node.getLong("occurredAt"));
            event.setIpAddress(node.getString("ipAddress"));
            event.setDevice(node.getString("device"));
            return event;
        }
        if ("comment".equals(type)) {
            CommentEvent event = new CommentEvent();
            event.setType(type);
            event.setUserId(node.getLong("userId"));
            event.setOccurredAt(node.getLong("occurredAt"));
            event.setCommentId(node.getLong("commentId"));
            event.setBody(node.getString("body"));
            event.setReplyTo(node.getLong("replyTo"));
            return event;
        }
        throw new IllegalArgumentException("Unknown user event type: " + type);
    }

    private static Map<String, StringValue> readLabels(JsonObject node) {
        Map<String, StringValue> labels = new LinkedHashMap<>(node.size());
        for (String key : node.keySet()) {
            labels.put(key, StringValue.fromRaw(node.getString(key)));
        }
        return labels;
    }

    private static JsonObject writeUserGraph(UserGraph graph) {
        JsonObject node = new JsonObject();
        node.put("owner", writeUser(graph.getOwner()));
        node.put("users", writeUsers(graph.getUsers()));
        node.put("events", writeEvents(graph.getEvents()));
        node.put("labels", writeLabels(graph.getLabels()));
        node.put("primaryLabel", graph.getPrimaryLabel().toRaw());
        return node;
    }

    private static JsonObject writeUsers(Users users) {
        JsonObject node = new JsonObject();
        JsonArray userNodes = new JsonArray();
        for (User user : users.getUsers()) {
            userNodes.add(writeUser(user));
        }
        node.put("users", userNodes);
        node.put("total", users.getTotal());
        node.put("page", users.getPage());
        node.put("generatedAt", users.getGeneratedAt());
        return node;
    }

    private static JsonObject writeUser(User user) {
        JsonObject node = new JsonObject();
        node.put("id", user.getId());
        node.put("createdAt", user.getCreatedAt());
        node.put("updatedAt", user.getUpdatedAt());
        node.put("reputation", user.getReputation());
        node.put("loginCount", user.getLoginCount());
        node.put("age", user.getAge());
        node.put("active", user.isActive());
        node.put("verified", user.isVerified());
        node.put("admin", user.isAdmin());
        node.put("suspended", user.isSuspended());
        node.put("score", user.getScore());
        node.put("latitude", user.getLatitude());
        node.put("longitude", user.getLongitude());
        node.put("username", user.getUsername());
        node.put("email", user.getEmail());
        node.put("displayName", user.getDisplayName());
        node.put("passwordHash", user.getPasswordHash());
        node.put("bio", user.getBio());
        node.put("website", user.getWebsite());
        node.put("department", user.getDepartment());
        node.put("address", writeAddress(user.getAddress()));

        JsonArray tags = new JsonArray();
        for (String tag : user.getTags()) {
            tags.add(tag);
        }
        node.put("tags", tags);

        JsonArray friends = new JsonArray();
        for (Friend friend : user.getFriends()) {
            friends.add(writeFriend(friend));
        }
        node.put("friends", friends);
        return node;
    }

    private static JsonObject writeAddress(Address address) {
        return JsonObject.of("street", address.getStreet(), "city", address.getCity(), "state", address.getState(),
                "zip", address.getZip(), "country", address.getCountry());
    }

    private static JsonObject writeFriend(Friend friend) {
        return JsonObject.of("id", friend.getId(), "since", friend.getSince(), "name", friend.getName(),
                "close", friend.isClose());
    }

    private static JsonArray writeEvents(List<UserEvent> events) {
        JsonArray nodes = new JsonArray();
        for (UserEvent event : events) {
            nodes.add(writeEvent(event));
        }
        return nodes;
    }

    private static JsonObject writeEvent(UserEvent event) {
        if (event instanceof LoginEvent login) {
            return JsonObject.of("type", login.getType(), "userId", login.getUserId(),
                    "occurredAt", login.getOccurredAt(), "ipAddress", login.getIpAddress(),
                    "device", login.getDevice());
        }
        if (event instanceof CommentEvent comment) {
            return JsonObject.of("type", comment.getType(), "userId", comment.getUserId(),
                    "occurredAt", comment.getOccurredAt(), "commentId", comment.getCommentId(),
                    "body", comment.getBody(), "replyTo", comment.getReplyTo());
        }
        throw new IllegalArgumentException("Unknown user event class: " + event.getClass().getName());
    }

    private static JsonObject writeLabels(Map<String, StringValue> labels) {
        JsonObject node = new JsonObject();
        for (Map.Entry<String, StringValue> label : labels.entrySet()) {
            node.put(label.getKey(), label.getValue().toRaw());
        }
        return node;
    }
}
