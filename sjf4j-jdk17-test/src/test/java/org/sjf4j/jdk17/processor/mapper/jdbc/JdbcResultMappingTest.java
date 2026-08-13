package org.sjf4j.jdk17.processor.mapper.jdbc;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.mapper.jdbc.CompiledJdbcMapper;
import org.sjf4j.annotation.mapper.jdbc.ColumnProjectionPolicy;
import org.sjf4j.annotation.mapper.jdbc.JdbcMapperOptions;
import org.sjf4j.annotation.mapper.jdbc.SingleResultPolicy;
import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.compiled.CompiledNodes;
import org.sjf4j.exception.BindingException;
import org.sjf4j.JsonObject;

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
import static org.sjf4j.jdk17.processor.mapper.jdbc.JdbcTestSupport.currentRowResult;
import static org.sjf4j.jdk17.processor.mapper.jdbc.JdbcTestSupport.indexedResult;
import static org.sjf4j.jdk17.processor.mapper.jdbc.JdbcTestSupport.result;

class JdbcResultMappingTest {
    @Test
    void mapsBasicValuesRowsAndMetadata() {
        Mapper mapper = CompiledNodes.instanceOf(Mapper.class);
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
        assertEquals(Map.of("a", 2), mapper.rows(result(new String[]{"a", "a"},
                new Object[]{1, 2})).get(0));
        assertEquals(Map.of("a", 2, "b", 3), mapper.currentRow(currentRowResult(
                new String[]{"a", "a", "b"}, new int[]{0}, 1, 2, 3), 0));

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
        Mapper mapper = CompiledNodes.instanceOf(Mapper.class);

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

    @Test
    void mapsJojoPropertiesAndPreservesUnconsumedColumnsDynamically() {
        Mapper mapper = CompiledNodes.instanceOf(Mapper.class);

        Jojo one = mapper.jojo(result(new String[]{"full_name", "age", "extra", "extra"},
                new Object[]{"Ada", 36, "first", "last"}));
        assertEquals("Ada", one.name);
        assertEquals(36, one.age);
        assertEquals("last", one.getNode("extra"));
        assertEquals(null, one.getNode("full_name"));

        List<Jojo> list = mapper.jojos(result(new String[]{"full_name", "age", "extra"},
                new Object[]{"Ada", 36, "one"}, new Object[]{"Grace", 40, "two"}));
        assertEquals(2, list.size());
        assertEquals("two", list.get(1).getNode("extra"));

        int[] nextCalls = {0};
        Jojo current = mapper.currentJojo(currentRowResult(new String[]{"full_name", "age", "extra"}, nextCalls,
                "Katherine", 41, "current"), 7);
        assertEquals("Katherine", current.name);
        assertEquals("current", current.getNode("extra"));
        assertEquals(0, nextCalls[0]);
    }

    @Test
    void mapsOnlyPresentJojoPropertiesAndKeepsOtherColumnsDynamic() {
        Mapper mapper = CompiledNodes.instanceOf(Mapper.class);

        Jojo jojo = mapper.presentJojo(result(new String[]{"FULL_NAME", "extra", "full_name"},
                new Object[]{"Ada", "first", "Grace"}));

        assertEquals("Ada", jojo.name);
        assertEquals(7, jojo.age);
        assertEquals("first", jojo.getNode("extra"));
        assertEquals(null, jojo.getNode("FULL_NAME"));
        assertEquals(null, jojo.getNode("full_name"));
    }

    @Test
    void ignoredRenamedMappingConsumesItsJdbcColumn() {
        Mapper mapper = CompiledNodes.instanceOf(Mapper.class);

        Jojo jojo = mapper.ignoredJojo(result(new String[]{"full_name", "age", "extra"},
                new Object[]{"Ada", 36, "yes"}));

        assertEquals(null, jojo.name);
        assertEquals(36, jojo.age);
        assertEquals(null, jojo.getNode("full_name"));
        assertEquals("yes", jojo.getNode("extra"));
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

        Map<String, Object> row(ResultSet rs);

        Map<String, Object> currentRow(ResultSet rs, int rowNum);

        @JdbcMapperOptions(singleResult = SingleResultPolicy.FIRST)
        User first(ResultSet rs);

        @Mapping(target = "name", source = "full_name")
        Jojo jojo(ResultSet rs);

        @Mapping(target = "name", source = "full_name")
        List<Jojo> jojos(ResultSet rs);

        @Mapping(target = "name", source = "full_name")
        Jojo currentJojo(ResultSet rs, int rowNum);

        @Mapping(target = "name", source = "full_name")
        @JdbcMapperOptions(columnProjection = ColumnProjectionPolicy.PRESENT_ONLY)
        Jojo presentJojo(ResultSet rs);

        @Mapping(target = "name", source = "full_name", ignore = true)
        Jojo ignoredJojo(ResultSet rs);
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

    public static final class Jojo extends JsonObject {
        public String name;
        public int age = 7;

        public Jojo() {
        }
    }
}
