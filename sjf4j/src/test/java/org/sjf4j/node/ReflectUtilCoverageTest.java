package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeNaming;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueCopy;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.exception.JsonException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectUtilCoverageTest {

    @NodeValue
    static class ValidValue {
        final String value;

        ValidValue(String value) {
            this.value = value;
        }

        @ValueToRaw
        String encode() {
            return value;
        }

        @RawToValue
        static ValidValue decode(String raw) {
            return new ValidValue(raw);
        }

        @ValueCopy
        ValidValue copy() {
            return new ValidValue(value);
        }
    }

    @NodeValue
    static class MissingEncode {
        @RawToValue
        static MissingEncode decode(String raw) {
            return new MissingEncode();
        }
    }

    @NodeValue
    static class MissingDecode {
        @ValueToRaw
        String encode() {
            return "x";
        }
    }

    @NodeValue
    static class StaticEncode {
        @ValueToRaw
        static String encode() {
            return "x";
        }

        @RawToValue
        static StaticEncode decode(String raw) {
            return new StaticEncode();
        }
    }

    @NodeValue
    static class NonStaticDecode {
        @ValueToRaw
        String encode() {
            return "x";
        }

        @RawToValue
        NonStaticDecode decode(String raw) {
            return this;
        }
    }

    @NodeValue
    static class WrongDecodeParam {
        @ValueToRaw
        String encode() {
            return "x";
        }

        @RawToValue
        static WrongDecodeParam decode(Integer raw) {
            return new WrongDecodeParam();
        }
    }

    @NodeValue
    static class WrongCopyReturn {
        @ValueToRaw
        String encode() {
            return "x";
        }

        @RawToValue
        static WrongCopyReturn decode(String raw) {
            return new WrongCopyReturn();
        }

        @ValueCopy
        String copy() {
            return "x";
        }
    }

    @NodeValue
    static class DuplicateEncode {
        @ValueToRaw
        String encode1() {
            return "x";
        }

        @ValueToRaw
        String encode2() {
            return "y";
        }

        @RawToValue
        static DuplicateEncode decode(String raw) {
            return new DuplicateEncode();
        }
    }

    @AnyOf(value = {
            @AnyOf.Mapping(value = DiscA.class, when = {"a"}),
            @AnyOf.Mapping(value = DiscB.class, when = {"b"})
    }, key = "type")
    interface DiscAnyOf {}

    static class DiscA extends JsonObject implements DiscAnyOf {}
    static class DiscB extends JsonObject implements DiscAnyOf {}

    @AnyOf(value = {
            @AnyOf.Mapping(value = DuplicateA.class),
            @AnyOf.Mapping(value = DuplicateB.class)
    })
    interface DuplicateRawAnyOf {}

    static class DuplicateA extends JsonObject implements DuplicateRawAnyOf {}
    static class DuplicateB extends JsonObject implements DuplicateRawAnyOf {}

    @AnyOf(value = {@AnyOf.Mapping(value = NotAssignable.class)})
    interface WrongAnyOf {}

    static class NotAssignable {}

    @AnyOf(value = {@AnyOf.Mapping(value = MissingWhenSubtype.class)}, key = "type")
    interface MissingWhenAnyOf {}

    static class MissingWhenSubtype extends JsonObject implements MissingWhenAnyOf {}

    @NodeNaming(NamingStrategy.IDENTITY)
    static class InvalidNamingPojo {}

    enum SampleEnum { A }

    interface SampleInterface {}

    static class FastjsonOnlyPojo {
        @com.alibaba.fastjson2.annotation.JSONField(name = "fast_name", alternateNames = {"f1", "f2"})
        public String name;
    }

    static class GetterPojo {
        private String name = "han";
        public String getName() { return name; }
    }

    static class BooleanPojo {
        private boolean active = true;
        public boolean isActive() { return active; }
    }

    static class BooleanGetterPojo {
        private Boolean active = Boolean.TRUE;
        public Boolean getActive() { return active; }
    }

    static class NoGetterPojo {
        String hidden;
    }

    static class LambdaCtorPojo {
        final String name;
        @NodeCreator
        LambdaCtorPojo(@NodeProperty("name") String name) {
            this.name = name;
        }
    }

    static class StaticCreatorPojo {
        final String name;

        StaticCreatorPojo(String name) {
            this.name = name;
        }

        @NodeCreator
        static StaticCreatorPojo of(@NodeProperty("name") String name) {
            return new StaticCreatorPojo(name);
        }
    }

    static class WrongStaticCreatorPojo {
        @NodeCreator
        static String of(@NodeProperty("name") String name) {
            return name;
        }
    }

    static class DuplicateCreatorPojo {
        @NodeCreator
        DuplicateCreatorPojo(@NodeProperty("name") String name) {}

        @NodeCreator
        static DuplicateCreatorPojo of(@NodeProperty("name") String name) {
            return new DuplicateCreatorPojo(name);
        }
    }

    static class NoArgsCtorPojo {
        public NoArgsCtorPojo() {}
    }

    @Test
    void testAnalyzeNodeValueSuccessAndValidationFailures() {
        NodeRegistry.ValueCodecInfo codecInfo = ReflectUtil.analyzeNodeValue(ValidValue.class);
        assertNotNull(codecInfo);
        assertEquals(String.class, codecInfo.rawClazz);
        assertEquals("x", codecInfo.valueToRaw(new ValidValue("x")));
        assertEquals("y", ((ValidValue) codecInfo.rawToValue("y")).value);
        assertEquals("z", ((ValidValue) codecInfo.valueCopy(new ValidValue("z"))).value);

        assertNull(ReflectUtil.analyzeNodeValue(String.class));
        assertThrows(JsonException.class, () -> ReflectUtil.analyzeNodeValue(MissingEncode.class));
        assertThrows(JsonException.class, () -> ReflectUtil.analyzeNodeValue(MissingDecode.class));
        assertThrows(JsonException.class, () -> ReflectUtil.analyzeNodeValue(StaticEncode.class));
        assertThrows(JsonException.class, () -> ReflectUtil.analyzeNodeValue(NonStaticDecode.class));
        assertThrows(JsonException.class, () -> ReflectUtil.analyzeNodeValue(WrongDecodeParam.class));
        assertThrows(JsonException.class, () -> ReflectUtil.analyzeNodeValue(WrongCopyReturn.class));
        assertThrows(JsonException.class, () -> ReflectUtil.analyzeNodeValue(DuplicateEncode.class));
    }

    @Test
    void testAnalyzeAnyOfAndNamingValidation() {
        NodeRegistry.AnyOfInfo disc = ReflectUtil.analyzeAnyOf(DiscAnyOf.class, DiscAnyOf.class.getAnnotation(AnyOf.class));
        assertTrue(disc.hasDiscriminator);
        assertEquals(DiscA.class, disc.resolveByWhen("a"));

        assertThrows(JsonException.class, () -> ReflectUtil.analyzeAnyOf(
                DuplicateRawAnyOf.class,
                DuplicateRawAnyOf.class.getAnnotation(AnyOf.class)
        ));
        assertThrows(JsonException.class, () -> ReflectUtil.analyzeAnyOf(
                WrongAnyOf.class,
                WrongAnyOf.class.getAnnotation(AnyOf.class)
        ));
        assertThrows(JsonException.class, () -> ReflectUtil.analyzeAnyOf(
                MissingWhenAnyOf.class,
                MissingWhenAnyOf.class.getAnnotation(AnyOf.class)
        ));

        assertNull(ReflectUtil.getDeclaredNamingStrategy(null));
        assertThrows(JsonException.class, () -> ReflectUtil.getDeclaredNamingStrategy(InvalidNamingPojo.class));

        assertFalse(ReflectUtil.isPojoCandidate(org.sjf4j.JsonArray.class));
        assertFalse(ReflectUtil.isPojoCandidate(JsonObject.class));
        assertFalse(ReflectUtil.isPojoCandidate(SampleEnum.class));
        assertFalse(ReflectUtil.isPojoCandidate(SampleInterface.class));

        try {
            Field field = FastjsonOnlyPojo.class.getDeclaredField("name");
            assertEquals("fast_name", ReflectUtil.getExplicitName(field));
            assertEquals("fast_name", ReflectUtil.getFieldName(field, FastjsonOnlyPojo.class));
            assertEquals(2, ReflectUtil.getAliases(field).length);
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void testLambdaHelpersAndAccessorFallbacks() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        Field nameField = GetterPojo.class.getDeclaredField("name");
        assertEquals("han", ReflectUtil.createLambdaGetter(lookup, GetterPojo.class, nameField).apply(new GetterPojo()));

        Field activeField = BooleanPojo.class.getDeclaredField("active");
        assertEquals(true, ReflectUtil.createLambdaGetter(lookup, BooleanPojo.class, activeField).apply(new BooleanPojo()));

        Field boolObjField = BooleanGetterPojo.class.getDeclaredField("active");
        assertEquals(Boolean.TRUE, ReflectUtil.createLambdaGetter(lookup, BooleanGetterPojo.class, boolObjField).apply(new BooleanGetterPojo()));

        Field hiddenField = NoGetterPojo.class.getDeclaredField("hidden");
        assertNull(ReflectUtil.createLambdaGetter(lookup, NoGetterPojo.class, hiddenField));

        assertEquals("Name", ReflectUtil.capitalize("name"));
        assertEquals("X", ReflectUtil.capitalize("x"));

        MethodHandle ctor = lookup.unreflectConstructor(NoArgsCtorPojo.class.getDeclaredConstructor());
        assertNotNull(ReflectUtil.createLambdaConstructor(lookup, NoArgsCtorPojo.class, ctor));
        assertNull(ReflectUtil.createLambdaConstructor(lookup, LambdaCtorPojo.class, null));

        NodeRegistry.CreatorInfo creatorInfo = ReflectUtil.analyzeCreator(LambdaCtorPojo.class, lookup);
        assertNotNull(ReflectUtil.createLambdaArgsCreator(lookup, creatorInfo.argsCreatorHandle, NodeRegistry.Func1.class, 1));
        assertNull(ReflectUtil.createLambdaArgsCreator(lookup, creatorInfo.argsCreatorHandle, NodeRegistry.Func1.class, 0));
        assertNull(ReflectUtil.createLambdaArgsCreator(lookup, creatorInfo.argsCreatorHandle, null, 1));

        assertFalse(ReflectUtil.isRecord(GetterPojo.class));
        assertNull(ReflectUtil.analyzeRecord(GetterPojo.class, lookup));
    }

    @Test
    void testAnalyzeCreatorStaticMethodBranches() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        NodeRegistry.CreatorInfo staticCreator = ReflectUtil.analyzeCreator(StaticCreatorPojo.class, lookup);
        StaticCreatorPojo pojo = (StaticCreatorPojo) staticCreator.newPojoWithArgs(new Object[]{"han"});
        assertEquals("han", pojo.name);

        assertThrows(JsonException.class, () -> ReflectUtil.analyzeCreator(WrongStaticCreatorPojo.class, lookup));
        assertThrows(JsonException.class, () -> ReflectUtil.analyzeCreator(DuplicateCreatorPojo.class, lookup));
    }
}
