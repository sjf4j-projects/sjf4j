package org.sjf4j.testbench.processor.mapper.jdbc;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper;
import org.sjf4j.annotation.mapping.jdbc.ColumnProjectionPolicy;
import org.sjf4j.annotation.mapping.jdbc.JdbcMappingOptions;
import org.sjf4j.CompiledInstances;
import org.sjf4j.exception.BindingException;
import org.sjf4j.mapping.JdbcRowMapper;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sjf4j.testbench.processor.mapper.jdbc.JdbcTestSupport.brokenCurrentRowResult;
import static org.sjf4j.testbench.processor.mapper.jdbc.JdbcTestSupport.currentRowResult;
import static org.sjf4j.testbench.processor.mapper.jdbc.JdbcTestSupport.metadata;

class JdbcCurrentRowTest {
    @Test
    void mapsAlreadyPositionedRowWithoutAdvancingCursor() {
        Mapper mapper = CompiledInstances.create(Mapper.class);
        int[] nextCalls = {0};

        JdbcRowMapper<User> users = mapper.prepareUser(metadata(new String[]{"name", "age"}));
        User user = users.map(currentRowResult(new String[]{"name", "age"}, nextCalls, "Ada", 36));
        assertEquals("Ada", user.name);
        assertEquals(36, user.age);
        assertEquals(0, nextCalls[0]);

        JdbcRowMapper<Map<String, Object>> maps = mapper.prepareMap(metadata(new String[]{"name"}));
        Map<String, Object> row = maps.map(currentRowResult(new String[]{"name"}, nextCalls, "Grace"));
        assertEquals(Map.of("name", "Grace"), row);
        assertEquals(0, nextCalls[0]);
    }

    @Test
    void wrapsRowSqlExceptionsWithoutHoldingResultSet() {
        Mapper mapper = CompiledInstances.create(Mapper.class);

        JdbcRowMapper<User> users = mapper.prepareUser(metadata(new String[]{"name", "age"}));
        assertInstanceOf(java.sql.SQLException.class,
                assertThrows(BindingException.class,
                        () -> users.map(brokenCurrentRowResult("getString"))).getCause());

        JdbcRowMapper<Map<String, Object>> maps = mapper.prepareMap(metadata(new String[]{"name"}));
        assertInstanceOf(java.sql.SQLException.class,
                assertThrows(BindingException.class,
                        () -> maps.map(brokenCurrentRowResult("getObject"))).getCause());
    }

    @Test
    void mapsPresentColumnsWithoutAdvancingCurrentRow() {
        Mapper mapper = CompiledInstances.create(Mapper.class);
        int[] nextCalls = {0};

        JdbcRowMapper<Present> rows = mapper.preparePresent(metadata(new String[]{"name"}));
        Present present = rows.map(currentRowResult(new String[]{"name"}, nextCalls, "Ada"));
        assertEquals("Ada", present.name);
        assertEquals(7, present.age);
        assertEquals(0, nextCalls[0]);
    }

    @CompiledJdbcMapper
    interface Mapper {
        JdbcRowMapper<User> prepareUser(ResultSetMetaData metadata);

        JdbcRowMapper<Map<String, Object>> prepareMap(ResultSetMetaData metadata);

        @JdbcMappingOptions(columnProjection = ColumnProjectionPolicy.PRESENT_ONLY)
        JdbcRowMapper<Present> preparePresent(ResultSetMetaData metadata);
    }

    static class User {
        public String name;
        public int age;

        public User() {
        }
    }

    static class Present {
        public String name;
        public int age = 7;

        public Present() {
        }
    }
}
