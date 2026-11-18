package org.sjf4j.spring;

import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.Read;
import org.sjf4j.annotation.mapping.CompiledMapper;
import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper;
import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.annotation.path.GetByPath;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

public final class SpringFixtures {

    private SpringFixtures() {}

    public static class User {
        public String name;

        public User() {}

        public User(String name) {
            this.name = name;
        }
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
    }

    @CompiledJdbcMapper
    public interface UserJdbcMapper {
        List<User> users(ResultSet resultSet);
    }

    @CompiledValidator
    public interface UserValidator {
        @ValidJsonSchema(value = "{\"type\":\"object\"}")
        boolean valid(Object source);
    }
}
