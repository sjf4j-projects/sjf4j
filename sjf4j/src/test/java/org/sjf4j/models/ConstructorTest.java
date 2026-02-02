package org.sjf4j.models;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.Sjf4j;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test cases for parameterized constructors with @JsonProperty and @JsonAlias
 */
@Slf4j
public class ConstructorTest {

    // ===== Simple Constructor with @JsonProperty =====
    
    public static class SimpleConstructorPojo {
        private final String name;
        private final int age;
        
        public SimpleConstructorPojo(
                @JsonProperty("name") String name,
                @JsonProperty("age") int age) {
            this.name = name;
            this.age = age;
        }
        
        public String getName() { return name; }
        public int getAge() { return age; }
    }
    
    @Test
    public void testSimpleConstructorWithJsonProperty() {
        String json = "{\"name\":\"Alice\",\"age\":30}";
        SimpleConstructorPojo obj = Sjf4j.fromJson(json, SimpleConstructorPojo.class);
        
        assertNotNull(obj);
        assertEquals("Alice", obj.getName());
        assertEquals(30, obj.getAge());
        log.info("SimpleConstructorPojo: name={}, age={}", obj.getName(), obj.getAge());
    }
    
    // ===== Constructor with @JsonAlias =====
    
    public static class AliasConstructorPojo {
        private final String username;
        private final String email;
        
        public AliasConstructorPojo(
                @JsonProperty("username")
                @JsonAlias({"user_name", "userName", "login"}) String username,
                @JsonProperty("email")
                @JsonAlias({"e_mail", "emailAddress"}) String email) {
            this.username = username;
            this.email = email;
        }
        
        public String getUsername() { return username; }
        public String getEmail() { return email; }
    }
    
    @Test
    public void testConstructorWithJsonAlias_MainProperty() {
        String json = "{\"username\":\"john\",\"email\":\"john@example.com\"}";
        AliasConstructorPojo obj = Sjf4j.fromJson(json, AliasConstructorPojo.class);
        
        assertNotNull(obj);
        assertEquals("john", obj.getUsername());
        assertEquals("john@example.com", obj.getEmail());
        log.info("AliasConstructorPojo (main): username={}, email={}", obj.getUsername(), obj.getEmail());
    }
    
    @Test
    public void testConstructorWithJsonAlias_Alias1() {
        String json = "{\"user_name\":\"jane\",\"e_mail\":\"jane@example.com\"}";
        AliasConstructorPojo obj = Sjf4j.fromJson(json, AliasConstructorPojo.class);
        
        assertNotNull(obj);
        assertEquals("jane", obj.getUsername());
        assertEquals("jane@example.com", obj.getEmail());
        log.info("AliasConstructorPojo (alias1): username={}, email={}", obj.getUsername(), obj.getEmail());
    }
    
    @Test
    public void testConstructorWithJsonAlias_Alias2() {
        String json = "{\"userName\":\"bob\",\"emailAddress\":\"bob@example.com\"}";
        AliasConstructorPojo obj = Sjf4j.fromJson(json, AliasConstructorPojo.class);
        
        assertNotNull(obj);
        assertEquals("bob", obj.getUsername());
        assertEquals("bob@example.com", obj.getEmail());
        log.info("AliasConstructorPojo (alias2): username={}, email={}", obj.getUsername(), obj.getEmail());
    }
    
    @Test
    public void testConstructorWithJsonAlias_Alias3() {
        String json = "{\"login\":\"charlie\",\"e_mail\":\"charlie@example.com\"}";
        AliasConstructorPojo obj = Sjf4j.fromJson(json, AliasConstructorPojo.class);
        
        assertNotNull(obj);
        assertEquals("charlie", obj.getUsername());
        assertEquals("charlie@example.com", obj.getEmail());
        log.info("AliasConstructorPojo (alias3): username={}, email={}", obj.getUsername(), obj.getEmail());
    }
    
    // ===== Constructor with required parameters =====
    
    public static class RequiredFieldsPojo {
        private final String id;
        private final String name;
        private final BigDecimal price;
        
        public RequiredFieldsPojo(
                @JsonProperty(value = "id", required = true) String id,
                @JsonProperty(value = "name", required = true) String name,
                @JsonProperty(value = "price", required = true) BigDecimal price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public BigDecimal getPrice() { return price; }
    }
    
