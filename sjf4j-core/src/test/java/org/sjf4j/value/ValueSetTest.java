package org.sjf4j.value;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonType;
import org.sjf4j.exception.BindingException;
import org.sjf4j.Sjf4j;
import org.sjf4j.RuntimeContext;
import org.sjf4j.value.ValueRegistry;
import org.sjf4j.node.NodeMapper;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeValue;

import java.util.Map;
import java.util.List;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ValueSetTest {

    public static final class InstantCreator {
        public final Instant time;

        @NodeCreator
        public InstantCreator(@NodeProperty("time") Instant time) {
            this.time = time;
        }
    }

    @Test
    void creatorArgumentWithoutExplicitCodecUsesIncomingShape() {
        Instant expected = Instant.parse("2024-01-01T10:00:00Z");
        RuntimeContext preferredEpoch = new RuntimeContext(Map.of(Instant.class, "epochMillis"));

        InstantCreator fromString = (InstantCreator) NodeMapper.convert(
                Map.of("time", expected.toString()), InstantCreator.class, false, preferredEpoch);
        InstantCreator fromNumber = (InstantCreator) NodeMapper.convert(
                Map.of("time", expected.toEpochMilli()), InstantCreator.class, false, RuntimeContext.EMPTY);

        assertEquals(expected, fromString.time);
        assertEquals(expected, fromNumber.time);
    }

    private static ValueInfo info(String format, Class<?> rawClass) {
        return new ValueInfo(format, Instant.class, rawClass, null, null, null, null);
    }

    @Test
    void selectsFirstInShapeWhenPreferredFormatIsAbsentOrUnmatched() {
        ValueInfo iso = info("iso", String.class);
        ValueInfo millis = info("epochMillis", Long.class);
        ValueInfo seconds = info("epochSeconds", Long.class);
        ValueSet set = new ValueSet(iso).append(millis, false).append(seconds, false);

        assertSame(iso, set.defaultInfo);
        assertTrue(set.multipleJsonTypes);
        assertArrayEquals(new ValueInfo[]{iso}, set.stringInfos);
        assertArrayEquals(new ValueInfo[]{millis, seconds}, set.numberInfos);
        assertSame(iso, set.selectForRead(JsonType.STRING, "epochMillis"));
        assertSame(millis, set.selectForRead(JsonType.NUMBER, "missing"));
        assertSame(millis, set.selectForRead(JsonType.NUMBER, null));
        assertSame(seconds, set.selectForRead(JsonType.NUMBER, "epochSeconds"));
        assertSame(iso, set.selectForRead(JsonType.NULL, null));
        assertSame(iso, set.forWrite(null));
        assertSame(seconds, set.forWrite("epochSeconds"));
        assertThrows(BindingException.class, () -> set.forWrite("missing"));
        assertThrows(BindingException.class, () -> set.selectForRead(JsonType.BOOLEAN, null));
    }

    @Test
    void defaultChoiceIsIndependentOfOrderWithinRawShape() {
        ValueInfo seconds = info("seconds", Long.class);
        ValueInfo millis = info("millis", Long.class);
        ValueSet set = new ValueSet(seconds).append(millis, true);
        assertFalse(set.multipleJsonTypes);
        assertSame(millis, set.defaultInfo);
        assertSame(seconds, set.selectForRead(JsonType.NUMBER, null));
        assertSame(millis, set.selectForRead(JsonType.NUMBER, "millis"));
        assertThrows(BindingException.class,
                () -> set.append(info("millis", String.class), false));
    }

    @Test
    void structuralRawRepresentationsAreIndexedByJsonType() {
        ValueInfo object = info("object", Map.class);
        ValueInfo array = info("array", List.class);
        ValueInfo bool = info("boolean", Boolean.class);
        ValueSet set = new ValueSet(object).append(array, false).append(bool, false);
        assertSame(object, set.selectForRead(JsonType.OBJECT, null));
        assertSame(array, set.selectForRead(JsonType.ARRAY, "object"));
        assertSame(bool, set.selectForRead(JsonType.BOOLEAN, null));
    }

    @Test
    void instantReadsEitherShapeAndConfigurationOnlyPrefersMatchingInput() {
        Instant expected = Instant.parse("2024-01-01T10:00:00Z");
        String iso = "\"" + expected + "\"";
        long millis = expected.toEpochMilli();
        Sjf4j defaults = Sjf4j.builder().build();
        Sjf4j epoch = Sjf4j.builder().defaultValueFormat(Instant.class, "epochMillis").build();

        assertEquals(expected, defaults.fromJson(iso, Instant.class));
        assertEquals(expected, defaults.fromJson(String.valueOf(millis), Instant.class));
        assertEquals(expected, epoch.fromJson(iso, Instant.class));
        assertEquals(expected, epoch.fromJson(String.valueOf(millis), Instant.class));

        assertEquals(iso, defaults.toJsonString(expected));
        assertEquals(String.valueOf(millis), epoch.toJsonString(expected));

        assertEquals(expected, NodeMapper.convert(millis, Instant.class, false, RuntimeContext.EMPTY));
        assertEquals(expected, NodeMapper.convert(expected.toString(), Instant.class,
                false, new RuntimeContext(Map.of(Instant.class, "epochMillis"))));
    }

    public static final class NullTagged {
        public final String origin;

        public NullTagged(String origin) {
            this.origin = origin;
        }
    }

    private static ValueCodec<NullTagged, Long> nullTaggedCodec(String name) {
        return new ValueCodec<NullTagged, Long>() {
            @Override
            public Long valueToRaw(NullTagged value) { return 1L; }

            @Override
            public NullTagged rawToValue(Long raw) {
                return new NullTagged(name + (raw == null ? "-null" : "-number"));
            }

            @Override
            public Class<NullTagged> valueClazz() { return NullTagged.class; }

            @Override
            public Class<Long> rawClazz() { return Long.class; }
        };
    }

    public static final class FirstNullCodec {
        @NodeValue(format = "first")
        public static final ValueCodec<NullTagged, Long> FIRST = nullTaggedCodec("first");
    }

    public static final class DefaultNullCodec {
        @NodeValue(format = "second", defaultCodec = true)
        public static final ValueCodec<NullTagged, Long> SECOND = nullTaggedCodec("default");
    }

    @Test
    void singleShapeNullUsesLaterForcedDefaultWithoutChangingNumberFallback() {
        // Register in separate holders: defaultCodec wins only on the second registration.
        ValueRegistry.registerAnnotated(FirstNullCodec.class);
        ValueRegistry.registerAnnotated(DefaultNullCodec.class);

        Sjf4j defaults = Sjf4j.builder().build();
        assertEquals("default-null", defaults.fromJson("null", NullTagged.class).origin);
        assertEquals("first-number", defaults.fromJson("7", NullTagged.class).origin);

        Sjf4j preferred = Sjf4j.builder().defaultValueFormat(NullTagged.class, "second").build();
        assertEquals("default-number", preferred.fromJson("7", NullTagged.class).origin);
        assertEquals("default-null", preferred.fromJson("null", NullTagged.class).origin);
    }
}
