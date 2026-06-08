package org.sjf4j.jdk17;

import org.mapstruct.factory.Mappers;
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
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.mapper.CompiledMapper;
import org.sjf4j.annotation.mapper.MapperOptions;
import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.annotation.mapper.NullValuePolicy;
import org.sjf4j.compiled.CompiledNodes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * User-shaped Order -> OrderDTO mapper benchmark.
 *
 * <pre>{@code
 * ./gradlew :sjf4j-jdk17-test:jmh -PjmhIncludes=OrderMapperBenchmark
 * }</pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Threads(1)
public class OrderMapperBenchmark {

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(new String[]{OrderMapperBenchmark.class.getSimpleName()});
    }

    @State(Scope.Thread)
    public static class BenchmarkState {
        public Order full;
        public Order noProducts;
        public Order missingCustomer;
        public Sjf4jIgnoreMapper sjf4jIgnoreMapper;
        public Sjf4jSetMapper sjf4jSetMapper;
        public MapStructMapper mapStructMapper;
        public HandMapper handMapper;

        @Setup(Level.Trial)
        public void setup() {
            full = new Order(
                    new Customer(
                            "Ada Lovelace",
                            new Address("12 Analytical Engine Way", "London"),
                            new Address("7 Byron Street", "London")),
                    products("Notebook", "Pencil", "Ruler", "Compass"),
                    attributes("priority", "gold", "coupon", "SUMMER", "region", "emea"),
                    metadata("web", "exp-a"));
            noProducts = new Order(
                    new Customer(
                            "Grace Hopper",
                            new Address("1 Compiler Road", "Arlington"),
                            new Address("9 Navy Yard", "Washington")),
                    null,
                    attributes("priority", "silver", "coupon", "NAVY", "region", "us"),
                    metadata("partner", "exp-b"));
            missingCustomer = new Order(
                    null,
                    products("Keyboard", "Monitor"),
                    attributes("priority", "bronze", "coupon", "BASIC", "region", "apac"),
                    metadata("store", "exp-c"));

            sjf4jIgnoreMapper = CompiledNodes.of(Sjf4jIgnoreMapper.class);
            sjf4jSetMapper = CompiledNodes.of(Sjf4jSetMapper.class);
            mapStructMapper = Mappers.getMapper(MapStructMapper.class);
            handMapper = new HandMapper();

            assertOrderEqual(handMapper.map(full), sjf4jIgnoreMapper.map(full), "sjf4j ignore full");
            assertOrderEqual(handMapper.map(noProducts), sjf4jIgnoreMapper.map(noProducts), "sjf4j ignore noProducts");
            assertOrderEqual(handMapper.map(missingCustomer), sjf4jIgnoreMapper.map(missingCustomer), "sjf4j ignore missingCustomer");
            assertOrderEqual(handMapper.map(full), sjf4jSetMapper.map(full), "sjf4j set full");
            assertOrderEqual(handMapper.map(full), mapStructMapper.map(full), "mapstruct full");
        }
    }

    @Benchmark
    public OrderDTO full_sjf4j_ignore(BenchmarkState state) {
        return state.sjf4jIgnoreMapper.map(state.full);
    }

    @Benchmark
    public OrderDTO full_sjf4j_set(BenchmarkState state) {
        return state.sjf4jSetMapper.map(state.full);
    }

    @Benchmark
    public OrderDTO full_mapstruct(BenchmarkState state) {
        return state.mapStructMapper.map(state.full);
    }

    @Benchmark
    public OrderDTO full_hand(BenchmarkState state) {
        return state.handMapper.map(state.full);
    }

    @Benchmark
    public OrderDTO noProducts_sjf4j_ignore(BenchmarkState state) {
        return state.sjf4jIgnoreMapper.map(state.noProducts);
    }

    @Benchmark
    public OrderDTO noProducts_hand(BenchmarkState state) {
        return state.handMapper.map(state.noProducts);
    }

    @Benchmark
    public OrderDTO missingCustomer_sjf4j_ignore(BenchmarkState state) {
        return state.sjf4jIgnoreMapper.map(state.missingCustomer);
    }

    @Benchmark
    public OrderDTO missingCustomer_hand(BenchmarkState state) {
        return state.handMapper.map(state.missingCustomer);
    }

    @CompiledMapper
    public interface Sjf4jIgnoreMapper {
        @MapperOptions(nulls = NullValuePolicy.IGNORE, using = {"productToProductDTO"})
        @Mapping(target = "customerName", source = "$.customer.name")
        @Mapping(target = "billingStreetAddress", source = "$.customer.billingAddress.street")
        @Mapping(target = "billingCity", source = "$.customer.billingAddress.city")
        @Mapping(target = "shippingStreetAddress", source = "$.customer.shippingAddress.street")
        @Mapping(target = "shippingCity", source = "$.customer.shippingAddress.city")
        @Mapping(target = "priority", source = "$.attributes.priority")
        @Mapping(target = "salesChannel", source = "$.metadata.salesChannel")
        @Mapping(target = "attributes", source = "attributes")
        OrderDTO map(Order source);

        ProductDTO productToProductDTO(Product product);
    }

    @CompiledMapper
    public interface Sjf4jSetMapper {
        @MapperOptions(using = {"productToProductDTO"})
        @Mapping(target = "customerName", source = "$.customer.name")
        @Mapping(target = "billingStreetAddress", source = "$.customer.billingAddress.street")
        @Mapping(target = "billingCity", source = "$.customer.billingAddress.city")
        @Mapping(target = "shippingStreetAddress", source = "$.customer.shippingAddress.street")
        @Mapping(target = "shippingCity", source = "$.customer.shippingAddress.city")
        @Mapping(target = "priority", source = "$.attributes.priority")
        @Mapping(target = "salesChannel", source = "$.metadata.salesChannel")
        @Mapping(target = "attributes", source = "attributes")
        OrderDTO map(Order source);

        ProductDTO productToProductDTO(Product product);
    }

    @org.mapstruct.Mapper
    public interface MapStructMapper {
        @org.mapstruct.Mapping(target = "customerName", source = "customer.name")
        @org.mapstruct.Mapping(target = "billingStreetAddress", source = "customer.billingAddress.street")
        @org.mapstruct.Mapping(target = "billingCity", source = "customer.billingAddress.city")
        @org.mapstruct.Mapping(target = "shippingStreetAddress", source = "customer.shippingAddress.street")
        @org.mapstruct.Mapping(target = "shippingCity", source = "customer.shippingAddress.city")
        @org.mapstruct.Mapping(target = "priority", expression = "java(source.getAttributes() == null ? null : source.getAttributes().get(\"priority\"))")
        @org.mapstruct.Mapping(target = "salesChannel", expression = "java(source.getMetadata() == null ? null : source.getMetadata().getNode(\"salesChannel\"))")
        OrderDTO map(Order source);

        ProductDTO productToProductDTO(Product product);
    }

    public static final class HandMapper {
        public OrderDTO map(Order source) {
            if (source == null) return null;
            OrderDTO target = new OrderDTO();
            Customer customer = source.getCustomer();
            if (customer != null) {
                target.setCustomerName(customer.getName());
                Address billingAddress = customer.getBillingAddress();
                if (billingAddress != null) {
                    target.setBillingStreetAddress(billingAddress.getStreet());
                    target.setBillingCity(billingAddress.getCity());
                }
                Address shippingAddress = customer.getShippingAddress();
                if (shippingAddress != null) {
                    target.setShippingStreetAddress(shippingAddress.getStreet());
                    target.setShippingCity(shippingAddress.getCity());
                }
            }
            List<Product> products = source.getProducts();
            if (products != null) {
                ArrayList<ProductDTO> productDTOs = new ArrayList<>(products.size());
                for (Product product : products) {
                    productDTOs.add(product == null ? null : new ProductDTO(product.getName()));
                }
                target.setProducts(productDTOs);
            }
            Map<String, String> attributes = source.getAttributes();
            if (attributes != null) {
                target.setPriority(attributes.get("priority"));
                target.setAttributes(new LinkedHashMap<>(attributes));
            }
            JsonObject metadata = source.getMetadata();
            if (metadata != null) {
                target.setSalesChannel(metadata.getNode("salesChannel"));
            }
            return target;
        }
    }

    public static final class Order {
        private Customer customer;
        private List<Product> products;
        private Map<String, String> attributes;
        private JsonObject metadata;

        public Order() {}

        public Order(Customer customer, List<Product> products, Map<String, String> attributes, JsonObject metadata) {
            this.customer = customer;
            this.products = products;
            this.attributes = attributes;
            this.metadata = metadata;
        }

        public Customer getCustomer() { return customer; }

        public void setCustomer(Customer customer) { this.customer = customer; }

        public List<Product> getProducts() { return products; }

        public void setProducts(List<Product> products) { this.products = products; }

        public Map<String, String> getAttributes() { return attributes; }

        public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }

        public JsonObject getMetadata() { return metadata; }

        public void setMetadata(JsonObject metadata) { this.metadata = metadata; }
    }

    public static final class Customer {
        private String name;
        private Address billingAddress;
        private Address shippingAddress;

        public Customer() {}

        public Customer(String name, Address billingAddress, Address shippingAddress) {
            this.name = name;
            this.billingAddress = billingAddress;
            this.shippingAddress = shippingAddress;
        }

        public String getName() { return name; }

        public void setName(String name) { this.name = name; }
        public Address getBillingAddress() { return billingAddress; }
        public void setBillingAddress(Address billingAddress) { this.billingAddress = billingAddress; }
        public Address getShippingAddress() { return shippingAddress; }
        public void setShippingAddress(Address shippingAddress) { this.shippingAddress = shippingAddress; }
    }

    public static final class Address {
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
    }

    public static final class Product {
        private String name;

        public Product() {}
        public Product(String name) { this.name = name; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static final class OrderDTO {
        private String customerName;
        private String billingStreetAddress;
        private String billingCity;
        private String shippingStreetAddress;
        private String shippingCity;
        private String priority;
        private Object salesChannel;
        private Map<String, String> attributes;
        private List<ProductDTO> products;

        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        public String getBillingStreetAddress() { return billingStreetAddress; }
        public void setBillingStreetAddress(String billingStreetAddress) { this.billingStreetAddress = billingStreetAddress; }
        public String getBillingCity() { return billingCity; }
        public void setBillingCity(String billingCity) { this.billingCity = billingCity; }
        public String getShippingStreetAddress() { return shippingStreetAddress; }
        public void setShippingStreetAddress(String shippingStreetAddress) { this.shippingStreetAddress = shippingStreetAddress; }
        public String getShippingCity() { return shippingCity; }
        public void setShippingCity(String shippingCity) { this.shippingCity = shippingCity; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public Object getSalesChannel() { return salesChannel; }
        public void setSalesChannel(Object salesChannel) { this.salesChannel = salesChannel; }
        public Map<String, String> getAttributes() { return attributes; }
        public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }
        public List<ProductDTO> getProducts() { return products; }
        public void setProducts(List<ProductDTO> products) { this.products = products; }
    }

    public static final class ProductDTO {
        private String name;

        public ProductDTO() {}
        public ProductDTO(String name) { this.name = name; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    private static List<Product> products(String... names) {
        ArrayList<Product> products = new ArrayList<>(names.length);
        for (String name : names) {
            products.add(new Product(name));
        }
        return products;
    }

    private static Map<String, String> attributes(String... keyValues) {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            attributes.put(keyValues[i], keyValues[i + 1]);
        }
        return attributes;
    }

    private static JsonObject metadata(String salesChannel, String experiment) {
        JsonObject metadata = new JsonObject();
        metadata.put("salesChannel", salesChannel);
        metadata.put("experiment", experiment);
        return metadata;
    }

    private static void assertOrderEqual(OrderDTO expected, OrderDTO actual, String label) {
        if (expected == actual) return;
        if (expected == null || actual == null
                || !Objects.equals(expected.getCustomerName(), actual.getCustomerName())
                || !Objects.equals(expected.getBillingStreetAddress(), actual.getBillingStreetAddress())
                || !Objects.equals(expected.getBillingCity(), actual.getBillingCity())
                || !Objects.equals(expected.getShippingStreetAddress(), actual.getShippingStreetAddress())
                || !Objects.equals(expected.getShippingCity(), actual.getShippingCity())
                || !Objects.equals(expected.getPriority(), actual.getPriority())
                || !Objects.equals(expected.getSalesChannel(), actual.getSalesChannel())
                || !Objects.equals(expected.getAttributes(), actual.getAttributes())) {
            throw new AssertionError(label + " mismatch");
        }
        assertProductsEqual(expected.getProducts(), actual.getProducts(), label);
    }

    private static void assertProductsEqual(List<ProductDTO> expected, List<ProductDTO> actual, String label) {
        if (expected == actual) return;
        if (expected == null || actual == null || expected.size() != actual.size()) {
            throw new AssertionError(label + " products mismatch");
        }
        for (int i = 0; i < expected.size(); i++) {
            ProductDTO e = expected.get(i);
            ProductDTO a = actual.get(i);
            if (e == a) continue;
            if (e == null || a == null || !Objects.equals(e.getName(), a.getName())) {
                throw new AssertionError(label + " products[" + i + "] mismatch");
            }
        }
    }
}