    @Test
    public void testConstructorWithRequiredFields() {
        String json = "{\"id\":\"P001\",\"name\":\"Product A\",\"price\":99.99}";
        RequiredFieldsPojo obj = Sjf4j.fromJson(json, RequiredFieldsPojo.class);
        
        assertNotNull(obj);
        assertEquals("P001", obj.getId());
        assertEquals("Product A", obj.getName());
        assertEquals(new BigDecimal("99.99"), obj.getPrice());
        log.info("RequiredFieldsPojo: id={}, name={}, price={}", obj.getId(), obj.getName(), obj.getPrice());
    }
    
    // ===== Constructor with mixed required and optional =====
    
    public static class MixedFieldsPojo {
        private final String name;
        private final String description;
        private final Integer quantity;
        
        public MixedFieldsPojo(
                @JsonProperty(value = "name", required = true) String name,
                @JsonProperty("description") String description,
                @JsonProperty("quantity") Integer quantity) {
            this.name = name;
            this.description = description;
            this.quantity = quantity;
        }
        
        public String getName() { return name; }
        public String getDescription() { return description; }
        public Integer getQuantity() { return quantity; }
    }
    
    @Test
    public void testConstructorWithMixedFields_AllPresent() {
        String json = "{\"name\":\"Item\",\"description\":\"Test item\",\"quantity\":10}";
        MixedFieldsPojo obj = Sjf4j.fromJson(json, MixedFieldsPojo.class);
        
        assertNotNull(obj);
        assertEquals("Item", obj.getName());
        assertEquals("Test item", obj.getDescription());
        assertEquals(10, obj.getQuantity());
        log.info("MixedFieldsPojo (all): name={}, description={}, quantity={}", 
                obj.getName(), obj.getDescription(), obj.getQuantity());
    }
    
    @Test
    public void testConstructorWithMixedFields_OnlyRequired() {
        String json = "{\"name\":\"Item\"}";
        MixedFieldsPojo obj = Sjf4j.fromJson(json, MixedFieldsPojo.class);
        
        assertNotNull(obj);
        assertEquals("Item", obj.getName());
        assertNull(obj.getDescription());
        assertNull(obj.getQuantity());
        log.info("MixedFieldsPojo (required only): name={}, description={}, quantity={}", 
                obj.getName(), obj.getDescription(), obj.getQuantity());
    }
    
    // ===== Constructor with nested objects =====
    
    public static class Address {
        private final String street;
        private final String city;
        
        public Address(
                @JsonProperty("street") String street,
                @JsonProperty("city") String city) {
            this.street = street;
            this.city = city;
        }
        
        public String getStreet() { return street; }
        public String getCity() { return city; }
    }
    
    public static class PersonWithAddress {
        private final String name;
        private final Address address;
        
        public PersonWithAddress(
                @JsonProperty("name") String name,
                @JsonProperty("address") Address address) {
            this.name = name;
            this.address = address;
        }
        
        public String getName() { return name; }
        public Address getAddress() { return address; }
    }
    
    @Test
    public void testConstructorWithNestedObject() {
        String json = "{\"name\":\"John\",\"address\":{\"street\":\"123 Main St\",\"city\":\"Boston\"}}";
        PersonWithAddress obj = Sjf4j.fromJson(json, PersonWithAddress.class);
        
        assertNotNull(obj);
        assertEquals("John", obj.getName());
        assertNotNull(obj.getAddress());
        assertEquals("123 Main St", obj.getAddress().getStreet());
        assertEquals("Boston", obj.getAddress().getCity());
        log.info("PersonWithAddress: name={}, street={}, city={}", 
                obj.getName(), obj.getAddress().getStreet(), obj.getAddress().getCity());
    }
    
    // ===== Constructor with collections =====
    
    public static class TeamPojo {
        private final String teamName;
        private final List<String> members;
        
        public TeamPojo(
                @JsonProperty("teamName") String teamName,
                @JsonProperty("members") List<String> members) {
            this.teamName = teamName;
            this.members = members;
        }
        
        public String getTeamName() { return teamName; }
        public List<String> getMembers() { return members; }
    }
    
