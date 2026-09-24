package org.sjf4j.testbench.processor.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.BindingBackend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.annotation.binding.WriteTo;
import org.sjf4j.annotation.node.NodeProperty;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end tests for the V2 compiled binder API. */
public class CompiledBinderTest {

    private static final String PERSON_JSON = "{"
            + "\"id\":7,"
            + "\"name\":\"Ada\","
            + "\"active\":true,"
            + "\"role\":\"ADMIN\","
            + "\"level\":3,"
            + "\"display_name\":\"Ada Lovelace\","
            + "\"unknown\":{\"deep\":[1,{\"x\":2}]}"
            + "}";


    @Test
    public void readsAndWritesScalarValues() throws IOException {
        TestBinder binder = CompiledInstances.of(TestBinder.class);

        assertEquals(42, binder.readInt("42"));
        assertEquals("Ada", binder.readString("\"Ada\""));
        assertNull(binder.readString("null"));
        assertEquals(Role.ADMIN, binder.readRole("\"ADMIN\""));
        assertEquals(new BigDecimal("98.75"), binder.readDecimal("98.75"));

        assertEquals("\"Ada\"", binder.writeString("Ada"));
        assertEquals("null", binder.writeString(null));
        assertEquals("\"USER\"", binder.writeRole(Role.USER));
        assertEquals("98.75", binder.writeDecimal(new BigDecimal("98.75")));
    }


    @Test
    public void supportsAllReadInputForms() throws IOException {
        TestBinder binder = CompiledInstances.of(TestBinder.class);
        byte[] bytes = PERSON_JSON.getBytes(StandardCharsets.UTF_8);

        assertBasicPerson(binder.readPerson(PERSON_JSON));
        assertBasicPerson(binder.readPerson(bytes));
        assertBasicPerson(binder.readPerson(new StringReader(PERSON_JSON)));
        assertBasicPerson(binder.readPerson(new ByteArrayInputStream(bytes)));
    }


    @Test
    public void supportsAllWriteOutputForms() throws IOException {
        TestBinder binder = CompiledInstances.of(TestBinder.class);
        Person source = personFixture();

        String stringJson = binder.writePerson(source);
        assertPersonEquals(source, binder.readPerson(stringJson));

        byte[] bytes = binder.writePersonBytes(source);
        assertPersonEquals(source, binder.readPerson(bytes));

        StringWriter chars = new StringWriter();
        binder.writePerson(source, chars);
        assertPersonEquals(source, binder.readPerson(chars.toString()));

        ByteArrayOutputStream binary = new ByteArrayOutputStream();
        binder.writePerson(source, binary);
        assertPersonEquals(source, binder.readPerson(binary.toByteArray()));
    }


    @Test
    public void roundTripsNestedPojoCollectionsAndMap() throws IOException {
        TestBinder binder = CompiledInstances.of(TestBinder.class);
        Person source = personFixture();

        Person result = binder.readPerson(binder.writePerson(source));

        assertPersonEquals(source, result);
    }


    @Test
    public void supportsRootCollectionsAndMaps() throws IOException {
        TestBinder binder = CompiledInstances.of(TestBinder.class);

        List<Integer> numbers = binder.readNumbers("[1,2,null,4]");
        assertEquals(Arrays.asList(1, 2, null, 4), numbers);
        assertEquals("[1,2,null,4]", binder.writeNumbers(numbers));

        Map<String, Address> addresses = binder.readAddresses(
                "{\"home\":{\"city\":\"London\",\"zip\":\"NW1\"},\"work\":null}");

        assertEquals("London", addresses.get("home").getCity());
        assertEquals("NW1", addresses.get("home").getZip());
        assertNull(addresses.get("work"));

        Map<String, Address> roundTrip =
                binder.readAddresses(binder.writeAddresses(addresses));

        assertEquals("London", roundTrip.get("home").getCity());
        assertEquals("NW1", roundTrip.get("home").getZip());
        assertNull(roundTrip.get("work"));
    }


    @Test
    public void handlesNullRootAndCompileTimeUnknownFallback() throws IOException {
        TestBinder binder = CompiledInstances.of(TestBinder.class);

        assertNull(binder.readPerson("null"));
        assertEquals("null", binder.writePerson(null));

        Object raw = binder.readAny(
                "{\"name\":\"Ada\",\"values\":[1,true,null]}");

        assertTrue(raw instanceof Map);
        Map<?, ?> object = (Map<?, ?>) raw;
        assertEquals("Ada", object.get("name"));
        assertTrue(object.get("values") instanceof List);

        Object roundTrip = binder.readAny(binder.writeAny(raw));
        assertEquals(raw, roundTrip);
    }


    @Test
    public void doesNotCloseCallerOwnedIo() throws IOException {
        TestBinder binder = CompiledInstances.of(TestBinder.class);
        byte[] bytes = PERSON_JSON.getBytes(StandardCharsets.UTF_8);

        TrackingReader reader = new TrackingReader(PERSON_JSON);
        assertBasicPerson(binder.readPerson(reader));
        assertFalse(reader.closed);

        TrackingInputStream input = new TrackingInputStream(bytes);
        assertBasicPerson(binder.readPerson(input));
        assertFalse(input.closed);

        TrackingWriter writer = new TrackingWriter();
        binder.writePerson(personFixture(), writer);
        assertFalse(writer.closed);
        assertPersonEquals(personFixture(), binder.readPerson(writer.toString()));

        TrackingOutputStream output = new TrackingOutputStream();
        binder.writePerson(personFixture(), output);
        assertFalse(output.closed);
        assertPersonEquals(personFixture(), binder.readPerson(output.toByteArray()));
    }


