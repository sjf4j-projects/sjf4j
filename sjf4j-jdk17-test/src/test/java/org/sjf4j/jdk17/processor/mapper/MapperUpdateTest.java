package org.sjf4j.jdk17.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.mapper.CompiledMapper;
import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.annotation.mapper.MappingConfig;
import org.sjf4j.annotation.mapper.NullValuePolicy;
import org.sjf4j.compiled.CompiledRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MapperUpdateTest {

    @Test
    public void updatesSameNamePropertiesInPlace() {
        UpdateMapper mapper = CompiledRegistry.of(UpdateMapper.class);
        Target target = new Target();

        mapper.update(target, new Source("Ada", "Lovelace", 36));

        assertEquals("Ada", target.name);
        assertEquals("Lovelace", target.last);
        assertEquals(36, target.age);
    }

    @Test
    public void supportsRenameAndIgnore() {
        UpdateMapper mapper = CompiledRegistry.of(UpdateMapper.class);
        Target target = new Target();
        target.age = 99;

        mapper.rename(target, new Source("Ada", "Lovelace", 36));

        assertEquals("Lovelace", target.name);
        assertEquals(99, target.age);
    }

    @Test
    public void nullPolicyIgnoreSkipsNullValues() {
        UpdateMapper mapper = CompiledRegistry.of(UpdateMapper.class);
        Target target = new Target();
        target.name = "old";
        target.age = 7;

        mapper.ignoreNulls(target, new BoxedSource(null, 42));

        assertEquals("old", target.name);
        assertEquals(42, target.age);
    }

    @Test
    public void defaultNullPolicySetsNullValues() {
        UpdateMapper mapper = CompiledRegistry.of(UpdateMapper.class);
        Target target = new Target();
        target.name = "old";

        mapper.setNulls(target, new BoxedSource(null, 1));

        assertNull(target.name);
        assertEquals(1, target.age);
    }

    @Test
    public void updatesFromMultipleSourcesAndReturnsWhenAllSourcesNull() {
        UpdateMapper mapper = CompiledRegistry.of(UpdateMapper.class);
        Target target = new Target();
        target.name = "old";
        target.city = "old-city";

        mapper.multi(target, new Source("Ada", "Lovelace", 36), new Address("London"));
        assertEquals("Ada", target.name);
        assertEquals("London", target.city);

        mapper.multi(target, null, null);
        assertEquals("Ada", target.name);
        assertEquals("London", target.city);

        mapper.multi(target, null, new Address("Paris"));
        assertNull(target.name);
        assertEquals("Paris", target.city);
    }

    public record Source(String name, String last, int age) {}
    public record BoxedSource(String name, Integer age) {}
    public record Address(String city) {}

    public static final class Target {
        public String name;
        public String last;
        public int age;
        public String city;
    }

    @CompiledMapper
    public interface UpdateMapper {
        void update(Target target, Source source);

        @Mapping(target = "name", source = "last")
        @Mapping(target = "age", ignore = true)
        void rename(Target target, Source source);

        @MappingConfig(nulls = NullValuePolicy.IGNORE)
        void ignoreNulls(Target target, BoxedSource source);

        void setNulls(Target target, BoxedSource source);

        @Mapping(target = "name", source = "source:name")
        @Mapping(target = "city", source = "address:city")
        @Mapping(target = "age", ignore = true)
        void multi(Target target, Source source, Address address);
    }
}