    @Test
    public void testConstructorWithCollection() {
        String json = "{\"teamName\":\"Alpha\",\"members\":[\"Alice\",\"Bob\",\"Charlie\"]}";
        TeamPojo obj = Sjf4j.fromJson(json, TeamPojo.class);
        
        assertNotNull(obj);
        assertEquals("Alpha", obj.getTeamName());
        assertNotNull(obj.getMembers());
        assertEquals(3, obj.getMembers().size());
        assertEquals("Alice", obj.getMembers().get(0));
        assertEquals("Bob", obj.getMembers().get(1));
        assertEquals("Charlie", obj.getMembers().get(2));
        log.info("TeamPojo: teamName={}, members={}", obj.getTeamName(), obj.getMembers());
    }
    
    @Test
    public void testConstructorWithEmptyCollection() {
        String json = "{\"teamName\":\"Beta\",\"members\":[]}";
        TeamPojo obj = Sjf4j.fromJson(json, TeamPojo.class);
        
        assertNotNull(obj);
        assertEquals("Beta", obj.getTeamName());
        assertNotNull(obj.getMembers());
        assertEquals(0, obj.getMembers().size());
        log.info("TeamPojo (empty): teamName={}, members={}", obj.getTeamName(), obj.getMembers());
    }
    
    // ===== Complex constructor with multiple types =====
    
    public static class ComplexPojo {
        private final String id;
        private final int count;
        private final Double rate;
        private final Boolean active;
        private final List<Integer> scores;
        
        public ComplexPojo(
                @JsonProperty("id") @JsonAlias("identifier") String id,
                @JsonProperty("count") @JsonAlias({"cnt", "number"}) int count,
                @JsonProperty("rate") @JsonAlias("percentage") Double rate,
                @JsonProperty("active") @JsonAlias({"enabled", "isActive"}) Boolean active,
                @JsonProperty("scores") List<Integer> scores) {
            this.id = id;
            this.count = count;
            this.rate = rate;
            this.active = active;
            this.scores = scores;
        }
        
        public String getId() { return id; }
        public int getCount() { return count; }
        public Double getRate() { return rate; }
        public Boolean getActive() { return active; }
        public List<Integer> getScores() { return scores; }
    }
    
    @Test
    public void testComplexConstructor_MainProperties() {
        String json = "{\"id\":\"C001\",\"count\":5,\"rate\":0.95,\"active\":true,\"scores\":[80,90,85]}";
        ComplexPojo obj = Sjf4j.fromJson(json, ComplexPojo.class);
        
        assertNotNull(obj);
        assertEquals("C001", obj.getId());
        assertEquals(5, obj.getCount());
        assertEquals(0.95, obj.getRate(), 0.001);
        assertTrue(obj.getActive());
        assertEquals(Arrays.asList(80, 90, 85), obj.getScores());
        log.info("ComplexPojo (main): id={}, count={}, rate={}, active={}, scores={}", 
                obj.getId(), obj.getCount(), obj.getRate(), obj.getActive(), obj.getScores());
    }
    
    @Test
    public void testComplexConstructor_WithAliases() {
        String json = "{\"identifier\":\"C002\",\"cnt\":10,\"percentage\":0.88,\"enabled\":false,\"scores\":[70,75,80]}";
        ComplexPojo obj = Sjf4j.fromJson(json, ComplexPojo.class);
        
        assertNotNull(obj);
        assertEquals("C002", obj.getId());
        assertEquals(10, obj.getCount());
        assertEquals(0.88, obj.getRate(), 0.001);
        assertFalse(obj.getActive());
        assertEquals(Arrays.asList(70, 75, 80), obj.getScores());
        log.info("ComplexPojo (aliases): id={}, count={}, rate={}, active={}, scores={}", 
                obj.getId(), obj.getCount(), obj.getRate(), obj.getActive(), obj.getScores());
    }
    
    @Test
    public void testComplexConstructor_MixedAliases() {
        String json = "{\"id\":\"C003\",\"number\":15,\"rate\":0.92,\"isActive\":true,\"scores\":[]}";
        ComplexPojo obj = Sjf4j.fromJson(json, ComplexPojo.class);
        
        assertNotNull(obj);
        assertEquals("C003", obj.getId());
        assertEquals(15, obj.getCount());
        assertEquals(0.92, obj.getRate(), 0.001);
        assertTrue(obj.getActive());
        assertEquals(0, obj.getScores().size());
        log.info("ComplexPojo (mixed): id={}, count={}, rate={}, active={}, scores={}", 
                obj.getId(), obj.getCount(), obj.getRate(), obj.getActive(), obj.getScores());
    }
    
