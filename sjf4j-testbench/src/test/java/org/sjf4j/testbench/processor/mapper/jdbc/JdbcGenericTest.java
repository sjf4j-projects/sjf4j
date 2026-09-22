package org.sjf4j.testbench.processor.mapper.jdbc;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.mapping.Mapping;
import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sjf4j.testbench.processor.mapper.jdbc.JdbcTestSupport.result;

/** Coverage for inherited generic JDBC mapper signatures and result boundaries. */
class JdbcGenericTest {

    @Test
    void specializesInheritedGenericSingleAndListResults() {
        UserMapper mapper = CompiledInstances.of(UserMapper.class);

        User one = mapper.row(result(
                new String[]{"full_name", "age"},
                new Object[]{"Ada", 36}));
        assertEquals("Ada", one.name);
        assertEquals(36, one.age);

        List<User> rows = mapper.rows(result(
                new String[]{"full_name", "age"},
                new Object[]{"Ada", 36},
                new Object[]{"Grace", 40}));
        assertEquals(2, rows.size());
        assertEquals("Ada", rows.get(0).name);
        assertEquals(36, rows.get(0).age);
        assertEquals("Grace", rows.get(1).name);
        assertEquals(40, rows.get(1).age);
    }

    @Test
    void inheritedGenericResultsKeepSingleAndMultiRowEmptySemantics() {
        UserMapper mapper = CompiledInstances.of(UserMapper.class);

        assertNull(mapper.row(result(new String[]{"full_name", "age"})));
        assertTrue(mapper.rows(result(new String[]{"full_name", "age"})).isEmpty());
    }

    @Test
    void inheritedGenericRecordTargetIsResolvedAfterSpecialization() {
        UserRecordMapper mapper = CompiledInstances.of(UserRecordMapper.class);

        UserRecord user = mapper.row(result(
                new String[]{"full_name", "age"},
                new Object[]{"Katherine", 41}));
        assertEquals(new UserRecord("Katherine", 41), user);

        assertEquals(
                List.of(
                        new UserRecord("Katherine", 41),
                        new UserRecord("Dorothy", 38)),
                mapper.rows(result(
                        new String[]{"full_name", "age"},
                        new Object[]{"Katherine", 41},
                        new Object[]{"Dorothy", 38})));
    }


    interface BaseMapper<T> {
        @Mapping(target = "name", source = "full_name")
        T row(ResultSet rs);

        @Mapping(target = "name", source = "full_name")
        List<T> rows(ResultSet rs);
    }

    interface IntermediateMapper<T> extends BaseMapper<T> {
    }

    @CompiledJdbcMapper
    interface UserMapper extends IntermediateMapper<User> {
    }

    @CompiledJdbcMapper
    interface UserRecordMapper extends IntermediateMapper<UserRecord> {
    }

    public static final class User {
        public String name;
        public int age;

        public User() {
        }
    }

    public record UserRecord(String name, int age) {
    }
}
