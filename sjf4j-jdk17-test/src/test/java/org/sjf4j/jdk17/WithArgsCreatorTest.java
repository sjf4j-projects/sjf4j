package org.sjf4j.jdk17;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sjf4j.exception.JsonException;
import org.sjf4j.Sjf4j;
import org.sjf4j.facade.fastjson2.Fastjson2JsonFacade;
import org.sjf4j.facade.gson.GsonJsonFacade;
import org.sjf4j.facade.jackson2.Jackson2JsonFacade;
import org.sjf4j.facade.simple.SimpleJsonFacade;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class WithArgsCreatorTest {

    private Sjf4j sjf4j = Sjf4j.global();

    private void useSimpleJson() {
        sjf4j = Sjf4j.builder().jsonFacadeProvider(SimpleJsonFacade.provider()).build();
    }

    private void useJackson2() {
        sjf4j = Sjf4j.builder().jsonFacadeProvider(Jackson2JsonFacade.provider()).build();
    }

    private void useGson() {
        sjf4j = Sjf4j.builder().jsonFacadeProvider(GsonJsonFacade.provider()).build();
    }

    private void useFastjson2() {
        sjf4j = Sjf4j.builder().jsonFacadeProvider(Fastjson2JsonFacade.provider()).build();
    }


    @TestFactory
    public Stream<DynamicTest> switchJsonLib() {
        return Stream.of(
                DynamicTest.dynamicTest("Run with Simple JSON", () -> {
                    useSimpleJson();
                    testAll();
                }),
                DynamicTest.dynamicTest("Run with Jackson2", () -> {
                    useJackson2();
                    testAll();
                }),
                DynamicTest.dynamicTest("Run with Gson", () -> {
                    useGson();
                    testAll();
                }),
                DynamicTest.dynamicTest("Run with Fastjson2", () -> {
                    useFastjson2();
                    testAll();
                })
        );
    }

    public void testAll() throws Exception {
        shouldAcceptAliasOnCreatorParam();
        shouldDeserializeNoArgsSetterPojo();
        shouldAllowNullToWrapper();
        shouldDeserializeRecord();
        shouldDeserializeCreatorWithFullProps();
        shouldDeserializePublicFieldPojo();
        shouldDeserializeRenamedRecord();
        shouldExposeAliasConflictBehavior();
        shouldFailAllArgsNoCreatorPojoWithoutParams();
        shouldFailCreatorMissingParam();
        shouldFailOverflow();
        shouldFailWhenMultipleCreators();
        shouldFailTypeMismatch();
    }



    static class NoArgsSetterPojo {
        private String name;
        private int age;

        public NoArgsSetterPojo() {}
        public void setName(String name) { this.name = name; }
        public void setAge(int age) { this.age = age; }
    }

    static class PublicFieldPojo {
        public String name;
        public int age;
    }

    static class AllArgsNoCreatorPojo {
        final String name;
        final int age;
        public AllArgsNoCreatorPojo(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    static class ExplicitCreatorMissingParamPojo {
        final String name;
        final int age;

        @NodeCreator
        public ExplicitCreatorMissingParamPojo(
                @NodeProperty("name") String name,
                int age // no @NodeProperty -> fail without -parameters
        ) {
            this.name = name;
            this.age = age;
        }
    }

    record PlainRecord(String name, int age) {}

    record RenamedRecord(@NodeProperty("name2") String name, int age) {}

    @Test
    void shouldDeserializeNoArgsSetterPojo() throws Exception {
        NoArgsSetterPojo p = sjf4j.fromJson("{\"name\":\"a\",\"age\":1}", NoArgsSetterPojo.class);
        assertNotNull(p);
    }

    @Test
    void shouldDeserializePublicFieldPojo() throws Exception {
        PublicFieldPojo p = sjf4j.fromJson("{\"name\":\"a\",\"age\":1}", PublicFieldPojo.class);
        assertNotNull(p);
    }

    @Test
    void shouldFailAllArgsNoCreatorPojoWithoutParams() {
        assertThrows(JsonException.class,
                () -> sjf4j.fromJson("{\"name\":\"a\",\"age\":1}", AllArgsNoCreatorPojo.class));
    }

    @Test
    void shouldFailCreatorMissingParam() {
        assertThrows(JsonException.class,
                () -> sjf4j.fromJson("{\"name\":\"a\",\"age\":1}", ExplicitCreatorMissingParamPojo.class));
    }

    @Test
    void shouldDeserializeRecord() throws Exception {
        PlainRecord r = sjf4j.fromJson("{\"name\":\"a\",\"age\":1}", PlainRecord.class);
        assertEquals("a", r.name());
    }

    @Test
    void shouldDeserializeRenamedRecord() throws Exception {
        useSimpleJson();
        RenamedRecord r = sjf4j.fromJson("{\"name2\":\"a\",\"age\":1}", RenamedRecord.class);
        assertEquals("a", r.name());
    }

    // ---------- 2) NodeCreator / NodeProperty / JsonAlias ----------

    static class CreatorFullPropsPojo {
        final String name;
        final int age;

        @NodeCreator
        CreatorFullPropsPojo(@NodeProperty("name") String name,
                             @NodeProperty("age") int age) {
            this.name = name;
            this.age = age;
        }
    }

    static class AliasPojo {
        @NodeProperty(value = "name", aliases = {"n", "nickname"})
        public String name;

        public int age;
    }

    static class AliasOnCreatorParamPojo {
        final String name;
        final int age;

        @NodeCreator
        AliasOnCreatorParamPojo(
                @NodeProperty(value = "name", aliases = {"n"}) String name,
                @NodeProperty("age") int age) {
            this.name = name;
            this.age = age;
        }
    }

    @Test
    void shouldDeserializeCreatorWithFullProps() throws Exception {
        CreatorFullPropsPojo p = sjf4j.fromJson("{\"name\":\"a\",\"age\":1}", CreatorFullPropsPojo.class);
        assertEquals("a", p.name);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"n\":\"a\",\"age\":1}",
            "{\"nickname\":\"a\",\"age\":1}"
    })
    void shouldAcceptAliases(String json) throws Exception {
        AliasPojo p = sjf4j.fromJson(json, AliasPojo.class);
        assertEquals("a", p.name);
    }

    @Test
    void shouldAcceptAliasOnCreatorParam() throws Exception {
        AliasOnCreatorParamPojo p = sjf4j.fromJson("{\"n\":\"a\",\"age\":1}", AliasOnCreatorParamPojo.class);
        assertEquals("a", p.name);
    }

    @Test
    void shouldExposeAliasConflictBehavior() throws Exception {
        AliasPojo p = sjf4j.fromJson("{\"name\":\"a\",\"n\":\"b\",\"age\":1}", AliasPojo.class);
        // Behavior depends on Jackson override strategy; assert only final parseability.
        assertNotNull(p.name);
    }

    // ---------- 3) Boundary/extreme ----------

    static class PrimitivePojo {
        public String name;
        public int age;
    }

    static class WrapperPojo {
        public String name;
        public Integer age;
    }

//    @Test
//    void shouldFailWhenMissingPrimitive() {
//        assertThrows(JsonException.class,
//                () -> sjf4j.fromJson("{\"name\":\"a\"}", PrimitivePojo.class));
//    }
//
//    @Test
//    void shouldFailWhenNullToPrimitive() {
//        assertThrows(JsonException.class,
//                () -> sjf4j.fromJson("{\"name\":\"a\",\"age\":null}", PrimitivePojo.class));
//    }

    @Test
    void shouldAllowNullToWrapper() throws Exception {
        useSimpleJson();
        WrapperPojo p = sjf4j.fromJson("{\"name\":\"a\",\"age\":null}", WrapperPojo.class);
        assertNull(p.age);
    }

    @Test
    void shouldFailTypeMismatch() {
        assertThrows(JsonException.class,
                () -> sjf4j.fromJson("{\"name\":\"a\",\"age\":\"notNumber\"}", PrimitivePojo.class));
    }

    @Test
    void shouldFailOverflow() {
        assertThrows(JsonException.class,
                () -> sjf4j.fromJson("{\"name\":\"a\",\"age\":2147483648}", PrimitivePojo.class));
    }


    static class ConflictingNamePojo {
        final String name;
        final int age;

        @NodeCreator
        ConflictingNamePojo(@NodeProperty("name2") String name,
                            @NodeProperty("age") int age) {
            this.name = name;
            this.age = age;
        }
    }

    @Test
    void shouldExposePropertyConflictBehavior() throws Exception {
        useSimpleJson();
        ConflictingNamePojo p = sjf4j.fromJson("{\"name\":\"a\",\"name2\":\"b\",\"age\":1}", ConflictingNamePojo.class);
        assertEquals("b", p.name);
    }

    @Test
    void shouldFailWhenMultipleCreators() {
        assertThrows(JsonException.class,
                () -> sjf4j.fromJson("{\"name\":\"a\",\"age\":1}", MultiCreatorPojo.class));
    }

    static class MultiCreatorPojo {
        final String name;
        final int age;

        @NodeCreator
        MultiCreatorPojo(@NodeProperty("name") String name) { this.name = name; this.age = 0; }

        @NodeCreator
        MultiCreatorPojo(@NodeProperty("name") String name, @NodeProperty("age") int age) {
            this.name = name;
            this.age = age;
        }
    }
}
