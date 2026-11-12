package org.sjf4j.testbench.processor.mapper.jdbc;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.mapping.Mapping;
import org.sjf4j.annotation.mapping.jdbc.ColumnNamingPolicy;
import org.sjf4j.annotation.mapping.jdbc.ColumnProjectionPolicy;
import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper;
import org.sjf4j.annotation.mapping.jdbc.JdbcMappingOptions;
import org.sjf4j.exception.BindingException;
import org.sjf4j.internal.jdbc.JdbcColumnResolver;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sjf4j.testbench.processor.mapper.jdbc.JdbcTestSupport.currentRowResult;
import static org.sjf4j.testbench.processor.mapper.jdbc.JdbcTestSupport.result;

class JdbcColumnNamingTest {
    private final Mapper mapper = CompiledInstances.create(Mapper.class);

    @Test
    void emptySingleResultDoesNotResolveMissingOrAmbiguousColumns() {
        // Empty single-target methods return null without reading column metadata.
        assertNull(mapper.auto(result(new String[]{"unrelated"})));
        assertNull(mapper.auto(result(new String[]{"USERNAME", "UserName", "login_count"})));
        assertNull(mapper.exact(result(new String[]{"user_name"})));
        assertNull(mapper.record(result(new String[]{"unrelated"})));
        assertNull(mapper.dynamic(result(new String[]{"unrelated"})));
        assertNull(mapper.map(result(new String[]{"unrelated"})));
    }

    @Test
    void exactIsCaseSensitive() {
        Bean bean = mapper.exact(result(new String[]{"userName", "loginCount"}, new Object[]{"Ada", 3}));
        assertEquals("Ada", bean.userName);
        assertEquals(3, bean.loginCount);

        assertThrows(BindingException.class, () -> mapper.exact(
                result(new String[]{"USERNAME", "loginCount"}, new Object[]{"Ada", 3})));
    }

    @Test
    void insensitiveMatchesCaseButNotUnderscores() {
        Bean bean = mapper.insensitive(
                result(new String[]{"USERNAME", "LOGINCOUNT"}, new Object[]{"Ada", 3}));
        assertEquals("Ada", bean.userName);
        assertEquals(3, bean.loginCount);

        assertThrows(BindingException.class, () -> mapper.insensitive(
                result(new String[]{"user_name", "login_count"}, new Object[]{"Ada", 3})));
    }

    @Test
    void snakeMatchesBeanRecordAndListByDefault() {
        String[] columns = {"USER_NAME", "login_count"};
        Bean bean = mapper.auto(result(columns, new Object[]{"Ada", 3}));
        assertEquals("Ada", bean.userName);
        assertEquals(3, bean.loginCount);

        UserRecord record = mapper.record(result(columns, new Object[]{"Ada", 3}));
        assertEquals("Ada", record.userName());
        assertEquals(3, record.loginCount());

        List<Bean> beans = mapper.beans(result(columns,
                new Object[]{"Ada", 3}, new Object[]{"Grace", 4}));
        assertEquals(2, beans.size());
        assertEquals("Grace", beans.get(1).userName);
    }

    @Test
    void exactThenInsensitiveThenSnakePriority() {
        Bean exact = mapper.auto(result(new String[]{"user_name", "USERNAME", "userName", "login_count"},
                new Object[]{"snake", "insensitive", "exact", 3}));
        assertEquals("exact", exact.userName);

        Bean insensitive = mapper.auto(result(new String[]{"user_name", "USERNAME", "login_count"},
                new Object[]{"snake", "insensitive", 3}));
        assertEquals("insensitive", insensitive.userName);
    }

    @Test
    void ambiguityIsRejectedWithinMatchingTier() {
        BindingException error = assertThrows(BindingException.class, () -> mapper.auto(
                result(new String[]{"USERNAME", "UserName", "login_count"},
                        new Object[]{"first", "second", 3})));
        assertTrue(error.getMessage().contains("Ambiguous JDBC column"), error.getMessage());

        assertThrows(BindingException.class, () -> mapper.auto(
                result(new String[]{"USER_NAME", "user_name", "login_count"},
                        new Object[]{"first", "second", 3})));
    }

