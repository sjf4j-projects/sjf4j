package org.sjf4j.spring.boot;

import org.junit.jupiter.api.Test;
import org.sjf4j.mapping.JdbcRowMapper;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Sjf4jSpringJdbcTest {

    @Test
    void jdbcTemplateUsesCompiledJdbcMapperWithoutAnAdapter() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(Sjf4jAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CompiledFixtures.UserJdbcMapper.class);

                    DriverManagerDataSource dataSource = new DriverManagerDataSource(
                            "jdbc:h2:mem:sjf4j_jdbc_integration;DB_CLOSE_DELAY=-1", "sa", "");
                    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                    jdbc.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(80))");
                    jdbc.update("INSERT INTO users (id, name) VALUES (?, ?)", 1, "Alice");
                    jdbc.update("INSERT INTO users (id, name) VALUES (?, ?)", 2, "Bob");

                    CompiledFixtures.UserJdbcMapper mapper =
                            context.getBean(CompiledFixtures.UserJdbcMapper.class);
                    String query = "SELECT name FROM users ORDER BY id";

                    // The cursor-consuming mapper directly serves as ResultSetExtractor.
                    List<CompiledFixtures.User> users = jdbc.query(query,
                            (ResultSetExtractor<List<CompiledFixtures.User>>) mapper::users);
                    assertThat(users).extracting(user -> user.name)
                            .containsExactly("Alice", "Bob");

                    // Resolve columns once per ResultSet, not once per row.
                    List<CompiledFixtures.User> prepared = jdbc.query(query,
                            (ResultSetExtractor<List<CompiledFixtures.User>>) rs -> {
                                JdbcRowMapper<CompiledFixtures.User> rowMapper =
                                        mapper.prepare(rs.getMetaData());
                                List<CompiledFixtures.User> rows = new ArrayList<>();
                                while (rs.next()) {
                                    rows.add(rowMapper.map(rs));
                                }
                                return rows;
                            });
                    assertThat(prepared).extracting(user -> user.name)
                            .containsExactly("Alice", "Bob");
                });
    }
}
