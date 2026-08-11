package org.sjf4j.jdk17;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.annotation.mapper.CompiledJdbcMapper;
import org.sjf4j.compiled.CompiledNodes;
import org.sjf4j.node.Nodes;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.springframework.jdbc.core.BeanPropertyRowMapper;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Measures ResultSet-to-object conversion only. Each invocation calls {@code executeQuery()} during
 * JMH invocation setup, not in the timed benchmark method; result traversal remains measured.
 * Closing occurs in invocation teardown, so SQL execution, parsing, executor flow, pooling, and
 * network costs are outside the measurement boundary. Map benchmarks are deliberately separate from
 * bean mapping. Spring, MyBatis, and H2 here are JMH-only comparison dependencies, not production
 * dependencies; MyBatis uses its internal {@link DefaultResultSetHandler} for that comparison.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
public class CompiledJdbcMapperBenchmark {

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(new String[]{CompiledJdbcMapperBenchmark.class.getSimpleName()});
    }

    private static final int ROW_COUNT = 1000;

    @State(Scope.Thread)
    public static class BenchmarkState {
        Connection connection;
        PreparedStatement select;
        JdbcMapper mapper;
        BeanPropertyRowMapper<User> springMapper;
        Configuration mybatisConfiguration;
        MappedStatement mybatisAutoStatement;
        MappedStatement mybatisExplicitStatement;
        ResultSet resultSet;
        DefaultResultSetHandler mybatisAutoHandler;
        DefaultResultSetHandler mybatisExplicitHandler;

        @Setup(Level.Trial)
        public void setup() throws SQLException {
            connection = DriverManager.getConnection("jdbc:h2:mem:compiledJdbcMapperBenchmark");
            try (Statement statement = connection.createStatement()) {
                statement.execute("create table users (id integer primary key, name varchar(64), balance decimal(19, 2))");
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "insert into users (id, name, balance) values (?, ?, ?)")) {
                for (int i = 0; i < ROW_COUNT; i++) {
                    insert.setInt(1, i);
                    insert.setString(2, "user-" + i);
                    insert.setBigDecimal(3, BigDecimal.valueOf(100000L + i, 2));
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            select = connection.prepareStatement("select id, name, balance from users order by id");
            mapper = CompiledNodes.of(JdbcMapper.class);
            springMapper = BeanPropertyRowMapper.newInstance(User.class);
            mybatisConfiguration = new Configuration();
            mybatisConfiguration.setMapUnderscoreToCamelCase(true);
            mybatisAutoStatement = mybatisStatement("mybatisAuto", Collections.<ResultMapping>emptyList());
            mybatisExplicitStatement = mybatisStatement("mybatisExplicit", List.of(
                    new ResultMapping.Builder(mybatisConfiguration, "id", "id", Integer.class).build(),
                    new ResultMapping.Builder(mybatisConfiguration, "name", "name", String.class).build(),
                    new ResultMapping.Builder(mybatisConfiguration, "balance", "balance", BigDecimal.class).build()));

            try (ResultSet resultSet = select.executeQuery()) {
                if (mapper.users(resultSet).size() != ROW_COUNT) {
                    throw new AssertionError("Unexpected benchmark row count");
                }
            }
            validateMybatisUsers("automatic", handle(mybatisAutoStatement));
            validateMybatisUsers("explicit", handle(mybatisExplicitStatement));
        }

        private MappedStatement mybatisStatement(String id, List<ResultMapping> mappings) {
            ResultMap resultMap = new ResultMap.Builder(mybatisConfiguration, id + "Map", User.class, mappings).build();
            return new MappedStatement.Builder(mybatisConfiguration, id,
                    new StaticSqlSource(mybatisConfiguration, "select id, name, balance from users order by id"), SqlCommandType.SELECT)
                    .resultMaps(Collections.singletonList(resultMap)).build();
        }

        private List<User> handle(MappedStatement statement) throws SQLException {
            try (ResultSet ignored = select.executeQuery()) {
                BoundSql boundSql = statement.getBoundSql(null);
                @SuppressWarnings("unchecked")
                List<User> users = (List<User>) (List<?>) new DefaultResultSetHandler(
                        null, statement, null, null, boundSql, RowBounds.DEFAULT)
                        .handleResultSets(select);
                return users;
            }
        }

        private static void validateMybatisUsers(String variant, List<?> users) {
            if (users.size() != ROW_COUNT) throw new AssertionError("Unexpected MyBatis " + variant + " row count");
            Object first = users.get(0);
            Object last = users.get(ROW_COUNT - 1);
            if (!(first instanceof User) || !(last instanceof User)) {
                throw new AssertionError("MyBatis " + variant + " mapping did not produce User instances");
            }
            User firstUser = (User) first;
            User lastUser = (User) last;
            if (firstUser.getId() != 0 || !"user-0".equals(firstUser.getName())
                    || !BigDecimal.valueOf(100000L, 2).equals(firstUser.getBalance())
                    || lastUser.getId() != ROW_COUNT - 1 || !("user-" + (ROW_COUNT - 1)).equals(lastUser.getName())
                    || !BigDecimal.valueOf(100000L + ROW_COUNT - 1, 2).equals(lastUser.getBalance())) {
                throw new AssertionError("Unexpected MyBatis " + variant + " User values");
            }
        }

    @Setup(Level.Invocation)
    public void openResultSet() throws SQLException {
            // Query execution is setup; every benchmark still traverses this fresh result set while timed.
            resultSet = select.executeQuery();
            mybatisAutoHandler = resultSetHandler(mybatisAutoStatement);
            mybatisExplicitHandler = resultSetHandler(mybatisExplicitStatement);
        }

        private DefaultResultSetHandler resultSetHandler(MappedStatement statement) {
            return new DefaultResultSetHandler(null, statement, null, null, statement.getBoundSql(null), RowBounds.DEFAULT);
        }

        @TearDown(Level.Invocation)
        public void closeResultSet() throws SQLException {
            if (resultSet != null && !resultSet.isClosed()) resultSet.close();
            resultSet = null;
            mybatisAutoHandler = null;
            mybatisExplicitHandler = null;
        }

        @TearDown(Level.Trial)
        public void tearDown() throws SQLException {
            if (select != null) select.close();
            if (connection != null) connection.close();
        }
    }

    @Benchmark
    public List<User> users_sjf4j_indexed(BenchmarkState state) throws SQLException {
        return state.mapper.users(state.resultSet);
    }

    @Benchmark
    public List<User> users_handwritten_labels(BenchmarkState state) throws SQLException {
        ArrayList<User> users = new ArrayList<User>();
        while (state.resultSet.next()) {
            User user = new User();
            user.setId(Nodes.toInt(state.resultSet.getObject("id")));
            user.setName(Nodes.toString(state.resultSet.getObject("name")));
            user.setBalance(Nodes.toBigDecimal(state.resultSet.getObject("balance")));
            users.add(user);
        }
        return users;
    }

    /** Ideal lower bound: indexes are hard-coded from the known query projection. */
    @Benchmark
    public List<User> users_handwritten_indexes_ideal_lower_bound(BenchmarkState state) throws SQLException {
        ArrayList<User> users = new ArrayList<User>();
        while (state.resultSet.next()) {
            User user = new User();
            user.setId(Nodes.toInt(state.resultSet.getObject(1)));
            user.setName(Nodes.toString(state.resultSet.getObject(2)));
            user.setBalance(Nodes.toBigDecimal(state.resultSet.getObject(3)));
            users.add(user);
        }
        return users;
    }

    /** Fair indexed baseline: resolve the current result set's labels once, then use indexes. */
    @Benchmark
    public List<User> users_handwritten_resolved_indexes(BenchmarkState state) throws SQLException {
        ArrayList<User> users = new ArrayList<User>();
        if (!state.resultSet.next()) return users;
        int id = state.resultSet.findColumn("id");
        int name = state.resultSet.findColumn("name");
        int balance = state.resultSet.findColumn("balance");
        do {
            User user = new User();
            user.setId(Nodes.toInt(state.resultSet.getObject(id)));
            user.setName(Nodes.toString(state.resultSet.getObject(name)));
            user.setBalance(Nodes.toBigDecimal(state.resultSet.getObject(balance)));
            users.add(user);
        } while (state.resultSet.next());
        return users;
    }

    @Benchmark
    public List<Map<String, Object>> maps_sjf4j_cached_metadata(BenchmarkState state) throws SQLException {
        return state.mapper.maps(state.resultSet);
    }

    @Benchmark
    public List<Map<String, Object>> maps_handwritten_labels(BenchmarkState state) throws SQLException {
        ArrayList<Map<String, Object>> maps = new ArrayList<Map<String, Object>>();
        while (state.resultSet.next()) {
            ResultSetMetaData metadata = state.resultSet.getMetaData();
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            for (int i = 1, count = metadata.getColumnCount(); i <= count; i++) {
                map.put(metadata.getColumnLabel(i), state.resultSet.getObject(i));
            }
            maps.add(map);
        }
        return maps;
    }

    @Benchmark
    public List<Map<String, Object>> maps_handwritten_cached_labels(BenchmarkState state) throws SQLException {
        ResultSetMetaData metadata = state.resultSet.getMetaData();
        int columnCount = metadata.getColumnCount();
        String[] labels = new String[columnCount];
        for (int i = 0; i < columnCount; i++) {
            labels[i] = metadata.getColumnLabel(i + 1);
        }
        ArrayList<Map<String, Object>> maps = new ArrayList<Map<String, Object>>();
        while (state.resultSet.next()) {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            for (int i = 0; i < columnCount; i++) {
                map.put(labels[i], state.resultSet.getObject(i + 1));
            }
            maps.add(map);
        }
        return maps;
    }

    /**
     * Spring's per-row bean conversion. The mapper is configured in trial setup; this loop only
     * invokes {@link BeanPropertyRowMapper#mapRow(ResultSet, int)} on the invocation ResultSet.
     */
    @Benchmark
    public List<User> users_spring_BeanPropertyRowMapper(BenchmarkState state) throws SQLException {
        ArrayList<User> users = new ArrayList<User>();
        int rowNum = 0;
        while (state.resultSet.next()) users.add(state.springMapper.mapRow(state.resultSet, rowNum++));
        return users;
    }

    /**
     * MyBatis's internal, benchmark-only result conversion pipeline with an empty ResultMap and
     * automatic mapping into the same {@link User} JavaBean as SJF and Spring.
     */
    @Benchmark
    public List<User> users_mybatis_DefaultResultSetHandler_auto(BenchmarkState state) throws SQLException {
        @SuppressWarnings("unchecked")
        List<User> users = (List<User>) (List<?>) state.mybatisAutoHandler.handleResultSets(state.select);
        return users;
    }

    /**
     * MyBatis's internal, benchmark-only result conversion pipeline with explicit id/name/balance
     * ResultMappings into the same {@link User} JavaBean as SJF and Spring, rather than the automatic mapping used by
     * {@link #users_mybatis_DefaultResultSetHandler_auto(BenchmarkState)}.
     */
    @Benchmark
    public List<User> users_mybatis_DefaultResultSetHandler_explicit(BenchmarkState state) throws SQLException {
        @SuppressWarnings("unchecked")
        List<User> users = (List<User>) (List<?>) state.mybatisExplicitHandler.handleResultSets(state.select);
        return users;
    }

    @CompiledJdbcMapper
    public interface JdbcMapper {
        List<User> users(ResultSet resultSet);

        List<Map<String, Object>> maps(ResultSet resultSet);
    }

    public static final class User {
        private int id;
        private String name;
        private BigDecimal balance;

        public User() {}

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public BigDecimal getBalance() { return balance; }
        public void setBalance(BigDecimal balance) { this.balance = balance; }
    }
}
