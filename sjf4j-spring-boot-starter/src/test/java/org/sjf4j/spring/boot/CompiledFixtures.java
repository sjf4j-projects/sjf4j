package org.sjf4j.spring.boot;

import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.Read;
import org.sjf4j.annotation.binding.Write;
import org.sjf4j.annotation.mapping.CompiledMapper;
import org.sjf4j.annotation.mapping.Mapping;
import org.springframework.core.convert.converter.Converter;
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

    public static class Car {
        public String model;
        public int seatConfiguration;

        public Car() {}

        public Car(String model, int seatConfiguration) {
            this.model = model;
            this.seatConfiguration = seatConfiguration;
        }
    }

    public static class CarDto {
        public String model;
        public int seats;
    }

    @CompiledMapper
    public interface CarMapper extends Converter<Car, CarDto> {
        @Override
        @Mapping(target = "seats", source = "seatConfiguration")
        CarDto convert(Car car);
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
