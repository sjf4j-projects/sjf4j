package org.sjf4j.spring.boot;

import org.junit.jupiter.api.Test;
import org.sjf4j.mapping.JdbcRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = Sjf4jSpringJdbcTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class Sjf4jSpringJdbcTest {

    @Autowired
    private CompiledFixtures.UserJdbcMapper userJdbcMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void autowiredCompiledMapperWorksWithJdbcTemplate() {
        jdbcTemplate.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(80))");
        jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 1, "Alice");
        jdbcTemplate.update("INSERT INTO users (id, name) VALUES (?, ?)", 2, "Bob");

        String sql = "SELECT name FROM users ORDER BY id";

        // Compiled JDBC mapper can be injected and used directly.
        List<CompiledFixtures.User> users = jdbcTemplate.query(sql, userJdbcMapper::users);
        assertThat(users).extracting(user -> user.name).containsExactly("Alice", "Bob");

        // Or prepare column indexes once and map each row without repeated lookup.
        List<CompiledFixtures.User> prepared = jdbcTemplate.query(sql, rs -> {
            JdbcRowMapper<CompiledFixtures.User> rowMapper =
                    userJdbcMapper.prepare(rs.getMetaData());
            List<CompiledFixtures.User> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(rowMapper.map(rs));
            }
            return rows;
        });
        assertThat(prepared).extracting(user -> user.name).containsExactly("Alice", "Bob");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
