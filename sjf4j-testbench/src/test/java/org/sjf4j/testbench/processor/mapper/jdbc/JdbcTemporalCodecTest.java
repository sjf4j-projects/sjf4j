package org.sjf4j.testbench.processor.mapper.jdbc;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Built-in ValueCodecs must never override JDBC-native typed object reads. */
class JdbcTemporalCodecTest {
    private static final String[] COLUMNS =
            {"day", "dateTime", "offsetDateTime", "instant", "id"};

    @CompiledJdbcMapper
    interface Mapper {
        Row map(ResultSet resultSet);
    }

    public static final class Row {
        public LocalDate day;
        public LocalDateTime dateTime;
        public OffsetDateTime offsetDateTime;
        public Instant instant;
        public UUID id;
    }

    @Test
    void sqlTemporalAndUuidColumnsUseNativeTypedJdbcReads() {
        LocalDate day = LocalDate.of(2026, 11, 19);
        LocalDateTime dateTime = day.atTime(11, 30);
        OffsetDateTime offset = dateTime.atOffset(ZoneOffset.UTC);
        Instant instant = offset.toInstant();
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        Class<?>[] types = {LocalDate.class, LocalDateTime.class,
                OffsetDateTime.class, Instant.class, UUID.class};
        Object[] expected = {day, dateTime, offset, instant, id};
        int[] reads = new int[types.length];
        int[] row = {-1};

        ResultSet rs = (ResultSet) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{ResultSet.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "next":
                            return ++row[0] == 0;
                        case "getMetaData":
                            return JdbcTestSupport.metadata(COLUMNS);
                        case "getObject":
                            int index = args[0] instanceof Integer
                                    ? ((Integer) args[0]) - 1
                                    : List.of(COLUMNS).indexOf(args[0]);
                            if (args.length == 2) {
                                assertEquals(types[index], args[1],
                                        "JDBC driver should receive the target class");
                                reads[index]++;
                                return expected[index];
                            }
                            // Untyped JDBC values are not necessarily JSON
                            // codec raw values (notably java.sql.Date).
                            return index == 0 ? java.sql.Date.valueOf(day)
                                    : java.sql.Timestamp.valueOf(dateTime);
                        case "wasNull":
                            return false;
                        default:
                            return null;
                    }
                });

        Row mapped = CompiledInstances.create(Mapper.class).map(rs);
        assertEquals(day, mapped.day);
        assertEquals(dateTime, mapped.dateTime);
        assertEquals(offset, mapped.offsetDateTime);
        assertEquals(instant, mapped.instant);
        assertEquals(id, mapped.id);
        for (int count : reads) assertEquals(1, count);
    }
}