    @Test
    void explicitSourceOverridesAutomaticNaming() {
        Bean bean = mapper.renamed(result(new String[]{"account_name", "login_count"},
                new Object[]{"Ada", 3}));
        assertEquals("Ada", bean.userName);
        assertEquals(3, bean.loginCount);
    }

    @Test
    void currentRowReusesBindingWithoutAdvancingCursor() {
        int[] nextCalls = {0};
        ResultSet row = currentRowResult(new String[]{"user_name", "login_count"},
                nextCalls, "Ada", 3);
        assertEquals("Ada", mapper.row(row, 0).userName);
        assertEquals(3, mapper.row(row, 1).loginCount);
        assertEquals(0, nextCalls[0]);
    }

    @Test
    void presentOnlyLeavesMissingPropertyUnchanged() {
        Bean bean = mapper.partial(result(new String[]{"USER_NAME"}, new Object[]{"Ada"}));
        assertEquals("Ada", bean.userName);
        assertEquals(0, bean.loginCount);

        assertThrows(BindingException.class, () -> mapper.auto(
                result(new String[]{"USER_NAME"}, new Object[]{"Ada"})));
    }

    @Test
    void jojoConsumesAutomaticallyMappedColumnAndPreservesExtra() {
        Dynamic bean = mapper.dynamic(result(new String[]{"user_name", "extra"},
                new Object[]{"Ada", "payload"}));
        assertEquals("Ada", bean.userName);
        assertNull(bean.getNode("user_name"));
        assertEquals("payload", bean.getNode("extra"));
    }

    @Test
    void snakePreservesWordBoundariesAndAcronyms() {
        try {
            assertEquals(1, JdbcColumnResolver.resolve(
                    JdbcTestSupport.metadata(new String[]{"url_value"}),
                    new String[]{"URLValue"}, ColumnNamingPolicy.INSENSITIVE_SNAKE, true)[0]);
        } catch (java.sql.SQLException e) {
            throw new AssertionError(e);
        }
        assertThrows(BindingException.class, () -> mapper.lowercase(
                result(new String[]{"user_name"}, new Object[]{"Ada"})));
    }

    @Test
    void currentRowCacheBindsEachResultSetOnlyOnce() {
        int[] metadataCalls = {0};
        ResultSet rs = (ResultSet) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{ResultSet.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getMetaData")) {
                        metadataCalls[0]++;
                        return JdbcTestSupport.metadata(new String[]{"user_name", "login_count"});
                    }
                    if (method.getName().equals("getString")) return "Ada";
                    if (method.getName().equals("getInt")) return 3;
                    if (method.getName().equals("wasNull")) return false;
                    if (method.getName().equals("next")) {
                        throw new AssertionError("current-row mapper must not move the cursor");
                    }
                    return null;
                });
        assertEquals("Ada", mapper.row(rs, 0).userName);
        assertEquals("Ada", mapper.row(rs, 1).userName);
        assertEquals(1, metadataCalls[0]);
    }

    @Test
    void mapKeysRemainUntouched() {
        assertEquals(Map.of("USER_NAME", "Ada"), mapper.map(
                result(new String[]{"USER_NAME"}, new Object[]{"Ada"})));
    }

    @CompiledJdbcMapper
    interface Mapper {
        @JdbcMappingOptions(columnNaming = ColumnNamingPolicy.EXACT)
        Bean exact(ResultSet rs);

        @JdbcMappingOptions(columnNaming = ColumnNamingPolicy.INSENSITIVE)
        Bean insensitive(ResultSet rs);

        Bean auto(ResultSet rs);

        UserRecord record(ResultSet rs);

        List<Bean> beans(ResultSet rs);

        @Mapping(target = "userName", source = "account_name")
        Bean renamed(ResultSet rs);

        Bean row(ResultSet rs, int rowNum);

        @JdbcMappingOptions(columnProjection = ColumnProjectionPolicy.PRESENT_ONLY)
        Bean partial(ResultSet rs);

        Dynamic dynamic(ResultSet rs);

        Lowercase lowercase(ResultSet rs);

        Map<String, Object> map(ResultSet rs);
    }

    public static class Bean {
        public String userName;
        public int loginCount;
    }

    public record UserRecord(String userName, int loginCount) {
    }

    public static class Dynamic extends JsonObject {
        public String userName;
    }

    public static class Lowercase {
        public String username;
    }
}
