package org.sjf4j;

import org.sjf4j.JsonWalker.Target;
import org.sjf4j.JsonWalker.Order;
import org.sjf4j.JsonWalker.Control;
import org.sjf4j.util.TypeReference;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

public class SimpleExample {

    public static void main(String[] args) {

    }

    public void basic() {
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

        // Basic: `get`, `as`, `put`, `remove`, ...
        JsonObject jo = JsonObject.fromJson(json);
        Object nodeId = jo.getNode("id");                           // 获取原始 Node, 即 Object
        int id = jo.getInteger("id");                               // `getXx()` 获取特定类型的 Node，Type内转换
        String name = jo.get("name", String.class);                 // `get()` 动态指定类型
        String name1 = jo.get("name");                              // 动态指定类型，省略形式
        String active = jo.asString("active");                      // `asXxx()` 获取并转换类型，跨Type转换
        String active2 = jo.as("active");                           // `as()` 动态转换类型，省略形式
        String no = jo.getString("no", "yes");              // 带默认值的获取
        String role = jo.asJsonObject("user").get("role");      // 链式操作

        jo.put("extra", "blabla");                              // Just like a Map: `putIfAbsent()`, `putNonNull()`
        jo.toBuilder().putIfAbsent("x", "xx").putNonNull("y", "yy");    // 支持 Builder
        jo.remove("extra");                                         // `removeIf()`, `forEach()`,

        // By path: `getByPath`, `asByPath`, `putByPath`, `removeByPath`, ...
        String role2 = jo.getStringByPath("$.user.role");               // `getXxByPath()` 支持 JSON Path
        String role3 = jo.getByPath("/user/role");                  // 或 JSON Pointer ，省略形式
        String role4 = jo.asByPath("$..role");                      // 支持 Descendant

        jo.putByPath("/aa/bb", "cc");                           // 自动创建节点， {"aa":{"bb":"cc"},..}
        jo.putIfAbsentByPath("$..level", 8);                    // Do nothing
        jo.putNonNullByPath("$.scores[3]", 100);                // 支持 数组

        List<String> tags = jo.findAll("$.tags[*]", String.class);          // `findAll()`支持 Wildcard
        List<Short> scores = jo.findAllAs("$.scores[0:3]", Short.class);    // 支持 Slice
        List<Object> unions = jo.findAllNodes("$.user['role','profile']");  // 支持 Union

        // Walker and Stream  (目前尚不支持JSON Path 的 Filter 语法，不过有更强大的 JsonWalker 和 JsonStream 作为替代)
        jo.walk(Target.CONTAINER, Order.BOTTOM_UP, (path, node) -> {
            // Taget: Container, or Value
            // Order: from bottom to top, or from top to bottom
            System.out.println("path=" + path + ", node=" + node);
            return Control.CONTINUE; // continue, or stop if you need
        });

        List<String> tags2 = jo.stream()        // 参考 Java Stream 语法
                .findAll("$.tags[*]", String.class)
                .filter(tag -> tag.length() > 3)
                .map(s -> "'" + s + "'")
                .toList();

        int x = jo.stream()
                .findAllAs("$..profile", JsonObject.class)
                .filter(n -> n.hasNonNull("values"))        // 必须包含 'values' 的key
                .findAs("$..x", Integer.class)
                .findFirst()                                                // 返回列表中的第一个
                .orElse(4);

        double avgScore = jo.stream()
                .findAll("$.scores[*]", Double.class)
                .map(d -> d < 60 ? 60 : d)
                .collect(Collectors.averagingDouble(s -> s));       // 利用 java.util.Collectors 提供的平均数计算方法

        // JsonArray 的用法与 JsonObject 基本一样，JsonObject是对 Map 的包装，JsonArray则是对 List 的包装
        JsonArray ja = JsonArray.fromJson("[\"aa\", \"bb\"]");
        String aa = ja.get(0);

    }

    public void advanced() throws IOException {

        class User {
            int id;
            String name;
            List<User> friends;
        }

        class User2 extends JsonObject {
            int id;
            String name;
            List<User2> friends;
        }

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

        // JSON Input <==> JsonObject/Map/POJO/JOJO
        JsonObject jo = Sjf4j.fromJson(json);                   // = JsonObject.fromJson(json)
        Map<String, Object> map = Sjf4j.fromJson(json, new TypeReference<Map<String, Object>>() {
        });
        User user = Sjf4j.fromJson(json, User.class);
        User2 user2 = Sjf4j.fromJson(json, User2.class);

        System.out.println("jo=" + jo.toJson());                // = Sjf4j.toJson(jo)
        System.out.println("map=" + Sjf4j.toJson(map));         // JsonObject 只是 Map 的 Wrapper
        System.out.println("user=" + Sjf4j.toJson(user));       // 与其他3个不同，只输出 User 中定义的内容
        System.out.println("user2=" + user2.toJson());          // 既有 User2 中的固定Field，也有 JsonObject 中的动态nodes

        jo = Sjf4j.fromYaml(jo.toYaml());                       // YAML 与 JSON 基本一样

        Properties props = new Properties();
        props.load(new StringReader("aa.bb[0].cc=dd"));
        JsonObject tmpJo = JsonObject.fromProperties(props);    // = {"aa":{"bb":[{"cc":"dd"}]}}, 能与 Properties 进行有限的转换
        jo.toProperties(new Properties());

        // JsonObject <==> Map
        Map<String, Object> tmpMap = jo.toMap();
        tmpJo = new JsonObject(map);

        // JsonObject <==> POJO/JOJO
        User tmpUser = jo.toPojo(User.class);                   // = Sjf4j.fromPojo(jo, User.class)
        tmpJo = Sjf4j.fromPojo(user2);

        // Jojo 拥有 JsonObject 的全部方法，同时也有 POJO 的原生 Getter/Setter
        System.out.println("keys=" + user2.keySet());               // ["id", "name", "friends", "age"]
        System.out.println("name=" + user2.getString("name"));      // = user2.getName() or user2.name
        user2.put("name", "Jenny");                         // = user2.setName("Jenny") or user2.name = "Jenny"
        int allUsers = user2.findAllNodes("$..id").size();



    }



}
