package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.Sjf4j;
import org.sjf4j.annotation.node.AnyOf;
import org.sjf4j.annotation.node.NodeBinding;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueCopy;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.exception.JsonException;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeRegistryCoverageTest {

    @NodeValue
    static class MiniValue {
        final String value;

        MiniValue(String value) {
            this.value = value;
        }

        @ValueToRaw
        String valueToRaw() {
            return value;
        }

        @RawToValue
        static MiniValue rawToValue(String raw) {
            return new MiniValue(raw);
        }

        @ValueCopy
        MiniValue copy() {
            return new MiniValue(value);
        }
    }

    static class InvalidRawCodec implements ValueCodec<String, Instant> {
        @Override public Instant valueToRaw(String value) { return Instant.now(); }
        @Override public String rawToValue(Instant raw) { return raw.toString(); }
        @Override public Class<String> valueClass() { return String.class; }
        @Override public Class<Instant> rawClass() { return Instant.class; }
    }

    @NodeValue
    static class AnotherMiniValue {
        @ValueToRaw String valueToRaw() { return "x"; }
        @RawToValue static AnotherMiniValue rawToValue(String raw) { return new AnotherMiniValue(); }
    }

    static class ThrowingCodec implements ValueCodec<String, String> {
        @Override public String valueToRaw(String value) { throw new IllegalStateException("boom"); }
        @Override public String rawToValue(String raw) { throw new IllegalStateException("boom"); }
        @Override public Class<String> valueClass() { return String.class; }
        @Override public Class<String> rawClass() { return String.class; }
        @Override public String valueCopy(String value) { throw new IllegalStateException("boom"); }
    }

    static class OneArg {
        final String a;
        @NodeCreator OneArg(@NodeProperty("a") String a) { this.a = a; }
    }

    static class TwoArg {
        final String a;
        final String b;
        @NodeCreator TwoArg(@NodeProperty("a") String a, @NodeProperty("b") String b) { this.a = a; this.b = b; }
    }

    static class ThreeArg {
        final String a; final String b; final String c;
        @NodeCreator ThreeArg(@NodeProperty("a") String a, @NodeProperty("b") String b, @NodeProperty("c") String c) {
            this.a = a; this.b = b; this.c = c;
        }
    }

    static class FourArg {
        final String a; final String b; final String c; final String d;
        @NodeCreator FourArg(@NodeProperty("a") String a, @NodeProperty("b") String b,
                             @NodeProperty("c") String c, @NodeProperty("d") String d) {
            this.a = a; this.b = b; this.c = c; this.d = d;
        }
    }

    static class FiveArg {
        final String a; final String b; final String c; final String d; final String e;
        @NodeCreator FiveArg(@NodeProperty("a") String a, @NodeProperty("b") String b,
                             @NodeProperty("c") String c, @NodeProperty("d") String d,
                             @NodeProperty("e") String e) {
            this.a = a; this.b = b; this.c = c; this.d = d; this.e = e;
        }
    }

    static class PrimitiveArgs {
        final boolean boolValue;
        final byte byteValue;
        final short shortValue;
        final int intValue;
        final long longValue;
        final float floatValue;
        final double doubleValue;
        final char charValue;

        @NodeCreator
        PrimitiveArgs(@NodeProperty("boolValue") boolean boolValue, @NodeProperty("byteValue") byte byteValue,
                      @NodeProperty("shortValue") short shortValue, @NodeProperty("intValue") int intValue,
                      @NodeProperty("longValue") long longValue, @NodeProperty("floatValue") float floatValue,
                      @NodeProperty("doubleValue") double doubleValue, @NodeProperty("charValue") char charValue) {
            this.boolValue = boolValue;
            this.byteValue = byteValue;
            this.shortValue = shortValue;
            this.intValue = intValue;
            this.longValue = longValue;
            this.floatValue = floatValue;
            this.doubleValue = doubleValue;
            this.charValue = charValue;
        }
    }

    static class NoArgsPojo {
        String value = "created";
        public NoArgsPojo() {}
    }

    static class AliasCreatorPojo {
        final String name;
        @NodeCreator
        AliasCreatorPojo(@NodeProperty(value = "name", aliases = {"n"}) String name) {
            this.name = name;
        }
    }

    static class SessionPojo {
        final String id;
        String extra;

        @NodeCreator
        SessionPojo(@NodeProperty("id") String id) {
            this.id = id;
        }

        public void setExtra(String extra) {
            this.extra = extra;
        }
    }

    static class JsonSessionPojo extends JsonObject {
        final String id;

        @NodeCreator
        JsonSessionPojo(@NodeProperty("id") String id) {
            this.id = id;
        }
    }

    static class ThrowingAccessor {
        public String getName() {
            throw new IllegalStateException("getter boom");
        }

        public void setName(String value) {
            throw new IllegalStateException("setter boom");
        }
    }

    static class ThrowingHandleValue {
        String encode() {
            throw new IllegalStateException("encode boom");
        }

        static ThrowingHandleValue decode(String raw) {
            throw new IllegalStateException("decode boom");
        }

        ThrowingHandleValue copy() {
            throw new IllegalStateException("copy boom");
        }
    }

    static class ContainerPojo {
        public List<String> names;
        public Set<Integer> numbers;
        public Map<String, Long> mapping;
        public List<TypedAnyOf> typedList;
        public Map<String, TypedAnyOf> typedMap;
        public LinkedList<String> linkedNames;
        public TreeSet<Integer> sortedNumbers;
        public HashMap<String, Long> hashMapping;
        public String[] array;
        public String plain;
        public final String readOnly = "ro";
    }

    @NodeBinding(naming = NamingStrategy.SNAKE_CASE)
    static class NamingPojo {
        public String userName;
    }

    @NodeBinding(access = AccessStrategy.FIELD_BASED)
    static class FieldBasedPrivatePojo {
        private String userName;
    }

    static class ExplicitFieldPojo {
        @NodeProperty("user_name")
        public String userName;
    }

    static class PublicPlainPojo {
        public String userName;
        public int loginCount;
    }

    static class AccessorPojo {
        private String userName;
        private int loginCount;

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public int getLoginCount() {
            return loginCount;
        }

        public void setLoginCount(int loginCount) {
            this.loginCount = loginCount;
        }
    }

    static class PrivateFieldPojo {
        private String userName;
    }

    static class InvalidTransientNodePropertyPojo {
        @NodeProperty("user_name")
        transient String userName;
    }

    @AnyOf(value = {
            @AnyOf.Mapping(value = JsonSubtype.class, when = {"json"}),
            @AnyOf.Mapping(value = OtherSubtype.class, when = {"other"})
    }, key = "type")
    interface DiscriminatedAnyOf {}

    static class JsonSubtype extends JsonObject implements DiscriminatedAnyOf {}
    static class OtherSubtype extends JsonObject implements DiscriminatedAnyOf {}

    @AnyOf({
            @AnyOf.Mapping(value = TypedObjectSubtype.class),
            @AnyOf.Mapping(value = ArraySubtype.class)
    })
    interface TypedAnyOf {}

    static class TypedObjectSubtype extends JsonObject implements TypedAnyOf {}
    static class ArraySubtype extends org.sjf4j.JsonArray implements TypedAnyOf {}

    @Test
    void testTypeInfoRegistrationAndInstantRefresh() {
        NodeRegistry.TypeInfo none = NodeRegistry.registerTypeInfo(String.class);
        assertNull(none.pojoInfo);
        assertNull(none.valueCodecInfo);
        assertNull(none.anyOfInfo);

        NodeRegistry.TypeInfo pojoType = NodeRegistry.registerTypeInfo(ContainerPojo.class);
        assertNotNull(pojoType.pojoInfo);
        assertNull(pojoType.valueCodecInfo);
        assertFalse(pojoType.requiresPojoReader());
        assertFalse(pojoType.requiresPojoWriter());

        NodeRegistry.TypeInfo creatorType = NodeRegistry.registerTypeInfo(AliasCreatorPojo.class);
        assertTrue(creatorType.requiresPojoReader());
        assertTrue(creatorType.requiresPojoWriter());

        NodeRegistry.TypeInfo namingType = NodeRegistry.registerTypeInfo(NamingPojo.class);
        assertTrue(namingType.requiresPojoReader());
        assertTrue(namingType.requiresPojoWriter());

        NodeRegistry.TypeInfo explicitFieldType = NodeRegistry.registerTypeInfo(ExplicitFieldPojo.class);
        assertTrue(explicitFieldType.requiresPojoReader());
        assertTrue(explicitFieldType.requiresPojoWriter());

        NodeRegistry.TypeInfo publicPlainType = NodeRegistry.registerTypeInfo(PublicPlainPojo.class);
        assertFalse(publicPlainType.requiresPojoReader());
        assertFalse(publicPlainType.requiresPojoWriter());

        NodeRegistry.TypeInfo accessorType = NodeRegistry.registerTypeInfo(AccessorPojo.class);
        assertFalse(accessorType.requiresPojoReader());
        assertFalse(accessorType.requiresPojoWriter());

        NodeRegistry.TypeInfo privateFieldType = NodeRegistry.registerTypeInfo(PrivateFieldPojo.class);
        assertTrue(privateFieldType.requiresPojoReader());
        assertTrue(privateFieldType.requiresPojoWriter());

        NodeRegistry.TypeInfo fieldBasedType = NodeRegistry.registerTypeInfo(FieldBasedPrivatePojo.class);
        assertNotNull(fieldBasedType.pojoInfo.fields.get("userName"));
        assertEquals(AccessStrategy.FIELD_BASED, fieldBasedType.pojoInfo.accessStrategy);
        assertTrue(fieldBasedType.requiresPojoReader());
        assertTrue(fieldBasedType.requiresPojoWriter());

        assertThrows(JsonException.class,
                () -> NodeRegistry.registerTypeInfo(InvalidTransientNodePropertyPojo.class));

        NodeRegistry.TypeInfo valueType = NodeRegistry.registerTypeInfo(MiniValue.class);
        assertNotNull(valueType.valueCodecInfo);
        assertThrows(JsonException.class, () -> NodeRegistry.registerTypeInfo(MiniValue.class, true));
        assertThrows(JsonException.class, () -> NodeRegistry.registerTypeInfo(AnotherMiniValue.class, true));

        NodeRegistry.TypeInfo anyOfType = NodeRegistry.registerTypeInfo(DiscriminatedAnyOf.class);
        assertNotNull(anyOfType.anyOfInfo);

        assertThrows(JsonException.class, () -> NodeRegistry.registerValueCodec(new InvalidRawCodec()));

        assertThrows(JsonException.class, () -> NodeRegistry.registerValueCodec(new ValueCodec.LocaleValueCodec()));
        NodeRegistry.ValueCodecInfo localeCodec = NodeRegistry.overrideValueCodec(new ValueCodec.LocaleValueCodec());
        assertEquals(String.class, localeCodec.rawClazz);

        assertEquals(String.class, NodeRegistry.instantStringCodecInfo().rawClazz);

        NodeRegistry.registerPojo(ContainerPojo.class);
        NodeRegistry.clearPojoCache();
        assertNotNull(NodeRegistry.registerPojoOrElseThrow(ContainerPojo.class));
    }

    @Test
    void testCreatorInfoPathsAndPrimitiveDefaults() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        OneArg one = (OneArg) ReflectUtil.analyzeCreator(OneArg.class, lookup).newPojoWithArgs(new Object[]{"a"});
        assertEquals("a", one.a);

        TwoArg two = (TwoArg) ReflectUtil.analyzeCreator(TwoArg.class, lookup).newPojoWithArgs(new Object[]{"a", "b"});
        assertEquals("b", two.b);

        ThreeArg three = (ThreeArg) ReflectUtil.analyzeCreator(ThreeArg.class, lookup)
                .newPojoWithArgs(new Object[]{"a", "b", "c"});
        assertEquals("c", three.c);

        FourArg four = (FourArg) ReflectUtil.analyzeCreator(FourArg.class, lookup)
                .newPojoWithArgs(new Object[]{"a", "b", "c", "d"});
        assertEquals("d", four.d);

        FiveArg five = (FiveArg) ReflectUtil.analyzeCreator(FiveArg.class, lookup)
                .newPojoWithArgs(new Object[]{"a", "b", "c", "d", "e"});
        assertEquals("e", five.e);

        NodeRegistry.CreatorInfo primitiveCreator = ReflectUtil.analyzeCreator(PrimitiveArgs.class, lookup);
        PrimitiveArgs primitiveArgs = (PrimitiveArgs) primitiveCreator.newPojoWithArgs(new Object[8]);
        assertFalse(primitiveArgs.boolValue);
        assertEquals(0, primitiveArgs.byteValue);
        assertEquals(0, primitiveArgs.shortValue);
        assertEquals(0, primitiveArgs.intValue);
        assertEquals(0L, primitiveArgs.longValue);
        assertEquals(0f, primitiveArgs.floatValue);
        assertEquals(0d, primitiveArgs.doubleValue);
        assertEquals('\0', primitiveArgs.charValue);

        NodeRegistry.CreatorInfo noArgs = ReflectUtil.analyzeCreator(NoArgsPojo.class, lookup);
        assertTrue(noArgs.hasNoArgsCreator());
        assertTrue(noArgs.newPojoNoArgs() instanceof NoArgsPojo);
        assertTrue(noArgs.forceNewPojo() instanceof NoArgsPojo);

        NodeRegistry.CreatorInfo aliasCreator = ReflectUtil.analyzeCreator(AliasCreatorPojo.class, lookup);
        assertEquals(0, aliasCreator.getArgIndex("name"));
        assertEquals(0, aliasCreator.getArgIndexOrAlias("n"));
        assertEquals(-1, aliasCreator.getArgIndex("missing"));

        NodeRegistry.CreatorInfo badNoArgs = new NodeRegistry.CreatorInfo(NoArgsPojo.class, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null);
        assertThrows(JsonException.class, badNoArgs::newPojoNoArgs);
        assertThrows(JsonException.class, () -> badNoArgs.newPojoWithArgs(new Object[0]));
    }

    @Test
    void testPojoCreationSessionFieldAndJsonReplay() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        NodeRegistry.CreatorInfo sessionCreator = ReflectUtil.analyzeCreator(SessionPojo.class, lookup);
        NodeRegistry.PojoCreationSession session = new NodeRegistry.PojoCreationSession(sessionCreator, 1);
        Map<String, Object> pending = new HashMap<>();
        NodeRegistry.PojoPendingApplier applier = (pojo, key, value) -> {
            pending.put(String.valueOf(key), value);
            ((SessionPojo) pojo).setExtra(String.valueOf(value));
        };
        session.acceptResolved(-1, "later", "extra", applier);
        session.accept("id", "abc", "id", applier);
        SessionPojo pojo = (SessionPojo) session.finish(applier);
        assertEquals("abc", pojo.id);
        assertEquals("later", pojo.extra);
        assertEquals("later", pending.get("extra"));

        NodeRegistry.PojoInfo sessionInfo = NodeRegistry.registerPojoOrElseThrow(SessionPojo.class);
        NodeRegistry.FieldInfo extraField = sessionInfo.fields.get("extra");
        NodeRegistry.PojoCreationSession fieldSession = new NodeRegistry.PojoCreationSession(sessionCreator, 0);
        fieldSession.acceptResolvedField(-1, "value", extraField);
        fieldSession.acceptResolved(0, "id-1", "id", (receiver, key, value) -> {});
        SessionPojo fieldPojo = (SessionPojo) fieldSession.finishField();
        assertEquals("value", fieldPojo.extra);

        NodeRegistry.CreatorInfo jsonCreator = ReflectUtil.analyzeCreator(JsonSessionPojo.class, lookup);
        NodeRegistry.PojoCreationSession jsonSession = new NodeRegistry.PojoCreationSession(jsonCreator, 0);
        jsonSession.acceptResolvedJsonEntry(-1, "extra", 1);
        jsonSession.acceptResolvedJsonEntry(0, "id", "json-id");
        JsonObject jsonObject = jsonSession.finishJsonObject();
        assertEquals(1, jsonObject.getInt("extra"));

        NodeRegistry.PojoInfo containerInfo = NodeRegistry.registerPojoOrElseThrow(ContainerPojo.class);
        NodeRegistry.PojoCreationSession noArgsSession = new NodeRegistry.PojoCreationSession(containerInfo.creatorInfo, 2);
        assertEquals(-1, noArgsSession.resolveArgIndex("plain"));

        NodeRegistry.PojoCreationSession growthSession = new NodeRegistry.PojoCreationSession(sessionCreator, 0);
        for (int i = 0; i < 5; i++) {
            growthSession.acceptResolved(-1, "v" + i, "k" + i, (receiver, key, value) -> ((SessionPojo) receiver).setExtra(String.valueOf(value)));
        }
        SessionPojo grown = (SessionPojo) growthSession.finish((receiver, key, value) -> ((SessionPojo) receiver).setExtra(String.valueOf(value)));
        assertEquals("v4", grown.extra);

        NodeRegistry.PojoCreationSession fieldGrowth = new NodeRegistry.PojoCreationSession(sessionCreator, 0);
        for (int i = 0; i < 5; i++) {
            fieldGrowth.acceptResolvedField(-1, "f" + i, extraField);
        }
        fieldGrowth.acceptResolved(0, "field-id", "id", (receiver, key, value) -> {});
        assertEquals("f4", ((SessionPojo) fieldGrowth.finishField()).extra);

        NodeRegistry.PojoCreationSession jsonGrowth = new NodeRegistry.PojoCreationSession(jsonCreator, 0);
        for (int i = 0; i < 5; i++) {
            jsonGrowth.acceptResolvedJsonEntry(-1, "k" + i, i);
        }
        jsonGrowth.acceptResolvedJsonEntry(0, "id", "grow");
        JsonObject grownJson = jsonGrowth.finishJsonObject();
        assertEquals(4, grownJson.getInt("k4"));
    }

    @Test
    void testFieldInfoValueCodecInfoAndAnyOfInfoHelpers() throws Exception {
        NodeRegistry.PojoInfo pojoInfo = NodeRegistry.registerPojoOrElseThrow(ContainerPojo.class);
        NodeRegistry.FieldInfo namesField = pojoInfo.fields.get("names");
        NodeRegistry.FieldInfo numbersField = pojoInfo.fields.get("numbers");
        NodeRegistry.FieldInfo mappingField = pojoInfo.fields.get("mapping");
        NodeRegistry.FieldInfo typedListField = pojoInfo.fields.get("typedList");
        NodeRegistry.FieldInfo typedMapField = pojoInfo.fields.get("typedMap");
        NodeRegistry.FieldInfo linkedNamesField = pojoInfo.fields.get("linkedNames");
        NodeRegistry.FieldInfo sortedNumbersField = pojoInfo.fields.get("sortedNumbers");
        NodeRegistry.FieldInfo hashMappingField = pojoInfo.fields.get("hashMapping");
        NodeRegistry.FieldInfo arrayField = pojoInfo.fields.get("array");
        NodeRegistry.FieldInfo plainField = pojoInfo.fields.get("plain");
        NodeRegistry.FieldInfo readOnlyField = pojoInfo.fields.get("readOnly");

        assertEquals(NodeRegistry.FieldInfo.ContainerKind.LIST, namesField.containerKind);
        assertEquals(String.class, namesField.argRawClazz);
        assertEquals(NodeRegistry.FieldInfo.ContainerKind.SET, numbersField.containerKind);
        assertEquals(Integer.class, numbersField.argRawClazz);
        assertEquals(NodeRegistry.FieldInfo.ContainerKind.MAP, mappingField.containerKind);
        assertEquals(Long.class, mappingField.argRawClazz);
        assertEquals(NodeRegistry.FieldInfo.ContainerKind.LIST, typedListField.containerKind);
        assertEquals(TypedAnyOf.class, typedListField.argRawClazz);
        assertNotNull(typedListField.argAnyOfInfo);
        assertEquals(NodeRegistry.FieldInfo.ContainerKind.MAP, typedMapField.containerKind);
        assertEquals(TypedAnyOf.class, typedMapField.argRawClazz);
        assertNotNull(typedMapField.argAnyOfInfo);
        assertEquals(NodeRegistry.FieldInfo.ContainerKind.LIST, linkedNamesField.containerKind);
        assertEquals(NodeRegistry.FieldInfo.ContainerKind.SET, sortedNumbersField.containerKind);
        assertEquals(NodeRegistry.FieldInfo.ContainerKind.MAP, hashMappingField.containerKind);
        assertEquals(NodeRegistry.FieldInfo.ContainerKind.ARRAY, arrayField.containerKind);
        assertEquals(String.class, arrayField.argRawClazz);
        assertEquals(NodeRegistry.FieldInfo.ContainerKind.NONE, plainField.containerKind);

        ContainerPojo pojo = new ContainerPojo();
        plainField.invokeSetter(pojo, "plain");
        assertEquals("plain", plainField.invokeGetter(pojo));
        assertTrue(plainField.hasGetter());
        assertTrue(plainField.hasSetter());
        assertTrue(plainField.invokeSetterIfPresent(pojo, "again"));
        assertEquals("again", pojo.plain);
        assertFalse(readOnlyField.invokeSetterIfPresent(pojo, "x"));
        assertThrows(JsonException.class, () -> readOnlyField.invokeSetter(pojo, "x"));
        assertThrows(NullPointerException.class, () -> plainField.invokeGetter(null));

        NodeRegistry.FieldInfo missingGetter = new NodeRegistry.FieldInfo("name", String.class, null, null, null, null, null);
        assertThrows(JsonException.class, () -> missingGetter.invokeGetter(new Object()));

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Method getterMethod = ThrowingAccessor.class.getDeclaredMethod("getName");
        Method setterMethod = ThrowingAccessor.class.getDeclaredMethod("setName", String.class);
        NodeRegistry.FieldInfo throwingField = new NodeRegistry.FieldInfo(
                "name",
                String.class,
                lookup.unreflect(getterMethod),
                null,
                lookup.unreflect(setterMethod),
                null,
                null
        );
        ThrowingAccessor accessor = new ThrowingAccessor();
        assertThrows(JsonException.class, () -> throwingField.invokeGetter(accessor));
        assertThrows(JsonException.class, () -> throwingField.invokeSetter(accessor, "x"));

        NodeRegistry.ValueCodecInfo codecInfo = NodeRegistry.getValueCodecInfo(MiniValue.class);
        MiniValue value = new MiniValue("v");
        assertEquals("v", codecInfo.valueToRaw(value));
        assertEquals("v", ((MiniValue) codecInfo.rawToValue("v")).value);
        assertEquals("v", ((MiniValue) codecInfo.valueCopy(value)).value);

        NodeRegistry.ValueCodecInfo throwing = new NodeRegistry.ValueCodecInfo(String.class, String.class, new ThrowingCodec());
        assertThrows(JsonException.class, () -> throwing.valueToRaw("x"));
        assertThrows(JsonException.class, () -> throwing.rawToValue("x"));
        assertThrows(JsonException.class, () -> throwing.valueCopy("x"));
        assertThrows(JsonException.class, () -> throwing.rawToValue(1));

        NodeRegistry.ValueCodecInfo none = new NodeRegistry.ValueCodecInfo(String.class, String.class, null);
        assertThrows(JsonException.class, () -> none.valueToRaw("x"));
        assertThrows(JsonException.class, () -> none.rawToValue("x"));
        assertThrows(JsonException.class, () -> none.valueCopy("x"));

        NodeRegistry.ValueCodecInfo throwingHandles = new NodeRegistry.ValueCodecInfo(
                ThrowingHandleValue.class,
                String.class,
                null,
                lookup.unreflect(ThrowingHandleValue.class.getDeclaredMethod("encode")),
                lookup.unreflect(ThrowingHandleValue.class.getDeclaredMethod("decode", String.class)),
                lookup.unreflect(ThrowingHandleValue.class.getDeclaredMethod("copy"))
        );
        assertThrows(JsonException.class, () -> throwingHandles.valueToRaw(new ThrowingHandleValue()));
        assertThrows(JsonException.class, () -> throwingHandles.rawToValue("x"));
        assertThrows(JsonException.class, () -> throwingHandles.valueCopy(new ThrowingHandleValue()));

        NodeRegistry.AnyOfInfo discriminated = ReflectUtil.analyzeAnyOf(
                DiscriminatedAnyOf.class,
                DiscriminatedAnyOf.class.getAnnotation(AnyOf.class)
        );
        assertTrue(discriminated.hasDiscriminator);
        assertEquals(JsonSubtype.class, discriminated.resolveByWhen("json"));
        assertNull(discriminated.resolveByWhen(null));

        NodeRegistry.AnyOfInfo typed = ReflectUtil.analyzeAnyOf(
                TypedAnyOf.class,
                TypedAnyOf.class.getAnnotation(AnyOf.class)
        );
        assertFalse(typed.hasDiscriminator);
        assertEquals(TypedObjectSubtype.class, typed.resolveByJsonType(JsonType.OBJECT));
        assertEquals(ArraySubtype.class, typed.resolveByJsonType(JsonType.ARRAY));
        assertNull(typed.resolveByJsonType(null));

        NodeRegistry.AnyOfInfo compiled = new NodeRegistry.AnyOfInfo(
                DiscriminatedAnyOf.class,
                DiscriminatedAnyOf.class.getAnnotation(AnyOf.class).value(),
                "",
                "$.type",
                AnyOf.Scope.PARENT,
                AnyOf.OnNoMatch.FAILBACK_NULL
        );
        assertNotNull(compiled.compiledPath);
        assertEquals(JsonSubtype.class, compiled.resolveByWhen("json"));

        NodeRegistry.RecordInfo recordInfo = new NodeRegistry.RecordInfo(
                ContainerPojo.class,
                null,
                null,
                1,
                new String[]{"plain"},
                new Class<?>[]{String.class},
                new java.lang.reflect.Type[]{String.class}
        );
        assertEquals(1, recordInfo.compCount);
        assertEquals("plain", recordInfo.compNames[0]);

        NodeRegistry.CreatorInfo forceArgs = ReflectUtil.analyzeCreator(OneArg.class, MethodHandles.lookup());
        assertTrue(forceArgs.forceNewPojo() instanceof OneArg);

        assertFalse(ReflectUtil.isRecord(ContainerPojo.class));
        assertNull(ReflectUtil.analyzeRecord(ContainerPojo.class, MethodHandles.lookup()));
    }
}
