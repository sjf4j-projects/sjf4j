package org.sjf4j.spring.boot;

import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.Read;
import org.sjf4j.annotation.binding.Write;
import org.sjf4j.annotation.mapping.CompiledMapper;
import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper;
import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.annotation.path.GetByPath;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.mapping.JdbcRowMapper;

import java.sql.ResultSetMetaData;
import java.util.Map;

public final class CompiledFixtures {

    private CompiledFixtures() {}

    public static class User {
        public String name;
        public User() {}
        public User(String name) { this.name = name; }
    }

    public static class UserDto {
        public String name;
    }

    @CompiledMapper
    public interface UserMapper {
        UserDto toDto(User source);
    }

    @CompiledNavigator
    public interface UserNavigator {
        @GetByPath("$.name")
        String name(Map<String, String> source);
    }

    @CompiledBinder(backend = Backend.SIMPLE)
    public interface UserBinder {
        @Read User read(String json);
        @Write String write(User user);
    }

    @CompiledJdbcMapper
    public interface UserJdbcMapper {
        JdbcRowMapper<User> prepare(ResultSetMetaData metadata);
    }

    @CompiledValidator
    public interface UserValidator {
        @ValidJsonSchema(value = "{\"type\":\"object\"}")
        boolean valid(Object source);
    }
}
