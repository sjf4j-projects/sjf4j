package org.sjf4j.testbench.processor.mapper.jdbc;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.mapping.Mapping;
import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sjf4j.testbench.processor.mapper.jdbc.JdbcTestSupport.result;

class JdbcComputeTest {
    @Test
    void computesValueFromWhitelistedJdbcType() {
        Mapper mapper = CompiledInstances.of(Mapper.class);
        Instant created = Instant.parse("2020-01-01T00:00:00Z");

        Row row = mapper.row(result(new String[]{"created"},
                new Object[]{Timestamp.from(created)}));

        assertEquals(created, row.created);
    }

    @Test
    void rejectsNullResultSet() {
        Mapper mapper = CompiledInstances.of(Mapper.class);

        NullPointerException error = assertThrows(
                NullPointerException.class,
                () -> mapper.row(null));

        assertEquals("resultSet", error.getMessage());
    }

    @CompiledJdbcMapper
    interface Mapper {
        @Mapping(target = "created", sources = "created", compute = "this::toInstant")
        Row row(ResultSet resultSet);

        default Instant toInstant(Timestamp value) {
            return value == null ? null : value.toInstant();
        }
    }

    public static final class Row {
        public Instant created;
    }
}
