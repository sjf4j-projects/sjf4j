package org.sjf4j.testbench.mapping;

import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.mapping.Mapping;
import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapperResultSetExtractor;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Isolates JDBC ResultSet traversal and mapping from query execution. The result set is
 * opened during invocation setup and closed during teardown. Each benchmark consumes an
 * equivalent result set into a List. MyBatis Cursor methods materialize their cursor into
 * a List and are NOT direct per-row mapper comparisons.
 *
 * H2 is an in-memory ResultSet provider, not a database throughput benchmark.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Threads(1)
@Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
public class JdbcFullBenchmark {
    private static final LocalDateTime EPOCH = LocalDateTime.of(2024, 1, 1, 0, 0);
    private static final String QUERY = "select id as \"id\", user_name as \"user_name\", "
            + "email as \"email\", phone as \"phone\", age as \"age\", active as \"active\", "
            + "balance as \"balance\", login_count as \"login_count\", score as \"score\", "
            + "city as \"city\", created_at as \"created_at\", updated_at as \"updated_at\" "
            + "from accounts where id < ? order by id";

    @State(Scope.Thread)
    public static class Data {
        @Param({"1", "10", "1000", "100000"})
        public int rows;

        private Connection connection;
        private PreparedStatement select;
        private ResultSet resultSet;
        private AccountMapper compiled;
        private BeanPropertyRowMapper<AccountBean> beanRowMapper;
        private DataClassRowMapper<AccountRecord> recordRowMapper;
        private ColumnMapRowMapper mapRowMapper;
        private RowMapperResultSetExtractor<AccountBean> beanExtractor;
        private RowMapperResultSetExtractor<AccountRecord> recordExtractor;
        private RowMapperResultSetExtractor<Map<String, Object>> mapExtractor;
        private Configuration mybatisConfig;
        private MappedStatement beanAuto;
        private MappedStatement beanExplicit;
        private MappedStatement mapExplicit;
        private DefaultResultSetHandler autoHandler;
        private DefaultResultSetHandler explicitHandler;
        private DefaultResultSetHandler mapHandler;

        @Setup(Level.Trial)
        public void setup() throws SQLException {
            connection = DriverManager.getConnection("jdbc:h2:mem:jdbcBenchmark_" + java.util.UUID.randomUUID());
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("create table accounts (id bigint primary key, user_name varchar(80), "
                        + "email varchar(120), phone varchar(32), age integer, active boolean, "
                        + "balance decimal(19,2), login_count integer, score double, "
                        + "city varchar(80), created_at timestamp, updated_at timestamp)");
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "insert into accounts values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (int i = 0; i < rows; i++) {
                    insert.setLong(1, i);
                    insert.setString(2, "user-" + i);
                    insert.setString(3, "user" + i + "@example.org");
                    insert.setString(4, "555-" + i);
                    insert.setInt(5, 18 + i % 60);
                    insert.setBoolean(6, i % 2 == 0);
                    insert.setBigDecimal(7, BigDecimal.valueOf(100000L + i, 2));
                    insert.setInt(8, i % 500);
                    insert.setDouble(9, i * 0.25);
                    insert.setString(10, "city-" + i % 50);
                    insert.setObject(11, EPOCH.plusSeconds(i));
                    insert.setObject(12, EPOCH.plusSeconds(i + 3600L));
                    insert.addBatch();
                    if ((i + 1) % 1000 == 0) insert.executeBatch();
                }
                insert.executeBatch();
            }
            select = connection.prepareStatement(QUERY);
            select.setInt(1, rows);
            compiled = CompiledInstances.create(AccountMapper.class);
            beanRowMapper = BeanPropertyRowMapper.newInstance(AccountBean.class);
            recordRowMapper = DataClassRowMapper.newInstance(AccountRecord.class);
            mapRowMapper = new ColumnMapRowMapper();
            beanExtractor = new RowMapperResultSetExtractor<>(beanRowMapper);
            recordExtractor = new RowMapperResultSetExtractor<>(recordRowMapper);
            mapExtractor = new RowMapperResultSetExtractor<>(mapRowMapper);

