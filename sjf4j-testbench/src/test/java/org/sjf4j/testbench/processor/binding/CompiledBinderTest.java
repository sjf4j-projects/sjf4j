package org.sjf4j.testbench.processor.binding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.BindingBackend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.annotation.binding.WriteTo;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.binding.simple.SimpleJsonBinder;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end tests for compiled JSON binders across supported backends. */
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

    private static final String REORDERED_PERSON_JSON = "{"
            + "\"unknown\":{\"deep\":[1,{\"x\":2}]},"
            + "\"display_name\":\"Ada Lovelace\","
            + "\"role\":\"ADMIN\","
            + "\"id\":7,"
            + "\"active\":true,"
            + "\"level\":3,"
            + "\"name\":\"Ada\""
            + "}";


    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonBackends")
    public void readsAndWritesScalarValues(String backend, BinderContract binder) throws IOException {

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


    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonBackends")
    public void supportsAllReadInputForms(String backend, BinderContract binder) throws IOException {
        byte[] bytes = PERSON_JSON.getBytes(StandardCharsets.UTF_8);

        assertBasicPerson(binder.readPerson(PERSON_JSON));
        assertBasicPerson(binder.readPerson(bytes));
        assertBasicPerson(binder.readPerson(new StringReader(PERSON_JSON)));
        assertBasicPerson(binder.readPerson(new ByteArrayInputStream(bytes)));
    }


    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonBackends")
    public void supportsAllWriteOutputForms(String backend, BinderContract binder) throws IOException {
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


    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonBackends")
    public void roundTripsNestedPojoCollectionsAndMap(String backend, BinderContract binder) throws IOException {
        Person source = personFixture();

        Person result = binder.readPerson(binder.writePerson(source));

        assertPersonEquals(source, result);
    }


    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonBackends")
    public void supportsRootCollectionsAndMaps(String backend, BinderContract binder) throws IOException {

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


    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonBackends")
    public void handlesNullRootAndCompileTimeUnknownFallback(String backend, BinderContract binder) throws IOException {

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


    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonBackends")
    public void readsObjectValuesLikeRuntimeBinding(String backend, BinderContract binder) throws IOException {
        String input = "{"
                + "\"text\":\"Ada\","
                + "\"number\":7,"
                + "\"enabled\":true,"
                + "\"empty\":null,"
                + "\"nested\":{\"first\":\"one\",\"second\":2},"
                + "\"items\":[false,{\"third\":3},null]"
                + "}";

        Map<String, Object> compiled = binder.readObjectMap(input);
        Map<?, ?> runtime = (Map<?, ?>) new SimpleJsonBinder().readNode(input, Object.class);

        assertEquals(runtime, compiled);
        assertEquals(LinkedHashMap.class, compiled.getClass());
        assertEquals(Arrays.asList("text", "number", "enabled", "empty", "nested", "items"),
                new ArrayList<String>(compiled.keySet()));

        Map<?, ?> nested = (Map<?, ?>) compiled.get("nested");
        List<?> items = (List<?>) compiled.get("items");
        assertEquals(LinkedHashMap.class, nested.getClass());
        assertEquals(ArrayList.class, items.getClass());
        assertEquals(Arrays.asList("first", "second"), new ArrayList<Object>(nested.keySet()));
        assertEquals(LinkedHashMap.class, items.get(1).getClass());

        String holderInput = "{"
                + "\"value\":{\"text\":\"Ada\",\"nested\":[1,{\"enabled\":true}]},"
                + "\"values\":[\"first\",{\"inner\":[null,2]},[false,3]]"
                + "}";
        ObjectHolder holder = binder.readObjectHolder(holderInput);
        Map<?, ?> runtimeHolder = (Map<?, ?>) new SimpleJsonBinder().readNode(holderInput, Object.class);
        Map<?, ?> holderValue = (Map<?, ?>) holder.getValue();
        List<?> holderNested = (List<?>) holderValue.get("nested");
        List<?> holderValues = holder.getValues();

        assertEquals(runtimeHolder.get("value"), holder.getValue());
        assertEquals(runtimeHolder.get("values"), holderValues);
        assertEquals(LinkedHashMap.class, holderValue.getClass());
        assertEquals(Arrays.asList("text", "nested"), new ArrayList<Object>(holderValue.keySet()));
        assertEquals(ArrayList.class, holderNested.getClass());
        assertEquals(ArrayList.class, holderValues.getClass());
        assertEquals(LinkedHashMap.class, holderValues.get(1).getClass());
        assertEquals(ArrayList.class, holderValues.get(2).getClass());
    }

    @Test
    public void jackson2ReadsExactObjectMapAsRawGraph() throws IOException {
        BinderContract binder = CompiledInstances.of(Jackson2Binder.class);

        assertNull(binder.readObjectMap("null"));
        assertThrows(IOException.class, () -> binder.readObjectMap("[]"));

        Map<String, Object> value = binder.readObjectMap(
                "{\"name\":\"Ada\",\"nested\":{\"enabled\":true},\"items\":[1,null]}");

        assertEquals(LinkedHashMap.class, value.getClass());
        assertEquals("Ada", value.get("name"));
        assertEquals(LinkedHashMap.class, value.get("nested").getClass());
        assertEquals(ArrayList.class, value.get("items").getClass());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonBackends")
    public void doesNotCloseCallerOwnedIo(String backend, BinderContract binder) throws IOException {
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


    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonBackends")
    public void matchesReorderedAndUnknownProperties(String backend, BinderContract binder) throws IOException {
        assertBasicPerson(binder.readPerson(REORDERED_PERSON_JSON));
    }


    private static Stream<Arguments> jsonBackends() {
        return Stream.of(
                Arguments.of("simple", CompiledInstances.of(SimpleBinder.class)),
                Arguments.of("jackson3", CompiledInstances.of(Jackson3Binder.class)),
                Arguments.of("jackson2", CompiledInstances.of(Jackson2Binder.class)),
                Arguments.of("gson", CompiledInstances.of(GsonBinder.class)),
                Arguments.of("fastjson2", CompiledInstances.of(Fastjson2Binder.class)),
                Arguments.of("jsonp", CompiledInstances.of(JsonpBinder.class)),
                Arguments.of("auto", CompiledInstances.of(AutoBinder.class))
        );
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


    public static final class ObjectHolder {

        private Object value;
        private List<Object> values;

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public List<Object> getValues() {
            return values;
        }

        public void setValues(List<Object> values) {
            this.values = values;
        }
    }


    public interface BinderContract {

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

        @ReadFrom
        Map<String, Object> readObjectMap(String input) throws IOException;

        @ReadFrom
        ObjectHolder readObjectHolder(String input) throws IOException;

        @WriteTo
        String writeAddresses(Map<String, Address> values) throws IOException;

        @ReadFrom
        Object readAny(String input) throws IOException;

        @WriteTo
        String writeAny(Object value) throws IOException;
    }

    @CompiledBinder(backend = BindingBackend.SIMPLE)
    public interface SimpleBinder extends BinderContract {}

    @CompiledBinder(backend = BindingBackend.JACKSON3)
    public interface Jackson3Binder extends BinderContract {}

    @CompiledBinder(backend = BindingBackend.JACKSON2)
    public interface Jackson2Binder extends BinderContract {}

    @CompiledBinder(backend = BindingBackend.GSON)
    public interface GsonBinder extends BinderContract {}

    @CompiledBinder(backend = BindingBackend.FASTJSON2)
    public interface Fastjson2Binder extends BinderContract {}

    @CompiledBinder(backend = BindingBackend.JSONP)
    public interface JsonpBinder extends BinderContract {}

    /** Leaves backend selection at AUTO to exercise compile-time resolution. */
    @CompiledBinder
    public interface AutoBinder extends BinderContract {}


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
