package org.sjf4j.testbench.processor.mapper.jdbc;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper;
import org.sjf4j.CompiledInstances;
import org.sjf4j.mapping.JdbcRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcSpringIntegrationTest {
    @Test
    void mapsRowsThroughJdbcTemplateRowMapper() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:jdbc-spring-integration;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("create table users (name varchar(100), age int)");
        jdbcTemplate.update("insert into users (name, age) values (?, ?)", "Ada", 36);
        jdbcTemplate.update("insert into users (name, age) values (?, ?)", "Grace", 40);

        Mapper mapper = CompiledInstances.create(Mapper.class);
        List<User> users = jdbcTemplate.query("select name, age from users order by age", rs -> {
            JdbcRowMapper<User> prepared = mapper.prepare(rs.getMetaData());
            List<User> out = new ArrayList<>();
            while (rs.next()) out.add(prepared.mapRow(rs));
            return out;
        });

        assertEquals(List.of(new User("Ada", 36), new User("Grace", 40)), users);
    }

    @CompiledJdbcMapper
    interface Mapper {
        JdbcRowMapper<User> prepare(ResultSetMetaData metadata);
    }

    public record User(String name, int age) {
    }
}
