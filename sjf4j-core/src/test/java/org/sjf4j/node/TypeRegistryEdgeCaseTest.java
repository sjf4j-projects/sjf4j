package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.annotation.node.NamingStrategy;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.NodeBinding;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.PropertyStrategy;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueCopy;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.exception.JsonException;
import org.sjf4j.facade.StreamingContext;
import org.sjf4j.facade.StreamingIO;
import org.sjf4j.facade.simple.SimpleJsonReader;

import java.io.StringReader;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Optional;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeRegistryEdgeCaseTest {

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

    static class InstantFieldPojo {
        @NodeProperty(codecName = "epochMillis")
        Instant createdAt;
    }

    static class InstantCreatorPojo {
        final Instant createdAt;

        @NodeCreator
        InstantCreatorPojo(@NodeProperty(value = "createdAt", codecName = "epochMillis") Instant createdAt) {
            this.createdAt = createdAt;
        }
    }

    static class JsonSessionPojo extends JsonObject {
        final String id;

        @NodeCreator
        JsonSessionPojo(@NodeProperty("id") String id) {
            this.id = id;
        }
    }

    static class MixedJsonSessionPojo extends JsonObject {
        final String id;
        String extra;

        @NodeCreator
        MixedJsonSessionPojo(@NodeProperty("id") String id) {
            this.id = id;
        }

        public void setExtra(String extra) {
            this.extra = extra;
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

    @Test
    void testNamedValueCodecsAndValueFormatMetadata() {
        ValueCodecInfo defaultCodec = TypeRegistry.registerTypeInfo(Instant.class).valueCodecInfo;
        ValueCodecInfo isoCodec = TypeRegistry.resolveValueCodecOrElseThrow(Instant.class, "iso");
        ValueCodecInfo epochCodec = TypeRegistry.resolveValueCodecOrElseThrow(Instant.class, "epochMillis");

        assertEquals("", defaultCodec.codecName);
        assertEquals("iso", isoCodec.codecName);
        assertEquals("epochMillis", epochCodec.codecName);
        assertEquals(String.class, isoCodec.rawClazz);
        assertEquals(Long.class, epochCodec.rawClazz);

        FieldInfo fi = TypeRegistry.registerPojoOrElseThrow(InstantFieldPojo.class).properties.get("createdAt");
        assertEquals("epochMillis", fi.codecName);
        assertNotNull(fi.resolvedValueCodec);
        assertEquals(Long.class, fi.resolvedValueCodec.rawClazz);

        CreatorInfo creatorInfo = TypeRegistry.registerPojoOrElseThrow(InstantCreatorPojo.class).creatorInfo;
        assertEquals("epochMillis", creatorInfo.argCodecNames[0]);
        assertNotNull(creatorInfo.argValueCodecs[0]);
        assertEquals(Long.class, creatorInfo.argValueCodecs[0].rawClazz);
    }

    @Test
    void testCodecPatternResolvesLocalDateCodec() {
        PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(LocalDatePatternPojo.class);
        FieldInfo fi = pi.properties.get("date");
        assertNotNull(fi);
        // codecName is null when only codecPattern is specified (separate attributes)
        assertNull(fi.codecName);
        assertNotNull(fi.resolvedValueCodec);
        // Round-trip through the patterned codec
        Object raw = fi.resolvedValueCodec.valueToRaw(LocalDate.of(2024, 1, 15));
        assertEquals("2024-01-15", raw);
        Object decoded = fi.resolvedValueCodec.rawToValue(raw);
        assertEquals(LocalDate.of(2024, 1, 15), decoded);
    }

    @Test
    void testCodecPatternOnCreatorParam() {
        CreatorInfo ci = TypeRegistry.registerPojoOrElseThrow(LocalDatePatternCreatorPojo.class).creatorInfo;
        // argCodecNames stores codecName (null when only codecPattern is set)
        assertNull(ci.argCodecNames[0]);
        assertNotNull(ci.argValueCodecs[0]);
        Object raw = ci.argValueCodecs[0].valueToRaw(LocalDate.of(2024, 6, 7));
        assertEquals("2024/06/07", raw);
    }

    @Test
    void testCodecPatternOnNonPatternTypeThrows() {
        assertThrows(JsonException.class, () ->
                TypeRegistry.registerPojoOrElseThrow(InvalidPatternPojo.class));
    }

    static class LocalDatePatternPojo {
        @NodeProperty(codecPattern = "yyyy-MM-dd")
        LocalDate date;
    }

    static class LocalDatePatternCreatorPojo {
        final LocalDate date;

        @NodeCreator
        LocalDatePatternCreatorPojo(@NodeProperty(value = "date", codecPattern = "yyyy/MM/dd") LocalDate date) {
            this.date = date;
        }
    }

    static class InvalidPatternPojo {
        @NodeProperty(codecPattern = "yyyy-MM-dd")
        String name;
    }

    // ── LocalTime ──

    @Test
    void testLocalTimeCodecRoundTrip() {
        TypeInfo ti = TypeRegistry.registerTypeInfo(LocalTime.class);
        assertTrue(ti.hasValueCodecs());
        ValueCodecInfo vci = ti.getValueCodecInfo("");
        assertNotNull(vci);
        Object raw = vci.valueToRaw(LocalTime.of(10, 30, 15));
        assertEquals("10:30:15", raw);
        Object decoded = vci.rawToValue(raw);
        assertEquals(LocalTime.of(10, 30, 15), decoded);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testLocalTimeCodecPattern() {
        ValueCodecInfo base = TypeRegistry.resolveValueCodecOrElseThrow(LocalTime.class, "");
        assertTrue(base.valueCodec instanceof PatternedValueCodec);
        // Direct PatternedValueCodec.withPattern() call (raw types for wildcard avoidance)
        PatternedValueCodec pc = (PatternedValueCodec) base.valueCodec;
        ValueCodec patterned = pc.withPattern("HH:mm");
        Object raw = patterned.valueToRaw(LocalTime.of(8, 5));
        assertEquals("08:05", raw);
        Object decoded = patterned.rawToValue(raw);
        assertEquals(LocalTime.of(8, 5), decoded);
    }

    // ── Optional ──

    @Test
    void testOptionalCodecPresent() {
        TypeInfo ti = TypeRegistry.registerTypeInfo(Optional.class);
        assertTrue(ti.hasValueCodecs());
        ValueCodecInfo vci = ti.getValueCodecInfo("");
        assertNotNull(vci);
        assertEquals(Object.class, vci.rawClazz);

        Object raw = vci.valueToRaw(Optional.of("hello"));
        assertEquals("hello", raw);

        Object decoded = vci.rawToValue(raw);
        assertInstanceOf(Optional.class, decoded);
        assertEquals(Optional.of("hello"), decoded);
    }

    @Test
    void testOptionalCodecEmpty() {
        ValueCodecInfo vci = TypeRegistry.resolveValueCodecOrElseThrow(Optional.class, "");
        assertNull(vci.valueToRaw(Optional.empty()));
        assertSame(Optional.empty(), vci.rawToValue(null));
    }

    static class LocalTimeFieldPojo {
        @NodeProperty(codecPattern = "HH:mm:ss")
        LocalTime time;
    }

    @Test
    void testLocalTimeFieldWithPattern() {
        PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(LocalTimeFieldPojo.class);
        FieldInfo fi = pi.properties.get("time");
        assertNotNull(fi);
        assertNull(fi.codecName);
        assertNotNull(fi.resolvedValueCodec);
        Object raw = fi.resolvedValueCodec.valueToRaw(LocalTime.of(14, 30, 0));
        assertEquals("14:30:00", raw);
    }

    static class OptionalFieldPojo {
        @NodeProperty(codecName = "")
        Optional<String> name;
    }

    @Test
    void testOptionalFieldWithCodec() {
        PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(OptionalFieldPojo.class);
        FieldInfo fi = pi.properties.get("name");
        assertNotNull(fi);
        assertNotNull(fi.resolvedValueCodec);
        // Present
        Object raw = fi.resolvedValueCodec.valueToRaw(Optional.of("Alice"));
        assertEquals("Alice", raw);
        // Empty
        assertNull(fi.resolvedValueCodec.valueToRaw(Optional.empty()));
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
        public List<TypedOneOf> typedList;
        public Map<String, TypedOneOf> typedMap;
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

    @NodeBinding(propertyStrategy = PropertyStrategy.FIELD_ONLY)
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

    @OneOf(value = {
            @OneOf.Mapping(value = JsonSubtype.class, when = {"json"}),
            @OneOf.Mapping(value = OtherSubtype.class, when = {"other"})
    }, key = "type")
    interface DiscriminatedOneOf {}

    static class JsonSubtype extends JsonObject implements DiscriminatedOneOf {}
    static class OtherSubtype extends JsonObject implements DiscriminatedOneOf {}

    @OneOf({
            @OneOf.Mapping(value = TypedObjectSubtype.class),
            @OneOf.Mapping(value = ArraySubtype.class)
    })
    interface TypedOneOf {}

    static class TypedObjectSubtype extends JsonObject implements TypedOneOf {}
    static class ArraySubtype extends JsonArray implements TypedOneOf {}

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

        CreatorInfo primitiveCreator = ReflectUtil.analyzeCreator(PrimitiveArgs.class, lookup);
        PrimitiveArgs primitiveArgs = (PrimitiveArgs) primitiveCreator.newPojoWithArgs(new Object[8]);
        assertFalse(primitiveArgs.boolValue);
        assertEquals(0, primitiveArgs.byteValue);
        assertEquals(0, primitiveArgs.shortValue);
        assertEquals(0, primitiveArgs.intValue);
        assertEquals(0L, primitiveArgs.longValue);
        assertEquals(0f, primitiveArgs.floatValue);
        assertEquals(0d, primitiveArgs.doubleValue);
        assertEquals('\0', primitiveArgs.charValue);

        CreatorInfo noArgs = ReflectUtil.analyzeCreator(NoArgsPojo.class, lookup);
        assertTrue(noArgs.hasNoArgsCreator());
        assertTrue(noArgs.newPojoNoArgs() instanceof NoArgsPojo);
        assertTrue(noArgs.forceNewPojo() instanceof NoArgsPojo);

        CreatorInfo aliasCreator = ReflectUtil.analyzeCreator(AliasCreatorPojo.class, lookup);
        assertEquals(0, aliasCreator.getArgIndex("name"));
        assertEquals(0, aliasCreator.getArgIndexOrAlias("n"));
        assertEquals(-1, aliasCreator.getArgIndex("missing"));

        CreatorInfo badNoArgs = new CreatorInfo(NoArgsPojo.class, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null);
        assertThrows(JsonException.class, badNoArgs::newPojoNoArgs);
        assertThrows(JsonException.class, () -> badNoArgs.newPojoWithArgs(new Object[0]));
    }

    @Test
    void testPojoCreationSessionFieldAndJsonReplay() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        CreatorInfo sessionCreator = ReflectUtil.analyzeCreator(SessionPojo.class, lookup);
        PojoInfo sessionInfo = TypeRegistry.registerPojoOrElseThrow(SessionPojo.class);
        FieldInfo extraField = sessionInfo.properties.get("extra");

        TypeRegistry.PojoCreationSession session = new TypeRegistry.PojoCreationSession(sessionCreator, 1);
        session.acceptProperty(extraField, "later");
        session.acceptCtorArg(0, "abc");
        SessionPojo pojo = (SessionPojo) session.finish();
        assertEquals("abc", pojo.id);
        assertEquals("later", pojo.extra);

        TypeRegistry.PojoCreationSession fieldSession = new TypeRegistry.PojoCreationSession(sessionCreator, 0);
        fieldSession.acceptProperty(extraField, "value");
        fieldSession.acceptCtorArg(0, "id-1");
        SessionPojo fieldPojo = (SessionPojo) fieldSession.finish();
        assertEquals("value", fieldPojo.extra);

        CreatorInfo jsonCreator = ReflectUtil.analyzeCreator(JsonSessionPojo.class, lookup);
        TypeRegistry.PojoCreationSession jsonSession = new TypeRegistry.PojoCreationSession(jsonCreator, 0);
        jsonSession.acceptDynamic("extra", 1);
        jsonSession.acceptCtorArg(0, "json-id");
        JsonObject jsonObject = (JsonObject) jsonSession.finish();
        assertEquals(1, jsonObject.getInt("extra"));

        PojoInfo mixedInfo = TypeRegistry.registerPojoOrElseThrow(MixedJsonSessionPojo.class);
        TypeRegistry.PojoCreationSession mixedSession = new TypeRegistry.PojoCreationSession(mixedInfo.creatorInfo, 2);
        mixedSession.acceptProperty(mixedInfo.properties.get("extra"), "later");
        mixedSession.acceptDynamic("dynamic", 2);
        mixedSession.acceptCtorArg(0, "mixed-id");
        MixedJsonSessionPojo mixedPojo = (MixedJsonSessionPojo) mixedSession.finish();
        assertEquals("later", mixedPojo.extra);
        assertEquals(2, mixedPojo.getInt("dynamic"));

        PojoInfo containerInfo = TypeRegistry.registerPojoOrElseThrow(ContainerPojo.class);
        TypeRegistry.PojoCreationSession noArgsSession = new TypeRegistry.PojoCreationSession(containerInfo.creatorInfo, 2);
        noArgsSession.acceptProperty(containerInfo.properties.get("plain"), "plain");
        assertEquals("plain", ((ContainerPojo) noArgsSession.finish()).plain);

        TypeRegistry.PojoCreationSession growthSession = new TypeRegistry.PojoCreationSession(sessionCreator, 0);
        for (int i = 0; i < 5; i++) {
            growthSession.acceptProperty(extraField, "v" + i);
        }
        growthSession.acceptCtorArg(0, "grow-id");
        SessionPojo grown = (SessionPojo) growthSession.finish();
        assertEquals("v4", grown.extra);

        TypeRegistry.PojoCreationSession fieldGrowth = new TypeRegistry.PojoCreationSession(sessionCreator, 0);
        for (int i = 0; i < 5; i++) {
            fieldGrowth.acceptProperty(extraField, "f" + i);
        }
        fieldGrowth.acceptCtorArg(0, "field-id");
        assertEquals("f4", ((SessionPojo) fieldGrowth.finish()).extra);

        TypeRegistry.PojoCreationSession jsonGrowth = new TypeRegistry.PojoCreationSession(jsonCreator, 0);
        for (int i = 0; i < 5; i++) {
            jsonGrowth.acceptDynamic("k" + i, i);
        }
        jsonGrowth.acceptCtorArg(0, "grow");
        JsonObject grownJson = (JsonObject) jsonGrowth.finish();
        assertEquals(4, grownJson.getInt("k4"));

        CreatorInfo aliasCreator = ReflectUtil.analyzeCreator(AliasCreatorPojo.class, lookup);
        TypeRegistry.PojoCreationSession duplicateSession = new TypeRegistry.PojoCreationSession(aliasCreator, 0);
        duplicateSession.acceptCtorArg(aliasCreator.getArgIndexOrAlias("name"), "first");
        JsonException duplicate = assertThrows(JsonException.class,
                () -> duplicateSession.acceptCtorArg(aliasCreator.getArgIndexOrAlias("n"), "second"));
        assertTrue(duplicate.getMessage().contains("duplicate creator argument assignment"));
    }

    @Test
    void testDuplicateCreatorBindingFailsAfterMaterialization() {
        PojoInfo pi = TypeRegistry.registerPojoOrElseThrow(AliasCreatorPojo.class);
        JsonException duplicate = assertThrows(JsonException.class,
                () -> StreamingIO.readPojo(new SimpleJsonReader(new StringReader("{\"name\":\"first\",\"n\":\"second\"}")),
                        AliasCreatorPojo.class, AliasCreatorPojo.class, pi, StreamingContext.EMPTY));
        assertTrue(duplicate.getMessage().contains("duplicate creator argument assignment"));
    }

    @Test
    void testPropertyInfoValueCodecInfoAndOneOfInfoHelpers() throws Exception {
        PojoInfo pojoInfo = TypeRegistry.registerPojoOrElseThrow(ContainerPojo.class);
        FieldInfo namesField = pojoInfo.properties.get("names");
        FieldInfo numbersField = pojoInfo.properties.get("numbers");
        FieldInfo mappingField = pojoInfo.properties.get("mapping");
        FieldInfo typedListField = pojoInfo.properties.get("typedList");
        FieldInfo typedMapField = pojoInfo.properties.get("typedMap");
        FieldInfo linkedNamesField = pojoInfo.properties.get("linkedNames");
        FieldInfo sortedNumbersField = pojoInfo.properties.get("sortedNumbers");
        FieldInfo hashMappingField = pojoInfo.properties.get("hashMapping");
        FieldInfo arrayField = pojoInfo.properties.get("array");
        FieldInfo plainField = pojoInfo.properties.get("plain");
        FieldInfo readOnlyField = pojoInfo.properties.get("readOnly");

        assertEquals(FieldInfo.ContainerKind.LIST, namesField.containerKind);
        assertEquals(String.class, namesField.argBoxed);
        assertEquals(FieldInfo.ContainerKind.SET, numbersField.containerKind);
        assertEquals(Integer.class, numbersField.argBoxed);
        assertEquals(FieldInfo.ContainerKind.MAP, mappingField.containerKind);
        assertEquals(Long.class, mappingField.argBoxed);
        assertEquals(FieldInfo.ContainerKind.LIST, typedListField.containerKind);
        assertEquals(TypedOneOf.class, typedListField.argBoxed);
        assertNotNull(typedListField.argOneOfInfo);
        assertEquals(FieldInfo.ContainerKind.MAP, typedMapField.containerKind);
        assertEquals(TypedOneOf.class, typedMapField.argBoxed);
        assertNotNull(typedMapField.argOneOfInfo);
        assertEquals(FieldInfo.ContainerKind.LIST, linkedNamesField.containerKind);
        assertEquals(FieldInfo.ContainerKind.SET, sortedNumbersField.containerKind);
        assertEquals(FieldInfo.ContainerKind.MAP, hashMappingField.containerKind);
        assertEquals(FieldInfo.ContainerKind.ARRAY, arrayField.containerKind);
        assertEquals(String.class, arrayField.argBoxed);
        assertEquals(FieldInfo.ContainerKind.NONE, plainField.containerKind);

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

        FieldInfo missingGetter = new FieldInfo(
                "name",
                null,
                String.class,
                false,
                String.class,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null, null
        );
        assertThrows(JsonException.class, () -> missingGetter.invokeGetter(new Object()));

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Method getterMethod = ThrowingAccessor.class.getDeclaredMethod("getName");
        Method setterMethod = ThrowingAccessor.class.getDeclaredMethod("setName", String.class);
        FieldInfo throwingField = new FieldInfo(
                "name",
                null,
                String.class,
                false,
                String.class,
                getterMethod,
                lookup.unreflect(getterMethod),
                null,
                setterMethod,
                lookup.unreflect(setterMethod),
                null,
                null,
                null,
                null,
                null
        );
        ThrowingAccessor accessor = new ThrowingAccessor();
        assertThrows(JsonException.class, () -> throwingField.invokeGetter(accessor));
        assertThrows(JsonException.class, () -> throwingField.invokeSetter(accessor, "x"));

        ValueCodecInfo codecInfo = TypeRegistry.registerTypeInfo(MiniValue.class).valueCodecInfo;
        MiniValue value = new MiniValue("v");
        assertEquals("v", codecInfo.valueToRaw(value));
        assertEquals("v", ((MiniValue) codecInfo.rawToValue("v")).value);
        assertEquals("v", ((MiniValue) codecInfo.valueCopy(value)).value);

        ValueCodecInfo throwing = new ValueCodecInfo("", String.class, String.class, new ThrowingCodec(), null, null, null);
        assertThrows(JsonException.class, () -> throwing.valueToRaw("x"));
        assertThrows(JsonException.class, () -> throwing.rawToValue("x"));
        assertThrows(JsonException.class, () -> throwing.valueCopy("x"));
        assertThrows(JsonException.class, () -> throwing.rawToValue(1));

        ValueCodecInfo none = new ValueCodecInfo("", String.class, String.class, null, null, null, null);
        assertThrows(JsonException.class, () -> none.valueToRaw("x"));
        assertThrows(JsonException.class, () -> none.rawToValue("x"));
        assertThrows(JsonException.class, () -> none.valueCopy("x"));

        ValueCodecInfo throwingHandles = new ValueCodecInfo(
                "",
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

        OneOfInfo discriminated = ReflectUtil.analyzeOneOf(
                DiscriminatedOneOf.class,
                DiscriminatedOneOf.class.getAnnotation(OneOf.class)
        );
        assertTrue(discriminated.hasDiscriminator);
        assertEquals(JsonSubtype.class, discriminated.matchByWhen("json"));
        assertNull(discriminated.matchByWhen(null));

        OneOfInfo typed = ReflectUtil.analyzeOneOf(
                TypedOneOf.class,
                TypedOneOf.class.getAnnotation(OneOf.class)
        );
        assertFalse(typed.hasDiscriminator);
        assertEquals(TypedObjectSubtype.class, typed.matchByJsonType(JsonType.OBJECT));
        assertEquals(ArraySubtype.class, typed.matchByJsonType(JsonType.ARRAY));
        assertNull(typed.matchByJsonType(null));

        OneOfInfo compiled = new OneOfInfo(
                DiscriminatedOneOf.class,
                DiscriminatedOneOf.class.getAnnotation(OneOf.class).value(),
                "",
                "$.type",
                OneOf.Scope.PARENT,
                OneOf.OnNoMatch.FAILBACK_NULL
        );
        assertNotNull(compiled.compiledPath);
        assertEquals(JsonSubtype.class, compiled.matchByWhen("json"));

        RecordInfo recordInfo = new RecordInfo(
                ContainerPojo.class,
                null,
                null,
                1,
                new String[]{"plain"},
                new Class<?>[]{String.class},
                new Type[]{String.class}
        );
        assertEquals(1, recordInfo.compCount);
        assertEquals("plain", recordInfo.compNames[0]);

        CreatorInfo forceArgs = ReflectUtil.analyzeCreator(OneArg.class, MethodHandles.lookup());
        assertTrue(forceArgs.forceNewPojo() instanceof OneArg);

        assertFalse(ReflectUtil.isRecord(ContainerPojo.class));
        assertNull(ReflectUtil.analyzeRecord(ContainerPojo.class, MethodHandles.lookup()));
    }
}
