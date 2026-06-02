package org.sjf4j.jdk17.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.mapper.CompiledMapper;
import org.sjf4j.annotation.mapper.EnsureMapping;
import org.sjf4j.annotation.mapper.Mapping;
import org.sjf4j.annotation.mapper.MappingIfParentPresent;
import org.sjf4j.compiled.CompiledNodes;
import org.sjf4j.exception.JsonException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MapperTargetPathTest {

    @Test
    public void strictTargetPathWritesWhenParentExists() {
        TargetPathMapper mapper = CompiledNodes.of(TargetPathMapper.class);

        InitializedTarget jsonPath = mapper.strictCreate(new Source("Ada", "X"));
        InitializedTarget pointer = mapper.pointerCreate(new Source("Grace", "Y"));

        assertEquals("Ada", jsonPath.profile.name);
        assertEquals("Grace", pointer.profile.name);
    }

    @Test
    public void strictTargetPathFailsWhenParentMissing() {
        TargetPathMapper mapper = CompiledNodes.of(TargetPathMapper.class);
        MissingTarget target = new MissingTarget();

        assertThrows(JsonException.class, () -> mapper.strictUpdate(target, new Source("Ada", "X")));
        assertNull(target.profile);

        target.profile = new Profile();
        mapper.strictUpdate(target, new Source("Ada", "X"));
        assertEquals("Ada", target.profile.name);
    }

    @Test
    public void ensureTargetPathCreatesParentsForCreateAndUpdate() {
        TargetPathMapper mapper = CompiledNodes.of(TargetPathMapper.class);

        MissingTarget created = mapper.ensureCreate(new Source("Ada", "X"));
        assertEquals("Ada", created.profile.name);

        MissingTarget updated = new MissingTarget();
        mapper.ensureUpdate(updated, new Source("Grace", "Y"));
        assertEquals("Grace", updated.profile.name);
    }

    @Test
    public void ifParentPresentSkipsOrWrites() {
        TargetPathMapper mapper = CompiledNodes.of(TargetPathMapper.class);
        MissingTarget target = new MissingTarget();

        mapper.ifParentPresent(target, new Source("Ada", "X"));
        assertNull(target.profile);

        target.profile = new Profile();
        mapper.ifParentPresent(target, new Source("Ada", "X"));
        assertEquals("Ada", target.profile.name);
    }

    @Test
    public void ensureTargetPathCreatesDeepBeanPath() {
        TargetPathMapper mapper = CompiledNodes.of(TargetPathMapper.class);

        DeepTarget target = mapper.ensureDeep(new Source("Ada", "X"));

        assertEquals("Ada", target.a.b.c.name);
    }

    @Test
    public void targetPathSupportsComputeValues() {
        TargetPathMapper mapper = CompiledNodes.of(TargetPathMapper.class);

        InitializedTarget target = mapper.compute(new Source("Ada", "X"));

        assertEquals("AdaX", target.profile.name);
    }

    public record Source(String name, String value) {}

    public static final class Profile {
        public String name;
    }

    public static final class InitializedTarget {
        public Profile profile = new Profile();
    }

    public static final class MissingTarget {
        public Profile profile;
    }

    public static final class DeepTarget {
        public A a;
    }

    public static final class A {
        public B b;
    }

    public static final class B {
        public C c;
    }

    public static final class C {
        public String name;
    }

    @CompiledMapper
    public interface TargetPathMapper {
        @Mapping(target = "$.profile.name", source = "name")
        InitializedTarget strictCreate(Source source);

        @Mapping(target = "/profile/name", source = "name")
        InitializedTarget pointerCreate(Source source);

        @Mapping(target = "$.profile.name", source = "name")
        void strictUpdate(MissingTarget target, Source source);

        @EnsureMapping(target = "$.profile.name", source = "name")
        MissingTarget ensureCreate(Source source);

        @EnsureMapping(target = "/profile/name", source = "name")
        void ensureUpdate(MissingTarget target, Source source);

        @MappingIfParentPresent(target = "$.profile.name", source = "name")
        void ifParentPresent(MissingTarget target, Source source);

        @EnsureMapping(target = "$.a.b.c.name", source = "name")
        DeepTarget ensureDeep(Source source);

        @Mapping(target = "$.profile.name", sources = {"name", "value"}, compute = "(name, value) -> name + value")
        InitializedTarget compute(Source source);
    }
}
