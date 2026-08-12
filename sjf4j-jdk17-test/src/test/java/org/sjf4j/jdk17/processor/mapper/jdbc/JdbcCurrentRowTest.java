package org.sjf4j.jdk17.processor.mapper.jdbc;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.mapper.jdbc.CompiledJdbcMapper;
import org.sjf4j.annotation.mapper.jdbc.ColumnProjectionPolicy;
import org.sjf4j.annotation.mapper.jdbc.JdbcMapperOptions;
import org.sjf4j.compiled.CompiledNodes;
import org.sjf4j.exception.BindingException;

import java.sql.ResultSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sjf4j.jdk17.processor.mapper.jdbc.JdbcTestSupport.brokenCurrentRowResult;
import static org.sjf4j.jdk17.processor.mapper.jdbc.JdbcTestSupport.currentRowResult;

class JdbcCurrentRowTest {
    @Test
    void mapsAlreadyPositionedRowWithoutAdvancingCursor() {
        Mapper mapper = CompiledNodes.instanceOf(Mapper.class);
        int[] nextCalls = {0};

        User user = mapper.anyName(currentRowResult(new String[]{"name", "age"}, nextCalls, "Ada", 36), 42);
        assertEquals("Ada", user.name);
        assertEquals(36, user.age);
        assertEquals(0, nextCalls[0]);

        Map<String, Object> row = mapper.row(currentRowResult(new String[]{"name"}, nextCalls, "Grace"), 0);
        assertEquals(Map.of("name", "Grace"), row);
        assertEquals(0, nextCalls[0]);
    }

    @Test
    void wrapsCurrentRowSqlExceptions() {
        Mapper mapper = CompiledNodes.instanceOf(Mapper.class);

        assertInstanceOf(java.sql.SQLException.class,
                assertThrows(BindingException.class, () -> mapper.anyName(brokenCurrentRowResult("getString"), 0)).getCause());
        assertInstanceOf(java.sql.SQLException.class,
                assertThrows(BindingException.class, () -> mapper.row(brokenCurrentRowResult("getMetaData"), 0)).getCause());
    }

    @Test
    void mapsPresentColumnsWithoutAdvancingCurrentRow() {
        Mapper mapper = CompiledNodes.instanceOf(Mapper.class);
        int[] nextCalls = {0};

        Present present = mapper.present(currentRowResult(new String[]{"name"}, nextCalls, "Ada"), 0);
        assertEquals("Ada", present.name);
        assertEquals(7, present.age);
        assertEquals(0, nextCalls[0]);
    }

    @CompiledJdbcMapper
    interface Mapper {
        User anyName(ResultSet rs, int rowNum);

        Map<String, Object> row(ResultSet rs, int rowNum);

        @JdbcMapperOptions(columnProjection = ColumnProjectionPolicy.PRESENT_ONLY)
        Present present(ResultSet rs, int rowNum);
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
