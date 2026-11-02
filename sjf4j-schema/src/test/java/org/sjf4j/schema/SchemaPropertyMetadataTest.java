package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeProperty;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaPropertyMetadataTest {

    public static class Address {
        public String city;
        public Address() {}
        Address(String city) { this.city = city; }
    }

    public static class ExtendedAddress extends Address {
        public int zip;
        public ExtendedAddress() {}
        ExtendedAddress(String city, int zip) {
            super(city);
            this.zip = zip;
        }
    }

    public static class Holder {
        public Address address;
        public List<Address> addresses;
        public Map<String, Address> addressMap;
        @NodeProperty(valueFormat = "epochMillis")
        public Instant timestamp;
    }

    @Test
    void nestedPropertiesPreserveRuntimeSubtypeAndExplicitCodecSelection() {
        SchemaPlan plan = JsonSchema.fromJson("{\"type\":\"object\",\"properties\":{"
                + "\"address\":{\"type\":\"object\",\"properties\":{"
                + "\"city\":{\"type\":\"string\"},"
                + "\"zip\":{\"type\":\"integer\",\"minimum\":0}}},"
                + "\"addresses\":{\"type\":\"array\",\"items\":{\"type\":\"object\","
                + "\"properties\":{\"city\":{\"type\":\"string\"}}}},"
                + "\"addressMap\":{\"type\":\"object\",\"additionalProperties\":"
                + "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}},"
                + "\"timestamp\":{\"type\":\"integer\"}}}").createPlan();

        Holder holder = new Holder();
        holder.address = new ExtendedAddress("Shanghai", 100);
        holder.addresses = Arrays.asList(new Address("Beijing"));
        holder.addressMap = new LinkedHashMap<>();
        holder.addressMap.put("primary", new Address("Shenzhen"));
        holder.timestamp = Instant.ofEpochMilli(123456789L);

        assertTrue(plan.isValid(holder));
        ((ExtendedAddress) holder.address).zip = -1;
        assertFalse(plan.isValid(holder));

        ((ExtendedAddress) holder.address).zip = 100;
        holder.addressMap.get("primary").city = null;
        assertFalse(plan.isValid(holder));
    }
}
