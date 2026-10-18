package org.sjf4j.testbench.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sjf4j.CompiledInstances;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.annotation.binding.WriteTo;
import org.sjf4j.annotation.mapping.CompiledMapper;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.testbench.model.CreatorUserRecord;
import org.sjf4j.testbench.model.CreatorUserRecord.AddressRecord;
import org.sjf4j.testbench.model.CreatorUserRecord.FriendRecord;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Contracts shared by runtime/compiled binders and compiled mapping of immutable records. */
public class CreatorSemanticsTest {

    private static final ObjectMapper JACKSON = new ObjectMapper();

    private static CreatorUserRecord fixture() {
        return new CreatorUserRecord(
                839201L, 1700000000123L, 1701234567890L, 9876543210L,
                421, 34, true, true, false, false,
                98.75, 37.7749, -122.4194,
                "alice.builder", null, "Alice \"the Builder\"", "$2a$12$abcdef",
                "Builds fast JSON", URI.create("https://example.test/a?b=1"),
                "Platform Engineering",
                new AddressRecord("1 Main St", "San Francisco", "CA", "94105", "US"),
                Arrays.asList("tag-0-value", null),
                Arrays.asList(new FriendRecord(1000L, "Bill", 1600000000000L, true),
                        null, new FriendRecord(1001L, "Cindy", 1600000000001L, false)));
    }

    static Stream<Arguments> backends() {
        return Stream.of(
                Arguments.of("simple", CompiledInstances.global(SimpleCreatorBinder.class), new SimpleJsonBinder()),
                Arguments.of("gson", CompiledInstances.global(GsonCreatorBinder.class), new GsonBinder()),
                Arguments.of("jackson2", CompiledInstances.global(Jackson2CreatorBinder.class), new Jackson2Binder()),
                Arguments.of("jackson3", CompiledInstances.global(Jackson3CreatorBinder.class), new Jackson3Binder()),
                Arguments.of("fastjson2", CompiledInstances.global(Fastjson2CreatorBinder.class), new Fastjson2Binder()),
                Arguments.of("jsonp", CompiledInstances.global(JsonpCreatorBinder.class), new JsonpBinder())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void readsImmutableGraphAndPreservesNulls(String name, CreatorBinder compiled,
                                               org.sjf4j.binding.Binder<?, ?> runtime) throws Exception {
        CreatorUserRecord expected = fixture();
        String json = JACKSON.writeValueAsString(expected);
        byte[] utf8 = json.getBytes(StandardCharsets.UTF_8);
        assertEquals(expected, compiled.readRecord(json), name + " compiled string");
        assertEquals(expected, compiled.readRecord(utf8), name + " compiled bytes");
        assertEquals(expected, runtime.readNode(json, CreatorUserRecord.class), name + " runtime string");
        assertEquals(expected, runtime.readNode(utf8, CreatorUserRecord.class), name + " runtime bytes");
        assertEquals(expected, compiled.readRecord(compiled.writeRecord(expected)), name + " roundtrip");

        String reordered = "{\"friends\":" + JACKSON.writeValueAsString(expected.friends())
                + ",\"address\":" + JACKSON.writeValueAsString(expected.address())
                + ",\"username\":\"alice.builder\",\"id\":839201}";
        CreatorUserRecord partial = compiled.readRecord(reordered);
        assertEquals(expected.friends(), partial.friends(), name);
        assertEquals(expected.address(), partial.address(), name);
        assertEquals(0, partial.age(), name + " missing primitive");
        assertNull(partial.website(), name + " missing nullable");
        assertNull(compiled.readRecord("null"), name);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void creatorArgumentsAndMutableFieldsHaveSeparatePresenceSemantics(
            String name, CreatorBinder compiled, org.sjf4j.binding.Binder<?, ?> runtime) throws Exception {
        String before = "{\"age\":25,\"legacy_name\":\"Ada\"}";
        String after = "{\"legacy_name\":\"Ada\",\"age\":25}";
        for (String input : List.of(before, after)) {
            CreatorBean actual = compiled.readBean(input);
            CreatorBean expected = (CreatorBean) runtime.readNode(input, CreatorBean.class);
            assertEquals(expected.getName(), actual.getName(), name);
            assertEquals(expected.getAge(), actual.getAge(), name);
            assertEquals(1, actual.setterCalls, name + " present property");
        }
        CreatorBean absent = compiled.readBean("{\"name\":\"Bob\"}");
        assertEquals(17, absent.getAge(), name + " keep constructor default");
        assertEquals(0, absent.setterCalls, name + " absent property");
        assertNull(compiled.readBean("null"), name);
    }

    @Test
    void compiledMapperBuildsSameSharedRecordAndProjectsIt() {
        CreatorUserRecord original = fixture();
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", original.id());
        source.put("createdAt", original.createdAt());
        source.put("updatedAt", original.updatedAt());
        source.put("reputation", original.reputation());
        source.put("loginCount", original.loginCount());
        source.put("age", original.age());
        source.put("active", original.active());
        source.put("verified", original.verified());
        source.put("admin", original.admin());
        source.put("suspended", original.suspended());
        source.put("score", original.score());
        source.put("latitude", original.latitude());
        source.put("longitude", original.longitude());
        source.put("username", original.username());
        source.put("email", original.email());
        source.put("displayName", original.displayName());
        source.put("passwordHash", original.passwordHash());
        source.put("bio", original.bio());
        source.put("website", original.website());
        source.put("department", original.department());
        source.put("address", original.address());
        source.put("tags", original.tags());
        source.put("friends", original.friends());

        CreatorMapper mapper = CompiledInstances.global(CreatorMapper.class);
        assertEquals(original, mapper.toRecord(source));
        assertNull(mapper.toRecord(null));
        JsonObject projected = mapper.project(original);
        assertEquals(original.id(), ((Number) projected.get("id")).longValue());
        assertEquals(original.username(), projected.get("username"));
        assertEquals(original.address(), projected.get("address"));
    }

    public static final class CreatorBean {
        private final String name;
        private int age;
        public int setterCalls;

        @NodeCreator
        public CreatorBean(@NodeProperty(value = "name", aliases = "legacy_name") String name) {
            this.name = name;
            this.age = 17;
        }

        public String getName() { return name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; setterCalls++; }
    }

    public interface CreatorBinder {
        @ReadFrom CreatorUserRecord readRecord(String json) throws IOException;
        @ReadFrom CreatorUserRecord readRecord(byte[] json) throws IOException;
        @WriteTo String writeRecord(CreatorUserRecord value) throws IOException;
        @ReadFrom CreatorBean readBean(String json) throws IOException;
    }

    @CompiledBinder(backend = Backend.SIMPLE)
    public interface SimpleCreatorBinder extends CreatorBinder {}
    @CompiledBinder(backend = Backend.GSON)
    public interface GsonCreatorBinder extends CreatorBinder {}
    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2CreatorBinder extends CreatorBinder {}
    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3CreatorBinder extends CreatorBinder {}
    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface Fastjson2CreatorBinder extends CreatorBinder {}
    @CompiledBinder(backend = Backend.JSONP)
    public interface JsonpCreatorBinder extends CreatorBinder {}

    @CompiledMapper
    public interface CreatorMapper {
        CreatorUserRecord toRecord(Map<String, Object> values);
        JsonObject project(CreatorUserRecord value);
    }
}
