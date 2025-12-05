package org.sjf4j;

import java.util.List;
import java.util.Map;

/**
 * SJF4J库使用示例，从简单到高级分块展示
 */
public class Sjf4jExample {

    public static void main(String[] args) {
        // 创建一个示例对象用于演示
        User user = new User("John", 30, new Address("123 Main St", "New York"));
        
        System.out.println("=== SJF4J库使用示例 ===");
        
        // 1. 简单JSON创建与操作
        simpleJsonObjectExample();
        
        // 2. JSON对象与POJO转换
        jsonPojoConversionExample(user);
        
        // 3. JSON路径查询
        jsonPathExample(user);
        
        // 4. JSON流处理
        jsonStreamExample();
        
        // 5. JSON遍历
        jsonWalkerExample(user);
    }

    /**
     * 1. 简单JSON对象创建与操作示例
     */
    private static void simpleJsonObjectExample() {
        System.out.println("\n1. 简单JSON对象创建与操作");
        
        // 创建JSON对象
        JsonObject json = new JsonObject();
        json.put("name", "Alice");
        json.put("age", 25);
        json.put("active", true);
        json.put("scores", new int[]{85, 90, 95});
        
        // 从Map创建JSON对象
        Map<String, Object> map = Map.of(
            "key1", "value1",
            "key2", 123
        );
        JsonObject fromMap = new JsonObject(map);
        
        // 访问JSON对象属性
        String name = json.get("name", String.class);
        int age = json.get("age", Integer.class);
        boolean active = json.get("active", Boolean.class);
        int[] scores = json.get("scores", int[].class);
        
        System.out.println("创建的JSON对象: " + json);
        System.out.println("从Map创建: " + fromMap);
        System.out.println("获取属性 - 姓名: " + name + ", 年龄: " + age + ", 活跃: " + active);
        System.out.println("分数数组: " + java.util.Arrays.toString(scores));
    }

    /**
     * 2. JSON对象与POJO转换示例
     */
    private static void jsonPojoConversionExample(User user) {
        System.out.println("\n2. JSON对象与POJO转换");
        
        // POJO转换为JSON
        JsonObject json = new JsonObject(user);
        System.out.println("User对象转JSON: " + json);
        
        // JSON转换为POJO
        User userFromJson = json.toJavaObject(User.class);
        System.out.println("JSON转User对象: " + userFromJson);
        
        // 使用JsonConfig进行配置
        JsonConfig config = JsonConfig.global();
        String jsonString = config.toJson(user);
        System.out.println("使用全局配置转换为JSON字符串: " + jsonString);
        
        User userFromString = config.fromJson(jsonString, User.class);
        System.out.println("从JSON字符串转换为User对象: " + userFromString);
    }

    /**
     * 3. JSON路径查询示例
     */
    private static void jsonPathExample(User user) {
        System.out.println("\n3. JSON路径查询");
        
        JsonObject json = new JsonObject(user);
        System.out.println("基础JSON: " + json);
        
        // 使用JSON Path查询
        String name = JsonPath.compile("$.name").find(json, String.class);
        int age = JsonPath.compile("$.age").find(json, Integer.class);
        String street = JsonPath.compile("$.address.street").find(json, String.class);
        
        System.out.println("路径查询结果:");
        System.out.println("$.name = " + name);
        System.out.println("$.age = " + age);
        System.out.println("$.address.street = " + street);
        
        // 修改路径上的值
        JsonPath.compile("$.age").set(json, 31);
        System.out.println("修改后年龄: " + JsonPath.compile("$.age").find(json, Integer.class));
        
        // 批量添加数据用于演示
        JsonArray users = new JsonArray();
        users.add(user);
        users.add(new User("Alice", 25, new Address("456 Oak Ave", "Boston")));
        users.add(new User("Bob", 35, new Address("789 Pine St", "Chicago")));
        
        // 查询所有用户的姓名
        List<String> names = JsonPath.compile("$[*].name").findAll(users, String.class);
        System.out.println("所有用户姓名: " + names);
        
        // 查询年龄大于30的用户
        List<User> olderUsers = JsonPath.compile("$[?(@.age > 30)]").findAll(users, User.class);
        System.out.println("年龄大于30的用户: " + olderUsers);
    }

    /**
     * 4. JSON流处理示例
     */
    private static void jsonStreamExample() {
        System.out.println("\n4. JSON流处理");
        
        // 创建示例数据
        JsonArray users = new JsonArray();
        users.add(new User("John", 30, new Address("123 Main St", "New York")));
        users.add(new User("Alice", 25, new Address("456 Oak Ave", "Boston")));
        users.add(new User("Bob", 35, new Address("789 Pine St", "Chicago")));
        users.add(new User("Charlie", 28, new Address("101 Maple Ave", "Los Angeles")));
        
        // 使用JsonStream处理
        List<String> userNames = JsonStream.of(users)
                .find("$.name", String.class)
                .map(name -> name.toUpperCase())
                .filter(name -> name.startsWith("A") || name.startsWith("J"))
                .distinct()
                .sorted()
                .map(String::new)
                .filter(name -> name.length() > 3)
                .collect(java.util.stream.Collectors.toList());
        
        System.out.println("流处理结果 - 用户名(大写，以A或J开头，长度>3): " + userNames);
        
        // 计算年龄总和
        int totalAge = JsonStream.of(users)
                .find("$.age", Integer.class)
                .mapToInt(Integer::intValue)
                .sum();
        
        System.out.println("所有用户年龄总和: " + totalAge);
    }

    /**
     * 5. JSON遍历示例
     */
    private static void jsonWalkerExample(User user) {
        System.out.println("\n5. JSON遍历");
        
        JsonObject json = new JsonObject(user);
        System.out.println("要遍历的JSON: " + json);
        
        // 遍历所有节点
        System.out.println("遍历所有节点(Top-Down):");
        JsonWalker.walk(json, (path, value) -> {
            System.out.println("路径: " + path + ", 值: " + value + ", 类型: " + value.getClass().getSimpleName());
            return JsonWalker.Control.CONTINUE;
        });
        
        // 只遍历容器节点
        System.out.println("\n只遍历容器节点:");
        JsonWalker.walk(json, JsonWalker.Target.CONTAINER, (path, value) -> {
            System.out.println("容器路径: " + path + ", 类型: " + value.getClass().getSimpleName());
            return JsonWalker.Control.CONTINUE;
        });
        
        // 查找特定值
        System.out.println("\n查找地址相关节点:");
        JsonWalker.walk(json, (path, value) -> {
            if (path.toString().contains("address")) {
                System.out.println("地址相关 - 路径: " + path + ", 值: " + value);
            }
            return JsonWalker.Control.CONTINUE;
        });
    }

    // 用于演示的POJO类
    static class User {
        private String name;
        private int age;
        private Address address;
        
        public User() {}
        
        public User(String name, int age, Address address) {
            this.name = name;
            this.age = age;
            this.address = address;
        }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
        
        public Address getAddress() { return address; }
        public void setAddress(Address address) { this.address = address; }
        
        @Override
        public String toString() {
            return "User{name='" + name + "', age=" + age + ", address=" + address + "}";
        }
    }
    
    static class Address {
        private String street;
        private String city;
        
        public Address() {}
        
        public Address(String street, String city) {
            this.street = street;
            this.city = city;
        }
        
        public String getStreet() { return street; }
        public void setStreet(String street) { this.street = street; }
        
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        
        @Override
        public String toString() {
            return "Address{street='" + street + "', city='" + city + "'}";
        }
    }
}