    // ===== Edge Cases: Null Values =====
    
    public static class NullablePojo {
        private final String name;
        private final Integer age;
        
        public NullablePojo(
                @JsonProperty("name") String name,
                @JsonProperty("age") Integer age) {
            this.name = name;
            this.age = age;
        }
        
        public String getName() { return name; }
        public Integer getAge() { return age; }
    }
    
    @Test
    public void testConstructorWithNullValues() {
        String json = "{\"name\":null,\"age\":null}";
        NullablePojo obj = Sjf4j.fromJson(json, NullablePojo.class);
        
        assertNotNull(obj);
        assertNull(obj.getName());
        assertNull(obj.getAge());
        log.info("NullablePojo: name={}, age={}", obj.getName(), obj.getAge());
    }
    
    @Test
    public void testConstructorWithMissingOptionalFields() {
        String json = "{}";
        NullablePojo obj = Sjf4j.fromJson(json, NullablePojo.class);
        
        assertNotNull(obj);
        assertNull(obj.getName());
        assertNull(obj.getAge());
        log.info("NullablePojo (missing): name={}, age={}", obj.getName(), obj.getAge());
    }
    
    // ===== Edge Cases: Empty Strings =====
    
    @Test
    public void testConstructorWithEmptyString() {
        String json = "{\"name\":\"\",\"age\":0}";
        SimpleConstructorPojo obj = Sjf4j.fromJson(json, SimpleConstructorPojo.class);
        
        assertNotNull(obj);
        assertEquals("", obj.getName());
        assertEquals(0, obj.getAge());
        log.info("SimpleConstructorPojo (empty string): name='{}', age={}", obj.getName(), obj.getAge());
    }
    
    // ===== Edge Cases: Special Characters =====
    
    @Test
    public void testConstructorWithSpecialCharacters() {
        String json = "{\"name\":\"测试用户\",\"age\":25}";
        SimpleConstructorPojo obj = Sjf4j.fromJson(json, SimpleConstructorPojo.class);
        
        assertNotNull(obj);
        assertEquals("测试用户", obj.getName());
        assertEquals(25, obj.getAge());
        log.info("SimpleConstructorPojo (special chars): name={}, age={}", obj.getName(), obj.getAge());
    }
    
    @Test
    public void testConstructorWithEscapedCharacters() {
        String json = "{\"name\":\"Line1\\nLine2\\tTabbed\",\"age\":30}";
        SimpleConstructorPojo obj = Sjf4j.fromJson(json, SimpleConstructorPojo.class);
        
        assertNotNull(obj);
        assertEquals("Line1\nLine2\tTabbed", obj.getName());
        assertEquals(30, obj.getAge());
        log.info("SimpleConstructorPojo (escaped): name={}, age={}", obj.getName(), obj.getAge());
    }
    
    // ===== Edge Cases: Unicode and Emoji =====
    
    @Test
    public void testConstructorWithUnicodeAndEmoji() {
        String json = "{\"name\":\"Hello 👋 世界 🌍\",\"age\":28}";
        SimpleConstructorPojo obj = Sjf4j.fromJson(json, SimpleConstructorPojo.class);
        
        assertNotNull(obj);
        assertEquals("Hello 👋 世界 🌍", obj.getName());
        assertEquals(28, obj.getAge());
        log.info("SimpleConstructorPojo (unicode): name={}, age={}", obj.getName(), obj.getAge());
    }
    
    // ===== Edge Cases: Very Long Strings =====
    
    @Test
    public void testConstructorWithLongString() {
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longName.append("a");
        }
        String json = "{\"name\":\"" + longName.toString() + "\",\"age\":35}";
        SimpleConstructorPojo obj = Sjf4j.fromJson(json, SimpleConstructorPojo.class);
        
        assertNotNull(obj);
        assertEquals(1000, obj.getName().length());
        assertEquals(35, obj.getAge());
        log.info("SimpleConstructorPojo (long string): name length={}, age={}", obj.getName().length(), obj.getAge());
    }
    
    // ===== Edge Cases: Numeric Edge Values =====
    
