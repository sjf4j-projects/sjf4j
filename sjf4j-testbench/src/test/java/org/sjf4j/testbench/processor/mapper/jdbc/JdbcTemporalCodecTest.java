package org.sjf4j.testbench.processor.mapper.jdbc;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** JDBC temporal columns must use the driver's typed conversion, not JSON ValueCodecs. */
class JdbcTemporalCodecTest {

    @CompiledJdbcMapper
    interface Mapper {
        Row map(ResultSet resultSet);
    }

    public static final class Row {
        public LocalDate day;
    }

    @Test
    void sqlDateIsConvertedByTypedJdbcRead() {
        LocalDate expected = LocalDate.of(2026, 11, 19);
        java.sql.Date sqlDate = java.sql.Date.valueOf(expected);
        int[] row = {-1};
        ResultSet resultSet = (ResultSet) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{ResultSet.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "next":
                            return ++row[0] == 0;
                        case "getMetaData":
                            return JdbcTestSupport.metadata(new String[]{"day"});
                        case "getObject":
                            if (args.length == 2 && args[1] == LocalDate.class) return sqlDate.toLocalDate();
                            // Reproduce an untyped JDBC driver result. Routing this through
                            // NodeMapper would invoke java.util.Date's codec and fail.
                            if (args.length == 1) return sqlDate;
                            throw new AssertionError("unexpected JDBC getObject call");
                        case "wasNull":
                            return false;
                        default:
                            return null;
                    }
                });

        Row mapped = CompiledInstances.create(Mapper.class).map(resultSet);
        assertEquals(expected, mapped.day);
    }
}
