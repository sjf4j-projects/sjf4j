package org.sjf4j.jdk17.processor.mapper;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.mapper.CompiledMapper;
import org.sjf4j.compiled.CompiledRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MapperNestedAutoTest {

    @Test
    public void autoMapsNestedBeanProperty() {
        AutoOnlyMapper mapper = CompiledRegistry.of(AutoOnlyMapper.class);

        Target target = mapper.map(new Source(new ChildSource("Ada")));

        assertEquals("Ada", target.child.name);
        assertNull(mapper.map(null));
        assertNull(mapper.map(new Source(null)).child);
    }

    @Test
    public void declaredMapperTakesPriorityOverAutoHelper() {
        DeclaredMapper mapper = CompiledRegistry.of(DeclaredMapper.class);

        Target target = mapper.mapWithDeclared(new Source(new ChildSource("Ada")));

        assertEquals("user:Ada", target.child.name);
    }

    @Test
    public void declaredMapperTakesPriorityForContainerValues() {
        DeclaredMapper mapper = CompiledRegistry.of(DeclaredMapper.class);

        ContainerTarget target = mapper.containersWithDeclared(new ContainerSource(
                List.of(new ChildSource("one")),
                Map.of("x", new ChildSource("map"))));

        assertEquals("user:one", target.children.get(0).name);
        assertEquals("user:map", target.byName.get("x").name);
    }

    @Test
    public void autoMapsCollectionElementsAndMapValues() {
        AutoOnlyMapper mapper = CompiledRegistry.of(AutoOnlyMapper.class);

        ContainerTarget target = mapper.containers(new ContainerSource(
                List.of(new ChildSource("one"), new ChildSource("two")),
                Map.of("x", new ChildSource("map"))));

        assertEquals("one", target.children.get(0).name);
        assertEquals("two", target.children.get(1).name);
        assertEquals("map", target.byName.get("x").name);
    }

    @Test
    public void autoMapsNestedRecordAndConstructorTargets() {
        AutoOnlyMapper mapper = CompiledRegistry.of(AutoOnlyMapper.class);

        MixedTarget target = mapper.mixed(new MixedSource(new ChildSource("record"), new ChildSource("ctor")));

        assertEquals("record", target.recordChild.name());
        assertEquals("ctor", target.ctorChild.name());
    }

    @Test
    public void autoMapsNestedBeanAndEnumOnUpdate() {
        AutoOnlyMapper mapper = CompiledRegistry.of(AutoOnlyMapper.class);
        UpdateTarget target = new UpdateTarget();

        mapper.update(target, new UpdateSource(new ChildSource("updated"), "B"));

        assertEquals("updated", target.child.name);
        assertEquals(TargetKind.B, target.kind);
    }

    @Test
    public void mapsEnumFallbacks() {
        AutoOnlyMapper mapper = CompiledRegistry.of(AutoOnlyMapper.class);

        EnumTarget fromEnum = mapper.enums(new EnumSource(SourceKind.B));
        assertEquals(TargetKind.B, fromEnum.kind);

        EnumTarget fromString = mapper.stringEnum(new StringEnumSource("A"));
        assertEquals(TargetKind.A, fromString.kind);

        assertNull(mapper.enums(new EnumSource(null)).kind);
        assertNull(mapper.stringEnum(new StringEnumSource(null)).kind);
    }

    public record Source(ChildSource child) {}
    public record ChildSource(String name) {}
    public record ContainerSource(List<ChildSource> children, Map<String, ChildSource> byName) {}
    public record MixedSource(ChildSource recordChild, ChildSource ctorChild) {}
    public record ChildRecord(String name) {}
    public record UpdateSource(ChildSource child, String kind) {}
    public record EnumSource(SourceKind kind) {}
    public record StringEnumSource(String kind) {}
    public enum SourceKind { A, B }
    public enum TargetKind { A, B }

    public static final class Target {
        public ChildTarget child;
        public Target() {}
    }

    public static final class ChildTarget {
        public String name;
        public ChildTarget() {}
    }

    public static final class ContainerTarget {
        public List<ChildTarget> children;
        public Map<String, ChildTarget> byName;
        public ContainerTarget() {}
    }

    public static final class MixedTarget {
        public ChildRecord recordChild;
        public ChildCtor ctorChild;
        public MixedTarget() {}
    }

    public static final class ChildCtor {
        private final String name;

        public ChildCtor(String name) {
            this.name = name;
        }

        public String name() { return name; }
    }

    public static final class UpdateTarget {
        public ChildTarget child;
        public TargetKind kind;
    }

    public static final class EnumTarget {
        public TargetKind kind;
        public EnumTarget() {}
    }

    @CompiledMapper
    public interface AutoOnlyMapper {
        Target map(Source source);
        ContainerTarget containers(ContainerSource source);
        MixedTarget mixed(MixedSource source);
        void update(UpdateTarget target, UpdateSource source);
        EnumTarget enums(EnumSource source);
        EnumTarget stringEnum(StringEnumSource source);
    }

    @CompiledMapper
    public interface DeclaredMapper {
        Target mapWithDeclared(Source source);
        ContainerTarget containersWithDeclared(ContainerSource source);

        default ChildTarget child(ChildSource source) {
            if (source == null) return null;
            ChildTarget target = new ChildTarget();
            target.name = "user:" + source.name();
            return target;
        }
    }
}
