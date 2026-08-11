package org.sjf4j.jdk17.processor.mapper.jdbc;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.mapper.jdbc.CompiledJdbcMapper;
import org.sjf4j.annotation.mapper.jdbc.DuplicateColumnPolicy;
import org.sjf4j.annotation.mapper.jdbc.JdbcMapperOptions;
import org.sjf4j.annotation.mapper.jdbc.SingleResultPolicy;
import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.compiled.CompiledNodes;
import org.sjf4j.exception.BindingException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sjf4j.jdk17.processor.mapper.jdbc.JdbcTestSupport.brokenFindColumnResult;
import static org.sjf4j.jdk17.processor.mapper.jdbc.JdbcTestSupport.brokenResult;
import static org.sjf4j.jdk17.processor.mapper.jdbc.JdbcTestSupport.cachedMetadataResult;
import static org.sjf4j.jdk17.processor.mapper.jdbc.JdbcTestSupport.indexedResult;
import static org.sjf4j.jdk17.processor.mapper.jdbc.JdbcTestSupport.result;

class JdbcResultMappingTest {
    @Test
    void mapsBasicValuesRowsAndMetadata() {
        Mapper mapper = CompiledNodes.of(Mapper.class);
        Instant created = Instant.parse("2020-01-01T00:00:00Z");

        User user = mapper.user(result(new String[]{"full_name", "age", "created"},
                new Object[]{"Ada", 36, Timestamp.from(created)}));
        assertEquals("Ada", user.name);
        assertEquals(36, user.age);
        assertEquals(created, user.created);

        assertEquals("Ada", mapper.recordUser(result(new String[]{"name", "age"},
                new Object[]{"Ada", 36})).name());
        assertEquals("Grace", mapper.name(result(new String[]{"alias"}, new Object[]{"Grace"})).value());
        assertEquals(null, mapper.name(result(new String[]{"alias"}, new Object[]{null})).value());
        assertEquals("Katherine", mapper.names(result(new String[]{"alias"},
                new Object[]{"Katherine"})).get(0).value());
        assertEquals(2, mapper.users(result(new String[]{"name", "age", "created"},
                new Object[]{"Ada", 36, null}, new Object[]{"Grace", 40, null})).size());

        List<Map<String, Object>> rows = mapper.rows(result(new String[]{"b", "a"},
                new Object[]{2, 1}, new Object[]{4, 3}));
        assertEquals(Map.of("b", 2, "a", 1), rows.get(0));
        assertEquals(Map.of("b", 4, "a", 3), rows.get(1));
        assertEquals(List.of("b", "a"), new ArrayList<>(rows.get(0).keySet()));

        Map<String, Object> duplicateColumnMap = mapper.row(result(new String[]{"a", "a", "b"},
                new Object[]{1, 2, 3}));
        assertEquals(Map.of("a", 2, "b", 3), duplicateColumnMap);
        assertEquals(List.of("a", "b"), new ArrayList<>(duplicateColumnMap.keySet()));

        int[] findColumns = {0};
        assertEquals(2, mapper.users(indexedResult(new String[]{"name", "age", "created"}, findColumns,
                new Object[]{"Ada", 36, null}, new Object[]{"Grace", 40, null})).size());
        assertEquals(3, findColumns[0]);

        int[] metadataCalls = {0};
        assertEquals(2, mapper.rows(cachedMetadataResult(new String[]{"b", "a"}, metadataCalls,
                new Object[]{2, 1}, new Object[]{4, 3})).size());
        assertEquals(1, metadataCalls[0]);
        assertEquals(List.of("b", "a"), new ArrayList<>(mapper.row(result(new String[]{"b", "a"},
                new Object[]{2, 1})).keySet()));
    }

    @Test
    void appliesCardinalityPoliciesAndWrapsSqlExceptions() {
        Mapper mapper = CompiledNodes.of(Mapper.class);

        BindingException multiple = assertThrows(BindingException.class, () -> mapper.user(
                result(new String[]{"full_name", "age", "created"},
                        new Object[]{"first", 1, null}, new Object[]{"second", 2, null})));
        assertEquals("Expected one JDBC row but found multiple", multiple.getMessage());
        assertEquals("first", mapper.first(result(new String[]{"name", "age", "created"},
                new Object[]{"first", 1, null}, new Object[]{"second", 2, null})).name);

        assertInstanceOf(SQLException.class,
                assertThrows(BindingException.class, () -> mapper.row(brokenResult())).getCause());
        assertInstanceOf(SQLException.class,
                assertThrows(BindingException.class, () -> mapper.users(brokenFindColumnResult())).getCause());
    }

    @CompiledJdbcMapper
    interface Mapper {
        @Mapping(target = "name", source = "full_name")
        User user(ResultSet rs);

        @Mapping(target = "value", source = "alias")
        Name name(ResultSet rs);

        @Mapping(target = "value", source = "alias")
        List<Name> names(ResultSet rs);

        UserRecord recordUser(ResultSet rs);

        List<User> users(ResultSet rs);

        List<Map<String, Object>> rows(ResultSet rs);

        @JdbcMapperOptions(duplicateColumn = DuplicateColumnPolicy.LAST_WINS)
        Map<String, Object> row(ResultSet rs);

        @JdbcMapperOptions(singleResult = SingleResultPolicy.FIRST)
        User first(ResultSet rs);
    }

    public static final class User {
        public String name;
        public int age;
        public Instant created;

        public User() {
        }
    }

    public record Name(String value) {
    }

    public record UserRecord(String name, int age) {
    }
}
