package org.sjf4j.value;

import org.junit.jupiter.api.Test;
import org.sjf4j.Sjf4j;
import org.sjf4j.TypeReference;
import org.sjf4j.meta.TypeRegistry;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** SQL date/time values use their local temporal semantics, not java.util.Date's instant codec. */
class SqlTemporalValueCodecTest {

    private static final Sjf4j RUNTIME = Sjf4j.builder().build();

    static class SqlTemporalBean {
        public Date date;
        public Time time;
        public Timestamp timestamp;
    }

    @Test
    void directCodecsPreserveSqlTemporalValuesAndHandleNulls() {
        Date date = Date.valueOf(LocalDate.of(2026, 11, 19));
        Time time = Time.valueOf(LocalTime.of(12, 30, 1));
        Timestamp timestamp = Timestamp.valueOf(LocalDateTime.of(2026, 11, 19, 12, 30, 1, 123456789));

        assertCodec(ValueCodec.SQL_DATE, date, "2026-11-19", Date.class);
        assertCodec(ValueCodec.SQL_TIME, time, "12:30:01", Time.class);
        assertCodec(ValueCodec.SQL_TIMESTAMP, timestamp, "2026-11-19T12:30:01.123456789", Timestamp.class);

        assertNotNull(TypeRegistry.requireRegisteredValueInfo(Date.class, null).streamParser);
        assertNotNull(TypeRegistry.requireRegisteredValueInfo(Time.class, null).streamGenerator);
        assertNotNull(TypeRegistry.requireRegisteredValueInfo(Timestamp.class, null).streamParser);
    }

    @Test
    void timestampEpochMillisIsOptInAndTruncatesFractionalNanoseconds() {
        Timestamp timestamp = Timestamp.from(Instant.parse("2026-11-19T12:30:01.123456789Z"));
        long millis = timestamp.getTime();

        assertEquals("iso", TypeRegistry.requireRegisteredValueInfo(Timestamp.class, null).valueFormat);
        assertEquals(Long.class, TypeRegistry.requireRegisteredValueInfo(Timestamp.class, "epochMillis").rawClazz);
        assertSame(ValueCodec.SQL_TIMESTAMP_EPOCH_MILLIS,
                TypeRegistry.requireRegisteredValueInfo(Timestamp.class, "epochMillis").codec);

        assertNull(ValueCodec.SQL_TIMESTAMP_EPOCH_MILLIS.valueToRaw(null));
        assertNull(ValueCodec.SQL_TIMESTAMP_EPOCH_MILLIS.rawToValue(null));
        assertEquals(millis, ValueCodec.SQL_TIMESTAMP_EPOCH_MILLIS.valueToRaw(timestamp));

        Timestamp truncated = Timestamp.from(Instant.ofEpochMilli(millis));
        assertEquals(truncated, ValueCodec.SQL_TIMESTAMP_EPOCH_MILLIS.rawToValue(millis));
        assertNotEquals(timestamp, truncated);

        Sjf4j epochMillis = Sjf4j.builder().valueFormat(Timestamp.class, "epochMillis").build();
        assertEquals(Long.toString(millis), epochMillis.toJsonString(timestamp));
        assertEquals(truncated, epochMillis.fromJson(Long.toString(millis), Timestamp.class));

        // A pre-1970 fractional timestamp must preserve the negative epoch millisecond.
        Timestamp negative = Timestamp.from(Instant.parse("1969-12-31T23:59:59.123456789Z"));
        long negativeMillis = negative.getTime();
        assertEquals(-877L, negativeMillis);
        assertEquals(negativeMillis, ValueCodec.SQL_TIMESTAMP_EPOCH_MILLIS.valueToRaw(negative));
        assertEquals(Timestamp.from(Instant.ofEpochMilli(negativeMillis)),
                ValueCodec.SQL_TIMESTAMP_EPOCH_MILLIS.rawToValue(negativeMillis));
    }

    @Test
    void simpleBindingRoundTripsAllSqlTemporalTypes() {
        assertRoundTrip(Date.class, Date.valueOf("2026-11-19"), "\"2026-11-19\"");
        assertRoundTrip(Time.class, Time.valueOf("12:30:01"), "\"12:30:01\"");
        assertRoundTrip(Timestamp.class, Timestamp.valueOf("2026-11-19 12:30:01.123456789"),
                "\"2026-11-19T12:30:01.123456789\"");

        assertNull(RUNTIME.fromJson("null", Date.class));
        assertNull(RUNTIME.fromJson("null", Time.class));
        assertNull(RUNTIME.fromJson("null", Timestamp.class));
    }

    @Test
    void roundTripsSqlTemporalsInsidePojoAndCollections() {
        String json = "{\"date\":\"2026-11-19\",\"time\":\"12:30:01\","
                + "\"timestamp\":\"2026-11-19T12:30:01.123456789\"}";
        SqlTemporalBean bean = RUNTIME.fromJson(json, SqlTemporalBean.class);
        assertEquals(Date.valueOf("2026-11-19"), bean.date);
        assertEquals(Time.valueOf("12:30:01"), bean.time);
        assertEquals(Timestamp.valueOf("2026-11-19 12:30:01.123456789"), bean.timestamp);
        assertEquals(json, RUNTIME.toJsonString(bean));

        List<Date> dates = Arrays.asList(Date.valueOf("2026-11-19"), Date.valueOf("2026-11-20"));
        String datesJson = "[\"2026-11-19\",\"2026-11-20\"]";
        assertEquals(datesJson, RUNTIME.toJsonString(dates));
        assertEquals(dates, RUNTIME.fromJson(datesJson, new TypeReference<List<Date>>() {}));
    }

    private static <T> void assertCodec(ValueCodec<T, String> codec, T value, String raw, Class<T> type) {
        assertEquals(type, codec.valueClazz());
        assertEquals(String.class, codec.rawClazz());
        assertNull(codec.valueToRaw(null));
        assertNull(codec.rawToValue(null));
        assertEquals(raw, codec.valueToRaw(value));
        assertEquals(value, codec.rawToValue(raw));
    }

    private static <T> void assertRoundTrip(Class<T> type, T value, String json) {
        assertEquals(json, RUNTIME.toJsonString(value));
        assertEquals(value, RUNTIME.fromJson(json, type));
    }
}
