package org.sjf4j;

import lombok.Getter;
import lombok.Setter;
import org.sjf4j.NodeWalker.Target;
import org.sjf4j.NodeWalker.Order;
import org.sjf4j.NodeWalker.Control;
import org.sjf4j.util.TypeReference;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SimpleExample {

    public static void main(String[] args) {
        basic();
        withPojo();
    }

    public static void basic() {
        String json = "{\n" +
                "  \"id\": 1,\n" +
                "  \"name\": \"Alice\",\n" +
                "  \"active\": true,\n" +
                "  \"tags\": [\"java\", \"json\"],\n" +
                "  \"scores\": [95, 88.8, 0.5],\n" +
                "  \"user\": {\n" +
                "    \"role\": \"coder\",\n" +
                "    \"profile\": {\n" +
                "      \"level\": 7,\n" +
                "      \"values\": [1, \"two\", true, null, { \"x\": 3 }]\n" +
                "    }\n" +
                "  }\n" +
                "}";

        JsonObject jo = JsonObject.fromJson(json);  // Parse JSON string to JsonObject

        /// Basic

        Object nodeId = jo.getNode("id");
        // Retrieve the raw node as an Object without type conversion.

        Integer id = jo.getInteger("id");
        // Retrieve the node as a specific type (int) using `getXx(key)`.
        // Performs an internal cast/conversion if necessary.

        int id2 = jo.getInteger("id", 0);
        // Retrieve the node value with a default if the key is missing.

        String name = jo.get("name", String.class);
        // Retrieve the node with an explicit type parameter.
        // Ensures type-safe casting at runtime.

        String name1 = jo.get("name");
        // Dynamic type inference version of `get()`.
        // Type is inferred based on the context, convenient for shorthand usage.

        String active = jo.asString("active");
        // Retrieve and convert the node value across types using `asXxx(key)`.
        // Supports cross-type casting (e.g., Number → String).

        String active2 = jo.as("active");
        // Dynamic type conversion, short form of `asXxx()`.

        /// By path

        String role = jo.asJsonObject("user").get("role");
        // Chain operations for nested nodes.
        // First converts "user" node to JsonObject, then retrieves "role".

        jo.put("extra", "blabla");
        // See also: `putNonNull()`, `putIfAbsent()`, `computeIfAbsent()`

        jo.toBuilder().putIfAbsent("x", "xx").put("y", "yy");
        // Supports Builder-style chained operations

        jo.remove("extra");
        // See also: `removeIf()`, `forEach()`, `merge()` etc.

        String role2 = jo.getStringByPath("$.user.role");
        // `getXxByPath()` supports JSON Path expressions

        String role3 = jo.getByPath("/user/role");
        // And JSON Pointer as an alternative

        String role4 = jo.asByPath("$..role");
        // Supports descendant operator for deep traversal

        jo.ensurePutByPath("/aa/bb", "cc");
        // Automatically creates intermediate nodes!! e.g., {"aa":{"bb":"cc"},..}

        jo.ensurePutNonNullByPath("$.scores[3]", 100);
        // Supports array index insertion

        List<String> tags = jo.findAll("$.tags[*]", String.class);
        // Supports Wildcard '.*' or '[*]', `findAll()` return a list of nodes

        List<Short> scores = jo.findAllAs("$.scores[0:3]", Short.class);
        // Supports Slice '[from:to:step]'

        List<Object> unions = jo.findAllNodes("$.user['role','profile']");
        // Supports Union '[A,B,..]' of multiple fields

        /// Walk and stream

        jo.walk(Target.CONTAINER, Order.BOTTOM_UP, (path, node) -> {
            // Target: CONTAINER or VALUE
            // Order: BOTTOM_UP (leaf-to-root) or TOP_DOWN (root-to-leaf)
            System.out.println("path=" + path + ", node=" + node);
            return Control.CONTINUE; // CONTINUE to proceed, or STOP if needed
        });

        List<String> tags2 = jo.stream()                    // Follows Java Stream syntax
                .findAll("$.tags[*]", String.class)
                .filter(tag -> tag.length() > 3)            // Filter using Java codes
                .toList();

        int x = jo.stream()
                .findAllAs("$..profile", JsonObject.class)  // Primary find all
                .filter(n -> n.hasNonNull("values"))
                .findAs("$..x", Integer.class)              // Secondary find one
                .findFirst()
                .orElse(4);

        double avgScore = jo.stream()
                .findAll("$.scores[*]", Double.class)
                .map(d -> d < 60 ? 60 : d)                  // No one failed!
                .collect(Collectors.averagingDouble(s -> s));
    }


    // POJO example
    @Getter @Setter
    static class User {
        int id;
        String name;
        List<User> friends;
    }

    // JOJO example
    @Getter @Setter
    static class User2 extends JsonObject {
        int id;
        String name;
        List<User2> friends;
    }

    public static void withPojo() {

        String json = "{\n" +
                "  \"id\": 1,\n" +
                "  \"name\": \"Alice\",\n" +
                "  \"friends\": [\n" +
                "    { \"id\": 2, \"name\": \"Bill\", \"active\": true },\n" +
                "    {\n" +
                "      \"id\": 3,\n" +
                "      \"name\": \"Cindy\",\n" +
                "      \"friends\": [\n" +
                "        {\"id\": 4, \"name\": \"Dino\"},\n" +
                "        {\"id\": 5, \"info\": \"bla bla\"}\n" +
                "      ]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"age\": 18\n" +
                "}\n";

        JsonObject jo = Sjf4j.fromJson(json);               // = JsonObject.fromJson(json), to JsonObject
        Map<String, Object> map = Sjf4j.fromJson(json,      // to Map
                new TypeReference<Map<String, Object>>() {});
        User user = Sjf4j.fromJson(json, User.class);       // to POJO
        User2 user2 = Sjf4j.fromJson(json, User2.class);    // to JOJO

        // Serialize back to JSON
        System.out.println("jo=" + jo.toJson());            // = Sjf4j.toJson(jo)
        System.out.println("map=" + Sjf4j.toJson(map));     // Output dynamic nodes in Map
        System.out.println("user=" + Sjf4j.toJson(user));   // Only outputs fields defined in User
        System.out.println("user2=" + user2.toJson());
        // Outputs both fixed fields in User2 and dynamic nodes in super JsonObject

        // YAML is handled the same way as JSON
        jo = JsonObject.fromYaml(jo.toYaml());

        // Limited conversion to/from Properties:
        jo.toProperties(System.getProperties());    // {"aa":{"bb":[{"cc":"dd"}]}} => aa.bb[0].cc=dd

        // JsonObject <==> Map
        Map<String, Object> tmpMap = jo.toMap();
        JsonObject tmpJo = new JsonObject(map);    // Just wrap it

        // JsonObject <==> POJO/JOJO
        User tmpUser = jo.toPojo(User.class);
        tmpJo = Sjf4j.fromPojo(user2);

        // JOJO <==> POJO
        tmpUser = user2.toPojo(User.class);
        User2 tmpUser2 = Sjf4j.fromPojo(user, User2.class);

        System.out.println("keys=" + user2.keySet());
        // ["id",  "name",  "friends",  "age"]
        //   └────────┼─────────┘         │
        //            ↓                   ↓
        //      Fields in POJO       Property in JsonObject

        System.out.println("name=" + user2.getName());
        // = user2.getString("name"));

        user2.put("name", "Jenny");
        // = user2.setName("Jenny")

        String bill = user2.getFriends().get(0).getName();
        // = user2.getStringByPath("$.friends[0].name")

        int allUsers = user2.findAllNodes("$..id").size();
        // Use powerful methods from JsonObject
    }


}
