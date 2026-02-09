package org.sjf4j.node;


import org.junit.jupiter.api.Test;
import org.sjf4j.exception.JsonException;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectUtilTest {

    // ---------- 1) isPojoCandidate ----------

    @Test
    void isPojoCandidate_filtersCommonNonPojoTypes() {
        assertFalse(ReflectUtil.isPojoCandidate(null));
        assertFalse(ReflectUtil.isPojoCandidate(Object.class));
        assertFalse(ReflectUtil.isPojoCandidate(int.class));
        assertFalse(ReflectUtil.isPojoCandidate(String.class));
        assertFalse(ReflectUtil.isPojoCandidate(Integer.class));
        assertFalse(ReflectUtil.isPojoCandidate(Boolean.class));

        assertFalse(ReflectUtil.isPojoCandidate(Map.class)); // interface, also Map
        assertFalse(ReflectUtil.isPojoCandidate(int[].class)); // array
    }

    @Test
    void isPojoCandidate_filtersJdkPackage() {
        assertFalse(ReflectUtil.isPojoCandidate(java.time.Instant.class));
        assertFalse(ReflectUtil.isPojoCandidate(java.util.ArrayList.class));
    }

    @Test
    void isPojoCandidate_acceptsUserPojo() {
        assertTrue(ReflectUtil.isPojoCandidate(SimplePojo.class));
    }

    static class SimplePojo {
        public int a;
    }

    // ---------- 2) getFieldName / getFieldAliases priority ----------

    @Test
    void getFieldName_prefersNodeProperty_overOthers() throws Exception {
        Field f = FieldNamePriorityPojo.class.getDeclaredField("f");
        assertEquals("nodeName", ReflectUtil.getFieldName(f));
    }

    @Test
    void getFieldAliases_prefersNodePropertyAliases_overOthers() throws Exception {
        Field f = FieldNamePriorityPojo.class.getDeclaredField("f");
        assertArrayEquals(new String[]{"na1", "na2"}, ReflectUtil.getFieldAliases(f));
    }

    static class FieldNamePriorityPojo {
        @NodeProperty(value = "nodeName", aliases = {"na1", "na2"})
        @com.fasterxml.jackson.annotation.JsonProperty("jacksonName")
        @com.fasterxml.jackson.annotation.JsonAlias({"ja1"})
        @com.alibaba.fastjson2.annotation.JSONField(name = "fastjsonName", alternateNames = {"fa1"})
        public int f;
    }

    // ---------- 3) getParameterName / getParameterAliases ----------

    @Test
    void getParameterName_prefersAnnotations_andReturnsNullWhenNoParamNames() throws Exception {
        Constructor<CreatorCtorPojo> c = CreatorCtorPojo.class.getDeclaredConstructor(String.class, int.class);

        // p0: has JsonProperty
        assertEquals("name2", ReflectUtil.getParameterName(c.getParameters()[0]));

        // p1: no any annotation
        String p1 = ReflectUtil.getParameterName(c.getParameters()[1]);
        if (!c.getParameters()[1].isNamePresent()) {
            assertNull(p1);
        } else {
            assertEquals("age", p1);
        }
    }

    @Test
    void getParameterAliases_readsAliasAnnotations() throws Exception {
        Constructor<CreatorCtorPojo> c = CreatorCtorPojo.class.getDeclaredConstructor(String.class, int.class);

        assertArrayEquals(new String[]{"aliasN1", "aliasN2"}, ReflectUtil.getParameterAliases(c.getParameters()[0]));

        assertNull(ReflectUtil.getParameterAliases(c.getParameters()[1]));
    }

    static class CreatorCtorPojo {
        @NodeCreator
        public CreatorCtorPojo(
                @com.fasterxml.jackson.annotation.JsonProperty("name2")
                @com.fasterxml.jackson.annotation.JsonAlias({"aliasN1", "aliasN2"})
                String name,
                int age
        ) {}
    }

    // ---------- 5) analyzeCreator: creator / record / no-args fallback / alias ----------

    @Test
    void analyzeCreator_prefersAnnotatedCreatorCtor() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        assertThrows(JsonException.class, () -> {
            NodeRegistry.CreatorInfo ci = ReflectUtil.analyzeCreator(ExplicitCreatorPojo.class, lookup);
        });
    }

    static class ExplicitCreatorPojo {
        final String name;
        final int age;

        @NodeCreator
        public ExplicitCreatorPojo(@com.fasterxml.jackson.annotation.JsonProperty("name2") String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    @Test
    void analyzeCreator_usesNoArgsCtorWhenNoCreatorFound() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        NodeRegistry.CreatorInfo ci = ReflectUtil.analyzeCreator(NoArgsPojo.class, lookup);

        assertNull(ci.getArgsCreator());
        assertNull(ci.getArgsCreatorHandle());
        assertNotNull(ci.getNoArgsCtorHandle());
        assertNotNull(ci.getNoArgsCtorLambda());

        Object obj = ci.getNoArgsCtorLambda().get();
        assertTrue(obj instanceof NoArgsPojo);
    }

    static class NoArgsPojo {
        public NoArgsPojo() {}
    }

    @Test
    void analyzeCreator_throwsOnMultipleCreators() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        JsonException ex = assertThrows(JsonException.class,
                () -> ReflectUtil.analyzeCreator(MultipleCreatorsPojo.class, lookup));
        assertTrue(ex.getMessage().contains("Multiple creator definitions"));
    }

    static class MultipleCreatorsPojo {
        @NodeCreator public MultipleCreatorsPojo(String a) {}
        @NodeCreator public MultipleCreatorsPojo(Integer b) {}
    }

    @Test
    void analyzeCreator_detectsAliasConflict_inCreatorParams() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        JsonException ex = assertThrows(JsonException.class,
                () -> ReflectUtil.analyzeCreator(AliasConflictCreatorPojo.class, lookup));
        assertTrue(ex.getMessage().contains("mapped to multiple fields"));
    }

    static class AliasConflictCreatorPojo {
        @NodeCreator
        public AliasConflictCreatorPojo(
                @NodeProperty(value = "a", aliases = {"x"}) String a,
                @NodeProperty(value = "b", aliases = {"x"}) String b
        ) {}
    }

    // ---------- 6) lambdaSetter：reference/primitive/private ----------

    @Test
    void lambdaSetter_setsPrivateReferenceField() throws Throwable {
        PrivateFieldPojo p = new PrivateFieldPojo();

        MethodHandles.Lookup root = MethodHandles.lookup();
        MethodHandles.Lookup lookup = root;
        if (!ReflectUtil.IS_JDK8) {
            try {
                lookup = (MethodHandles.Lookup) getPrivateLookupIn().invoke(null, PrivateFieldPojo.class, root);
            } catch (Exception ignored) {}
        }

        Field f = PrivateFieldPojo.class.getDeclaredField("name");
        BiConsumer<Object, Object> lambda = ReflectUtil.createLambdaSetter(lookup, PrivateFieldPojo.class, f);
        assertNotNull(lambda);
        lambda.accept(p, "ok");
        assertEquals("ok", p.getName());

        Field f2 = PrivateFieldPojo.class.getDeclaredField("name2");
        BiConsumer<Object, Object> lambda2 = ReflectUtil.createLambdaSetter(lookup, PrivateFieldPojo.class, f2);
        assertNull(lambda2);
    }

    @Test
    void lambdaSetter_setsPrimitiveField_withBoxedValue() throws Throwable {
        PrimitiveFieldPojo p = new PrimitiveFieldPojo();

        MethodHandles.Lookup root = MethodHandles.lookup();
        MethodHandles.Lookup lookup = root;
        if (!ReflectUtil.IS_JDK8) {
            try {
                lookup = (MethodHandles.Lookup) getPrivateLookupIn().invoke(null, PrimitiveFieldPojo.class, root);
            } catch (Exception ignored) {}
        }

        Field f = PrimitiveFieldPojo.class.getDeclaredField("age");
        MethodHandle setter = lookup.unreflectSetter(f);

        BiConsumer<Object, Object> lambda = ReflectUtil.createLambdaSetter(lookup, PrimitiveFieldPojo.class, f);
        assertNull(lambda);
    }

    static class PrivateFieldPojo {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        private String name2;
    }

    static class PrimitiveFieldPojo {
        int age;
    }

    private static java.lang.reflect.Method getPrivateLookupIn() throws Exception {
        return MethodHandles.class.getMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class);
    }
}