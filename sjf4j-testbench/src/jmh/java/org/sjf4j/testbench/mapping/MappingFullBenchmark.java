package org.sjf4j.testbench.mapping;

import org.mapstruct.factory.Mappers;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
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
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.mapping.CompiledMapper;
import org.sjf4j.annotation.mapping.Mapping;
import org.sjf4j.annotation.mapping.MappingOptions;
import org.sjf4j.annotation.mapping.NullValuePolicy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * MapStruct versus SJF4J CompiledMapper on eight comparable mapping workloads.
 * The classic order is deliberately separate from the extended order.
 * All fixtures, mapper lookups and equivalence checks are outside the measured methods.
 * This class does not reuse the exploratory mapper benchmark states.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@Threads(1)
public class MappingFullBenchmark {

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args.length == 0
                ? new String[] {MappingFullBenchmark.class.getSimpleName()}
                : args);
    }

    @State(Scope.Thread)
    public static class ClassicState {
        public ClassicOrder source;
        public MapStructMapper mapstruct;
        public Sjf4jMapper compiled;

        @Setup(Level.Trial)
        public void setup() {
            ClassicAddress billing = new ClassicAddress("1234 Market Street", "San Fran");
            ClassicAddress shipping = new ClassicAddress("1234 West Townsend", "Boston");
            source = new ClassicOrder(
                    new ClassicCustomer("Joe Smith", billing, shipping),
                    List.of(new ClassicProduct("socks"), new ClassicProduct("shoes")));
            mapstruct = Mappers.getMapper(MapStructMapper.class);
            compiled = CompiledInstances.create(Sjf4jMapper.class);
            ClassicTarget expected = mapstruct.classic(source);
            check(Objects.equals(expected, compiled.classic(source)), "classic order");
            check("Joe Smith".equals(expected.getCustomerName())
                    && "San Fran".equals(expected.getBillingCity())
                    && "Boston".equals(expected.getShippingCity()), "classic mapped fields");
            check(expected.getProducts() != null && expected.getProducts().size() == 2, "classic products");
            ClassicOrder partial = new ClassicOrder(
                    new ClassicCustomer("John Doe", new ClassicAddress("93 Newcastle Dr.", null), null),
                    List.of());
            check(Objects.equals(mapstruct.classic(partial), compiled.classic(partial)),
                    "classic partial-order null semantics");
            ClassicOrder empty = new ClassicOrder();
            check(Objects.equals(mapstruct.classic(empty), compiled.classic(empty)),
                    "classic empty-order null semantics");
            check(expected.getProducts() != (Object) source.getProducts(), "classic products must be mapped");
        }
    }

    @State(Scope.Thread)
    public static class PojoState {
        public FlatSource flat = new FlatSource("Ada", "Lovelace", "ignored");
        public Profile profile = new Profile("Grace", new Contact("Arlington", "US"));
        public Account account = new Account("navy", true);
        public UpdateSource update = new UpdateSource(null, "London", 5);
        public UpdateTarget mapstructTarget;
        public UpdateTarget compiledTarget;
        public MapStructMapper mapstruct;
        public Sjf4jMapper compiled;

        @Setup(Level.Trial)
        public void setup() {
            mapstruct = Mappers.getMapper(MapStructMapper.class);
            compiled = CompiledInstances.create(Sjf4jMapper.class);
            checkFlat(mapstruct.flat(flat), compiled.flat(flat));
            check(Objects.equals(mapstruct.nested(profile, account), compiled.nested(profile, account)), "nested record");
            mapstructTarget = new UpdateTarget("preserve", "old", 1);
            compiledTarget = new UpdateTarget("preserve", "old", 1);
            mapstruct.update(update, mapstructTarget);
            compiled.update(compiledTarget, update);
            checkUpdate(mapstructTarget, compiledTarget);
            check("preserve".equals(compiledTarget.name), "null-ignore update");
        }
    }

    @State(Scope.Thread)
    public static class SizedState {
        @Param({"1", "10", "100"})
        public int sizeKb;
    }

    @State(Scope.Thread)
    public static class OrderState extends SizedState {
        public ExtendedOrder source;
        public MapStructMapper mapstruct;
        public Sjf4jMapper compiled;

        @Setup(Level.Trial)
        public void setup() {
            // Approximate JSON-equivalent payload size; never serialize in the timed method.
            List<Item> products = items(Math.max(1, sizeKb * 1024 / 105));
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("priority", "gold");
            attributes.put("campaign", "spring");
            JsonObject metadata = new JsonObject();
            metadata.put("salesChannel", "web");
            metadata.put("experiment", "exp-a");
            source = new ExtendedOrder(
                    new ClassicCustomer("Ada Lovelace",
                            new ClassicAddress("12 Analytical Way", "London"),
                            new ClassicAddress("7 Byron Street", "Oxford")),
                    products, attributes, metadata);
            mapstruct = Mappers.getMapper(MapStructMapper.class);
            compiled = CompiledInstances.create(Sjf4jMapper.class);
            ExtendedTarget expected = mapstruct.extended(source);
            ExtendedTarget actual = compiled.extended(source);
            check(Objects.equals(expected, actual), "extended order");
            check("Ada Lovelace".equals(actual.customerName())
                    && "gold".equals(actual.priority())
                    && "web".equals(actual.salesChannel()), "extended mapped fields");
            ExtendedOrder sparse = new ExtendedOrder(null, List.of(), Map.of(), null);
            check(Objects.equals(mapstruct.extended(sparse), compiled.extended(sparse)),
                    "extended sparse-order null semantics");
            check(actual.products() != (Object) source.products(), "extended products must be converted");
            check(actual.products().size() == products.size(), "extended product count");
        }
    }

    @State(Scope.Thread)
    public static class CollectionState extends SizedState {
        public List<Item> list;
        public Map<String, List<Item>> map;
        public MapStructMapper mapstruct;
        public Sjf4jMapper compiled;

        @Setup(Level.Trial)
        public void setup() {
            list = items(Math.max(1, sizeKb * 1024 / 105));
            map = new LinkedHashMap<>();
            for (int i = 0; i < list.size(); i++) {
                map.computeIfAbsent("group-" + (i % 8), ignored -> new ArrayList<>()).add(list.get(i));
            }
            mapstruct = Mappers.getMapper(MapStructMapper.class);
            compiled = CompiledInstances.create(Sjf4jMapper.class);
            List<ItemDto> mappedList = compiled.list(list);
            check(Objects.equals(mapstruct.list(list), mappedList), "list mapping");
            check(mappedList != (Object) list, "list must be copied");
            Map<String, List<ItemDto>> mappedMap = compiled.map(map);
            check(Objects.equals(mapstruct.map(map), mappedMap), "map value mapping");
            check(mappedMap != (Object) map, "map must be copied");
        }
    }

    @State(Scope.Thread)
    public static class JsonState extends SizedState {
        public Map<String, Object> map;
        public JsonObject jsonObject;
        public MapStructMapper mapstruct;
        public Sjf4jMapper compiled;

        @Setup(Level.Trial)
        public void setup() {
            map = new LinkedHashMap<>();
            jsonObject = new JsonObject();
            map.put("sku", "sku-123");
            map.put("name", "widget");
            map.put("count", 7);
            jsonObject.put("sku", "sku-123");
            jsonObject.put("name", "widget");
            jsonObject.put("count", 7);
            // Extra, unmapped fields make the source 1/10/100KB-ish. The
            // fixed-key projection is deliberately independent of input width.
            int extra = sizeKb * 1024 / 64;
            for (int i = 0; i < extra; i++) {
                String key = "extra-" + i;
                String value = "abcdefghijklmnopqrstuvwxyz0123456789";
                map.put(key, value);
                jsonObject.put(key, value);
            }
            mapstruct = Mappers.getMapper(MapStructMapper.class);
            compiled = CompiledInstances.create(Sjf4jMapper.class);
            JsonTarget expectedMap = mapstruct.jsonMap(map);
            JsonTarget expectedObject = mapstruct.jsonObject(jsonObject);
            check("sku-123".equals(expectedMap.sku())
                    && "widget".equals(expectedMap.name())
                    && Objects.equals(7, expectedMap.count()), "Map projected fields");
            check(Objects.equals(expectedMap, compiled.jsonMap(map)), "Map to POJO");
            check(Objects.equals(expectedObject, compiled.jsonObject(jsonObject)),
                    "JsonObject to POJO");
        }
    }

    @Benchmark public ClassicTarget order_classic_mapstruct(ClassicState s) { return s.mapstruct.classic(s.source); }
    @Benchmark public ClassicTarget order_classic_compiled(ClassicState s) { return s.compiled.classic(s.source); }
    @Benchmark public ExtendedTarget order_extended_mapstruct(OrderState s) { return s.mapstruct.extended(s.source); }
    @Benchmark public ExtendedTarget order_extended_compiled(OrderState s) { return s.compiled.extended(s.source); }

    @Benchmark public FlatTarget pojo_flat_mapstruct(PojoState s) { return s.mapstruct.flat(s.flat); }
    @Benchmark public FlatTarget pojo_flat_compiled(PojoState s) { return s.compiled.flat(s.flat); }
    @Benchmark public CompositeTarget pojo_nested_mapstruct(PojoState s) { return s.mapstruct.nested(s.profile, s.account); }
    @Benchmark public CompositeTarget pojo_nested_compiled(PojoState s) { return s.compiled.nested(s.profile, s.account); }
    @Benchmark public UpdateTarget pojo_update_mapstruct(PojoState s) {
        s.mapstruct.update(s.update, s.mapstructTarget);
        return s.mapstructTarget;
    }
    @Benchmark public UpdateTarget pojo_update_compiled(PojoState s) {
        s.compiled.update(s.compiledTarget, s.update);
        return s.compiledTarget;
    }

    @Benchmark public List<ItemDto> collection_list_mapstruct(CollectionState s) { return s.mapstruct.list(s.list); }
    @Benchmark public List<ItemDto> collection_list_compiled(CollectionState s) { return s.compiled.list(s.list); }
    @Benchmark public Map<String, List<ItemDto>> collection_map_mapstruct(CollectionState s) { return s.mapstruct.map(s.map); }
    @Benchmark public Map<String, List<ItemDto>> collection_map_compiled(CollectionState s) { return s.compiled.map(s.map); }

    @Benchmark public JsonTarget json_map_mapstruct(JsonState s) { return s.mapstruct.jsonMap(s.map); }
    @Benchmark public JsonTarget json_map_compiled(JsonState s) { return s.compiled.jsonMap(s.map); }
    @Benchmark public JsonTarget json_object_mapstruct(JsonState s) { return s.mapstruct.jsonObject(s.jsonObject); }
    @Benchmark public JsonTarget json_object_compiled(JsonState s) { return s.compiled.jsonObject(s.jsonObject); }

    @CompiledMapper
    public interface Sjf4jMapper {
        @Mapping(target = "customerName", source = "$.customer.name")
        @Mapping(target = "billingStreetAddress", source = "$.customer.billingAddress.street")
        @Mapping(target = "billingCity", source = "$.customer.billingAddress.city")
        @Mapping(target = "shippingStreetAddress", source = "$.customer.shippingAddress.street")
        @Mapping(target = "shippingCity", source = "$.customer.shippingAddress.city")
        ClassicTarget classic(ClassicOrder source);

        ClassicProductDto classicProduct(ClassicProduct source);

        @Mapping(target = "customerName", source = "$.customer.name")
        @Mapping(target = "billingCity", source = "$.customer.billingAddress.city")
        @Mapping(target = "shippingCity", source = "$.customer.shippingAddress.city")
        @Mapping(target = "priority", source = "$.attributes.priority")
        @Mapping(target = "salesChannel", source = "$.metadata.salesChannel")
        ExtendedTarget extended(ExtendedOrder source);

        ItemDto item(Item source);

        @Mapping(target = "surname", source = "last")
        @Mapping(target = "fullName", sources = {"first", "last"},
                compute = "(first, last) -> first + \" \" + last")
        @Mapping(target = "ignored", ignore = true)
        FlatTarget flat(FlatSource source);

        @Mapping(target = "name", source = "profile:name")
        @Mapping(target = "tier", source = "account:tier")
        @Mapping(target = "active", source = "account:active")
        @Mapping(target = "contact", source = "profile:contact")
        CompositeTarget nested(Profile profile, Account account);

        ContactDto contact(Contact source);

        @MappingOptions(nulls = NullValuePolicy.IGNORE)
        void update(UpdateTarget target, UpdateSource source);

        List<ItemDto> list(List<Item> source);
        Map<String, List<ItemDto>> map(Map<String, List<Item>> source);

        JsonTarget jsonMap(Map<String, Object> source);
        JsonTarget jsonObject(JsonObject source);
    }

    @org.mapstruct.Mapper
    public interface MapStructMapper {
        @org.mapstruct.Mapping(target = "customerName", source = "customer.name")
        @org.mapstruct.Mapping(target = "billingStreetAddress", source = "customer.billingAddress.street")
        @org.mapstruct.Mapping(target = "billingCity", source = "customer.billingAddress.city")
        @org.mapstruct.Mapping(target = "shippingStreetAddress", source = "customer.shippingAddress.street")
        @org.mapstruct.Mapping(target = "shippingCity", source = "customer.shippingAddress.city")
        ClassicTarget classic(ClassicOrder source);

        ClassicProductDto classicProduct(ClassicProduct source);

        @org.mapstruct.Mapping(target = "customerName", source = "customer.name")
        @org.mapstruct.Mapping(target = "billingCity", source = "customer.billingAddress.city")
        @org.mapstruct.Mapping(target = "shippingCity", source = "customer.shippingAddress.city")
        @org.mapstruct.Mapping(target = "priority",
                expression = "java(source.attributes() == null ? null : source.attributes().get(\"priority\"))")
        @org.mapstruct.Mapping(target = "salesChannel",
                expression = "java(source.metadata() == null ? null : (String) source.metadata().getNode(\"salesChannel\"))")
        ExtendedTarget extended(ExtendedOrder source);

        ItemDto item(Item source);

        @org.mapstruct.Mapping(target = "surname", source = "last")
        @org.mapstruct.Mapping(target = "fullName",
                expression = "java(source.first() + \" \" + source.last())")
        @org.mapstruct.Mapping(target = "ignored", ignore = true)
        FlatTarget flat(FlatSource source);

        @org.mapstruct.Mapping(target = "name", source = "profile.name")
        @org.mapstruct.Mapping(target = "tier", source = "account.tier")
        @org.mapstruct.Mapping(target = "active", source = "account.active")
        @org.mapstruct.Mapping(target = "contact", source = "profile.contact")
        CompositeTarget nested(Profile profile, Account account);

        ContactDto contact(Contact source);

        @org.mapstruct.BeanMapping(
                nullValuePropertyMappingStrategy = org.mapstruct.NullValuePropertyMappingStrategy.IGNORE)
        void update(UpdateSource source, @org.mapstruct.MappingTarget UpdateTarget target);

        List<ItemDto> list(List<Item> source);
        Map<String, List<ItemDto>> map(Map<String, List<Item>> source);

        @org.mapstruct.Mapping(target = "sku", expression = "java((String) source.get(\"sku\"))")
        @org.mapstruct.Mapping(target = "name", expression = "java((String) source.get(\"name\"))")
        @org.mapstruct.Mapping(target = "count", expression = "java(source.get(\"count\"))")
        JsonTarget jsonMap(Map<String, Object> source);

        @org.mapstruct.Mapping(target = "sku", expression = "java((String) source.getNode(\"sku\"))")
        @org.mapstruct.Mapping(target = "name", expression = "java((String) source.getNode(\"name\"))")
        @org.mapstruct.Mapping(target = "count", expression = "java(source.getNode(\"count\"))")
        JsonTarget jsonObject(JsonObject source);
    }

    public record FlatSource(String first, String last, String ignored) {}
    public static final class FlatTarget {
        public String first;
        public String surname;
        public String fullName;
        public String ignored;
    }
    public record Contact(String city, String country) {}
    public record ContactDto(String city, String country) {}
    public record Profile(String name, Contact contact) {}
    public record Account(String tier, boolean active) {}
    public record CompositeTarget(String name, String tier, boolean active, ContactDto contact) {}
    public record UpdateSource(String name, String city, int level) {}
    public static final class UpdateTarget {
        public String name;
        public String city;
        public int level;
        public UpdateTarget() {}
        public UpdateTarget(String name, String city, int level) {
            this.name = name;
            this.city = city;
            this.level = level;
        }
    }
    public record Item(String sku, String name, int quantity, BigDecimal price) {}
    public record ItemDto(String sku, String name, int quantity, BigDecimal price) {}
    public record ExtendedOrder(ClassicCustomer customer, List<Item> products,
                                Map<String, String> attributes, JsonObject metadata) {}
    public record ExtendedTarget(String customerName, String billingCity, String shippingCity,
                                 String priority, String salesChannel, List<ItemDto> products) {}
    public record JsonTarget(String sku, String name, Object count) {}

    // The classic model is intentionally conventional JavaBeans, matching the
    // structure and two-product workload used by arey/java-object-mapper-benchmark.
    public static final class ClassicOrder {
        private ClassicCustomer customer;
        private List<ClassicProduct> products;
        public ClassicOrder() {}
        public ClassicOrder(ClassicCustomer customer, List<ClassicProduct> products) {
            this.customer = customer;
            this.products = products;
        }
        public ClassicCustomer getCustomer() { return customer; }
        public void setCustomer(ClassicCustomer customer) { this.customer = customer; }
        public List<ClassicProduct> getProducts() { return products; }
        public void setProducts(List<ClassicProduct> products) { this.products = products; }
    }
    public static final class ClassicCustomer {
        private String name;
        private ClassicAddress billingAddress;
        private ClassicAddress shippingAddress;
        public ClassicCustomer() {}
        public ClassicCustomer(String name, ClassicAddress billingAddress, ClassicAddress shippingAddress) {
            this.name = name;
            this.billingAddress = billingAddress;
            this.shippingAddress = shippingAddress;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public ClassicAddress getBillingAddress() { return billingAddress; }
        public void setBillingAddress(ClassicAddress billingAddress) { this.billingAddress = billingAddress; }
        public ClassicAddress getShippingAddress() { return shippingAddress; }
        public void setShippingAddress(ClassicAddress shippingAddress) { this.shippingAddress = shippingAddress; }
    }
    public static final class ClassicAddress {
        private String street;
        private String city;
        public ClassicAddress() {}
        public ClassicAddress(String street, String city) { this.street = street; this.city = city; }
        public String getStreet() { return street; }
        public void setStreet(String street) { this.street = street; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
    }
    public static final class ClassicProduct {
        private String name;
        public ClassicProduct() {}
        public ClassicProduct(String name) { this.name = name; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
    public static final class ClassicProductDto {
        private String name;
        public ClassicProductDto() {}
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        @Override public boolean equals(Object obj) {
            return obj instanceof ClassicProductDto other && Objects.equals(name, other.name);
        }
        @Override public int hashCode() { return Objects.hashCode(name); }
    }
    public static final class ClassicTarget {
        private String customerName;
        private String billingStreetAddress;
        private String billingCity;
        private String shippingStreetAddress;
        private String shippingCity;
        private List<ClassicProductDto> products;
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String value) { customerName = value; }
        public String getBillingStreetAddress() { return billingStreetAddress; }
        public void setBillingStreetAddress(String value) { billingStreetAddress = value; }
        public String getBillingCity() { return billingCity; }
        public void setBillingCity(String value) { billingCity = value; }
        public String getShippingStreetAddress() { return shippingStreetAddress; }
        public void setShippingStreetAddress(String value) { shippingStreetAddress = value; }
        public String getShippingCity() { return shippingCity; }
        public void setShippingCity(String value) { shippingCity = value; }
        public List<ClassicProductDto> getProducts() { return products; }
        public void setProducts(List<ClassicProductDto> value) { products = value; }
        @Override public boolean equals(Object obj) {
            if (!(obj instanceof ClassicTarget other)) return false;
            return Objects.equals(customerName, other.customerName)
                    && Objects.equals(billingStreetAddress, other.billingStreetAddress)
                    && Objects.equals(billingCity, other.billingCity)
                    && Objects.equals(shippingStreetAddress, other.shippingStreetAddress)
                    && Objects.equals(shippingCity, other.shippingCity)
                    && Objects.equals(products, other.products);
        }
        @Override public int hashCode() {
            return Objects.hash(customerName, billingStreetAddress, billingCity,
                    shippingStreetAddress, shippingCity, products);
        }
    }

    private static List<Item> items(int count) {
        ArrayList<Item> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(new Item("SKU-" + i, "Product " + i + " / standard catalog item",
                    1 + (i % 13), BigDecimal.valueOf(1099 + (i % 200), 2)));
        }
        return result;
    }

    private static void checkFlat(FlatTarget expected, FlatTarget actual) {
        check(expected != null && actual != null
                && Objects.equals(expected.first, actual.first)
                && Objects.equals(expected.surname, actual.surname)
                && Objects.equals(expected.fullName, actual.fullName)
                && Objects.equals(expected.ignored, actual.ignored), "flat mapping");
    }
    private static void checkUpdate(UpdateTarget expected, UpdateTarget actual) {
        check(expected != null && actual != null
                && Objects.equals(expected.name, actual.name)
                && Objects.equals(expected.city, actual.city)
                && expected.level == actual.level, "update mapping");
    }
    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError("Mapping equivalence failure: " + description);
    }
}
