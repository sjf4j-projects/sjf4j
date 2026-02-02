package org.sjf4j.models;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.Sjf4j;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test cases for Record-like immutable classes with @JsonProperty and @JsonAlias
 * Note: Since the project targets Java 8, we use regular immutable classes with record-like accessors
 */
@Slf4j
public class RecordStyleTest {

    // ===== Simple Record-style class with @JsonProperty =====
    
    public static class SimpleRecordStyle {
        private final String name;
        private final int age;
        
        public SimpleRecordStyle(
                @JsonProperty("name") String name,
                @JsonProperty("age") int age) {
            this.name = name;
            this.age = age;
        }
        
        public String name() { return name; }
        public int age() { return age; }
    }
    
    @Test
    public void testSimpleRecordStyle() {
        String json = "{\"name\":\"Alice\",\"age\":30}";
        SimpleRecordStyle obj = Sjf4j.fromJson(json, SimpleRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("Alice", obj.name());
        assertEquals(30, obj.age());
        log.info("SimpleRecordStyle: name={}, age={}", obj.name(), obj.age());
    }
    
    // ===== Record-style class with @JsonAlias =====
    
    public static class AliasRecordStyle {
        private final String username;
        private final String email;
        
        public AliasRecordStyle(
                @JsonProperty("username")
                @JsonAlias({"user_name", "userName", "login"}) String username,
                @JsonProperty("email")
                @JsonAlias({"e_mail", "emailAddress"}) String email) {
            this.username = username;
            this.email = email;
        }
        
        public String username() { return username; }
        public String email() { return email; }
    }
    
    @Test
    public void testRecordStyleWithAlias_MainProperty() {
        String json = "{\"username\":\"john\",\"email\":\"john@example.com\"}";
        AliasRecordStyle obj = Sjf4j.fromJson(json, AliasRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("john", obj.username());
        assertEquals("john@example.com", obj.email());
        log.info("AliasRecordStyle (main): username={}, email={}", obj.username(), obj.email());
    }
    
    @Test
    public void testRecordStyleWithAlias_Alias1() {
        String json = "{\"user_name\":\"jane\",\"e_mail\":\"jane@example.com\"}";
        AliasRecordStyle obj = Sjf4j.fromJson(json, AliasRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("jane", obj.username());
        assertEquals("jane@example.com", obj.email());
        log.info("AliasRecordStyle (alias1): username={}, email={}", obj.username(), obj.email());
    }
    
    @Test
    public void testRecordStyleWithAlias_Alias2() {
        String json = "{\"userName\":\"bob\",\"emailAddress\":\"bob@example.com\"}";
        AliasRecordStyle obj = Sjf4j.fromJson(json, AliasRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("bob", obj.username());
        assertEquals("bob@example.com", obj.email());
        log.info("AliasRecordStyle (alias2): username={}, email={}", obj.username(), obj.email());
    }
    
    @Test
    public void testRecordStyleWithAlias_MixedAliases() {
        String json = "{\"login\":\"charlie\",\"e_mail\":\"charlie@example.com\"}";
        AliasRecordStyle obj = Sjf4j.fromJson(json, AliasRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("charlie", obj.username());
        assertEquals("charlie@example.com", obj.email());
        log.info("AliasRecordStyle (mixed): username={}, email={}", obj.username(), obj.email());
    }
    
    // ===== Record-style class with Multiple Fields and Types =====
    
    public static class ProductRecordStyle {
        private final String id;
        private final String name;
        private final BigDecimal price;
        private final boolean inStock;
        private final Integer quantity;
        
        public ProductRecordStyle(
                @JsonProperty("id") @JsonAlias("productId") String id,
                @JsonProperty("name") @JsonAlias("productName") String name,
                @JsonProperty("price") @JsonAlias({"cost", "amount"}) BigDecimal price,
                @JsonProperty("inStock") @JsonAlias({"available", "in_stock"}) boolean inStock,
                @JsonProperty("quantity") @JsonAlias("qty") Integer quantity) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.inStock = inStock;
            this.quantity = quantity;
        }
        
        public String id() { return id; }
        public String name() { return name; }
        public BigDecimal price() { return price; }
        public boolean inStock() { return inStock; }
        public Integer quantity() { return quantity; }
    }
    
    @Test
    public void testProductRecordStyle_MainProperties() {
        String json = "{\"id\":\"P001\",\"name\":\"Laptop\",\"price\":999.99,\"inStock\":true,\"quantity\":10}";
        ProductRecordStyle obj = Sjf4j.fromJson(json, ProductRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("P001", obj.id());
        assertEquals("Laptop", obj.name());
        assertEquals(new BigDecimal("999.99"), obj.price());
        assertTrue(obj.inStock());
        assertEquals(10, obj.quantity());
        log.info("ProductRecordStyle (main): id={}, name={}, price={}, inStock={}, quantity={}", 
                obj.id(), obj.name(), obj.price(), obj.inStock(), obj.quantity());
    }
    
    @Test
    public void testProductRecordStyle_WithAliases() {
        String json = "{\"productId\":\"P002\",\"productName\":\"Mouse\",\"cost\":25.50,\"available\":false,\"qty\":0}";
        ProductRecordStyle obj = Sjf4j.fromJson(json, ProductRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("P002", obj.id());
        assertEquals("Mouse", obj.name());
        assertEquals(new BigDecimal("25.50"), obj.price());
        assertFalse(obj.inStock());
        assertEquals(0, obj.quantity());
        log.info("ProductRecordStyle (aliases): id={}, name={}, price={}, inStock={}, quantity={}", 
                obj.id(), obj.name(), obj.price(), obj.inStock(), obj.quantity());
    }
    
    @Test
    public void testProductRecordStyle_MixedAliases() {
        String json = "{\"id\":\"P003\",\"productName\":\"Keyboard\",\"amount\":45.00,\"in_stock\":true,\"quantity\":5}";
        ProductRecordStyle obj = Sjf4j.fromJson(json, ProductRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("P003", obj.id());
        assertEquals("Keyboard", obj.name());
        assertEquals(new BigDecimal("45.00"), obj.price());
        assertTrue(obj.inStock());
        assertEquals(5, obj.quantity());
        log.info("ProductRecordStyle (mixed): id={}, name={}, price={}, inStock={}, quantity={}", 
                obj.id(), obj.name(), obj.price(), obj.inStock(), obj.quantity());
    }
    
    // ===== Nested Record-style classes =====
    
    public static class AddressRecordStyle {
        private final String street;
        private final String city;
        private final String zipCode;
        
        public AddressRecordStyle(
                @JsonProperty("street") @JsonAlias("streetAddress") String street,
                @JsonProperty("city") String city,
                @JsonProperty("zipCode") @JsonAlias({"zip", "postalCode"}) String zipCode) {
            this.street = street;
            this.city = city;
            this.zipCode = zipCode;
        }
        
        public String street() { return street; }
        public String city() { return city; }
        public String zipCode() { return zipCode; }
    }
    
    public static class PersonRecordStyle {
        private final String name;
        private final int age;
        private final AddressRecordStyle address;
        
        public PersonRecordStyle(
                @JsonProperty("name") @JsonAlias("fullName") String name,
                @JsonProperty("age") int age,
                @JsonProperty("address") AddressRecordStyle address) {
            this.name = name;
            this.age = age;
            this.address = address;
        }
        
        public String name() { return name; }
        public int age() { return age; }
        public AddressRecordStyle address() { return address; }
    }
    
    @Test
    public void testNestedRecordStyle() {
        String json = "{\"name\":\"John Doe\",\"age\":35,\"address\":{\"street\":\"123 Main St\",\"city\":\"Boston\",\"zipCode\":\"02101\"}}";
        PersonRecordStyle obj = Sjf4j.fromJson(json, PersonRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("John Doe", obj.name());
        assertEquals(35, obj.age());
        assertNotNull(obj.address());
        assertEquals("123 Main St", obj.address().street());
        assertEquals("Boston", obj.address().city());
        assertEquals("02101", obj.address().zipCode());
        log.info("PersonRecordStyle: name={}, age={}, address=[street={}, city={}, zip={}]", 
                obj.name(), obj.age(), obj.address().street(), obj.address().city(), obj.address().zipCode());
    }
    
    @Test
    public void testNestedRecordStyle_WithAliases() {
        String json = "{\"fullName\":\"Jane Smith\",\"age\":28,\"address\":{\"streetAddress\":\"456 Oak Ave\",\"city\":\"New York\",\"zip\":\"10001\"}}";
        PersonRecordStyle obj = Sjf4j.fromJson(json, PersonRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("Jane Smith", obj.name());
        assertEquals(28, obj.age());
        assertNotNull(obj.address());
        assertEquals("456 Oak Ave", obj.address().street());
        assertEquals("New York", obj.address().city());
        assertEquals("10001", obj.address().zipCode());
        log.info("PersonRecordStyle (aliases): name={}, age={}, address=[street={}, city={}, zip={}]", 
                obj.name(), obj.age(), obj.address().street(), obj.address().city(), obj.address().zipCode());
    }
    
    // ===== Record-style class with Collections =====
    
    public static class TeamRecordStyle {
        private final String teamName;
        private final List<String> members;
        private final List<Integer> scores;
        
        public TeamRecordStyle(
                @JsonProperty("teamName") @JsonAlias({"team_name", "name"}) String teamName,
                @JsonProperty("members") @JsonAlias("teamMembers") List<String> members,
                @JsonProperty("scores") List<Integer> scores) {
            this.teamName = teamName;
            this.members = members;
            this.scores = scores;
        }
        
        public String teamName() { return teamName; }
        public List<String> members() { return members; }
        public List<Integer> scores() { return scores; }
    }
    
    @Test
    public void testRecordStyleWithCollections() {
        String json = "{\"teamName\":\"Alpha\",\"members\":[\"Alice\",\"Bob\",\"Charlie\"],\"scores\":[95,88,92]}";
        TeamRecordStyle obj = Sjf4j.fromJson(json, TeamRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("Alpha", obj.teamName());
        assertEquals(Arrays.asList("Alice", "Bob", "Charlie"), obj.members());
        assertEquals(Arrays.asList(95, 88, 92), obj.scores());
        log.info("TeamRecordStyle: teamName={}, members={}, scores={}", obj.teamName(), obj.members(), obj.scores());
    }
    
    @Test
    public void testRecordStyleWithCollections_Aliases() {
        String json = "{\"team_name\":\"Beta\",\"teamMembers\":[\"David\",\"Eve\"],\"scores\":[80,85]}";
        TeamRecordStyle obj = Sjf4j.fromJson(json, TeamRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("Beta", obj.teamName());
        assertEquals(Arrays.asList("David", "Eve"), obj.members());
        assertEquals(Arrays.asList(80, 85), obj.scores());
        log.info("TeamRecordStyle (aliases): teamName={}, members={}, scores={}", obj.teamName(), obj.members(), obj.scores());
    }
    
    @Test
    public void testRecordStyleWithEmptyCollections() {
        String json = "{\"teamName\":\"Gamma\",\"members\":[],\"scores\":[]}";
        TeamRecordStyle obj = Sjf4j.fromJson(json, TeamRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("Gamma", obj.teamName());
        assertEquals(0, obj.members().size());
        assertEquals(0, obj.scores().size());
        log.info("TeamRecordStyle (empty): teamName={}, members={}, scores={}", obj.teamName(), obj.members(), obj.scores());
    }
    
    // ===== Record-style class with List of Nested classes =====
    
    public static class ItemRecordStyle {
        private final String itemId;
        private final int quantity;
        private final double price;
        
        public ItemRecordStyle(
                @JsonProperty("itemId") @JsonAlias("item_id") String itemId,
                @JsonProperty("quantity") @JsonAlias("qty") int quantity,
                @JsonProperty("price") double price) {
            this.itemId = itemId;
            this.quantity = quantity;
            this.price = price;
        }
        
        public String itemId() { return itemId; }
        public int quantity() { return quantity; }
        public double price() { return price; }
    }
    
    public static class OrderRecordStyle {
        private final String orderId;
        private final List<ItemRecordStyle> items;
        private final BigDecimal total;
        
        public OrderRecordStyle(
                @JsonProperty("orderId") @JsonAlias("order_id") String orderId,
                @JsonProperty("items") List<ItemRecordStyle> items,
                @JsonProperty("total") @JsonAlias("totalAmount") BigDecimal total) {
            this.orderId = orderId;
            this.items = items;
            this.total = total;
        }
        
        public String orderId() { return orderId; }
        public List<ItemRecordStyle> items() { return items; }
        public BigDecimal total() { return total; }
    }
    
    @Test
    public void testRecordStyleWithListOfNested() {
        String json = "{\"orderId\":\"ORD001\",\"items\":[" +
                "{\"itemId\":\"I1\",\"quantity\":2,\"price\":10.5}," +
                "{\"item_id\":\"I2\",\"qty\":1,\"price\":25.0}" +
                "],\"total\":46.0}";
        OrderRecordStyle obj = Sjf4j.fromJson(json, OrderRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("ORD001", obj.orderId());
        assertEquals(2, obj.items().size());
        assertEquals("I1", obj.items().get(0).itemId());
        assertEquals(2, obj.items().get(0).quantity());
        assertEquals(10.5, obj.items().get(0).price(), 0.001);
        assertEquals("I2", obj.items().get(1).itemId());
        assertEquals(1, obj.items().get(1).quantity());
        assertEquals(25.0, obj.items().get(1).price(), 0.001);
        assertEquals(new BigDecimal("46.0"), obj.total());
        log.info("OrderRecordStyle: orderId={}, items count={}, total={}", obj.orderId(), obj.items().size(), obj.total());
    }
    
    // ===== Record-style class with Required Fields =====
    
    public static class RequiredRecordStyle {
        private final String id;
        private final String name;
        private final String description;
        
        public RequiredRecordStyle(
                @JsonProperty(value = "id", required = true) String id,
                @JsonProperty(value = "name", required = true) String name,
                @JsonProperty("description") String description) {
            this.id = id;
            this.name = name;
            this.description = description;
        }
        
        public String id() { return id; }
        public String name() { return name; }
        public String description() { return description; }
    }
    
    @Test
    public void testRecordStyleWithRequiredFields_AllPresent() {
        String json = "{\"id\":\"R001\",\"name\":\"Record Name\",\"description\":\"A description\"}";
        RequiredRecordStyle obj = Sjf4j.fromJson(json, RequiredRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("R001", obj.id());
        assertEquals("Record Name", obj.name());
        assertEquals("A description", obj.description());
        log.info("RequiredRecordStyle (all): id={}, name={}, description={}", obj.id(), obj.name(), obj.description());
    }
    
    @Test
    public void testRecordStyleWithRequiredFields_OnlyRequired() {
        String json = "{\"id\":\"R002\",\"name\":\"Record Name\"}";
        RequiredRecordStyle obj = Sjf4j.fromJson(json, RequiredRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("R002", obj.id());
        assertEquals("Record Name", obj.name());
        assertNull(obj.description());
        log.info("RequiredRecordStyle (required only): id={}, name={}, description={}", obj.id(), obj.name(), obj.description());
    }
    
    // ===== Edge Cases: Null Values =====
    
    public static class NullableRecordStyle {
        private final String name;
        private final Integer value;
        private final List<String> data;
        
        public NullableRecordStyle(
                @JsonProperty("name") String name,
                @JsonProperty("value") Integer value,
                @JsonProperty("data") List<String> data) {
            this.name = name;
            this.value = value;
            this.data = data;
        }
        
        public String name() { return name; }
        public Integer value() { return value; }
        public List<String> data() { return data; }
    }
    
    @Test
    public void testRecordStyleWithNullValues() {
        String json = "{\"name\":null,\"value\":null,\"data\":null}";
        NullableRecordStyle obj = Sjf4j.fromJson(json, NullableRecordStyle.class);
        
        assertNotNull(obj);
        assertNull(obj.name());
        assertNull(obj.value());
        assertNull(obj.data());
        log.info("NullableRecordStyle: name={}, value={}, data={}", obj.name(), obj.value(), obj.data());
    }
    
    @Test
    public void testRecordStyleWithMissingFields() {
        String json = "{}";
        NullableRecordStyle obj = Sjf4j.fromJson(json, NullableRecordStyle.class);
        
        assertNotNull(obj);
        assertNull(obj.name());
        assertNull(obj.value());
        assertNull(obj.data());
        log.info("NullableRecordStyle (missing): name={}, value={}, data={}", obj.name(), obj.value(), obj.data());
    }
    
    // ===== Edge Cases: Empty String =====
    
    @Test
    public void testRecordStyleWithEmptyString() {
        String json = "{\"name\":\"\",\"age\":0}";
        SimpleRecordStyle obj = Sjf4j.fromJson(json, SimpleRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("", obj.name());
        assertEquals(0, obj.age());
        log.info("SimpleRecordStyle (empty string): name='{}', age={}", obj.name(), obj.age());
    }
    
    // ===== Edge Cases: Special Characters =====
    
    @Test
    public void testRecordStyleWithSpecialCharacters() {
        String json = "{\"name\":\"测试用户\",\"age\":25}";
        SimpleRecordStyle obj = Sjf4j.fromJson(json, SimpleRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("测试用户", obj.name());
        assertEquals(25, obj.age());
        log.info("SimpleRecordStyle (special chars): name={}, age={}", obj.name(), obj.age());
    }
    
    @Test
    public void testRecordStyleWithEscapedCharacters() {
        String json = "{\"name\":\"Line1\\nLine2\\tTabbed\",\"age\":30}";
        SimpleRecordStyle obj = Sjf4j.fromJson(json, SimpleRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("Line1\nLine2\tTabbed", obj.name());
        assertEquals(30, obj.age());
        log.info("SimpleRecordStyle (escaped): name={}, age={}", obj.name(), obj.age());
    }
    
    // ===== Edge Cases: Unicode and Emoji =====
    
    @Test
    public void testRecordStyleWithUnicodeAndEmoji() {
        String json = "{\"name\":\"Hello 👋 世界 🌍\",\"age\":28}";
        SimpleRecordStyle obj = Sjf4j.fromJson(json, SimpleRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("Hello 👋 世界 🌍", obj.name());
        assertEquals(28, obj.age());
        log.info("SimpleRecordStyle (unicode): name={}, age={}", obj.name(), obj.age());
    }
    
    // ===== Edge Cases: Very Long Strings =====
    
    @Test
    public void testRecordStyleWithLongString() {
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longName.append("a");
        }
        String json = "{\"name\":\"" + longName.toString() + "\",\"age\":35}";
        SimpleRecordStyle obj = Sjf4j.fromJson(json, SimpleRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals(1000, obj.name().length());
        assertEquals(35, obj.age());
        log.info("SimpleRecordStyle (long string): name length={}, age={}", obj.name().length(), obj.age());
    }
    
    // ===== Edge Cases: Numeric Boundaries =====
    
    @Test
    public void testRecordStyleWithMaxInteger() {
        String json = "{\"name\":\"Max Int\",\"age\":" + Integer.MAX_VALUE + "}";
        SimpleRecordStyle obj = Sjf4j.fromJson(json, SimpleRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("Max Int", obj.name());
        assertEquals(Integer.MAX_VALUE, obj.age());
        log.info("SimpleRecordStyle (max int): name={}, age={}", obj.name(), obj.age());
    }
    
    @Test
    public void testRecordStyleWithMinInteger() {
        String json = "{\"name\":\"Min Int\",\"age\":" + Integer.MIN_VALUE + "}";
        SimpleRecordStyle obj = Sjf4j.fromJson(json, SimpleRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("Min Int", obj.name());
        assertEquals(Integer.MIN_VALUE, obj.age());
        log.info("SimpleRecordStyle (min int): name={}, age={}", obj.name(), obj.age());
    }
    
    // ===== Deeply Nested Record-style classes =====
    
    public static class Level3RecordStyle {
        private final String value;
        
        public Level3RecordStyle(
                @JsonProperty("value") @JsonAlias("val") String value) {
            this.value = value;
        }
        
        public String value() { return value; }
    }
    
    public static class Level2RecordStyle {
        private final Level3RecordStyle level3;
        private final String data;
        
        public Level2RecordStyle(
                @JsonProperty("level3") Level3RecordStyle level3,
                @JsonProperty("data") @JsonAlias("info") String data) {
            this.level3 = level3;
            this.data = data;
        }
        
        public Level3RecordStyle level3() { return level3; }
        public String data() { return data; }
    }
    
    public static class Level1RecordStyle {
        private final Level2RecordStyle level2;
        private final String id;
        
        public Level1RecordStyle(
                @JsonProperty("level2") Level2RecordStyle level2,
                @JsonProperty("id") String id) {
            this.level2 = level2;
            this.id = id;
        }
        
        public Level2RecordStyle level2() { return level2; }
        public String id() { return id; }
    }
    
    @Test
    public void testDeeplyNestedRecordStyle() {
        String json = "{\"id\":\"L1\",\"level2\":{\"data\":\"some data\",\"level3\":{\"value\":\"deep value\"}}}";
        Level1RecordStyle obj = Sjf4j.fromJson(json, Level1RecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("L1", obj.id());
        assertNotNull(obj.level2());
        assertEquals("some data", obj.level2().data());
        assertNotNull(obj.level2().level3());
        assertEquals("deep value", obj.level2().level3().value());
        log.info("Level1RecordStyle: id={}, data={}, deep value={}", 
                obj.id(), obj.level2().data(), obj.level2().level3().value());
    }
    
    @Test
    public void testDeeplyNestedRecordStyle_WithAliases() {
        String json = "{\"id\":\"L2\",\"level2\":{\"info\":\"info data\",\"level3\":{\"val\":\"nested value\"}}}";
        Level1RecordStyle obj = Sjf4j.fromJson(json, Level1RecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("L2", obj.id());
        assertNotNull(obj.level2());
        assertEquals("info data", obj.level2().data());
        assertNotNull(obj.level2().level3());
        assertEquals("nested value", obj.level2().level3().value());
        log.info("Level1RecordStyle (aliases): id={}, data={}, deep value={}", 
                obj.id(), obj.level2().data(), obj.level2().level3().value());
    }
    
    // ===== Complex Record-style class with Multiple Aliases and Types =====
    
    public static class ComplexRecordStyle {
        private final String stringField;
        private final int intField;
        private final Double doubleField;
        private final Boolean boolField;
        private final List<String> listField;
        private final AddressRecordStyle nestedField;
        
        public ComplexRecordStyle(
                @JsonProperty("stringField") @JsonAlias({"str", "string_field", "strField"}) String stringField,
                @JsonProperty("intField") @JsonAlias({"int", "int_field", "intVal"}) int intField,
                @JsonProperty("doubleField") @JsonAlias({"dbl", "double_field"}) Double doubleField,
                @JsonProperty("boolField") @JsonAlias({"bool", "boolean_field", "flag"}) Boolean boolField,
                @JsonProperty("listField") @JsonAlias({"list", "items"}) List<String> listField,
                @JsonProperty("nestedField") @JsonAlias("nested") AddressRecordStyle nestedField) {
            this.stringField = stringField;
            this.intField = intField;
            this.doubleField = doubleField;
            this.boolField = boolField;
            this.listField = listField;
            this.nestedField = nestedField;
        }
        
        public String stringField() { return stringField; }
        public int intField() { return intField; }
        public Double doubleField() { return doubleField; }
        public Boolean boolField() { return boolField; }
        public List<String> listField() { return listField; }
        public AddressRecordStyle nestedField() { return nestedField; }
    }
    
    @Test
    public void testComplexRecordStyle_MainProperties() {
        String json = "{\"stringField\":\"test\",\"intField\":42,\"doubleField\":3.14," +
                "\"boolField\":true,\"listField\":[\"a\",\"b\"],\"nestedField\":{\"street\":\"Main St\",\"city\":\"Boston\",\"zipCode\":\"02101\"}}";
        ComplexRecordStyle obj = Sjf4j.fromJson(json, ComplexRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("test", obj.stringField());
        assertEquals(42, obj.intField());
        assertEquals(3.14, obj.doubleField(), 0.001);
        assertTrue(obj.boolField());
        assertEquals(Arrays.asList("a", "b"), obj.listField());
        assertEquals("Main St", obj.nestedField().street());
        log.info("ComplexRecordStyle (main): stringField={}, intField={}, doubleField={}, boolField={}, listField={}", 
                obj.stringField(), obj.intField(), obj.doubleField(), obj.boolField(), obj.listField());
    }
    
    @Test
    public void testComplexRecordStyle_AllAliases() {
        String json = "{\"str\":\"alias\",\"int\":99,\"dbl\":2.71," +
                "\"bool\":false,\"items\":[\"x\",\"y\",\"z\"],\"nested\":{\"streetAddress\":\"Oak Ave\",\"city\":\"NYC\",\"zip\":\"10001\"}}";
        ComplexRecordStyle obj = Sjf4j.fromJson(json, ComplexRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("alias", obj.stringField());
        assertEquals(99, obj.intField());
        assertEquals(2.71, obj.doubleField(), 0.001);
        assertFalse(obj.boolField());
        assertEquals(Arrays.asList("x", "y", "z"), obj.listField());
        assertEquals("Oak Ave", obj.nestedField().street());
        log.info("ComplexRecordStyle (aliases): stringField={}, intField={}, doubleField={}, boolField={}, listField={}", 
                obj.stringField(), obj.intField(), obj.doubleField(), obj.boolField(), obj.listField());
    }
    
    @Test
    public void testComplexRecordStyle_MixedAliases() {
        String json = "{\"string_field\":\"mixed\",\"intVal\":50,\"doubleField\":1.414," +
                "\"flag\":true,\"list\":[\"m\"],\"nestedField\":{\"street\":\"Elm St\",\"city\":\"LA\",\"postalCode\":\"90001\"}}";
        ComplexRecordStyle obj = Sjf4j.fromJson(json, ComplexRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals("mixed", obj.stringField());
        assertEquals(50, obj.intField());
        assertEquals(1.414, obj.doubleField(), 0.001);
        assertTrue(obj.boolField());
        assertEquals(Arrays.asList("m"), obj.listField());
        assertEquals("Elm St", obj.nestedField().street());
        log.info("ComplexRecordStyle (mixed): stringField={}, intField={}, doubleField={}, boolField={}, listField={}", 
                obj.stringField(), obj.intField(), obj.doubleField(), obj.boolField(), obj.listField());
    }
    
    // ===== Record-style class with Primitive vs Boxed Types =====
    
    public static class PrimitiveRecordStyle {
        private final int primitiveInt;
        private final boolean primitiveBoolean;
        private final double primitiveDouble;
        
        public PrimitiveRecordStyle(
                @JsonProperty("primitiveInt") int primitiveInt,
                @JsonProperty("primitiveBoolean") boolean primitiveBoolean,
                @JsonProperty("primitiveDouble") double primitiveDouble) {
            this.primitiveInt = primitiveInt;
            this.primitiveBoolean = primitiveBoolean;
            this.primitiveDouble = primitiveDouble;
        }
        
        public int primitiveInt() { return primitiveInt; }
        public boolean primitiveBoolean() { return primitiveBoolean; }
        public double primitiveDouble() { return primitiveDouble; }
    }
    
    public static class BoxedRecordStyle {
        private final Integer boxedInt;
        private final Boolean boxedBoolean;
        private final Double boxedDouble;
        
        public BoxedRecordStyle(
                @JsonProperty("boxedInt") Integer boxedInt,
                @JsonProperty("boxedBoolean") Boolean boxedBoolean,
                @JsonProperty("boxedDouble") Double boxedDouble) {
            this.boxedInt = boxedInt;
            this.boxedBoolean = boxedBoolean;
            this.boxedDouble = boxedDouble;
        }
        
        public Integer boxedInt() { return boxedInt; }
        public Boolean boxedBoolean() { return boxedBoolean; }
        public Double boxedDouble() { return boxedDouble; }
    }
    
    @Test
    public void testRecordStyleWithPrimitiveTypes() {
        String json = "{\"primitiveInt\":100,\"primitiveBoolean\":true,\"primitiveDouble\":99.99}";
        PrimitiveRecordStyle obj = Sjf4j.fromJson(json, PrimitiveRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals(100, obj.primitiveInt());
        assertTrue(obj.primitiveBoolean());
        assertEquals(99.99, obj.primitiveDouble(), 0.001);
        log.info("PrimitiveRecordStyle: int={}, bool={}, double={}", 
                obj.primitiveInt(), obj.primitiveBoolean(), obj.primitiveDouble());
    }
    
    @Test
    public void testRecordStyleWithBoxedTypes_AllPresent() {
        String json = "{\"boxedInt\":200,\"boxedBoolean\":false,\"boxedDouble\":88.88}";
        BoxedRecordStyle obj = Sjf4j.fromJson(json, BoxedRecordStyle.class);
        
        assertNotNull(obj);
        assertEquals(200, obj.boxedInt());
        assertFalse(obj.boxedBoolean());
        assertEquals(88.88, obj.boxedDouble(), 0.001);
        log.info("BoxedRecordStyle: int={}, bool={}, double={}", 
                obj.boxedInt(), obj.boxedBoolean(), obj.boxedDouble());
    }
    
    @Test
    public void testRecordStyleWithBoxedTypes_NullValues() {
        String json = "{\"boxedInt\":null,\"boxedBoolean\":null,\"boxedDouble\":null}";
        BoxedRecordStyle obj = Sjf4j.fromJson(json, BoxedRecordStyle.class);
        
        assertNotNull(obj);
        assertNull(obj.boxedInt());
        assertNull(obj.boxedBoolean());
        assertNull(obj.boxedDouble());
        log.info("BoxedRecordStyle (null): int={}, bool={}, double={}", 
                obj.boxedInt(), obj.boxedBoolean(), obj.boxedDouble());
    }
    
    // ===== Record-style class with Array Types =====
    
    public static class ArrayRecordStyle {
        private final String[] stringArray;
        private final int[] intArray;
        
        public ArrayRecordStyle(
                @JsonProperty("stringArray") String[] stringArray,
                @JsonProperty("intArray") int[] intArray) {
            this.stringArray = stringArray;
            this.intArray = intArray;
        }
        
        public String[] stringArray() { return stringArray; }
        public int[] intArray() { return intArray; }
    }
    
    @Test
    public void testRecordStyleWithArrays() {
        String json = "{\"stringArray\":[\"a\",\"b\",\"c\"],\"intArray\":[1,2,3]}";
        ArrayRecordStyle obj = Sjf4j.fromJson(json, ArrayRecordStyle.class);
        
        assertNotNull(obj);
        assertNotNull(obj.stringArray());
        assertArrayEquals(new String[]{"a", "b", "c"}, obj.stringArray());
        assertNotNull(obj.intArray());
        assertArrayEquals(new int[]{1, 2, 3}, obj.intArray());
        log.info("ArrayRecordStyle: stringArray length={}, intArray length={}", 
                obj.stringArray().length, obj.intArray().length);
    }
    
    @Test
    public void testRecordStyleWithEmptyArrays() {
        String json = "{\"stringArray\":[],\"intArray\":[]}";
        ArrayRecordStyle obj = Sjf4j.fromJson(json, ArrayRecordStyle.class);
        
        assertNotNull(obj);
        assertNotNull(obj.stringArray());
        assertEquals(0, obj.stringArray().length);
        assertNotNull(obj.intArray());
        assertEquals(0, obj.intArray().length);
        log.info("ArrayRecordStyle (empty): stringArray length={}, intArray length={}", 
                obj.stringArray().length, obj.intArray().length);
    }
}
