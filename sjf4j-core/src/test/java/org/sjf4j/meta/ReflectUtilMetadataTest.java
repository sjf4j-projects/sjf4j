package org.sjf4j.meta;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.NamingStrategy;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.NodeObject;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.OneOfMapping;
import org.sjf4j.annotation.node.PropertyStrategy;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueCopy;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.exception.BindingException;
import org.sjf4j.meta.CreatorInfo;
import org.sjf4j.meta.OneOfInfo;
import org.sjf4j.meta.ReflectUtil;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.util.Invokers;
import org.sjf4j.util.Strings;
import org.sjf4j.value.ValueInfo;
import org.sjf4j.value.ValueRegistry;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectUtilMetadataTest {

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

    @OneOf(mappings = {
            @OneOfMapping(type = DiscA.class, when = {"a"}),
            @OneOfMapping(type = DiscB.class, when = {"b"})
    }, discriminatorKey = "type")
    interface DiscOneOf {}

    static class DiscA extends JsonObject implements DiscOneOf {}
    static class DiscB extends JsonObject implements DiscOneOf {}

    @OneOf(mappings = {
            @OneOfMapping(type = DuplicateA.class),
            @OneOfMapping(type = DuplicateB.class)
    })
    interface DuplicateRawOneOf {}

    static class DuplicateA extends JsonObject implements DuplicateRawOneOf {}
    static class DuplicateB extends JsonObject implements DuplicateRawOneOf {}

    @OneOf(discriminatorKey = "type", discriminatorPath = "$.type",
            mappings = @OneOfMapping(type = BothDiscriminatorSubtype.class, when = "a"))
    interface BothDiscriminatorsOneOf {}

    static class BothDiscriminatorSubtype implements BothDiscriminatorsOneOf {}

    @OneOf(mappings = {@OneOfMapping(type = NotAssignable.class)})
    interface WrongOneOf {}

    static class NotAssignable {}

    @OneOf(mappings = {@OneOfMapping(type = MissingWhenSubtype.class)}, discriminatorKey = "type")
    interface MissingWhenOneOf {}

    static class MissingWhenSubtype extends JsonObject implements MissingWhenOneOf {}

    @OneOf(mappings = @OneOfMapping(type = SelfDiscriminatorOneOf.class, when = "self"), discriminatorKey = "kind")
    static class SelfDiscriminatorOneOf {}

    @OneOf(mappings = @OneOfMapping(type = SelfShapeOneOf.class))
    static class SelfShapeOneOf {}

    static class SelfFieldChoice {}

    static class SelfFieldOneOf {
        @OneOf(mappings = @OneOfMapping(type = SelfFieldChoice.class, when = "self"), discriminatorKey = "kind")
        public SelfFieldChoice value;
    }

    @NodeObject(naming = NamingStrategy.IDENTITY)
    static class IdentityNamingPojo {}

    @NodeObject(propertyStrategy = PropertyStrategy.FIELD_ONLY)
    static class FieldBindingPojo {}

    static class TransientNodePropertyPojo {
        @NodeProperty("name")
        transient String name;
    }

    enum SampleEnum { A }

    interface SampleInterface {}

    static class FastjsonOnlyPojo {
        @com.alibaba.fastjson2.annotation.JSONField(name = "fast_name", alternateNames = {"f1", "f2"})
        public String name;
    }

    static class GetterPojo {
        private String name = "han";
        public String getName() {
                return name;
            }
    }

    static class BooleanPojo {
        private boolean active = true;
        public boolean isActive() {
                return active;
            }
    }

    static class BooleanGetterPojo {
        private Boolean active = Boolean.TRUE;
        public Boolean getActive() {
                return active;
            }
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
    void analyzesNodeValuesAndRejectsInvalidDeclarations() {
        ValueInfo codecInfo = ValueRegistry.analyzeByAnnotation(ValidValue.class);
        assertNotNull(codecInfo);
        assertEquals(String.class, codecInfo.rawClazz);
        assertEquals("x", codecInfo.valueToRaw(new ValidValue("x")));
        assertEquals("y", ((ValidValue) codecInfo.rawToValue("y")).value);
        assertEquals("z", ((ValidValue) codecInfo.valueCopy(new ValidValue("z"))).value);

        assertNull(ValueRegistry.analyzeByAnnotation(String.class));
        assertThrows(BindingException.class, () -> ValueRegistry.analyzeByAnnotation(MissingEncode.class));
        assertThrows(BindingException.class, () -> ValueRegistry.analyzeByAnnotation(MissingDecode.class));
        assertThrows(BindingException.class, () -> ValueRegistry.analyzeByAnnotation(StaticEncode.class));
        assertThrows(BindingException.class, () -> ValueRegistry.analyzeByAnnotation(NonStaticDecode.class));
        assertThrows(BindingException.class, () -> ValueRegistry.analyzeByAnnotation(WrongDecodeParam.class));
        assertThrows(BindingException.class, () -> ValueRegistry.analyzeByAnnotation(WrongCopyReturn.class));
        assertThrows(BindingException.class, () -> ValueRegistry.analyzeByAnnotation(DuplicateEncode.class));
    }

    @Test
    void analyzesOneOfAndNamingMetadata() {
        OneOfInfo disc = ReflectUtil.analyzeOneOf(DiscOneOf.class, DiscOneOf.class.getAnnotation(OneOf.class));
        assertTrue(disc.hasDiscriminator);
        assertEquals("type", disc.discriminatorKey);
        assertEquals(DiscA.class, disc.matchByWhen("a"));

        BindingException both = assertThrows(BindingException.class, () -> ReflectUtil.analyzeOneOf(
                BothDiscriminatorsOneOf.class, BothDiscriminatorsOneOf.class.getAnnotation(OneOf.class)));
        assertTrue(both.getMessage().contains("mutually exclusive"));

        assertThrows(BindingException.class, () -> ReflectUtil.analyzeOneOf(
                DuplicateRawOneOf.class,
                DuplicateRawOneOf.class.getAnnotation(OneOf.class)
        ));
        assertThrows(BindingException.class, () -> ReflectUtil.analyzeOneOf(
                WrongOneOf.class,
                WrongOneOf.class.getAnnotation(OneOf.class)
        ));
        assertThrows(BindingException.class, () -> ReflectUtil.analyzeOneOf(
                MissingWhenOneOf.class,
                MissingWhenOneOf.class.getAnnotation(OneOf.class)
        ));

        for (Class<?> clazz : new Class<?>[]{SelfDiscriminatorOneOf.class, SelfShapeOneOf.class}) {
            BindingException error = assertThrows(BindingException.class,
                    () -> TypeRegistry.registerTypeInfo(clazz));
            assertTrue(error.getMessage().contains("must differ from declared type"));
        }
        BindingException fieldError = assertThrows(BindingException.class,
                () -> TypeRegistry.registerTypeInfo(SelfFieldOneOf.class));
        assertTrue(fieldError.getMessage().contains("must differ from declared type"));

        assertEquals(NamingStrategy.IDENTITY, ReflectUtil.getNamingStrategy(null));
        assertEquals(NamingStrategy.IDENTITY, ReflectUtil.getNamingStrategy(IdentityNamingPojo.class));
        assertThrows(BindingException.class,
                () -> ReflectUtil.analyzePojo(TransientNodePropertyPojo.class, true));

        assertFalse(ReflectUtil.isPojoCandidate(JsonArray.class));
        assertFalse(ReflectUtil.isPojoCandidate(JsonObject.class));
        assertFalse(ReflectUtil.isPojoCandidate(SampleEnum.class));
        assertFalse(ReflectUtil.isPojoCandidate(SampleInterface.class));
        assertNull(ReflectUtil.analyzePojo(WrongStaticCreatorPojo.class, false));

        try {
            Field field = FastjsonOnlyPojo.class.getDeclaredField("name");
            assertEquals("fast_name", ReflectUtil.getExplicitName(field));
            assertEquals(2, ReflectUtil.getAliases(field).length);
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void createsLambdaHelpersAndAccessorFallbacks() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        MethodHandle nameGetter = lookup.unreflect(GetterPojo.class.getDeclaredMethod("getName"));
        assertEquals("han", Invokers.createGetterLambda(lookup, nameGetter, Function.class, Object.class).apply(new GetterPojo()));

        MethodHandle activeGetter = lookup.unreflect(BooleanPojo.class.getDeclaredMethod("isActive"));
        assertEquals(true, Invokers.createGetterLambda(lookup, activeGetter, Function.class, Object.class).apply(new BooleanPojo()));

        MethodHandle boolObjGetter = lookup.unreflect(BooleanGetterPojo.class.getDeclaredMethod("getActive"));
        assertEquals(Boolean.TRUE, Invokers.createGetterLambda(lookup, boolObjGetter, Function.class, Object.class).apply(new BooleanGetterPojo()));

        assertNull(Invokers.createGetterLambda(lookup, null, Function.class, Object.class));

        assertEquals("Name", Strings.capitalize("name"));
        assertEquals("X", Strings.capitalize("x"));

        MethodHandle ctor = lookup.unreflectConstructor(NoArgsCtorPojo.class.getDeclaredConstructor());
        Supplier<NoArgsCtorPojo> noArgsCtor = Invokers.createConstructorLambda(lookup, NoArgsCtorPojo.class, ctor);
        assertNotNull(noArgsCtor);
        assertNotNull(noArgsCtor.get());
        assertNull(Invokers.createConstructorLambda(lookup, LambdaCtorPojo.class, null));

        CreatorInfo creatorInfo = ReflectUtil.analyzeCreator(LambdaCtorPojo.class, lookup);
        CreatorInfo.Function1 creator = Invokers.createArgsCreatorLambda(lookup, creatorInfo.argsCreatorHandle, CreatorInfo.Function1.class, 1);
        assertNotNull(creator);
        LambdaCtorPojo created = (LambdaCtorPojo) creator.apply("han");
        assertEquals("han", created.name);
        assertNull(Invokers.createArgsCreatorLambda(lookup, creatorInfo.argsCreatorHandle, CreatorInfo.Function1.class, 0));
        assertNull(Invokers.createArgsCreatorLambda(lookup, creatorInfo.argsCreatorHandle, null, 1));

        assertFalse(ReflectUtil.isRecord(GetterPojo.class));
        assertNull(ReflectUtil.analyzeRecord(GetterPojo.class, lookup));
    }

    @Test
    void analyzesStaticCreatorMethods() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        CreatorInfo staticCreator = ReflectUtil.analyzeCreator(StaticCreatorPojo.class, lookup);
        StaticCreatorPojo pojo = (StaticCreatorPojo) staticCreator.newPojoWithArgs(new Object[]{"han"});
        assertEquals("han", pojo.name);

        assertThrows(BindingException.class, () -> ReflectUtil.analyzeCreator(WrongStaticCreatorPojo.class, lookup));
        assertThrows(BindingException.class, () -> ReflectUtil.analyzeCreator(DuplicateCreatorPojo.class, lookup));
    }
}