    @Test
    public void autoBackendBinderUsesTheSamePublicContract() throws IOException {
        AutoBinder binder = CompiledInstances.of(AutoBinder.class);

        assertEquals("Ada", binder.readName("\"Ada\""));
        assertEquals("\"Ada\"", binder.writeName("Ada"));
    }


    private static Person personFixture() {
        Person person = new Person();
        person.setId(839201L);
        person.setName("Alice");
        person.setActive(true);
        person.setRole(Role.USER);
        person.level = 5;
        person.setDisplayName("Alice Builder");
        person.setAddress(new Address("San Francisco", "94105"));
        person.setTags(Arrays.asList("java", null, "json"));
        person.setScores(new LinkedHashSet<Integer>(Arrays.asList(3, 5, 8)));

        Map<String, Address> offices = new LinkedHashMap<String, Address>();
        offices.put("home", new Address("San Francisco", "94105"));
        offices.put("work", new Address("Oakland", "94607"));
        person.setOffices(offices);

        return person;
    }


    private static void assertBasicPerson(Person person) {
        assertEquals(7L, person.getId());
        assertEquals("Ada", person.getName());
        assertTrue(person.isActive());
        assertEquals(Role.ADMIN, person.getRole());
        assertEquals(3, person.level);
        assertEquals("Ada Lovelace", person.getDisplayName());
    }


    private static void assertPersonEquals(
            Person expected,
            Person actual) {

        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.isActive(), actual.isActive());
        assertEquals(expected.getRole(), actual.getRole());
        assertEquals(expected.level, actual.level);
        assertEquals(expected.getDisplayName(), actual.getDisplayName());

        if (expected.getAddress() == null) {
            assertNull(actual.getAddress());
        } else {
            assertEquals(expected.getAddress().getCity(), actual.getAddress().getCity());
            assertEquals(expected.getAddress().getZip(), actual.getAddress().getZip());
        }

        assertEquals(expected.getTags(), actual.getTags());
        assertEquals(expected.getScores(), actual.getScores());
        assertEquals(expected.getOffices().keySet(), actual.getOffices().keySet());

        for (String key : expected.getOffices().keySet()) {
            Address left = expected.getOffices().get(key);
            Address right = actual.getOffices().get(key);

            if (left == null) {
                assertNull(right);
            } else {
                assertEquals(left.getCity(), right.getCity());
                assertEquals(left.getZip(), right.getZip());
            }
        }
    }


    public enum Role {
        USER,
        ADMIN
    }


    public static final class Address {

        private String city;
        private String zip;

        public Address() {
        }

        public Address(String city, String zip) {
            this.city = city;
            this.zip = zip;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getZip() {
            return zip;
        }

        public void setZip(String zip) {
            this.zip = zip;
        }
    }


    public static final class Person {

        private long id;
        private String name;
        private boolean active;
        private Role role;
        public int level;
        private String displayName;
        private Address address;
        private List<String> tags;
        private Set<Integer> scores;
        private Map<String, Address> offices;

        public Person() {
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        public Role getRole() {
            return role;
        }

        public void setRole(Role role) {
            this.role = role;
        }

        @NodeProperty("display_name")
        public String getDisplayName() {
            return displayName;
        }

        @NodeProperty("display_name")
        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public Address getAddress() {
            return address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public Set<Integer> getScores() {
            return scores;
        }

        public void setScores(Set<Integer> scores) {
            this.scores = scores;
        }

        public Map<String, Address> getOffices() {
            return offices;
        }

        public void setOffices(Map<String, Address> offices) {
            this.offices = offices;
        }
    }


    @CompiledBinder(backend = BindingBackend.SIMPLE)
    public interface TestBinder {

        @ReadFrom
        int readInt(String input) throws IOException;

        @ReadFrom
        String readString(String input) throws IOException;

        @WriteTo
        String writeString(String value) throws IOException;

        @ReadFrom
        Role readRole(String input) throws IOException;

        @WriteTo
        String writeRole(Role value) throws IOException;

        @ReadFrom
        BigDecimal readDecimal(String input) throws IOException;

        @WriteTo
        String writeDecimal(BigDecimal value) throws IOException;

        @ReadFrom
        Person readPerson(String input) throws IOException;

        @ReadFrom
        Person readPerson(byte[] input) throws IOException;

        @ReadFrom
        Person readPerson(Reader input) throws IOException;

        @ReadFrom
        Person readPerson(InputStream input) throws IOException;

        @WriteTo
        String writePerson(Person value) throws IOException;

        @WriteTo
        byte[] writePersonBytes(Person value) throws IOException;

        @WriteTo
        void writePerson(Person value, Writer output) throws IOException;

        @WriteTo
        void writePerson(Person value, OutputStream output) throws IOException;

        @ReadFrom
        List<Integer> readNumbers(String input) throws IOException;

        @WriteTo
        String writeNumbers(List<Integer> values) throws IOException;

        @ReadFrom
        Map<String, Address> readAddresses(String input) throws IOException;

        @WriteTo
        String writeAddresses(Map<String, Address> values) throws IOException;

        @ReadFrom
        Object readAny(String input) throws IOException;

        @WriteTo
        String writeAny(Object value) throws IOException;
    }


    /** Leaves backend selection at AUTO to exercise compile-time resolution. */
    @CompiledBinder
    public interface AutoBinder {

        @ReadFrom
        String readName(String input) throws IOException;

        @WriteTo
        String writeName(String value) throws IOException;
    }


    private static final class TrackingReader extends StringReader {
        private boolean closed;

        private TrackingReader(String value) {
            super(value);
        }

        @Override
        public void close() {
            closed = true;
            super.close();
        }
    }


    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackingInputStream(byte[] value) {
            super(value);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }


    private static final class TrackingWriter extends StringWriter {
        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }


    private static final class TrackingOutputStream extends ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
