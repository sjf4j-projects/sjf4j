package org.sjf4j;

import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Currency;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CodecTypesTest {

//    @TestFactory
    public Stream<DynamicTest> testWithJsonLib() {
        return Stream.of(
                DynamicTest.dynamicTest("Run with Simple JSON", () -> {
                    Sjf4jConfig.useSimpleJsonAsGlobal();
                    testAll();
                }),
                DynamicTest.dynamicTest("Run with Jackson", () -> {
                    Sjf4jConfig.useJacksonAsGlobal();
                    testAll();
                }),
                DynamicTest.dynamicTest("Run with Gson", () -> {
                    Sjf4jConfig.useGsonAsGlobal();
                    testAll();
                }),
                DynamicTest.dynamicTest("Run with Fastjson2", () -> {
                    Sjf4jConfig.useFastjson2AsGlobal();
                    testAll();
                })
        );
    }

    public void testAll() throws Exception {
//        optional_missing_property();
        optional_null_property();
//        optional_present_property();
//        optional_primitive_present();
//
//        java_time_mapping();
//        collection_mapping();
//        enum_mapping();
//        uuid_uri_url_mapping();
//        locale_currency_zoneid_mapping();
//        nested_optional_object();
    }


    /// Optional
    @Getter @Setter
    static class OptionalBean {
        public Optional<String> name;
    }
    void optional_missing_property() {
        String json = "{}";
        OptionalBean bean = Sjf4j.fromJson(json, OptionalBean.class);
        assertFalse(bean.name.isPresent());
    }
    void optional_null_property() {
        String json = "{ \"name\": null }";
        OptionalBean bean = Sjf4j.fromJson(json, OptionalBean.class);
        assertFalse(bean.name.isPresent());
    }
    void optional_present_property() {
        String json = "{ \"name\": \"Alice\" }";
        OptionalBean bean = Sjf4j.fromJson(json, OptionalBean.class);
        assertEquals("Alice", bean.name.get());
    }

    static class OptionalPrimitiveBean {
        public OptionalInt count;
        public OptionalLong total;
        public OptionalDouble ratio;
    }
    void optional_primitive_present() {
        String json = "{ \"count\": 3, \"total\": 10, \"ratio\": 0.5 }";
        OptionalPrimitiveBean bean = Sjf4j.fromJson(json, OptionalPrimitiveBean.class);
        assertEquals(3, bean.count.getAsInt());
        assertEquals(10L, bean.total.getAsLong());
        assertEquals(0.5, bean.ratio.getAsDouble());
    }

    /// java.time
    static class TimeBean {
        public LocalDate date;
        public LocalDateTime dateTime;
        public Instant instant;
        public OffsetDateTime offsetDateTime;
        public ZonedDateTime zonedDateTime;
        public Duration duration;
    }
    void java_time_mapping() {
        String json = "{\n" +
                "          \"date\": \"2024-01-01\",\n" +
                "          \"dateTime\": \"2024-01-01T10:00:00\",\n" +
                "          \"instant\": \"2024-01-01T10:00:00Z\",\n" +
                "          \"offsetDateTime\": \"2024-01-01T10:00:00+08:00\",\n" +
                "          \"zonedDateTime\": \"2024-01-01T10:00:00+08:00[Asia/Shanghai]\",\n" +
                "          \"duration\": \"PT10S\"\n" +
                "        }";
        TimeBean bean = Sjf4j.fromJson(json, TimeBean.class);
        assertEquals(LocalDate.of(2024,1,1), bean.date);
        assertEquals(Duration.ofSeconds(10), bean.duration);
    }

    /// Collection
    static class CollectionBean {
        public List<String> list;
        public Set<Integer> set;
        public Queue<String> queue;
        public Deque<String> deque;
    }
    void collection_mapping() {
        String json = "        {\n" +
                "          \"list\": [\"a\", \"b\"],\n" +
                "          \"set\": [1, 2, 2],\n" +
                "          \"queue\": [\"x\", \"y\"],\n" +
                "          \"deque\": [\"m\", \"n\"]\n" +
                "        }";

        CollectionBean bean = Sjf4j.fromJson(json, CollectionBean.class);

        assertEquals(Arrays.asList("a","b"), bean.list);
        assertEquals(new Integer[]{1,2}, bean.set.toArray(new Integer[0]));
        assertEquals("x", bean.queue.poll());
    }

    /// Enum
    enum Status {
        NEW, DONE
    }
    static class EnumBean {
        public Status status;
    }
    void enum_mapping() {
        String json = "{ \"status\": \"NEW\" }";
        EnumBean bean = Sjf4j.fromJson(json, EnumBean.class);
        assertEquals(Status.NEW, bean.status);
    }

    /// UUID/URI/URL
    static class IdBean {
        public UUID id;
        public URI uri;
        public URL url;
    }
    void uuid_uri_url_mapping() throws Exception {
        String json = "{\n" +
                "          \"id\": \"550e8400-e29b-41d4-a716-446655440000\",\n" +
                "          \"uri\": \"https://example.com/a\",\n" +
                "          \"url\": \"https://example.com/b\"\n" +
                "        }";
        IdBean bean = Sjf4j.fromJson(json, IdBean.class);
        assertNotNull(bean.id);
        assertEquals(new URI("https://example.com/a"), bean.uri);
    }

    /// I18n
    static class I18nBean {
        public Locale locale;
        public Currency currency;
        public ZoneId zoneId;
    }
    void locale_currency_zoneid_mapping() {
        String json = "{\n" +
                "          \"locale\": \"en_US\",\n" +
                "          \"currency\": \"USD\",\n" +
                "          \"zoneId\": \"Asia/Shanghai\"\n" +
                "        }";
        I18nBean bean = Sjf4j.fromJson(json, I18nBean.class);
        assertEquals(Locale.US, bean.locale);
        assertEquals(Currency.getInstance("USD"), bean.currency);
        assertEquals(ZoneId.of("Asia/Shanghai"), bean.zoneId);
    }

    /// Nested Node
    static class Address {
        public String city;
    }
    static class Person {
        public String name;
        public Optional<Address> address;
    }
    void nested_optional_object() {
        String json = "{ \"name\": \"Bob\" }";
        Person p = Sjf4j.fromJson(json, Person.class);
        assertTrue(p.address.isPresent());
    }


}