            mybatisConfig = new Configuration();
            mybatisConfig.setMapUnderscoreToCamelCase(true);
            beanAuto = statement("beanAuto", AccountBean.class, List.of());
            beanExplicit = statement("beanExplicit", AccountBean.class, mappings(true));
            mapExplicit = statement("mapExplicit", Map.class, mappings(false));
            checkEquivalence();
        }

        private List<ResultMapping> mappings(boolean camelNames) {
            String[] columns = {"id", "user_name", "email", "phone", "age", "active",
                    "balance", "login_count", "score", "city", "created_at", "updated_at"};
            String[] properties = {"id", "userName", "email", "phone", "age", "active",
                    "balance", "loginCount", "score", "city", "createdAt", "updatedAt"};
            Class<?>[] types = {long.class, String.class, String.class, String.class,
                    int.class, boolean.class, BigDecimal.class, int.class, double.class,
                    String.class, camelNames ? LocalDateTime.class : java.sql.Timestamp.class,
                    camelNames ? LocalDateTime.class : java.sql.Timestamp.class};
            List<ResultMapping> mappings = new ArrayList<>();
            for (int i = 0; i < columns.length; i++) {
                mappings.add(new ResultMapping.Builder(mybatisConfig,
                        camelNames ? properties[i] : columns[i], columns[i], types[i]).build());
            }
            return mappings;
        }

        private MappedStatement statement(String name, Class<?> type, List<ResultMapping> mappings) {
            ResultMap resultMap = new ResultMap.Builder(mybatisConfig, name + "Map", type, mappings).build();
            return new MappedStatement.Builder(mybatisConfig, name,
                    new StaticSqlSource(mybatisConfig, QUERY), SqlCommandType.SELECT)
                    .resultMaps(List.of(resultMap)).build();
        }

        @Setup(Level.Invocation)
        public void open() throws SQLException {
            resultSet = select.executeQuery();
            autoHandler = handler(beanAuto);
            explicitHandler = handler(beanExplicit);
            mapHandler = handler(mapExplicit);
        }

        private DefaultResultSetHandler handler(MappedStatement statement) {
            return new DefaultResultSetHandler(null, statement, null, null,
                    statement.getBoundSql(null), RowBounds.DEFAULT);
        }

        @TearDown(Level.Invocation)
        public void close() throws SQLException {
            if (resultSet != null && !resultSet.isClosed()) resultSet.close();
            resultSet = null;
            autoHandler = null;
            explicitHandler = null;
            mapHandler = null;
        }

        @TearDown(Level.Trial)
        public void finish() throws SQLException {
            if (select != null) select.close();
            if (connection != null) connection.close();
        }

        private void checkEquivalence() throws SQLException {
            // The same full result (including row count and first/last values) is required
            // across list implementations before timed measurements are admitted.
            JdbcFullBenchmark bench = new JdbcFullBenchmark();
            verifyBeans(invoke(bench, 0));
            verifyBeans(invoke(bench, 1));
            verifyBeans(invoke(bench, 2));
            verifyBeans(invoke(bench, 3));
            verifyBeans(invoke(bench, 4));
            verifyBeans(invoke(bench, 5));
            verifyBeans(invoke(bench, 6));
            verifyRecords(invoke(bench, 7));
            verifyRecords(invoke(bench, 8));
            verifyRecords(invoke(bench, 9));
            verifyRecords(invoke(bench, 10));
            verifyMaps(invoke(bench, 11));
            verifyMaps(invoke(bench, 12));
            verifyMaps(invoke(bench, 13));
            verifyMaps(invoke(bench, 14));
            verifyMaps(invoke(bench, 15));
            verifyMaps(invoke(bench, 16));
        }

        private List<?> invoke(JdbcFullBenchmark bench, int index) throws SQLException {
            open();
            try {
                switch (index) {
                    case 0: return bench.pojo_list_compiled(this);
                    case 1: return bench.pojo_list_spring(this);
                    case 2: return bench.pojo_list_mybatisAuto(this);
                    case 3: return bench.pojo_list_mybatisExplicit(this);
                    case 4: return bench.pojo_list_mybatisCursor(this);
                    case 5: return bench.pojo_row_compiled(this);
                    case 6: return bench.pojo_row_spring(this);
                    case 7: return bench.record_list_compiled(this);
                    case 8: return bench.record_list_spring(this);
                    case 9: return bench.record_row_compiled(this);
                    case 10: return bench.record_row_spring(this);
                    case 11: return bench.map_list_compiled(this);
                    case 12: return bench.map_list_spring(this);
                    case 13: return bench.map_list_mybatisExplicit(this);
                    case 14: return bench.map_list_mybatisCursor(this);
                    case 15: return bench.map_row_compiled(this);
                    case 16: return bench.map_row_spring(this);
                    default: throw new AssertionError(index);
                }
            } finally {
                close();
            }
        }

        private void verifyBeans(List<?> result) {
            requireSize(result);
            checkBean((AccountBean) result.get(0), 0);
            checkBean((AccountBean) result.get(rows - 1), rows - 1);
        }

        private void checkBean(AccountBean b, int i) {
            if (b.id != i || !("user-" + i).equals(b.userName)
                    || !("user" + i + "@example.org").equals(b.email)
                    || !("555-" + i).equals(b.phone)
                    || b.age != 18 + i % 60 || b.active != (i % 2 == 0)
                    || !BigDecimal.valueOf(100000L + i, 2).equals(b.balance)
                    || b.loginCount != i % 500 || b.score != i * 0.25
                    || !("city-" + i % 50).equals(b.city)
                    || !EPOCH.plusSeconds(i).equals(b.createdAt)
                    || !EPOCH.plusSeconds(i + 3600L).equals(b.updatedAt)) {
                throw new AssertionError("Bean mismatch at row " + i);
            }
        }

        private void verifyRecords(List<?> result) {
            requireSize(result);
            checkRecord((AccountRecord) result.get(0), 0);
            checkRecord((AccountRecord) result.get(rows - 1), rows - 1);
        }

        private void checkRecord(AccountRecord r, int i) {
            if (r.id() != i || !("user-" + i).equals(r.userName())
                    || !("user" + i + "@example.org").equals(r.email())
                    || !("555-" + i).equals(r.phone())
                    || r.age() != 18 + i % 60 || r.active() != (i % 2 == 0)
                    || !BigDecimal.valueOf(100000L + i, 2).equals(r.balance())
                    || r.loginCount() != i % 500 || r.score() != i * 0.25
                    || !("city-" + i % 50).equals(r.city())
                    || !EPOCH.plusSeconds(i).equals(r.createdAt())
                    || !EPOCH.plusSeconds(i + 3600L).equals(r.updatedAt())) {
                throw new AssertionError("Record mismatch at row " + i);
            }
        }

        private void verifyMaps(List<?> result) {
            requireSize(result);
            checkMap((Map<?, ?>) result.get(0), 0);
            checkMap((Map<?, ?>) result.get(rows - 1), rows - 1);
        }

        private void checkMap(Map<?, ?> m, int i) {
            if (m.size() != 12
                    || ((Number) m.get("id")).longValue() != i
                    || !("user-" + i).equals(m.get("user_name"))
                    || !("user" + i + "@example.org").equals(m.get("email"))
                    || !("555-" + i).equals(m.get("phone"))
                    || ((Number) m.get("age")).intValue() != 18 + i % 60
                    || !Boolean.valueOf(i % 2 == 0).equals(m.get("active"))
                    || !BigDecimal.valueOf(100000L + i, 2).equals(m.get("balance"))
                    || ((Number) m.get("login_count")).intValue() != i % 500
                    || ((Number) m.get("score")).doubleValue() != i * 0.25
                    || !("city-" + i % 50).equals(m.get("city"))
                    || !EPOCH.plusSeconds(i).equals(asLocalDateTime(m.get("created_at")))
                    || !EPOCH.plusSeconds(i + 3600L).equals(asLocalDateTime(m.get("updated_at")))) {
                throw new AssertionError("Map mismatch at row " + i + ": " + m);
            }
        }

        private LocalDateTime asLocalDateTime(Object value) {
            if (value instanceof java.sql.Timestamp) return ((java.sql.Timestamp) value).toLocalDateTime();
            if (value instanceof LocalDateTime) return (LocalDateTime) value;
            throw new AssertionError("Unexpected JDBC timestamp type: " + value);
        }

        private void requireSize(List<?> result) {
            if (result.size() != rows) {
                throw new AssertionError("Expected " + rows + " rows, got " + result.size());
            }
        }
    }

    @Benchmark
    public List<AccountBean> pojo_list_compiled(Data s) throws SQLException {
        return s.compiled.beans(s.resultSet);
    }

    @Benchmark
    public List<AccountBean> pojo_list_spring(Data s) throws SQLException {
        return s.beanExtractor.extractData(s.resultSet);
    }

    @Benchmark
    @SuppressWarnings("unchecked")
    public List<AccountBean> pojo_list_mybatisAuto(Data s) throws SQLException {
        return (List<AccountBean>) (List<?>) s.autoHandler.handleResultSets(s.select);
    }

    @Benchmark
    @SuppressWarnings("unchecked")
    public List<AccountBean> pojo_list_mybatisExplicit(Data s) throws SQLException {
        return (List<AccountBean>) (List<?>) s.explicitHandler.handleResultSets(s.select);
    }

    /** Streaming Cursor converted to List; unlike a direct RowMapper this uses MyBatis's pipeline. */
    @Benchmark
    public List<AccountBean> pojo_list_mybatisCursor(Data s) throws SQLException {
        ArrayList<AccountBean> out = new ArrayList<>();
        try (Cursor<AccountBean> cursor = s.autoHandler.handleCursorResultSets(s.select)) {
            for (AccountBean bean : cursor) out.add(bean);
        } catch (java.io.IOException e) {
            throw new SQLException("Failed to close MyBatis cursor", e);
        }
        return out;
    }

    @Benchmark
    public List<AccountBean> pojo_row_compiled(Data s) throws SQLException {
        List<AccountBean> out = new ArrayList<>();
        int rowNum = 0;
        while (s.resultSet.next()) out.add(s.compiled.beanRow(s.resultSet, rowNum++));
        return out;
    }

    @Benchmark
    public List<AccountBean> pojo_row_spring(Data s) throws SQLException {
        List<AccountBean> out = new ArrayList<>();
        int rowNum = 0;
        while (s.resultSet.next()) out.add(s.beanRowMapper.mapRow(s.resultSet, rowNum++));
        return out;
    }

    @Benchmark
    public List<AccountRecord> record_list_compiled(Data s) throws SQLException {
        return s.compiled.records(s.resultSet);
    }

    @Benchmark
    public List<AccountRecord> record_list_spring(Data s) throws SQLException {
        return s.recordExtractor.extractData(s.resultSet);
    }

    @Benchmark
    public List<AccountRecord> record_row_compiled(Data s) throws SQLException {
        List<AccountRecord> out = new ArrayList<>();
        int rowNum = 0;
        while (s.resultSet.next()) out.add(s.compiled.recordRow(s.resultSet, rowNum++));
        return out;
    }

    @Benchmark
    public List<AccountRecord> record_row_spring(Data s) throws SQLException {
        List<AccountRecord> out = new ArrayList<>();
        int rowNum = 0;
        while (s.resultSet.next()) out.add(s.recordRowMapper.mapRow(s.resultSet, rowNum++));
        return out;
    }

    @Benchmark
    public List<Map<String, Object>> map_list_compiled(Data s) throws SQLException {
        return s.compiled.maps(s.resultSet);
    }

    @Benchmark
    public List<Map<String, Object>> map_list_spring(Data s) throws SQLException {
        return s.mapExtractor.extractData(s.resultSet);
    }

    @Benchmark
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> map_list_mybatisExplicit(Data s) throws SQLException {
        return (List<Map<String, Object>>) (List<?>) s.mapHandler.handleResultSets(s.select);
    }

    @Benchmark
    public List<Map<String, Object>> map_list_mybatisCursor(Data s) throws SQLException {
        ArrayList<Map<String, Object>> out = new ArrayList<>();
        try (Cursor<Map<String, Object>> cursor = s.mapHandler.handleCursorResultSets(s.select)) {
            for (Map<String, Object> row : cursor) out.add(row);
        } catch (java.io.IOException e) {
            throw new SQLException("Failed to close MyBatis cursor", e);
        }
        return out;
    }

    @Benchmark
    public List<Map<String, Object>> map_row_compiled(Data s) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        int rowNum = 0;
        while (s.resultSet.next()) out.add(s.compiled.mapRow(s.resultSet, rowNum++));
        return out;
    }

    @Benchmark
    public List<Map<String, Object>> map_row_spring(Data s) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        int rowNum = 0;
        while (s.resultSet.next()) out.add(s.mapRowMapper.mapRow(s.resultSet, rowNum++));
        return out;
    }

    @CompiledJdbcMapper
    public interface AccountMapper {
        List<AccountBean> beans(ResultSet rs);

        AccountBean beanRow(ResultSet rs, int rowNum);

        List<AccountRecord> records(ResultSet rs);

        AccountRecord recordRow(ResultSet rs, int rowNum);

        List<Map<String, Object>> maps(ResultSet rs);

        Map<String, Object> mapRow(ResultSet rs, int rowNum);
    }

    public static final class AccountBean {
        private long id;
        private String userName;
        private String email;
        private String phone;
        private int age;
        private boolean active;
        private BigDecimal balance;
        private int loginCount;
        private double score;
        private String city;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public AccountBean() {}
        public void setId(long v) { id = v; }
        public void setUserName(String v) { userName = v; }
        public void setEmail(String v) { email = v; }
        public void setPhone(String v) { phone = v; }
        public void setAge(int v) { age = v; }
        public void setActive(boolean v) { active = v; }
        public void setBalance(BigDecimal v) { balance = v; }
        public void setLoginCount(int v) { loginCount = v; }
        public void setScore(double v) { score = v; }
        public void setCity(String v) { city = v; }
        public void setCreatedAt(LocalDateTime v) { createdAt = v; }
        public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
    }

    public record AccountRecord(
            long id, String userName, String email, String phone, int age, boolean active,
            BigDecimal balance, int loginCount, double score, String city,
            LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
