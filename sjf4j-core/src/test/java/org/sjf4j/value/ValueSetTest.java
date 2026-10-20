package org.sjf4j.value;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonType;
import org.sjf4j.exception.BindingException;
import org.sjf4j.Sjf4j;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ValueSetTest {

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
    }
}