    @Test
    public void testConstructorWithMaxInteger() {
        String json = "{\"name\":\"Max Int\",\"age\":" + Integer.MAX_VALUE + "}";
        SimpleConstructorPojo obj = Sjf4j.fromJson(json, SimpleConstructorPojo.class);
        
        assertNotNull(obj);
        assertEquals("Max Int", obj.getName());
        assertEquals(Integer.MAX_VALUE, obj.getAge());
        log.info("SimpleConstructorPojo (max int): name={}, age={}", obj.getName(), obj.getAge());
    }
    
    @Test
    public void testConstructorWithMinInteger() {
        String json = "{\"name\":\"Min Int\",\"age\":" + Integer.MIN_VALUE + "}";
        SimpleConstructorPojo obj = Sjf4j.fromJson(json, SimpleConstructorPojo.class);
        
        assertNotNull(obj);
        assertEquals("Min Int", obj.getName());
        assertEquals(Integer.MIN_VALUE, obj.getAge());
        log.info("SimpleConstructorPojo (min int): name={}, age={}", obj.getName(), obj.getAge());
    }
    
    // ===== Nested List with Complex Objects =====
    
    public static class OrderItem {
        private final String productId;
        private final int quantity;
        private final double price;
        
        public OrderItem(
                @JsonProperty("productId") @JsonAlias("product_id") String productId,
                @JsonProperty("quantity") @JsonAlias("qty") int quantity,
                @JsonProperty("price") double price) {
            this.productId = productId;
            this.quantity = quantity;
            this.price = price;
        }
        
        public String getProductId() { return productId; }
        public int getQuantity() { return quantity; }
        public double getPrice() { return price; }
    }
    
    public static class Order {
        private final String orderId;
        private final List<OrderItem> items;
        
        public Order(
                @JsonProperty("orderId") @JsonAlias("order_id") String orderId,
                @JsonProperty("items") List<OrderItem> items) {
            this.orderId = orderId;
            this.items = items;
        }
        
        public String getOrderId() { return orderId; }
        public List<OrderItem> getItems() { return items; }
    }
    
    @Test
    public void testConstructorWithNestedListOfObjects() {
        String json = "{\"orderId\":\"ORD001\",\"items\":[" +
                "{\"productId\":\"P1\",\"quantity\":2,\"price\":10.5}," +
                "{\"product_id\":\"P2\",\"qty\":1,\"price\":25.0}" +
                "]}";
        Order obj = Sjf4j.fromJson(json, Order.class);
        
        assertNotNull(obj);
        assertEquals("ORD001", obj.getOrderId());
        assertEquals(2, obj.getItems().size());
        assertEquals("P1", obj.getItems().get(0).getProductId());
        assertEquals(2, obj.getItems().get(0).getQuantity());
        assertEquals(10.5, obj.getItems().get(0).getPrice(), 0.001);
        assertEquals("P2", obj.getItems().get(1).getProductId());
        assertEquals(1, obj.getItems().get(1).getQuantity());
        assertEquals(25.0, obj.getItems().get(1).getPrice(), 0.001);
        log.info("Order: orderId={}, items count={}", obj.getOrderId(), obj.getItems().size());
    }
    
    // ===== Deeply Nested Objects =====
    
    public static class Level3 {
        private final String value;
        
        public Level3(@JsonProperty("value") String value) {
            this.value = value;
        }
        
        public String getValue() { return value; }
    }
    
    public static class Level2 {
        private final Level3 level3;
        
        public Level2(@JsonProperty("level3") Level3 level3) {
            this.level3 = level3;
        }
        
        public Level3 getLevel3() { return level3; }
    }
    
    public static class Level1 {
        private final Level2 level2;
        
        public Level1(@JsonProperty("level2") Level2 level2) {
            this.level2 = level2;
        }
        
        public Level2 getLevel2() { return level2; }
    }
    
    @Test
    public void testDeeplyNestedConstructors() {
        String json = "{\"level2\":{\"level3\":{\"value\":\"deep value\"}}}";
        Level1 obj = Sjf4j.fromJson(json, Level1.class);
        
        assertNotNull(obj);
        assertNotNull(obj.getLevel2());
        assertNotNull(obj.getLevel2().getLevel3());
        assertEquals("deep value", obj.getLevel2().getLevel3().getValue());
        log.info("Level1: deep value={}", obj.getLevel2().getLevel3().getValue());
    }
}
